<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import MetricCard from "../components/MetricCard.vue";
import PanelCard from "../components/PanelCard.vue";
import { apiFetch } from "../composables/useApi";
import type { HeatmapPoint, ParkingOpinion, TurnoverStat } from "../types";
import { formatRate, formatTime } from "../utils/parking";

type InsightDetail = {
  id: string;
  title: string;
  subtitle: string;
  level: string;
  scoreLabel: string;
  scoreValue: string;
  summary: string;
  details: string[];
};

type CloudWord = {
  id: string;
  text: string;
  color: "blue" | "navy" | "green" | "amber" | "purple";
  size: 2 | 3 | 4 | 5 | 6;
  top: string;
  left: string;
  rotate: number;
  detail: InsightDetail;
};

const heatmap = ref<HeatmapPoint[]>([]);
const turnover = ref<TurnoverStat[]>([]);
const opinions = ref<ParkingOpinion[]>([]);
const selectedInsight = ref<InsightDetail | null>(null);
const activeVisualIndex = ref(0);
let visualTimer: number | null = null;

const topHotspot = computed(() => heatmap.value[0]);
const bestTurnover = computed(() => turnover.value[0]);

const topicStats = computed(() => {
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

const topicRanking = computed(() =>
  topicStats.value.slice(0, 5).map((item, index) => ({
    id: item.topic,
    rank: index + 1,
    title: item.topic,
    mentionCount: item.count,
    score: Math.round(item.heat * 22),
    summary:
      item.avgRating >= 4.2
        ? `该话题以正向体验反馈为主，常与停车效率、环境和导航便利性一起被提及。`
        : `该话题目前存在较多关注项，用户更在意规则透明度、导视清晰度或高峰期排队体验。`,
    relatedLots: Array.from(item.lots).slice(0, 3),
    examples: item.examples,
    level: item.avgRating >= 4.2 ? "正向热点" : "关注热点"
  }))
);

const rankingList = computed(() => {
  const maxScore = Math.max(...topicRanking.value.map((item) => item.score), 1);
  return topicRanking.value.map((item) => ({
    ...item,
    width: `${Math.max(30, Math.round((item.score / maxScore) * 100))}%`,
    tone: item.rank === 1 ? "gold" : item.rank === 2 ? "silver" : item.rank === 3 ? "bronze" : "default"
  }));
});

const parkingVisuals = computed(() => {
  return heatmap.value.slice(0, 5).map((lot) => {
    const lotOpinions = opinions.value.filter((item) => item.parkingLotId === lot.parkingLotId);
    const lotTurnover = turnover.value.find((item) => item.parkingLotId === lot.parkingLotId);
    const avgRating = lotOpinions.length
      ? lotOpinions.reduce((sum, item) => sum + item.rating, 0) / lotOpinions.length
      : 4;
    const positive = lotOpinions.filter((item) => item.sentiment === "正向").length;
    const neutral = lotOpinions.filter((item) => item.sentiment === "总体较好").length;
    const focus = lotOpinions.filter((item) => item.sentiment === "关注项").length;
    const total = Math.max(lotOpinions.length, 1);
    const feeAttention = lotOpinions.filter((item) => item.topic === "停车场收费").length;
    const envAttention = lotOpinions.filter((item) => item.topic === "周围环境").length;
    const radarMetrics = [
      { label: "环境", value: Math.round((avgRating / 5) * 100) },
      { label: "收费", value: Math.min(100, 30 + feeAttention * 18) },
      { label: "余位时效", value: Math.min(100, Math.round(lot.intensity * 100)) },
      { label: "停车效率", value: Math.min(100, Math.round(38 + (lotTurnover?.turnoverRate ?? 3.5) * 11)) },
      { label: "周围环境", value: Math.min(100, 35 + envAttention * 16) }
    ];
    const count = radarMetrics.length;
    const radarAxes = radarMetrics.map((item, index) => {
      const angle = (-90 + (360 / count) * index) * (Math.PI / 180);
      return {
        ...item,
        lineX: 110 + Math.cos(angle) * 92,
        lineY: 110 + Math.sin(angle) * 92,
        pointX: 110 + Math.cos(angle) * 88 * (item.value / 100),
        pointY: 110 + Math.sin(angle) * 88 * (item.value / 100),
        labelX: 110 + Math.cos(angle) * 102,
        labelY: 110 + Math.sin(angle) * 102
      };
    });
    const radarGridPoints = radarMetrics
      .map((item, index) => {
        const angle = (-90 + (360 / count) * index) * (Math.PI / 180);
        const radius = 88 * (item.value / 100);
        return `${110 + Math.cos(angle) * radius},${110 + Math.sin(angle) * radius}`;
      })
      .join(" ");

    const positivePercent = Math.round((positive / total) * 100);
    const neutralPercent = Math.round((neutral / total) * 100);

    return {
      id: lot.parkingLotId,
      name: lot.name,
      topicCount: lotOpinions.length,
      positive,
      neutral,
      focus,
      positivePercent,
      neutralPercent,
      focusPercent: Math.round((focus / total) * 100),
      pieStyle: {
        background: `conic-gradient(#35a972 0 ${positivePercent}%, #4f89e3 ${positivePercent}% ${positivePercent + neutralPercent}%, #f0a337 ${positivePercent + neutralPercent}% 100%)`
      },
      radarAxes,
      radarGridPoints
    };
  });
});

const activeVisual = computed(() => parkingVisuals.value[activeVisualIndex.value] ?? null);

const wordCloudItems = computed<CloudWord[]>(() => {
  const positions = [
    { top: "14%", left: "23%", rotate: -8 },
    { top: "14%", left: "54%", rotate: 4 },
    { top: "22%", left: "76%", rotate: -5 },
    { top: "34%", left: "19%", rotate: 7 },
    { top: "36%", left: "50%", rotate: -4 },
    { top: "49%", left: "74%", rotate: 5 },
    { top: "57%", left: "25%", rotate: -6 },
    { top: "62%", left: "50%", rotate: 3 },
    { top: "75%", left: "72%", rotate: -5 },
    { top: "79%", left: "32%", rotate: 6 },
    { top: "24%", left: "38%", rotate: -2 },
    { top: "56%", left: "60%", rotate: 4 }
  ];

  const items: CloudWord[] = [];
  topicRanking.value.slice(0, 8).forEach((topic, index) => {
    const position = positions[index];
    items.push({
      id: `topic-${topic.id}`,
      text: topic.title,
      color: topic.level === "正向热点" ? "green" : index % 2 === 0 ? "blue" : "amber",
      size: (Math.min(6, Math.max(3, 6 - Math.floor(index / 2))) as 2 | 3 | 4 | 5 | 6),
      top: position.top,
      left: position.left,
      rotate: position.rotate,
      detail: {
        id: topic.id,
        title: topic.title,
        subtitle: "词云话题详情",
        level: topic.level,
        scoreLabel: "综合热度",
        scoreValue: String(topic.score),
        summary: topic.summary,
        details: [
          `提及次数：${topic.mentionCount}`,
          `关联停车场：${topic.relatedLots.join("、") || "暂无"}`,
          ...topic.examples.map((item) => `${item.authorName}：${item.content}`)
        ]
      }
    });
  });

  const lotFocus = opinions.value
    .reduce((acc, item) => {
      const current = acc.get(item.parkingLotName) ?? 0;
      acc.set(item.parkingLotName, current + 1);
      return acc;
    }, new Map<string, number>());
  Array.from(lotFocus.entries())
    .sort((a, b) => b[1] - a[1])
    .slice(0, 4)
    .forEach(([name, count], index) => {
      const position = positions[index + 8];
      items.push({
        id: `lot-${name}`,
        text: name.replace("停车场", ""),
        color: index % 2 === 0 ? "navy" : "purple",
        size: (index === 0 ? 4 : 3) as 2 | 3 | 4 | 5 | 6,
        top: position.top,
        left: position.left,
        rotate: position.rotate,
        detail: {
          id: `lot-${name}`,
          title: `${name} 舆情焦点`,
          subtitle: "词云停车场详情",
          level: count >= 3 ? "高关注" : "持续关注",
          scoreLabel: "评论条数",
          scoreValue: String(count),
          summary: `${name} 是当前用户反馈较多的停车场之一，适合在演示时重点展示评论与推荐联动。`,
          details: opinions.value
            .filter((item) => item.parkingLotName === name)
            .slice(0, 3)
            .map((item) => `${item.topic} · ${item.authorName}：${item.content}`)
        }
      });
    });

  return items;
});

const latestReviews = computed(() => opinions.value.slice(0, 3));
const latestReviewTicker = computed(() =>
  latestReviews.value.length > 1 ? [...latestReviews.value, ...latestReviews.value] : latestReviews.value
);

function nextVisual() {
  if (!parkingVisuals.value.length) return;
  activeVisualIndex.value = (activeVisualIndex.value + 1) % parkingVisuals.value.length;
}

function startVisualAutoPlay() {
  if (visualTimer !== null || parkingVisuals.value.length <= 1) return;
  visualTimer = window.setInterval(() => {
    nextVisual();
  }, 3200);
}

function stopVisualAutoPlay() {
  if (visualTimer !== null) {
    window.clearInterval(visualTimer);
    visualTimer = null;
  }
}

function openInsight(detail: InsightDetail) {
  selectedInsight.value = detail;
}

function openTopicDetail(topicId: string) {
  const topic = topicRanking.value.find((item) => item.id === topicId);
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
    ]
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
      `来源：${review.source}`
    ]
  });
}

async function loadData() {
  const [heatmapData, turnoverData, opinionsData] = await Promise.all([
    apiFetch<HeatmapPoint[]>("/api/analysis/heatmap"),
    apiFetch<TurnoverStat[]>("/api/analysis/turnover"),
    apiFetch<ParkingOpinion[]>("/api/analysis/opinions")
  ]);
  heatmap.value = heatmapData;
  turnover.value = turnoverData;
  opinions.value = opinionsData;
}

onMounted(async () => {
  await loadData();
  startVisualAutoPlay();
});

onBeforeUnmount(() => {
  stopVisualAutoPlay();
});
</script>

<template>
  <div class="analysis-page">
    <section class="metric-grid">
      <MetricCard label="高关注车场" :value="topHotspot?.name ?? '-'" hint="当前最容易引发集中讨论的停车场" />
      <MetricCard label="关注热度" accent="amber" :value="topHotspot ? formatRate(topHotspot.intensity) : '-'" hint="结合空位压力与用户评论的综合强度" />
      <MetricCard label="评论样本" accent="lime" :value="opinions.length" hint="当前已汇聚的市民与旅客评论数量" />
    </section>

    <div id="analysis-demo" class="panorama-grid">
      <section class="visual-panel">
        <div class="visual-stack" @mouseenter="stopVisualAutoPlay" @mouseleave="startVisualAutoPlay">
          <div v-if="activeVisual" class="visual-panel__head">
            <div>
              <strong>{{ activeVisual.name }}</strong>
              <p>{{ activeVisual.topicCount }} 条相关评论</p>
            </div>
            <div class="visual-dots">
              <button
                v-for="(item, index) in parkingVisuals"
                :key="item.id"
                class="visual-dots__item"
                :class="{ 'visual-dots__item--active': activeVisualIndex === index }"
                @click="activeVisualIndex = index"
              ></button>
            </div>
          </div>
          <div class="radar-panel">
            <svg viewBox="0 0 220 220" class="radar-chart" aria-hidden="true">
              <polygon points="110,22 193,82 161,182 59,182 27,82" class="radar-chart__ring radar-chart__ring--outer" />
              <polygon points="110,44 171,88 147,162 73,162 49,88" class="radar-chart__ring radar-chart__ring--mid" />
              <polygon points="110,66 149,94 133,142 87,142 71,94" class="radar-chart__ring radar-chart__ring--inner" />
              <line v-for="axis in activeVisual?.radarAxes ?? []" :key="axis.label" x1="110" y1="110" :x2="axis.lineX" :y2="axis.lineY" class="radar-chart__axis" />
              <polygon :points="activeVisual?.radarGridPoints ?? ''" class="radar-chart__shape" />
              <circle v-for="axis in activeVisual?.radarAxes ?? []" :key="`${axis.label}-point`" :cx="axis.pointX" :cy="axis.pointY" r="4" class="radar-chart__point" />
              <text
                v-for="axis in activeVisual?.radarAxes ?? []"
                :key="`${axis.label}-text`"
                :x="axis.labelX"
                :y="axis.labelY"
                class="radar-chart__label"
                text-anchor="middle"
              >
                {{ axis.label }}
              </text>
            </svg>
          </div>

          <div class="pie-panel">
            <div class="pie-chart" :style="activeVisual?.pieStyle"></div>
            <div class="pie-legend">
              <article class="pie-legend__item">
                <span class="sentiment-dot sentiment-dot--green"></span>
                <div>
                  <strong>正向</strong>
                  <p>{{ activeVisual?.positive ?? 0 }} 条，约 {{ activeVisual?.positivePercent ?? 0 }}%</p>
                </div>
              </article>
              <article class="pie-legend__item">
                <span class="sentiment-dot sentiment-dot--blue"></span>
                <div>
                  <strong>总体较好</strong>
                  <p>{{ activeVisual?.neutral ?? 0 }} 条，约 {{ activeVisual?.neutralPercent ?? 0 }}%</p>
                </div>
              </article>
              <article class="pie-legend__item">
                <span class="sentiment-dot sentiment-dot--amber"></span>
                <div>
                  <strong>关注项</strong>
                  <p>{{ activeVisual?.focus ?? 0 }} 条，约 {{ activeVisual?.focusPercent ?? 0 }}%</p>
                </div>
              </article>
            </div>
          </div>
        </div>
      </section>

      <section class="center-column">
        <PanelCard title="舆情词云">
          <div class="word-cloud-stage">
            <div class="word-cloud-stage__glow word-cloud-stage__glow--left"></div>
            <div class="word-cloud-stage__glow word-cloud-stage__glow--right"></div>
            <button
              v-for="item in wordCloudItems"
              :key="item.id"
              class="word-cloud__item"
              :class="[`word-cloud__item--${item.color}`, `word-cloud__item--w${item.size}`]"
              :style="{ top: item.top, left: item.left, transform: `translate(-50%, -50%) rotate(${item.rotate}deg)` }"
              @click="openInsight(item.detail)"
            >
              {{ item.text }}
            </button>
          </div>
        </PanelCard>

        <PanelCard title="最新评论">
          <div class="review-ticker">
            <div class="review-ticker__track" :class="{ 'review-ticker__track--static': latestReviews.length <= 1 }">
              <article
                v-for="(item, index) in latestReviewTicker"
                :key="`${item.id}-${index}`"
                class="review-ticker__item"
                @click="openReviewDetail(item)"
              >
                <div class="review-ticker__head">
                  <strong>{{ item.parkingLotName }}</strong>
                  <span>{{ item.rating.toFixed(1) }}</span>
                </div>
                <p>{{ item.content }}</p>
                <small>{{ item.authorName }} · {{ item.topic }} · {{ formatTime(item.createdAt) }}</small>
              </article>
            </div>
          </div>
        </PanelCard>
      </section>

      <PanelCard title="热度排行">
        <div class="ranking-list">
          <button
            v-for="item in rankingList"
            :key="item.id"
            class="ranking-item"
            :class="`ranking-item--${item.tone}`"
            @click="openTopicDetail(item.id)"
          >
            <div class="ranking-item__head">
              <strong class="ranking-item__badge">{{ item.rank }}</strong>
              <span>{{ item.score }}</span>
            </div>
            <p>{{ item.title }}</p>
            <div class="ranking-item__track">
              <span class="ranking-item__fill" :style="{ width: item.width }"></span>
            </div>
          </button>
        </div>
      </PanelCard>
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
      </div>
    </div>

  </div>
</template>

<style scoped>
.analysis-page,
.metric-grid,
.panorama-grid,
.visual-stack,
.ranking-list,
.insight-grid,
.insight-list,
.pie-legend {
  display: grid;
  gap: 18px;
}

.metric-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.panorama-grid {
  grid-template-columns: minmax(280px, 0.92fr) minmax(460px, 1.4fr) minmax(280px, 0.88fr);
  align-items: stretch;
  scroll-margin-top: 96px;
}

.visual-stack {
  align-content: start;
}

.center-column {
  display: grid;
  gap: 18px;
  align-content: start;
}

.visual-panel {
  display: grid;
  align-content: start;
  gap: 18px;
  padding: 20px;
  border-radius: 28px;
  border: 1px solid rgba(207, 220, 236, 0.95);
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(246, 250, 255, 0.98)),
    radial-gradient(circle at top left, rgba(56, 189, 248, 0.08), transparent 40%);
  box-shadow: 0 18px 42px rgba(55, 94, 138, 0.08);
}

.visual-panel__head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: center;
}

.visual-panel__head p {
  margin: 6px 0 0;
  color: #6a839f;
}

.visual-dots {
  display: flex;
  gap: 8px;
  align-items: center;
}

.visual-dots__item {
  width: 10px;
  height: 10px;
  padding: 0;
  border-radius: 999px;
  background: #d3dbe5;
  border: 0;
  box-shadow: none;
}

.visual-dots__item--active {
  background: #7f92a8;
}

.radar-panel,
.pie-panel,
.insight-card,
.ranking-item {
  border-radius: 24px;
  background: linear-gradient(180deg, #f9fbff, #f2f7fd);
  border: 1px solid rgba(214, 225, 238, 0.96);
}

.radar-panel,
.pie-panel {
  padding: 16px;
}

.radar-chart {
  width: 100%;
  max-width: 280px;
  margin: 0 auto;
  display: block;
  overflow: visible;
}

.radar-chart__ring {
  fill: rgba(79, 137, 227, 0.06);
  stroke: rgba(96, 139, 192, 0.24);
}

.radar-chart__axis {
  stroke: rgba(96, 139, 192, 0.22);
  stroke-width: 1;
}

.radar-chart__shape {
  fill: rgba(47, 111, 211, 0.22);
  stroke: #2f6fd3;
  stroke-width: 2;
}

.radar-chart__point {
  fill: #2f6fd3;
}

.radar-chart__label {
  fill: #5b7694;
  font-size: 11px;
  font-weight: 600;
}

.pie-panel {
  display: grid;
  grid-template-columns: 112px 1fr;
  align-items: center;
  gap: 16px;
}

.pie-chart {
  width: 112px;
  height: 112px;
  border-radius: 50%;
  position: relative;
  margin: 0 auto;
}

.pie-chart::after {
  content: "";
  position: absolute;
  inset: 22px;
  border-radius: 50%;
  background: #ffffff;
  box-shadow: inset 0 0 0 1px rgba(224, 232, 242, 0.9);
}

.pie-legend__item {
  display: flex;
  align-items: center;
  gap: 10px;
}

.pie-legend__item p {
  margin: 4px 0 0;
  color: #6a839f;
}

.sentiment-dot {
  width: 12px;
  height: 12px;
  border-radius: 999px;
  flex-shrink: 0;
}

.sentiment-dot--green {
  background: #35a972;
}

.sentiment-dot--blue {
  background: #4f89e3;
}

.sentiment-dot--amber {
  background: #f0a337;
}

.word-cloud-stage {
  position: relative;
  min-height: 368px;
  border-radius: 30px;
  overflow: hidden;
  background:
    radial-gradient(circle at 50% 50%, rgba(111, 163, 255, 0.18), rgba(111, 163, 255, 0.02) 46%, transparent 72%),
    linear-gradient(180deg, rgba(249, 252, 255, 0.96), rgba(241, 247, 254, 0.98));
  border: 1px solid rgba(214, 225, 238, 0.95);
}

.word-cloud-stage__glow {
  position: absolute;
  width: 180px;
  height: 180px;
  border-radius: 50%;
  filter: blur(26px);
  opacity: 0.42;
}

.word-cloud-stage__glow--left {
  left: -30px;
  top: 24px;
  background: rgba(111, 163, 255, 0.24);
}

.word-cloud-stage__glow--right {
  right: -20px;
  bottom: 20px;
  background: rgba(122, 199, 169, 0.22);
}

.word-cloud__item {
  position: absolute;
  z-index: 1;
  border: 0;
  background: transparent;
  box-shadow: none;
  padding: 4px 8px;
  font-weight: 800;
  line-height: 1;
  text-shadow: 0 8px 24px rgba(255, 255, 255, 0.6);
}

.word-cloud__item--blue {
  color: #2f6fd3;
}

.word-cloud__item--navy {
  color: #183452;
}

.word-cloud__item--green {
  color: #228b62;
}

.word-cloud__item--amber {
  color: #c88218;
}

.word-cloud__item--purple {
  color: #7667d8;
}

.word-cloud__item--w2 {
  font-size: 18px;
}

.word-cloud__item--w3 {
  font-size: 22px;
}

.word-cloud__item--w4 {
  font-size: 28px;
}

.word-cloud__item--w5 {
  font-size: 34px;
}

.word-cloud__item--w6 {
  font-size: 42px;
}

.ranking-item,
.insight-card {
  display: grid;
  gap: 10px;
  padding: 16px;
  text-align: left;
  box-shadow: none;
}

.ranking-item__head,
.insight-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.ranking-item__head span,
.insight-card__head span {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 0 10px;
  border-radius: 999px;
  background: #edf5ff;
  color: #2b68b2;
  font-size: 12px;
  font-weight: 700;
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

.ranking-item p,
.insight-card p,
.insight-card small {
  margin: 0;
  color: #6a839f;
}

.ranking-item__track {
  height: 10px;
  border-radius: 999px;
  background: #eaf1f9;
  overflow: hidden;
}

.ranking-item__fill {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #78a9ff, #2f6fd3);
}

.review-ticker {
  position: relative;
  height: 206px;
  overflow: hidden;
}

.review-ticker__track {
  display: grid;
  gap: 12px;
  animation: reviewTickerScroll 14s linear infinite;
}

.review-ticker__track--static {
  animation: none;
}

.review-ticker__item {
  display: grid;
  gap: 8px;
  padding: 14px 16px;
  border-radius: 20px;
  background: linear-gradient(180deg, #f9fbff, #f2f7fd);
  border: 1px solid rgba(214, 225, 238, 0.96);
  cursor: pointer;
}

.review-ticker__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.review-ticker__head span {
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

.review-ticker__item p,
.review-ticker__item small {
  margin: 0;
  color: #6a839f;
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

@media (max-width: 1250px) {
  .panorama-grid,
  .metric-grid,
  .pie-panel,
  .insight-modal__summary {
    grid-template-columns: 1fr;
  }

  .word-cloud-stage {
    min-height: 330px;
  }
}

@keyframes reviewTickerScroll {
  0% {
    transform: translateY(0);
  }
  100% {
    transform: translateY(calc(-50% - 6px));
  }
}

@media (max-width: 780px) {
  .word-cloud__item--w6 {
    font-size: 34px;
  }

  .word-cloud__item--w5 {
    font-size: 28px;
  }

  .word-cloud__item--w4 {
    font-size: 24px;
  }
}
</style>
