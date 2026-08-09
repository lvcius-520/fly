<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import MetricCard from "../components/MetricCard.vue";
import PanelCard from "../components/PanelCard.vue";
import { apiFetch } from "../composables/useApi";
import type {
  DashboardOverview,
  HeatmapPoint,
  MapLayersResponse,
  MissionEvent,
  ParkingLot,
  TurnoverStat
} from "../types";
import { formatRate, formatTime } from "../utils/parking";

const loading = ref(false);
const overview = ref<DashboardOverview | null>(null);
const layers = ref<MapLayersResponse | null>(null);
const heatmap = ref<HeatmapPoint[]>([]);
const turnover = ref<TurnoverStat[]>([]);
const events = ref<MissionEvent[]>([]);
let timer: number | undefined;

const lots = computed(() => layers.value?.parkingLots ?? []);

const occupancyChart = computed(() => {
  const avg = Math.round((overview.value?.averageOccupancyRate ?? 0) * 100);
  return `conic-gradient(#2f6fd3 0 ${avg}%, #9fd1ff ${avg}% ${Math.min(avg + 14, 100)}%, rgba(225, 236, 247, 0.92) 0 100%)`;
});

const suzhouDistricts = computed(() => {
  const labelMap: Record<string, string> = {
    "region-gusu": "姑苏区",
    "region-suzhou-industry-park": "工业园区",
    "region-wuzhong": "吴中区",
    "region-xiangcheng": "相城区",
    "region-gaoxin": "高新区"
  };

  return lots.value
    .map((item) => ({
      id: item.id,
      name: labelMap[item.regionId] ?? item.name.replace("停车场", ""),
      occupancyRate: item.occupancyRate,
      availableSpaces: item.availableSpaces,
      totalSpaces: item.totalSpaces
    }))
    .sort((a, b) => b.occupancyRate - a.occupancyRate);
});

const resourceBars = computed(() => {
  return lots.value
    .map((item) => ({
      id: item.id,
      name: item.name.replace("停车场", ""),
      total: item.totalSpaces,
      available: item.availableSpaces,
      occupied: item.totalSpaces - item.availableSpaces,
      freePercent: item.totalSpaces > 0 ? Math.round((item.availableSpaces / item.totalSpaces) * 100) : 0
    }))
    .sort((a, b) => b.total - a.total);
});

const trendRows = computed(() => {
  return turnover.value.slice(0, 5).map((item, index) => ({
    ...item,
    rank: index + 1,
    sentimentLabel: item.occupancyRate > 0.8 ? "高关注" : item.occupancyRate > 0.65 ? "持续关注" : "平稳"
  }));
});

const eventTicker = computed(() => events.value.slice(0, 6));

async function loadData() {
  loading.value = true;
  try {
    const [overviewData, layerData, heatmapData, turnoverData, eventData] = await Promise.all([
      apiFetch<DashboardOverview>("/api/ops/overview"),
      apiFetch<MapLayersResponse>("/api/ops/map-layers"),
      apiFetch<HeatmapPoint[]>("/api/analysis/heatmap"),
      apiFetch<TurnoverStat[]>("/api/analysis/turnover"),
      apiFetch<MissionEvent[]>("/api/events?limit=8")
    ]);
    overview.value = overviewData;
    layers.value = layerData;
    heatmap.value = heatmapData;
    turnover.value = turnoverData;
    events.value = eventData;
  } finally {
    loading.value = false;
  }
}

onMounted(async () => {
  await loadData();
  timer = window.setInterval(() => {
    void loadData();
  }, 8000);
});

onBeforeUnmount(() => {
  if (timer) window.clearInterval(timer);
});
</script>

<template>
  <div class="screen-grid">
    <section class="screen-hero">
      <div class="screen-hero__copy">
        <p class="screen-hero__eyebrow">苏州市停车资源数据大屏</p>
        <h1>面向全市停车资源、巡检执行与设备运行的统一可视化指挥中枢</h1>
        <p>
          聚合停车资源分布、机场覆盖、车场负载、巡检轮次与重点事件，
          让管理端先看到苏州市整体态势，再进入巡检调度和动态监测。
        </p>
      </div>
      <div class="screen-hero__actions">
        <button :disabled="loading" @click="loadData">{{ loading ? "刷新中..." : "刷新数据大屏" }}</button>
      </div>
    </section>

    <section class="metric-grid">
      <MetricCard label="纳管停车场" :value="overview?.parkingLotCount ?? 0" hint="苏州市当前接入巡检体系的停车场" />
      <MetricCard label="总车位规模" accent="lime" :value="overview?.totalSpaces ?? 0" hint="按停车场总量汇总的可管控资源" />
      <MetricCard label="实时空位" accent="amber" :value="overview?.availableSpaces ?? 0" hint="来源于最近一轮巡检与识别结果" />
      <MetricCard label="在线设备" accent="violet" :value="overview?.onlineDevices ?? 0" hint="机场与无人机设备的在线数量" />
    </section>

    <div class="dashboard-grid">
      <PanelCard title="苏州市停车资源总览" subtitle="以资源规模、空位能力和巡检活跃度综合观察城市态势">
        <div class="overview-grid">
          <article class="ring-card">
            <div class="ring-chart" :style="{ background: occupancyChart }">
              <div class="ring-chart__inner">
                <strong>{{ formatRate(overview?.averageOccupancyRate ?? 0) }}</strong>
                <span>平均占用率</span>
              </div>
            </div>
            <div class="ring-copy">
              <strong>全市资源利用率</strong>
              <p>结合苏州市重点停车场实时占用情况，用于判断高峰期停车压力与调度优先级。</p>
            </div>
          </article>

          <div class="overview-stats">
            <article class="mini-stat">
              <span>今日巡检轮次</span>
              <strong>{{ overview?.inspectionRoundsToday ?? 0 }}</strong>
            </article>
            <article class="mini-stat">
              <span>活动任务</span>
              <strong>{{ overview?.activeMissions ?? 0 }}</strong>
            </article>
            <article class="mini-stat">
              <span>在线机场</span>
              <strong>{{ overview?.onlineAirports ?? 0 }}</strong>
            </article>
            <article class="mini-stat">
              <span>热点车场</span>
              <strong>{{ heatmap.length }}</strong>
            </article>
          </div>
        </div>
      </PanelCard>

      <PanelCard title="苏州市重点片区负载" subtitle="按重点片区查看停车压力与空位余量">
        <article v-for="item in suzhouDistricts" :key="item.id" class="district-row">
          <div>
            <strong>{{ item.name }}</strong>
            <p>空位 {{ item.availableSpaces }} / 总车位 {{ item.totalSpaces }}</p>
          </div>
          <div class="district-row__meta">
            <span>{{ formatRate(item.occupancyRate) }}</span>
            <div class="district-progress">
              <i :style="{ width: `${Math.round(item.occupancyRate * 100)}%` }" />
            </div>
          </div>
        </article>
      </PanelCard>
    </div>

    <div class="dashboard-grid dashboard-grid--wide">
      <PanelCard title="资源结构图表" subtitle="通过横向条形图查看各停车场资源总量与空位占比">
        <article v-for="item in resourceBars" :key="item.id" class="bar-row">
          <div class="bar-row__head">
            <strong>{{ item.name }}</strong>
            <span>总量 {{ item.total }} / 空位 {{ item.available }}</span>
          </div>
          <div class="bar-track">
            <i class="bar-track__occupied" :style="{ width: `${100 - item.freePercent}%` }" />
            <i class="bar-track__free" :style="{ width: `${item.freePercent}%` }" />
          </div>
        </article>
      </PanelCard>

      <PanelCard title="巡检热度与关注趋势" subtitle="用热度、周转率与关注等级识别高优先级区域">
        <article v-for="item in trendRows" :key="item.parkingLotId" class="trend-row">
          <div class="trend-row__rank">{{ item.rank }}</div>
          <div class="trend-row__main">
            <strong>{{ item.name }}</strong>
            <p>周转率 {{ item.turnoverRate.toFixed(1) }} 次/日，巡检次数 {{ item.inspections }}</p>
          </div>
          <div class="trend-row__tail">
            <span>{{ formatRate(item.occupancyRate) }}</span>
            <small>{{ item.sentimentLabel }}</small>
          </div>
        </article>
      </PanelCard>
    </div>

    <div class="dashboard-grid dashboard-grid--wide">
      <PanelCard title="重点预警事件" subtitle="告警、异常与任务提示按时间顺序滚动展示">
        <article v-for="item in overview?.alerts ?? []" :key="item.id" class="alert-row" :data-level="item.level">
          <div>
            <strong>{{ item.title }}</strong>
            <p>{{ item.message }}</p>
          </div>
          <small>{{ formatTime(item.timestamp) }}</small>
        </article>
      </PanelCard>

      <PanelCard title="实时事件播报" subtitle="最近任务事件、设备上报与调度过程提醒">
        <article v-for="item in eventTicker" :key="item.id" class="event-row">
          <div>
            <strong>{{ item.type }}</strong>
            <p>{{ item.message }}</p>
          </div>
          <small>{{ formatTime(item.timestamp) }}</small>
        </article>
      </PanelCard>
    </div>
  </div>
</template>

<style scoped>
.screen-grid,
.metric-grid,
.dashboard-grid,
.overview-grid,
.overview-stats {
  display: grid;
  gap: 18px;
}

.screen-hero {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 24px;
  align-items: end;
  padding: 30px;
  border-radius: 34px;
  background:
    radial-gradient(circle at top right, rgba(46, 111, 211, 0.14), transparent 28%),
    linear-gradient(135deg, #ffffff, #eff6ff);
  border: 1px solid rgba(208, 221, 236, 0.95);
  box-shadow: 0 18px 42px rgba(55, 94, 138, 0.08);
}

.screen-hero__eyebrow {
  margin: 0;
  color: #2f6fd3;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  font-size: 12px;
}

.screen-hero__copy h1 {
  margin: 10px 0 12px;
  font-size: clamp(28px, 3.5vw, 42px);
  line-height: 1.14;
}

.screen-hero__copy p:last-child {
  margin: 0;
  max-width: 820px;
  color: #6a839f;
  line-height: 1.8;
}

.metric-grid {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.dashboard-grid {
  grid-template-columns: 1.2fr 0.8fr;
}

.dashboard-grid--wide {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.overview-grid {
  grid-template-columns: 280px 1fr;
  align-items: center;
}

.ring-card {
  display: grid;
  gap: 18px;
  justify-items: center;
}

.ring-chart {
  display: grid;
  place-items: center;
  width: 184px;
  height: 184px;
  border-radius: 50%;
}

.ring-chart__inner {
  display: grid;
  place-items: center;
  width: 122px;
  height: 122px;
  border-radius: 50%;
  background: #ffffff;
  box-shadow: inset 0 0 0 1px rgba(221, 231, 240, 0.95);
}

.ring-chart__inner strong {
  font-size: 28px;
  color: #183452;
}

.ring-chart__inner span,
.ring-copy p,
.mini-stat span,
.district-row p,
.bar-row__head span,
.trend-row__main p,
.trend-row__tail small,
.alert-row p,
.event-row p,
.alert-row small,
.event-row small {
  color: #6a839f;
}

.ring-copy {
  text-align: center;
}

.ring-copy strong {
  display: block;
  margin-bottom: 8px;
  color: #183452;
}

.overview-stats {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.mini-stat {
  padding: 18px;
  border-radius: 24px;
  background: #f8fbff;
  border: 1px solid rgba(214, 225, 238, 0.95);
}

.mini-stat strong {
  display: block;
  margin-top: 10px;
  font-size: 28px;
  color: #183452;
}

.district-row,
.trend-row,
.alert-row,
.event-row {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 16px;
  border-radius: 22px;
  background: #f8fbff;
  border: 1px solid rgba(214, 225, 238, 0.95);
}

.district-row__meta,
.trend-row__tail {
  display: grid;
  gap: 8px;
  justify-items: end;
}

.district-progress {
  width: 144px;
  height: 8px;
  border-radius: 999px;
  background: rgba(219, 230, 241, 0.9);
  overflow: hidden;
}

.district-progress i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #2f6fd3, #77b6ff);
}

.bar-row {
  display: grid;
  gap: 10px;
}

.bar-row__head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
}

.bar-track {
  display: flex;
  height: 14px;
  border-radius: 999px;
  overflow: hidden;
  background: rgba(226, 236, 246, 0.96);
}

.bar-track i {
  display: block;
  height: 100%;
}

.bar-track__occupied {
  background: linear-gradient(90deg, #234f88, #2f6fd3);
}

.bar-track__free {
  background: linear-gradient(90deg, #85d9ff, #64f0df);
}

.trend-row {
  align-items: center;
}

.trend-row__rank {
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  border-radius: 14px;
  background: #eaf3ff;
  color: #2f6fd3;
  font-weight: 700;
}

.trend-row__main {
  flex: 1;
}

.trend-row__tail span {
  font-weight: 700;
  color: #183452;
}

.alert-row[data-level="critical"] {
  border-color: rgba(251, 146, 60, 0.28);
}

.alert-row[data-level="warning"] {
  border-color: rgba(250, 204, 21, 0.28);
}

@media (max-width: 1180px) {
  .metric-grid,
  .dashboard-grid,
  .dashboard-grid--wide,
  .overview-grid,
  .overview-stats {
    grid-template-columns: 1fr;
  }

  .screen-hero {
    grid-template-columns: 1fr;
  }
}
</style>
