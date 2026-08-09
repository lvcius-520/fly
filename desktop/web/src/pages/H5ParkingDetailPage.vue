<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRoute } from "vue-router";
import PanelCard from "../components/PanelCard.vue";
import { apiFetch } from "../composables/useApi";
import type { ParkingLotDetailResponse } from "../types";
import { buildAmapNavigationUrl, estimateTravelMinutes, formatDistance, formatRate, formatTime } from "../utils/parking";

const route = useRoute();
const detail = ref<ParkingLotDetailResponse | null>(null);
const currentPosition = {
  lat: 31.2992,
  lon: 120.6313
};

const freeSpaces = computed(() => detail.value?.spaces.filter((item) => item.status === "free") ?? []);
const opinions = computed(() => detail.value?.opinions ?? []);
const latestOpinion = computed(() => opinions.value[0] ?? null);
const opinionSummary = computed(() => {
  const source = opinions.value;
  const total = source.length;
  if (!total) {
    return {
      average: 0,
      positive: 0,
      neutral: 0,
      focus: 0
    };
  }
  const average = source.reduce((sum, item) => sum + item.rating, 0) / total;
  return {
    average,
    positive: source.filter((item) => item.sentiment === "正向").length,
    neutral: source.filter((item) => item.sentiment === "总体较好").length,
    focus: source.filter((item) => item.sentiment === "关注项").length
  };
});

const backTarget = computed(() => {
  const from = String(route.query.from ?? "");
  if (from === "navigation") return "/h5/navigation";
  if (from === "recommendations") return "/h5/recommendations";
  return "/h5/recommendations";
});
const backLabel = computed(() => {
  const from = String(route.query.from ?? "");
  if (from === "navigation") return "返回附近停车导航";
  if (from === "recommendations") return "返回推荐停车场";
  return "返回推荐停车场";
});
const navigationUrl = computed(() => {
  if (!detail.value) return "#";
  return buildAmapNavigationUrl(
    detail.value.parkingLot.latitude,
    detail.value.parkingLot.longitude,
    detail.value.parkingLot.name,
    currentPosition.lat,
    currentPosition.lon
  );
});

const travelMinutes = computed(() => {
  if (!detail.value) {
    return {
      distance: 0,
      drive: 0,
      walk: 0
    };
  }
  const distanceKm = Math.sqrt(
    Math.pow(detail.value.parkingLot.latitude - currentPosition.lat, 2) +
      Math.pow(detail.value.parkingLot.longitude - currentPosition.lon, 2)
  ) * 111;
  return {
    distance: distanceKm,
    drive: estimateTravelMinutes(distanceKm, "drive"),
    walk: estimateTravelMinutes(distanceKm, "walk")
  };
});

const opinionModuleLink = computed(() =>
  detail.value ? `/h5/opinions?parkingLotId=${detail.value.parkingLot.id}` : "/h5/opinions"
);

async function loadData() {
  detail.value = await apiFetch<ParkingLotDetailResponse>(`/api/h5/parking-lots/${route.params.id}`);
}

onMounted(loadData);
</script>

<template>
  <div v-if="detail" class="module-page detail-page">
    <section class="module-hero">
      <div class="detail-hero__main">
        <p class="module-hero__eyebrow">用户端模块</p>
        <h1>{{ detail.parkingLot.name }}</h1>
        <p class="module-hero__lead">{{ detail.parkingLot.address }}</p>

        <div class="detail-hero__chips">
          <span class="info-chip">驾车 {{ travelMinutes.drive }} 分钟</span>
          <span class="info-chip">步行 {{ travelMinutes.walk }} 分钟</span>
          <span class="info-chip">约 {{ formatDistance(travelMinutes.distance) }}</span>
          <span class="info-chip">最近巡检 {{ formatTime(detail.parkingLot.lastInspectionAt) }}</span>
        </div>

        <div class="action-row">
          <RouterLink :to="backTarget" class="ghost-action">{{ backLabel }}</RouterLink>
          <a class="primary-action" :href="navigationUrl" target="_blank" rel="noreferrer">规划路线并导航</a>
        </div>
      </div>

      <div class="module-hero__aside">
        <article>
          <span>当前空位</span>
          <strong>{{ detail.parkingLot.availableSpaces }}</strong>
        </article>
        <article>
          <span>总车位</span>
          <strong>{{ detail.parkingLot.totalSpaces }}</strong>
        </article>
        <article>
          <span>占用率</span>
          <strong>{{ formatRate(detail.parkingLot.occupancyRate) }}</strong>
        </article>
        <article>
          <span>综合评分</span>
          <strong>{{ opinionSummary.average ? opinionSummary.average.toFixed(1) : "-" }}</strong>
        </article>
      </div>
    </section>

    <section class="detail-grid">
      <PanelCard title="车位分布">
        <div class="space-grid">
          <article v-for="space in detail.spaces" :key="space.id" class="space-box" :data-status="space.status">
            <strong>{{ space.code }}</strong>
            <span>{{ space.status === "free" ? "空闲" : "占用" }}</span>
          </article>
        </div>
      </PanelCard>

      <PanelCard title="导航提示">
        <div class="hint-list">
          <article class="surface-card surface-card--soft">
            <strong>建议优先前往</strong>
            <p>当前可用车位 {{ freeSpaces.length }} 个，可优先前往空位更充足的区域。</p>
          </article>
          <article class="surface-card surface-card--soft">
            <strong>路线建议</strong>
            <p>驾车约 {{ travelMinutes.drive }} 分钟，步行约 {{ travelMinutes.walk }} 分钟。</p>
          </article>
          <article class="surface-card surface-card--soft">
            <strong>导航入口</strong>
            <p>支持直接跳转第三方地图，适合车主快速出发。</p>
          </article>
          <a class="primary-action detail-panel__link" :href="navigationUrl" target="_blank" rel="noreferrer">跳转第三方地图导航</a>
        </div>
      </PanelCard>
    </section>

    <PanelCard title="舆情评论模块">
      <div class="opinion-layout">
        <section class="opinion-summary">
          <article>
            <span>综合评分</span>
            <strong>{{ opinionSummary.average ? opinionSummary.average.toFixed(1) : "-" }}</strong>
          </article>
          <article>
            <span>正向</span>
            <strong>{{ opinionSummary.positive }}</strong>
          </article>
          <article>
            <span>总体较好</span>
            <strong>{{ opinionSummary.neutral }}</strong>
          </article>
          <article>
            <span>关注项</span>
            <strong>{{ opinionSummary.focus }}</strong>
          </article>
        </section>

        <section class="opinion-entry surface-card surface-card--soft">
          <strong>独立评论入口</strong>
          <p>该停车场的评论查看与发布已拆分为独立模块，便于统一浏览和集中提交。</p>
          <RouterLink :to="opinionModuleLink" class="primary-action opinion-entry__link">进入舆情评论模块</RouterLink>
        </section>
      </div>

      <div v-if="latestOpinion" class="opinion-list">
        <article class="opinion-card">
          <div class="opinion-card__head">
            <div>
              <strong>{{ latestOpinion.authorName }}</strong>
              <span>{{ latestOpinion.source }}</span>
            </div>
            <b>{{ latestOpinion.rating.toFixed(1) }}</b>
          </div>
          <div class="opinion-card__meta">
            <span>{{ latestOpinion.topic }}</span>
            <span>{{ latestOpinion.sentiment }}</span>
            <small>{{ formatTime(latestOpinion.createdAt) }}</small>
          </div>
          <p>{{ latestOpinion.content }}</p>
        </article>
      </div>
    </PanelCard>
  </div>
</template>

<style scoped>
.detail-page {
  color: #10233f;
}

.detail-hero__main {
  display: grid;
  gap: 16px;
}

.detail-hero__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.detail-grid {
  display: grid;
  grid-template-columns: 1.05fr 0.95fr;
  gap: 18px;
}

.space-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.space-box {
  padding: 14px;
  border-radius: 18px;
  background: rgba(16, 52, 85, 0.06);
}

.space-box[data-status="free"] {
  background: rgba(94, 234, 212, 0.14);
}

.space-box[data-status="occupied"] {
  background: rgba(251, 146, 60, 0.14);
}

.space-box span,
.opinion-summary span {
  color: #5f738e;
  font-size: 12px;
}

.space-box strong,
.opinion-summary strong {
  display: block;
  margin-top: 6px;
}

.hint-list,
.opinion-list {
  display: grid;
  gap: 12px;
}

.hint-list article {
  display: grid;
  gap: 6px;
}

.hint-list p {
  margin: 0;
  color: #5f738e;
}

.detail-panel__link {
  width: 100%;
}

.opinion-layout {
  display: grid;
  gap: 16px;
  grid-template-columns: 0.9fr 1.1fr;
  margin-bottom: 14px;
}

.opinion-summary {
  display: grid;
  gap: 10px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.opinion-summary article,
.opinion-card {
  padding: 14px;
  border-radius: 18px;
  background: rgba(16, 52, 85, 0.06);
}

.opinion-entry {
  display: grid;
  gap: 12px;
  padding: 16px;
  border-radius: 20px;
  box-shadow: none;
}

.opinion-card__head,
.opinion-card__meta {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  align-items: center;
  flex-wrap: wrap;
}

.opinion-card__head span,
.opinion-card__meta span {
  display: inline-flex;
  align-items: center;
  min-height: 26px;
  padding: 0 10px;
  border-radius: 999px;
  background: #edf5ff;
  color: #2b68b2;
  font-size: 12px;
  font-weight: 700;
}

.opinion-card__meta small {
  color: #6a839f;
}

.opinion-card p,
.opinion-entry p {
  margin: 8px 0 0;
  color: #5f738e;
}

.opinion-entry__link {
  width: 100%;
}

@media (max-width: 1024px) {
  .detail-grid,
  .opinion-layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .space-grid,
  .opinion-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .action-row {
    align-items: stretch;
  }

  .ghost-action,
  .primary-action {
    width: 100%;
  }
}
</style>
