<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { apiFetch } from "../composables/useApi";
import shareImage1 from "../assets/share_75758311e3041a15fcd853a8b8097ab7.png";
import shareImage2 from "../assets/share_9dd71d1a050ba4c2600a29f762d4cb4c.png";
import shareImage3 from "../assets/share_ac16df5a34ca8243db37d7dfd3f9540e.png";
import type { H5ParkingCard } from "../types";
import { buildAmapNavigationUrl, estimateTravelMinutes, formatDistance, formatRate } from "../utils/parking";

const defaultLocation = {
  lat: 31.2992,
  lon: 120.6313
};

const recommendations = ref<H5ParkingCard[]>([]);
const activeSlideIndex = ref(0);
const slideImages = [shareImage1, shareImage2, shareImage3];
let autoplayTimer: ReturnType<typeof setInterval> | null = null;

function resolveRecommendationReason(item: H5ParkingCard): string {
  if (item.availableSpaces >= 30) return "空位充足，适合优先推荐";
  if (item.distanceKm <= 2) return "距离较近，适合快速驶入";
  return "空位与路程较均衡，适合作为备选";
}

function resolveRecommendationLevel(score: number): string {
  if (score >= 4.6) return "强烈推荐";
  if (score >= 4.2) return "优先推荐";
  return "建议备选";
}

function resolveRecommendationScore(item: H5ParkingCard): number {
  const availabilityFactor = Math.min(1, item.availableSpaces / Math.max(60, item.totalSpaces * 0.3));
  const occupancyFactor = Math.max(0, 1 - item.occupancyRate / 100);
  const distanceFactor = Math.max(0, 1 - item.distanceKm / 8);
  const score = 3.6 + availabilityFactor * 0.6 + occupancyFactor * 0.45 + distanceFactor * 0.35;
  return Math.min(5, Math.round(score * 10) / 10);
}

const featuredRecommendations = computed(() =>
  recommendations.value.slice(0, 3).map((item, index) => ({
    ...item,
    rank: index + 1,
    reason: resolveRecommendationReason(item),
    travelMinutes: estimateTravelMinutes(item.distanceKm, "drive"),
    score: resolveRecommendationScore(item),
    level: resolveRecommendationLevel(resolveRecommendationScore(item)),
    imageUrl: slideImages[index % slideImages.length]
  }))
);

const activeRecommendation = computed(() => featuredRecommendations.value[activeSlideIndex.value] ?? null);

function goToSlide(index: number) {
  activeSlideIndex.value = index;
  startAutoplay();
}

function showPrevSlide() {
  if (!featuredRecommendations.value.length) return;
  activeSlideIndex.value =
    (activeSlideIndex.value - 1 + featuredRecommendations.value.length) % featuredRecommendations.value.length;
  startAutoplay();
}

function showNextSlide() {
  if (!featuredRecommendations.value.length) return;
  activeSlideIndex.value = (activeSlideIndex.value + 1) % featuredRecommendations.value.length;
  startAutoplay();
}

function startAutoplay() {
  if (autoplayTimer) {
    clearInterval(autoplayTimer);
  }

  if (featuredRecommendations.value.length <= 1) {
    autoplayTimer = null;
    return;
  }

  autoplayTimer = setInterval(() => {
    activeSlideIndex.value = (activeSlideIndex.value + 1) % featuredRecommendations.value.length;
  }, 3500);
}

async function loadData() {
  recommendations.value = await apiFetch<H5ParkingCard[]>("/api/h5/recommendations");
}

watch(featuredRecommendations, (items) => {
  if (!items.length) {
    activeSlideIndex.value = 0;
  } else if (activeSlideIndex.value >= items.length) {
    activeSlideIndex.value = 0;
  }
  startAutoplay();
});

onMounted(async () => {
  await loadData();
  startAutoplay();
});

onBeforeUnmount(() => {
  if (autoplayTimer) {
    clearInterval(autoplayTimer);
  }
});
</script>

<template>
  <div class="module-page">
    <section v-if="activeRecommendation" class="recommend-showcase">
      <button type="button" class="recommend-stage__arrow recommend-stage__arrow--prev" @click="showPrevSlide">
        <span>‹</span>
      </button>
      <article
        class="recommend-stage"
        :style="{ backgroundImage: `linear-gradient(180deg, rgba(16,32,52,0.12), rgba(16,32,52,0.45)), url(${activeRecommendation.imageUrl})` }"
      >
        <div class="recommend-stage__badge">TOP {{ activeRecommendation.rank }}</div>
        <div class="recommend-stage__content">
          <div class="recommend-stage__header">
            <div>
              <h2>{{ activeRecommendation.name }}</h2>
              <p>{{ activeRecommendation.address }}</p>
            </div>
          </div>

          <p class="recommend-stage__reason">{{ activeRecommendation.reason }}</p>

          <div class="recommend-stage__rating">
            <div class="recommend-stage__stars">
              <span class="recommend-stage__stars-base">★★★★★</span>
              <span class="recommend-stage__stars-fill" :style="{ width: `${(activeRecommendation.score / 5) * 100}%` }">★★★★★</span>
            </div>
            <strong>{{ activeRecommendation.score.toFixed(1) }}</strong>
          </div>

          <div class="recommend-stage__stats">
            <div>
              <span>空位</span>
              <strong>{{ activeRecommendation.availableSpaces }} 个</strong>
            </div>
            <div>
              <span>占用率</span>
              <strong>{{ formatRate(activeRecommendation.occupancyRate) }}</strong>
            </div>
            <div>
              <span>推荐等级</span>
              <strong>{{ activeRecommendation.level }}</strong>
            </div>
          </div>

          <div class="recommend-stage__actions">
            <RouterLink class="ghost-action" :to="`/h5/parking/${activeRecommendation.id}?from=recommendations`">查看详情</RouterLink>
            <a
              class="primary-action"
              :href="buildAmapNavigationUrl(activeRecommendation.latitude, activeRecommendation.longitude, activeRecommendation.name, defaultLocation.lat, defaultLocation.lon)"
              target="_blank"
              rel="noreferrer"
            >
              立即导航
            </a>
          </div>
        </div>

      </article>
      <button type="button" class="recommend-stage__arrow recommend-stage__arrow--next" @click="showNextSlide">
        <span>›</span>
      </button>
    </section>

    <section class="card-grid">
      <article v-for="item in featuredRecommendations" :key="item.id" class="recommend-card">
        <div class="recommend-card__head">
          <span class="info-chip">TOP {{ item.rank }}</span>
          <span class="info-chip recommend-card__rate">{{ item.score.toFixed(1) }} 分</span>
        </div>

        <h3>{{ item.name }}</h3>
        <p class="recommend-card__address">{{ item.address }}</p>

        <div class="recommend-card__rating">
          <div class="recommend-card__stars">
            <span class="recommend-card__stars-base">★★★★★</span>
            <span class="recommend-card__stars-fill" :style="{ width: `${(item.score / 5) * 100}%` }">★★★★★</span>
          </div>
        </div>

        <div class="recommend-card__stats">
          <div>
            <span>空位</span>
            <strong>{{ item.availableSpaces }}</strong>
          </div>
          <div>
            <span>占用率</span>
            <strong>{{ formatRate(item.occupancyRate) }}</strong>
          </div>
          <div>
            <span>推荐等级</span>
            <strong>{{ item.level }}</strong>
          </div>
        </div>
      </article>
    </section>
  </div>
</template>

<style scoped>
.recommend-card__address,
.recommend-stage__reason {
  margin: 0;
  color: #6a839f;
  line-height: 1.8;
}

.card-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.recommend-showcase {
  position: relative;
  display: grid;
  overflow: visible;
  margin-inline: -12px;
}

.recommend-stage {
  position: relative;
  min-height: 420px;
  padding: 28px;
  display: grid;
  align-items: end;
  border-radius: 0;
  overflow: hidden;
  background-position: center;
  background-size: cover;
  border: 0;
  box-shadow: none;
}

.recommend-stage__badge {
  position: absolute;
  top: 22px;
  left: 22px;
  min-height: 36px;
  padding: 0 16px;
  display: inline-flex;
  align-items: center;
  border-radius: 999px;
  background: linear-gradient(135deg, #f3b63f, #dc8b1d);
  color: #ffffff;
  font-weight: 800;
}

.recommend-stage__arrow {
  position: absolute;
  top: 50%;
  width: 44px;
  height: 120px;
  padding: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 0;
  border-radius: 0;
  background: transparent;
  color: #ffffff;
  box-shadow: none;
  z-index: 3;
  transform: translateY(-50%);
}

.recommend-stage__arrow span {
  width: 28px;
  height: 48px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.18);
  backdrop-filter: blur(6px);
  font-size: 26px;
  line-height: 1;
  color: #ffffff;
  text-shadow: 0 2px 10px rgba(10, 20, 34, 0.35);
}

.recommend-stage__arrow--prev {
  left: -22px;
}

.recommend-stage__arrow--next {
  right: -22px;
}

.recommend-stage__content {
  display: grid;
  gap: 18px;
  color: #ffffff;
  position: relative;
  z-index: 1;
}

.recommend-stage__header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: start;
}

.recommend-stage__header h2,
.recommend-card h3 {
  margin: 0;
  color: #18324d;
}

.recommend-stage__header h2 {
  color: #ffffff;
  font-size: 30px;
}

.recommend-stage__header p {
  margin: 8px 0 0;
  color: rgba(245, 249, 255, 0.88);
}

.recommend-stage__rating,
.recommend-card__rating {
  display: flex;
  align-items: center;
  gap: 10px;
}

.recommend-stage__stars,
.recommend-card__stars {
  position: relative;
  display: inline-block;
  line-height: 1;
  letter-spacing: 2px;
}

.recommend-stage__stars-base,
.recommend-card__stars-base {
  color: rgba(255, 255, 255, 0.35);
}

.recommend-card__stars-base {
  color: rgba(47, 111, 211, 0.18);
}

.recommend-stage__stars-fill,
.recommend-card__stars-fill {
  position: absolute;
  inset: 0 auto 0 0;
  overflow: hidden;
  white-space: nowrap;
  color: #ffd45c;
}

.recommend-stage__rating strong {
  font-size: 22px;
}

.recommend-stage__stats,
.recommend-card__stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.recommend-stage__stats div,
.recommend-card__stats div {
  padding: 14px;
  border-radius: 18px;
}

.recommend-stage__stats div {
  background: rgba(255, 255, 255, 0.14);
}

.recommend-card {
  display: grid;
  gap: 14px;
  padding: 20px;
  border-radius: 24px;
  background: #ffffff;
  border: 1px solid rgba(208, 221, 236, 0.95);
  box-shadow: 0 16px 36px rgba(55, 94, 138, 0.08);
}

.recommend-card__head,
.recommend-stage__actions {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  align-items: center;
}

.recommend-card__rate {
  white-space: nowrap;
}

.recommend-card h3 {
  font-size: 20px;
  background: #f6f9fd;
}

.recommend-card__stats span {
  color: #6a839f;
}

.recommend-card__stats strong {
  display: block;
  margin-top: 6px;
  color: #18324d;
}

.recommend-stage__stats span,
.recommend-stage__stats strong {
  color: #ffffff;
}

@media (max-width: 1024px) {
  .card-grid {
    grid-template-columns: 1fr;
  }

  .recommend-stage__stats,
  .recommend-card__stats {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .recommend-stage {
    min-height: 360px;
    padding: 22px;
  }

  .recommend-showcase {
    margin-inline: 0;
  }

  .recommend-stage__arrow {
    width: 34px;
    height: 88px;
  }

  .recommend-stage__arrow--prev {
    left: -14px;
  }

  .recommend-stage__arrow--next {
    right: -14px;
  }

  .recommend-stage__arrow span {
    font-size: 22px;
  }

  .recommend-stage__header,
  .recommend-stage__actions {
    flex-direction: column;
    align-items: flex-start;
  }

  .ghost-action,
  .primary-action {
    width: 100%;
  }

  .recommend-stage__header h2 {
    font-size: 24px;
  }
}
</style>
