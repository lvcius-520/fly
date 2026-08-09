<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { apiFetch } from "../composables/useApi";
import type { H5ParkingCard } from "../types";
import { buildAmapNavigationUrl, estimateTravelMinutes, formatDistance } from "../utils/parking";

type UserLocation = {
  lat: number;
  lon: number;
  source: "gps" | "demo";
};

const defaultLocation: UserLocation = {
  lat: 31.2992,
  lon: 120.6313,
  source: "demo"
};

const nearby = ref<H5ParkingCard[]>([]);
const userLocation = ref<UserLocation>(defaultLocation);
const locating = ref(false);

async function loadData(location: UserLocation = userLocation.value) {
  const query = `?lat=${location.lat}&lon=${location.lon}&radiusKm=12`;
  nearby.value = await apiFetch<H5ParkingCard[]>(`/api/h5/nearby-parking${query}`);
}

const routePlans = computed(() =>
  nearby.value.slice(0, 6).map((item, index) => ({
    ...item,
    sequence: index + 1,
    driveMinutes: estimateTravelMinutes(item.distanceKm, "drive"),
    walkMinutes: estimateTravelMinutes(item.distanceKm, "walk"),
    navigationUrl: buildAmapNavigationUrl(
      item.latitude,
      item.longitude,
      item.name,
      userLocation.value.lat,
      userLocation.value.lon
    )
  }))
);

const navigationMetrics = computed(() => ({
  total: routePlans.value.length,
  nearest: routePlans.value[0]?.name ?? "-",
  highestAvailability: Math.max(...routePlans.value.map((item) => item.availableSpaces), 0)
}));

const locationLabel = computed(() =>
  userLocation.value.source === "gps" ? "已获取当前位置" : "当前使用演示定位点"
);

async function locateUser() {
  if (!navigator.geolocation) {
    await loadData(defaultLocation);
    return;
  }

  locating.value = true;
  navigator.geolocation.getCurrentPosition(
    async (position) => {
      userLocation.value = {
        lat: position.coords.latitude,
        lon: position.coords.longitude,
        source: "gps"
      };
      locating.value = false;
      await loadData(userLocation.value);
    },
    async () => {
      locating.value = false;
      userLocation.value = defaultLocation;
      await loadData(defaultLocation);
    },
    { enableHighAccuracy: true, timeout: 8000, maximumAge: 60000 }
  );
}

onMounted(async () => {
  await loadData(defaultLocation);
  await locateUser();
});
</script>

<template>
  <div class="module-page">
    <section class="module-hero">
      <div>
        <p class="module-hero__eyebrow">用户端模块</p>
        <h1>附近停车导航</h1>
        <p class="module-hero__lead">围绕当前位置查看附近停车场，先判断距离与空位，再一键跳转地图导航。</p>
      </div>
      <div class="module-hero__aside">
        <article>
          <span>定位状态</span>
          <strong>{{ locationLabel }}</strong>
        </article>
        <article>
          <span>附近样本</span>
          <strong>{{ navigationMetrics.total }}</strong>
        </article>
        <article>
          <span>最近车场</span>
          <strong>{{ navigationMetrics.nearest }}</strong>
        </article>
        <article>
          <span>最大空位</span>
          <strong>{{ navigationMetrics.highestAvailability }} 个</strong>
        </article>
        <button :disabled="locating" @click="locateUser">{{ locating ? "定位中..." : "刷新附近导航" }}</button>
      </div>
    </section>

    <div class="route-layout">
      <aside class="route-panel surface-card">
        <h2>导航提示</h2>
        <article class="route-panel__tip surface-card surface-card--soft">
          <strong>先看距离，再决定是否前往</strong>
          <p>系统按距离从近到远排序，先帮你筛掉绕路选项。</p>
        </article>
        <article class="route-panel__tip surface-card surface-card--soft">
          <strong>优先推荐空位更多的近场</strong>
          <p>把距离和空位一起看，避免只近但无位的车场干扰判断。</p>
        </article>
        <article class="route-panel__tip surface-card surface-card--soft">
          <strong>定位失败不影响演示</strong>
          <p>浏览器定位不可用时，会自动回退到演示定位点继续展示。</p>
        </article>
      </aside>

      <div class="route-list">
        <article v-for="item in routePlans" :key="item.id" class="route-card">
          <div class="route-card__meta">
            <span class="info-chip route-card__seq">路线 {{ item.sequence }}</span>
            <div>
              <h3>{{ item.name }}</h3>
              <p>{{ item.address }}</p>
            </div>
          </div>

          <div class="route-card__stats">
            <span>距离 {{ formatDistance(item.distanceKm) }}</span>
            <span>驾车 {{ item.driveMinutes }} 分钟</span>
            <span>步行 {{ item.walkMinutes }} 分钟</span>
            <span>空位 {{ item.availableSpaces }} 个</span>
          </div>

          <div class="route-card__actions">
            <RouterLink class="ghost-action" :to="`/h5/parking/${item.id}?from=navigation`">查看车场详情</RouterLink>
            <a class="primary-action" :href="item.navigationUrl" target="_blank" rel="noreferrer">规划路线并导航</a>
          </div>
        </article>
      </div>
    </div>
  </div>
</template>

<style scoped>
.route-card__meta p,
.route-panel__tip p {
  margin: 0;
  color: #6a839f;
  line-height: 1.8;
}

.route-layout {
  display: grid;
  grid-template-columns: 320px 1fr;
  gap: 14px;
}
.route-panel,
.route-card {
  padding: 20px;
  border-radius: 24px;
  border: 1px solid rgba(208, 221, 236, 0.95);
  box-shadow: 0 16px 36px rgba(55, 94, 138, 0.08);
}

.route-panel {
  display: grid;
  gap: 12px;
  align-self: start;
}

.route-panel h2,
.route-card h3 {
  margin: 0;
  color: #18324d;
}

.route-panel__tip {
  display: grid;
  gap: 6px;
}

.route-list {
  display: grid;
  gap: 12px;
}

.route-card {
  display: grid;
  gap: 14px;
}

.route-card__meta {
  display: flex;
  gap: 14px;
  align-items: flex-start;
}

.route-card__seq {
  flex-shrink: 0;
}

.route-card__stats {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.route-card__stats span {
  padding: 8px 12px;
  border-radius: 999px;
  background: #f6f9fd;
  color: #6a839f;
  font-size: 12px;
}

.route-card__actions {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  align-items: center;
}

@media (max-width: 1024px) {
  .route-layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .route-card__actions {
    flex-direction: column;
    align-items: flex-start;
  }

  .ghost-action,
  .primary-action {
    width: 100%;
  }
}
</style>
