<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from "vue";
import { useRoute } from "vue-router";
import PanelCard from "../components/PanelCard.vue";
import { apiFetch } from "../composables/useApi";
import { useAuth } from "../composables/useAuth";
import shareImage1 from "../assets/share_75758311e3041a15fcd853a8b8097ab7.png";
import shareImage2 from "../assets/share_9dd71d1a050ba4c2600a29f762d4cb4c.png";
import shareImage3 from "../assets/share_ac16df5a34ca8243db37d7dfd3f9540e.png";
import shareImage4 from "../assets/share_b5b3c7e352bfdb331976d779a37bb27e.png";
import shareImage5 from "../assets/share_f5484c0c10412113b6ed246c79a031de.png";
import type { ParkingLot, ParkingOpinion } from "../types";
import { formatTime } from "../utils/parking";

type InsightDetail = {
  id: string;
  title: string;
  subtitle: string;
  level: string;
  scoreLabel: string;
  scoreValue: string;
  summary: string;
  details: string[];
  imageUrls?: string[];
};

const route = useRoute();
const { currentUser } = useAuth();

const parkingLots = ref<ParkingLot[]>([]);
const opinions = ref<ParkingOpinion[]>([]);
const loading = ref(false);
const submitting = ref(false);
const submitMessage = ref("");
const submitError = ref("");
const filterParkingLotId = ref("");
const rankingKeyword = ref("");
const selectedInsight = ref<InsightDetail | null>(null);
const shareImages = [shareImage1, shareImage2, shareImage3, shareImage4, shareImage5];

const opinionTopics = ["停车效率", "停车场收费", "周围环境", "夜间导视", "步行接驳", "余位更新", "导航准确"];

const opinionForm = reactive({
  parkingLotId: "",
  authorName: "",
  topic: "停车效率",
  rating: 4,
  content: "",
  imageUrls: [] as string[]
});

const filteredOpinions = computed(() =>
  filterParkingLotId.value
    ? opinions.value.filter((item) => item.parkingLotId === filterParkingLotId.value)
    : opinions.value
);

const parkingScoreCards = computed(() =>
  parkingLots.value
    .map((lot) => {
      const lotOpinions = opinions.value.filter((item) => item.parkingLotId === lot.id);
      const total = lotOpinions.length;
      const average = total
        ? lotOpinions.reduce((sum, item) => sum + item.rating, 0) / total
        : 0;
      const positive = lotOpinions.filter((item) => item.sentiment === "正向").length;
      const concern = lotOpinions.filter((item) => item.sentiment === "关注项").length;
      const topicAverage = (topic: string, fallback: number) => {
        const matches = lotOpinions.filter((item) => item.topic === topic);
        if (!matches.length) return fallback;
        return matches.reduce((sum, item) => sum + item.rating, 0) / matches.length;
      };
      const environmentScore = Math.round(topicAverage("周围环境", Math.max(3.6, average || 3.8)) * 20);
      const chargeScore = Math.round(topicAverage("停车场收费", Math.max(3.2, average || 3.6)) * 20);
      const spacesScore = Math.round(
        Math.max(3.4, Math.min(5, 2.8 + (lot.availableSpaces / Math.max(lot.totalSpaces, 1)) * 2.4)) * 20
      );
      const overallPercent = Math.round((average || 0) * 20);
      return {
        id: lot.id,
        name: lot.name,
        average,
        overallPercent,
        total,
        positive,
        concern,
        environmentScore,
        chargeScore,
        spacesScore,
        ringStyle: {
          background: `conic-gradient(#2f6fd3 0 ${overallPercent}%, rgba(226, 234, 244, 0.95) ${overallPercent}% 100%)`
        },
        active: filterParkingLotId.value === lot.id
      };
    })
    .sort((a, b) => b.average - a.average || b.total - a.total)
);

const scrollingParkingScores = computed(() =>
  parkingScoreCards.value.length > 1 ? [...parkingScoreCards.value, ...parkingScoreCards.value] : parkingScoreCards.value
);

const allTopicStats = computed(() => {
  const groups = new Map<string, { topic: string; count: number; avgRating: number; heat: number; lots: Set<string>; examples: ParkingOpinion[] }>();

  opinions.value.forEach((item) => {
    const existing = groups.get(item.topic) ?? {
      topic: item.topic,
      count: 0,
      avgRating: 0,
      heat: 0,
      lots: new Set<string>(),
      examples: []
    };
    existing.count += 1;
    existing.avgRating += item.rating;
    existing.heat += item.sentiment === "关注项" ? 1.35 : item.sentiment === "总体较好" ? 1 : 0.8;
    existing.lots.add(item.parkingLotName);
    if (existing.examples.length < 3) {
      existing.examples.push(item);
    }
    groups.set(item.topic, existing);
  });

  return Array.from(groups.values())
    .map((item) => ({
      ...item,
      avgRating: item.count ? item.avgRating / item.count : 0
    }))
    .sort((a, b) => b.heat - a.heat || b.count - a.count);
});

const rankingList = computed(() => {
  const source = allTopicStats.value.map((item, index) => ({
    id: item.topic,
    rank: index + 1,
    title: item.topic,
    mentionCount: item.count,
    score: Math.round(item.heat * 22),
    summary:
      item.avgRating >= 4.2
        ? "该话题以正向体验为主，常与停车效率、环境和导航便利性一起出现。"
        : "该话题存在较多关注项，用户更在意高峰体验、导视清晰度或收费规则。",
    relatedLots: Array.from(item.lots).slice(0, 3),
    examples: item.examples,
    level: item.avgRating >= 4.2 ? "正向热点" : "关注热点"
  }));
  const maxScore = Math.max(...source.map((item) => item.score), 1);
  return source.map((item) => ({
    ...item,
    width: `${Math.max(30, Math.round((item.score / maxScore) * 100))}%`,
    tone: item.rank === 1 ? "gold" : item.rank === 2 ? "silver" : item.rank === 3 ? "bronze" : "default"
  }));
});

const filteredRankingList = computed(() => {
  const keyword = rankingKeyword.value.trim().toLowerCase();
  if (!keyword) {
    return rankingList.value;
  }

  const matched = rankingList.value.filter((item) => {
    const haystack = [
      item.title,
      item.summary,
      ...item.relatedLots,
      ...item.examples.map((example) => `${example.authorName} ${example.parkingLotName} ${example.content}`)
    ]
      .join(" ")
      .toLowerCase();
    return haystack.includes(keyword);
  });

  return matched.map((item, index) => ({
    ...item,
    rank: index + 1,
    tone: index === 0 ? "gold" : index === 1 ? "silver" : index === 2 ? "bronze" : "default"
  }));
});

const barrageTracks = computed(() => {
  const source = filteredOpinions.value.slice(0, 10);
  if (!source.length) {
    return [[], []] as ParkingOpinion[][];
  }
  const firstRow = source.filter((_, index) => index % 2 === 0);
  const secondRow = source.filter((_, index) => index % 2 === 1);
  return [
    firstRow.length > 1 ? [...firstRow, ...firstRow] : firstRow,
    secondRow.length > 1 ? [...secondRow, ...secondRow] : secondRow
  ] as ParkingOpinion[][];
});

function applyRouteParkingLot() {
  const routeParkingLotId = String(route.query.parkingLotId ?? "").trim();
  if (!routeParkingLotId) return;
  filterParkingLotId.value = routeParkingLotId;
  opinionForm.parkingLotId = routeParkingLotId;
}

function syncOpinionParkingLot(value: string) {
  opinionForm.parkingLotId = value;
  filterParkingLotId.value = value;
}

function openInsight(detail: InsightDetail) {
  selectedInsight.value = detail;
}

function openTopicDetail(topicId: string) {
  const topic = rankingList.value.find((item) => item.id === topicId);
  if (!topic) return;
  openInsight({
    id: topic.id,
    title: topic.title,
    subtitle: "热门话题详情",
    level: topic.level,
    scoreLabel: "综合热度",
    scoreValue: String(topic.score),
    summary: topic.summary,
    details: [
      `提及次数：${topic.mentionCount}`,
      `关联停车场：${topic.relatedLots.join("、") || "暂无"}`,
      ...topic.examples.map((item) => `${item.authorName}：${item.content}`)
    ],
    imageUrls: []
  });
}

function openReviewDetail(review: ParkingOpinion) {
  openInsight({
    id: review.id,
    title: `${review.parkingLotName} · ${review.topic}`,
    subtitle: `${review.authorName} / ${review.source}`,
    level: review.sentiment,
    scoreLabel: "用户评分",
    scoreValue: review.rating.toFixed(1),
    summary: review.content,
    details: [
      `停车场：${review.parkingLotName}`,
      `评论时间：${formatTime(review.createdAt)}`,
      `话题：${review.topic}`,
      `来源：${review.source}`,
      `图片数量：${review.imageUrls?.length ?? 0}`
    ],
    imageUrls: review.imageUrls ?? []
  });
}

function readFileAsDataUrl(file: File) {
  return new Promise<string>((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result ?? ""));
    reader.onerror = () => reject(new Error("图片读取失败"));
    reader.readAsDataURL(file);
  });
}

async function handleImageChange(event: Event) {
  const input = event.target as HTMLInputElement | null;
  const files = Array.from(input?.files ?? []);
  if (!files.length) {
    return;
  }

  const remaining = Math.max(0, 3 - opinionForm.imageUrls.length);
  if (remaining <= 0) {
    submitError.value = "最多上传 3 张图片";
    if (input) input.value = "";
    return;
  }

  try {
    const acceptedFiles = files.slice(0, remaining);
    for (const file of acceptedFiles) {
      if (!file.type.startsWith("image/")) {
        throw new Error("只能上传图片文件");
      }
      if (file.size > 1.5 * 1024 * 1024) {
        throw new Error("单张图片请控制在 1.5MB 以内");
      }
    }

    const imageUrls = await Promise.all(acceptedFiles.map(readFileAsDataUrl));
    opinionForm.imageUrls = [...opinionForm.imageUrls, ...imageUrls];
    submitError.value = "";
  } catch (error) {
    submitError.value = error instanceof Error ? error.message : "图片上传失败";
  } finally {
    if (input) input.value = "";
  }
}

function removeImage(index: number) {
  opinionForm.imageUrls = opinionForm.imageUrls.filter((_, currentIndex) => currentIndex !== index);
}

function resolveOpinionStripImage(review: ParkingOpinion, seed: number) {
  if (review.imageUrls?.length) {
    return review.imageUrls[0];
  }
  return shareImages[seed % shareImages.length];
}

async function loadData() {
  loading.value = true;
  try {
    const [parkingLotData, opinionData] = await Promise.all([
      apiFetch<ParkingLot[]>("/api/parking-lots"),
      apiFetch<ParkingOpinion[]>("/api/analysis/opinions?limit=120")
    ]);
    parkingLots.value = parkingLotData;
    opinions.value = opinionData;

    applyRouteParkingLot();

    if (!opinionForm.parkingLotId && parkingLotData.length > 0) {
      opinionForm.parkingLotId = parkingLotData[0].id;
    }
  } finally {
    loading.value = false;
  }
}

async function submitOpinion() {
  submitMessage.value = "";
  submitError.value = "";

  if (!opinionForm.parkingLotId) {
    submitError.value = "请先选择停车场";
    return;
  }

  if (opinionForm.content.trim().length < 4) {
    submitError.value = "评论内容至少填写 4 个字";
    return;
  }

  submitting.value = true;
  try {
    const created = await apiFetch<ParkingOpinion>(`/api/h5/parking-lots/${opinionForm.parkingLotId}/opinions`, {
      method: "POST",
      body: JSON.stringify({
        authorName: opinionForm.authorName.trim() || currentUser.value?.displayName || currentUser.value?.username || "",
        topic: opinionForm.topic,
        rating: opinionForm.rating,
        content: opinionForm.content.trim(),
        imageUrls: opinionForm.imageUrls
      })
    });

    opinions.value = [created, ...opinions.value];
    filterParkingLotId.value = opinionForm.parkingLotId;
    opinionForm.content = "";
    opinionForm.rating = 4;
    opinionForm.topic = "停车效率";
    opinionForm.imageUrls = [];
    submitMessage.value = "评论已提交，已同步到管理端舆情分析";
  } catch (error) {
    submitError.value = error instanceof Error ? error.message : "评论提交失败";
  } finally {
    submitting.value = false;
  }
}

watch(
  () => route.query.parkingLotId,
  () => {
    applyRouteParkingLot();
  }
);

onMounted(loadData);
</script>

<template>
  <div class="module-page opinions-page">
    <div class="panorama-grid">
      <section class="control-panel">
        <PanelCard title="停车场综合评分">
          <div class="score-ticker">
            <div class="score-ticker__track" :class="{ 'score-ticker__track--static': parkingScoreCards.length <= 1 }">
              <article
                v-for="(item, index) in scrollingParkingScores"
                :key="`${item.id}-${index}`"
                class="score-card"
                :class="{ 'score-card--active': item.active }"
                @click="filterParkingLotId = item.id"
              >
                <div class="score-card__head">
                  <strong>{{ item.name }}</strong>
                  <span>{{ item.average ? item.average.toFixed(1) : "-" }} 分</span>
                </div>
                <div class="score-card__ring" :style="item.ringStyle">
                  <div class="score-card__ring-core">
                    <b>{{ item.average ? item.average.toFixed(1) : "-" }}</b>
                    <small>综合评分</small>
                  </div>
                </div>
                <div class="score-card__meta">
                  <span>评论 {{ item.total }}</span>
                  <span>正向 {{ item.positive }}</span>
                  <span>关注 {{ item.concern }}</span>
                </div>
                <div class="score-card__details">
                  <article>
                    <div>
                      <span>环境</span>
                      <strong>{{ item.environmentScore }}</strong>
                    </div>
                    <div class="score-card__bar">
                      <i :style="{ width: `${item.environmentScore}%` }"></i>
                    </div>
                  </article>
                  <article>
                    <div>
                      <span>收费</span>
                      <strong>{{ item.chargeScore }}</strong>
                    </div>
                    <div class="score-card__bar">
                      <i :style="{ width: `${item.chargeScore}%` }"></i>
                    </div>
                  </article>
                  <article>
                    <div>
                      <span>车位数量</span>
                      <strong>{{ item.spacesScore }}</strong>
                    </div>
                    <div class="score-card__bar">
                      <i :style="{ width: `${item.spacesScore}%` }"></i>
                    </div>
                  </article>
                </div>
              </article>
            </div>
          </div>
        </PanelCard>
      </section>

      <section class="center-column">
        <PanelCard title="实时弹幕">
          <div v-if="loading" class="opinion-empty">正在加载评论...</div>
          <div v-else-if="!filteredOpinions.length" class="opinion-empty">当前范围内还没有可展示的评论。</div>
          <div v-else class="barrage-board">
            <div class="barrage-board__glow barrage-board__glow--left"></div>
            <div class="barrage-board__glow barrage-board__glow--right"></div>
            <div class="barrage-lane barrage-lane--top">
              <div class="barrage-track barrage-track--left" :class="{ 'barrage-track--static': barrageTracks[0].length <= 1 }">
                <button
                  v-for="(item, index) in barrageTracks[0]"
                  :key="`${item.id}-left-${index}`"
                  class="barrage-item"
                  @click="openReviewDetail(item)"
                >
                  <img class="barrage-item__image" :src="resolveOpinionStripImage(item, index)" alt="评论配图" />
                  <strong>{{ item.topic }}</strong>
                  <span>{{ item.authorName }}</span>
                  <p>{{ item.content }}</p>
                </button>
              </div>
            </div>
            <div class="barrage-lane barrage-lane--bottom">
              <div class="barrage-track barrage-track--right" :class="{ 'barrage-track--static': barrageTracks[1].length <= 1 }">
                <button
                  v-for="(item, index) in barrageTracks[1]"
                  :key="`${item.id}-right-${index}`"
                  class="barrage-item barrage-item--soft"
                  @click="openReviewDetail(item)"
                >
                  <img class="barrage-item__image" :src="resolveOpinionStripImage(item, index + 7)" alt="评论配图" />
                  <strong>{{ item.parkingLotName }}</strong>
                  <span>{{ item.rating.toFixed(1) }} 分</span>
                  <p>{{ item.content }}</p>
                </button>
              </div>
            </div>
          </div>
        </PanelCard>

        <PanelCard title="评论发布">
          <div class="comment-toolbar surface-card surface-card--soft">
            <label>
              评论内容
              <input
                v-model="opinionForm.content"
                class="comment-toolbar__input"
                placeholder="请输入评论内容"
              />
            </label>
          </div>

          <div class="opinion-form opinion-form--compact">
            <select
              :value="opinionForm.parkingLotId"
              class="opinion-form__select opinion-form__select--lot"
              @change="syncOpinionParkingLot(($event.target as HTMLSelectElement).value)"
            >
              <option value="" disabled>选择停车场</option>
              <option v-for="item in parkingLots" :key="item.id" :value="item.id">{{ item.name }}</option>
            </select>
            <select v-model="opinionForm.topic" class="opinion-form__select">
              <option v-for="item in opinionTopics" :key="item" :value="item">{{ item }}</option>
            </select>
            <select v-model.number="opinionForm.rating" class="opinion-form__select opinion-form__select--score">
              <option v-for="score in [5, 4, 3, 2, 1]" :key="score" :value="score">{{ score }} 分</option>
            </select>
            <select
              :value="filterParkingLotId"
              class="opinion-form__select opinion-form__select--lot"
              @change="filterParkingLotId = ($event.target as HTMLSelectElement).value"
            >
              <option value="">查看全部车场</option>
              <option v-for="item in parkingLots" :key="`filter-${item.id}`" :value="item.id">{{ item.name }}</option>
            </select>
            <label class="opinion-form__upload">
              <input type="file" accept="image/*" multiple @change="handleImageChange" />
              <span>上传图片</span>
            </label>
            <button class="opinion-form__send" :disabled="submitting" @click="submitOpinion">
              {{ submitting ? "发送中" : "发送" }}
            </button>
          </div>
          <div v-if="opinionForm.imageUrls.length" class="opinion-images">
            <article v-for="(imageUrl, index) in opinionForm.imageUrls" :key="`${imageUrl}-${index}`" class="opinion-image-chip">
              <img :src="imageUrl" alt="评论图片预览" />
              <button @click="removeImage(index)">移除</button>
            </article>
          </div>
          <p v-if="submitMessage" class="opinion-message opinion-message--success">{{ submitMessage }}</p>
          <p v-if="submitError" class="opinion-message opinion-message--error">{{ submitError }}</p>
        </PanelCard>
      </section>

      <section class="topic-column">
        <PanelCard title="热门话题">
          <div class="ranking-panel">
            <div class="ranking-search">
              <input v-model="rankingKeyword" type="text" placeholder="搜索话题、停车场或评论内容" />
            </div>
            <div v-if="!filteredRankingList.length" class="opinion-empty">没有找到相关话题评论。</div>
            <div v-else class="ranking-list">
              <button
                v-for="item in filteredRankingList"
                :key="item.id"
                class="ranking-item"
                :class="`ranking-item--${item.tone}`"
                @click="openTopicDetail(item.id)"
              >
                <div class="ranking-item__head">
                  <div class="ranking-item__meta">
                    <strong class="ranking-item__badge">{{ item.rank }}</strong>
                    <span>{{ item.score }}</span>
                  </div>
                  <div class="ranking-item__topic">{{ item.title }}</div>
                  <div class="ranking-item__track">
                    <span class="ranking-item__fill" :style="{ width: item.width }"></span>
                  </div>
                </div>
              </button>
            </div>
          </div>
        </PanelCard>
      </section>
    </div>

    <div v-if="selectedInsight" class="insight-modal" @click.self="selectedInsight = null">
      <div class="insight-modal__panel">
        <div class="insight-modal__head">
          <div>
            <small>{{ selectedInsight.subtitle }}</small>
            <strong>{{ selectedInsight.title }}</strong>
          </div>
          <button class="insight-modal__close" @click="selectedInsight = null">关闭</button>
        </div>

        <div class="insight-modal__summary">
          <article>
            <span>{{ selectedInsight.level }}</span>
            <b>{{ selectedInsight.scoreValue }}</b>
            <small>{{ selectedInsight.scoreLabel }}</small>
          </article>
          <p>{{ selectedInsight.summary }}</p>
        </div>

        <div class="insight-modal__details">
          <p v-for="item in selectedInsight.details" :key="item">{{ item }}</p>
        </div>

        <div v-if="selectedInsight.imageUrls?.length" class="insight-modal__gallery">
          <img
            v-for="(imageUrl, index) in selectedInsight.imageUrls"
            :key="`${imageUrl}-${index}`"
            :src="imageUrl"
            alt="评论图片"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.opinions-page,
.panorama-grid,
.control-panel,
.center-column,
.topic-column,
.ranking-list {
  display: grid;
  gap: 18px;
}

.panorama-grid {
  grid-template-columns: minmax(300px, 0.95fr) minmax(460px, 1.35fr) minmax(280px, 0.86fr);
  align-items: stretch;
}

.control-panel,
.center-column,
.topic-column {
  height: 100%;
}

.control-panel :deep(.panel-card),
.topic-column :deep(.panel-card) {
  height: 100%;
  display: grid;
  grid-template-rows: auto 1fr;
}

.control-panel :deep(.panel-card .panel-body),
.topic-column :deep(.panel-card .panel-body) {
  min-height: 0;
}

.score-ticker {
  position: relative;
  height: 710px;
  overflow: hidden;
}

.score-ticker__track {
  display: grid;
  gap: 12px;
  animation: scoreTickerScroll 22s linear infinite;
}

.score-ticker__track--static {
  animation: none;
}

.score-ticker:hover .score-ticker__track {
  animation-play-state: paused;
}

.score-card {
  display: grid;
  gap: 10px;
  padding: 16px;
  border-radius: 20px;
  border: 1px solid rgba(214, 225, 238, 0.96);
  background: linear-gradient(180deg, #f9fbff, #f2f7fd);
  cursor: pointer;
}

.score-card--active {
  border-color: rgba(47, 111, 211, 0.3);
  box-shadow: 0 10px 24px rgba(47, 111, 211, 0.12);
}

.score-card__head,
.ranking-item__head {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  align-items: center;
}

.score-card__head strong {
  color: #18324d;
}

.score-card__head > span,
.ranking-item__meta > span {
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

.score-card__ring {
  width: 112px;
  height: 112px;
  margin: 0 auto;
  padding: 8px;
  border-radius: 50%;
  transition: transform 0.2s ease;
}

.score-card__ring-core {
  width: 100%;
  height: 100%;
  display: grid;
  place-items: center;
  align-content: center;
  gap: 4px;
  border-radius: 50%;
  background: #ffffff;
}

.score-card__ring-core b {
  color: #18324d;
  font-size: 24px;
}

.score-card__ring-core small {
  color: #6a839f;
  font-size: 12px;
}

.score-card__meta {
  display: none;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: center;
}

.score-card__meta span {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 0 10px;
  border-radius: 999px;
  background: rgba(16, 52, 85, 0.06);
  color: #5f738e;
  font-size: 12px;
}

.score-card__details {
  display: none;
  gap: 10px;
}

.score-card__details article {
  display: grid;
  gap: 6px;
}

.score-card__details article div:first-child {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  align-items: center;
}

.score-card__details span,
.score-card__details strong {
  color: #4e6884;
  font-size: 12px;
}

.score-card__bar {
  height: 8px;
  border-radius: 999px;
  background: #eaf1f9;
  overflow: hidden;
}

.score-card__bar i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #78a9ff, #2f6fd3);
}

.score-card:hover .score-card__details {
  display: grid;
}

.score-card:hover .score-card__meta {
  display: flex;
}

.score-card:hover .score-card__ring {
  transform: scale(0.98);
}

.opinion-form {
  display: grid;
  gap: 12px;
}

.opinion-form label,
.filter-block label {
  display: grid;
  gap: 8px;
  color: #355270;
}

.opinion-form__inline {
  display: grid;
  gap: 12px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.filter-block {
  box-shadow: none;
}

.comment-toolbar {
  box-shadow: none;
}

.comment-toolbar label {
  display: grid;
  gap: 8px;
  color: #355270;
}

.comment-toolbar__input {
  min-height: 48px;
  padding: 0 14px;
  border-radius: 14px;
  border: 1px solid rgba(214, 225, 238, 0.96);
  background: #ffffff;
  color: #18324d;
}

.opinion-form--compact {
  grid-template-columns: minmax(160px, 1.1fr) 130px 96px 1fr auto auto;
  align-items: center;
  gap: 10px;
  padding: 12px;
  border-radius: 18px;
  background: rgba(16, 52, 85, 0.04);
}

.opinion-form__select,
.opinion-form__input {
  min-height: 46px;
  border-radius: 14px;
  border: 1px solid rgba(214, 225, 238, 0.96);
  background: #ffffff;
  color: #18324d;
}

.opinion-form__input {
  padding: 0 14px;
}

.opinion-form__upload {
  position: relative;
  min-height: 46px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0 14px;
  border-radius: 14px;
  border: 1px dashed rgba(47, 111, 211, 0.35);
  background: rgba(255, 255, 255, 0.82);
  color: #2f6fd3;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
}

.opinion-form__upload input {
  position: absolute;
  inset: 0;
  opacity: 0;
  cursor: pointer;
}

.opinion-form__send {
  min-width: 92px;
}

.opinion-images {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.opinion-image-chip {
  position: relative;
  width: 96px;
  height: 96px;
  overflow: hidden;
  border-radius: 16px;
  border: 1px solid rgba(214, 225, 238, 0.96);
  background: #ffffff;
}

.opinion-image-chip img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.opinion-image-chip button {
  position: absolute;
  right: 6px;
  bottom: 6px;
  min-height: 26px;
  padding: 0 10px;
  border-radius: 999px;
  border: 0;
  background: rgba(18, 34, 58, 0.72);
  color: #ffffff;
  box-shadow: none;
}

.opinion-message,
.opinion-empty {
  margin: 0;
  color: #5f738e;
}

.opinion-message--success {
  color: #228b62;
}

.opinion-message--error {
  color: #d26a3e;
}

.barrage-board {
  position: relative;
  min-height: 396px;
  border-radius: 30px;
  overflow: hidden;
  background:
    radial-gradient(circle at 50% 50%, rgba(111, 163, 255, 0.18), rgba(111, 163, 255, 0.02) 46%, transparent 72%),
    linear-gradient(180deg, rgba(249, 252, 255, 0.96), rgba(241, 247, 254, 0.98));
  border: 1px solid rgba(214, 225, 238, 0.95);
  box-shadow: inset 0 -90px 120px rgba(12, 28, 48, 0.06);
}

.barrage-board__glow {
  position: absolute;
  width: 180px;
  height: 180px;
  border-radius: 50%;
  filter: blur(26px);
  opacity: 0.42;
}

.barrage-board__glow--left {
  left: -30px;
  top: 24px;
  background: rgba(111, 163, 255, 0.24);
}

.barrage-board__glow--right {
  right: -20px;
  bottom: 20px;
  background: rgba(122, 199, 169, 0.22);
}

.barrage-lane {
  position: absolute;
  left: 0;
  right: 0;
  overflow: hidden;
}

.barrage-lane--top {
  top: 112px;
}

.barrage-lane--bottom {
  top: 228px;
}

.barrage-track {
  display: flex;
  gap: 14px;
  width: max-content;
  pointer-events: none;
}

.barrage-track--left {
  animation: barrageLeft 22s linear infinite;
}

.barrage-track--right {
  animation: barrageRight 24s linear infinite;
}

.barrage-track--static {
  animation: none;
}

.barrage-item {
  width: 280px;
  min-height: 74px;
  display: grid;
  grid-template-columns: 84px 1fr;
  grid-template-areas:
    "image title"
    "image meta"
    "image content";
  column-gap: 12px;
  row-gap: 4px;
  padding: 10px;
  text-align: left;
  border-radius: 16px;
  border: 1px solid rgba(255, 255, 255, 0.28);
  background: rgba(18, 34, 58, 0.42);
  color: #f8fbff;
  backdrop-filter: blur(10px);
  box-shadow: none;
  pointer-events: auto;
}

.barrage-item--soft {
  background: rgba(34, 54, 84, 0.38);
}

.barrage-item__image {
  grid-area: image;
  width: 84px;
  height: 84px;
  object-fit: cover;
  border-radius: 12px;
  border: 1px solid rgba(255, 255, 255, 0.2);
}

.barrage-item strong {
  grid-area: title;
  color: #f8fbff;
  font-size: 13px;
}

.barrage-item span,
.barrage-item p {
  margin: 0;
  color: rgba(239, 246, 255, 0.86);
  font-size: 12px;
  line-height: 1.45;
}

.barrage-item span {
  grid-area: meta;
}

.barrage-item p {
  grid-area: content;
}

.insight-modal__gallery {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
}

.insight-modal__gallery img {
  width: 100%;
  height: 180px;
  object-fit: cover;
  border-radius: 18px;
  border: 1px solid rgba(214, 225, 238, 0.96);
}

.ranking-panel {
  display: grid;
  gap: 18px;
  max-height: 700px;
}

.ranking-item {
  display: grid;
  gap: 0;
  padding: 18px 16px;
  text-align: left;
  border-radius: 24px;
  border: 1px solid rgba(214, 225, 238, 0.96);
  background: linear-gradient(180deg, #f9fbff, #f2f7fd);
  box-shadow: none;
}

.ranking-search input {
  width: 100%;
  min-height: 44px;
  padding: 0 14px;
  border-radius: 14px;
  border: 1px solid rgba(214, 225, 238, 0.96);
  background: #ffffff;
  color: #18324d;
}

.ranking-list {
  max-height: 630px;
  overflow-y: auto;
  padding-right: 4px;
  align-content: start;
  scrollbar-width: thin;
  gap: 20px;
}

.ranking-item__head {
  display: flex;
  align-items: center;
  gap: 14px;
}

.ranking-item__meta {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.ranking-item__badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 999px;
  font-size: 14px;
  color: #ffffff;
  background: #7c95b5;
}

.ranking-item--gold .ranking-item__badge {
  background: linear-gradient(135deg, #f3b63f, #dc8b1d);
}

.ranking-item--silver .ranking-item__badge {
  background: linear-gradient(135deg, #8ea7d8, #5f77bf);
}

.ranking-item--bronze .ranking-item__badge {
  background: linear-gradient(135deg, #d49768, #b66c3a);
}

.ranking-item--default .ranking-item__badge {
  background: linear-gradient(135deg, #97abc4, #6d85a4);
}

.ranking-item__topic {
  display: flex;
  align-items: center;
  min-height: 36px;
  padding: 0 14px;
  border-radius: 14px;
  background: rgba(47, 111, 211, 0.08);
  color: #33597f;
  font-size: 14px;
  font-weight: 700;
  flex: 1;
  min-width: 0;
}

.ranking-item__track {
  flex: 0 0 108px;
  min-width: 108px;
  height: 14px;
  border-radius: 999px;
  background: rgba(47, 111, 211, 0.22);
  overflow: hidden;
  box-shadow: inset 0 0 0 1px rgba(47, 111, 211, 0.08);
}

.ranking-item__fill {
  display: block;
  height: 100%;
  min-width: 26px;
  border-radius: inherit;
  background: linear-gradient(90deg, #9fd0ff, #4d8cff 45%, #2f6fd3 100%);
  box-shadow:
    0 0 0 1px rgba(47, 111, 211, 0.12),
    0 3px 8px rgba(47, 111, 211, 0.18);
}

.insight-modal {
  position: fixed;
  inset: 0;
  z-index: 40;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgba(15, 32, 52, 0.28);
  backdrop-filter: blur(6px);
}

.insight-modal__panel {
  width: min(720px, 100%);
  display: grid;
  gap: 18px;
  padding: 24px;
  border-radius: 28px;
  background: #ffffff;
  box-shadow: 0 24px 60px rgba(31, 58, 92, 0.18);
}

.insight-modal__head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: start;
}

.insight-modal__head small {
  display: block;
  color: #6a839f;
  margin-bottom: 6px;
}

.insight-modal__head strong {
  font-size: 24px;
  color: #183452;
}

.insight-modal__close {
  min-height: 38px;
  padding: 0 14px;
  border-radius: 999px;
  background: #f4f8fd;
  color: #315d92;
  border: 1px solid rgba(216, 227, 239, 0.96);
  box-shadow: none;
}

.insight-modal__summary {
  display: grid;
  grid-template-columns: 160px 1fr;
  gap: 18px;
}

.insight-modal__summary article {
  display: grid;
  gap: 6px;
  padding: 16px;
  border-radius: 20px;
  background: #f8fbff;
}

.insight-modal__summary article span,
.insight-modal__summary p,
.insight-modal__details p {
  color: #6a839f;
}

.insight-modal__summary article b {
  font-size: 32px;
  color: #183452;
}

.insight-modal__summary p,
.insight-modal__details p {
  margin: 0;
  line-height: 1.8;
}

.insight-modal__details {
  display: grid;
  gap: 10px;
  padding: 18px;
  border-radius: 22px;
  background: #f8fbff;
}

@keyframes barrageLeft {
  0% {
    transform: translateX(0);
  }
  100% {
    transform: translateX(calc(-50% - 7px));
  }
}

@keyframes barrageRight {
  0% {
    transform: translateX(calc(-50% - 7px));
  }
  100% {
    transform: translateX(0);
  }
}

@keyframes scoreTickerScroll {
  0% {
    transform: translateY(0);
  }
  100% {
    transform: translateY(calc(-50% - 6px));
  }
}

@media (max-width: 1250px) {
  .panorama-grid,
  .insight-modal__summary {
    grid-template-columns: 1fr;
  }

  .barrage-board {
    min-height: 320px;
  }

  .barrage-lane--top {
    top: 96px;
  }

  .barrage-lane--bottom {
    top: 198px;
  }
}

@media (max-width: 768px) {
  .opinion-form--compact {
    grid-template-columns: 1fr;
  }

  .ranking-item__head {
    flex-wrap: wrap;
  }

  .ranking-item__topic {
    width: 100%;
  }

  .ranking-item__track {
    flex-basis: 100%;
  }

  .barrage-item {
    width: 220px;
    grid-template-columns: 68px 1fr;
  }

  .barrage-item__image {
    width: 68px;
    height: 68px;
  }
}
</style>
