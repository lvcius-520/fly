<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import PanelCard from "../components/PanelCard.vue";
import StatusPill from "../components/StatusPill.vue";
import { apiFetch } from "../composables/useApi";
import type { AirportStation, DeviceView, ParkingLot, ParkingLotDetailResponse, TelemetryPoint } from "../types";
import { formatRate, formatTime, occupancyTone } from "../utils/parking";

const parkingLots = ref<ParkingLot[]>([]);
const selectedLotId = ref("");
const detail = ref<ParkingLotDetailResponse | null>(null);
const airports = ref<AirportStation[]>([]);
const devices = ref<DeviceView[]>([]);
const telemetry = ref<TelemetryPoint[]>([]);

const selectedLot = computed(() => parkingLots.value.find((item) => item.id === selectedLotId.value) ?? null);
const onlineDevices = computed(() => devices.value.filter((item) => item.status !== "offline").length);
const busyLots = computed(() => parkingLots.value.filter((item) => item.occupancyRate >= 0.8).length);

async function loadParkingLots() {
  parkingLots.value = await apiFetch<ParkingLot[]>("/api/parking-lots");
  if (!selectedLotId.value && parkingLots.value[0]) {
    selectedLotId.value = parkingLots.value[0].id;
  }
  if (selectedLotId.value) {
    detail.value = await apiFetch<ParkingLotDetailResponse>(`/api/parking-lots/${selectedLotId.value}`);
  }
}

async function loadMonitorData() {
  const [airportData, deviceData, telemetryData] = await Promise.all([
    apiFetch<AirportStation[]>("/api/airports"),
    apiFetch<DeviceView[]>("/api/devices"),
    apiFetch<TelemetryPoint[]>("/api/telemetry?limit=8")
  ]);
  airports.value = airportData;
  devices.value = deviceData;
  telemetry.value = telemetryData;
}

async function selectLot(id: string) {
  selectedLotId.value = id;
  detail.value = await apiFetch<ParkingLotDetailResponse>(`/api/parking-lots/${id}`);
}

async function toggleSimulator(device: DeviceView) {
  const path = device.simulatorRunning
    ? `/api/devices/${device.id}/simulator/stop`
    : `/api/devices/${device.id}/simulator/start`;
  await apiFetch(path, { method: "POST" });
  await loadMonitorData();
}

onMounted(async () => {
  await Promise.all([loadParkingLots(), loadMonitorData()]);
});
</script>

<template>
  <div class="page-grid">
    <section class="summary-strip">
      <article class="summary-card">
        <span>重点停车场</span>
        <strong>{{ parkingLots.length }}</strong>
        <small>覆盖苏州市当前纳入巡检体系的停车资源</small>
      </article>
      <article class="summary-card">
        <span>高负载车场</span>
        <strong>{{ busyLots }}</strong>
        <small>占用率超过 80% 的停车场数量</small>
      </article>
      <article class="summary-card">
        <span>在线设备</span>
        <strong>{{ onlineDevices }}</strong>
        <small>机场与无人机的实时在线状态</small>
      </article>
    </section>

    <div class="monitor-layout">
      <PanelCard title="停车资源动态" subtitle="按车场负载查看资源状态与最近巡检结果">
        <div class="lot-list">
          <button
            v-for="item in parkingLots"
            :key="item.id"
            class="lot-button"
            :class="{ active: item.id === selectedLotId }"
            @click="selectLot(item.id)"
          >
            <div>
              <strong>{{ item.name }}</strong>
              <p>{{ item.address }}</p>
            </div>
            <StatusPill :text="formatRate(item.occupancyRate)" :tone="occupancyTone(item.occupancyRate)" />
          </button>
        </div>
      </PanelCard>

      <PanelCard
        v-if="detail && selectedLot"
        :title="selectedLot.name"
        subtitle="停车场档案、空位状态与任务联动"
      >
        <div class="detail-head">
          <div>
            <p>{{ selectedLot.address }}</p>
            <small>最近巡检：{{ formatTime(selectedLot.lastInspectionAt) }}</small>
          </div>
          <div class="detail-metrics">
            <div><span>空位</span><strong>{{ selectedLot.availableSpaces }}</strong></div>
            <div><span>总车位</span><strong>{{ selectedLot.totalSpaces }}</strong></div>
            <div><span>占用率</span><strong>{{ formatRate(selectedLot.occupancyRate) }}</strong></div>
          </div>
        </div>

        <div class="space-grid">
          <article v-for="space in detail.spaces.slice(0, 12)" :key="space.id" class="space-cell" :data-status="space.status">
            <strong>{{ space.code }}</strong>
            <span>{{ space.status === "free" ? "空闲" : "占用" }}</span>
          </article>
        </div>

        <div class="detail-columns">
          <div>
            <h4>关联任务</h4>
            <article v-for="mission in detail.missions" :key="mission.id" class="sub-row">
              <strong>{{ mission.name }}</strong>
              <span>{{ mission.status }}</span>
            </article>
          </div>
          <div>
            <h4>识别结果</h4>
            <article v-for="item in detail.detections" :key="item.id" class="sub-row">
              <strong>{{ item.label }}</strong>
              <span>{{ (item.score * 100).toFixed(0) }}%</span>
            </article>
          </div>
        </div>
      </PanelCard>
    </div>

    <div class="monitor-grid">
      <PanelCard title="设备与机场状态" subtitle="查看机场健康、无人机在线情况与模拟执行能力">
        <div class="device-grid">
          <article v-for="device in devices" :key="device.id" class="device-card">
            <div class="device-head">
              <div>
                <strong>{{ device.name }}</strong>
                <p>{{ device.code }}</p>
              </div>
              <StatusPill :text="device.status" :tone="device.status === 'executing' ? 'medium' : 'low'" />
            </div>
            <div class="device-stats">
              <span>电量 {{ device.batteryPercent }}%</span>
              <span>卫星 {{ device.satelliteCount }}</span>
              <span>最近上报 {{ formatTime(device.lastSeenAt) }}</span>
            </div>
            <button @click="toggleSimulator(device)">
              {{ device.simulatorRunning ? "停止模拟飞行" : "启动模拟飞行" }}
            </button>
          </article>
        </div>
      </PanelCard>

      <PanelCard title="机场与遥测播报" subtitle="同步展示机场运行状态与最近飞行遥测">
        <div class="airport-grid">
          <article v-for="airport in airports" :key="airport.id" class="airport-card">
            <div>
              <strong>{{ airport.name }}</strong>
              <p>覆盖半径 {{ airport.coverageRadiusMeters }} 米</p>
            </div>
            <StatusPill :text="airport.status" :tone="airport.status === 'online' ? 'low' : 'medium'" />
          </article>
        </div>

        <article v-for="item in telemetry" :key="item.id" class="telemetry-row">
          <div>
            <strong>{{ item.deviceId }}</strong>
            <p>{{ item.lat.toFixed(4) }}, {{ item.lon.toFixed(4) }}</p>
          </div>
          <div class="telemetry-meta">
            <span>{{ item.altitudeMeters.toFixed(1) }} m</span>
            <small>{{ formatTime(item.timestamp) }}</small>
          </div>
        </article>
      </PanelCard>
    </div>
  </div>
</template>

<style scoped>
.page-grid,
.monitor-layout,
.monitor-grid,
.summary-strip,
.lot-list,
.detail-head,
.detail-columns,
.device-grid,
.airport-grid {
  display: grid;
  gap: 18px;
}

.summary-strip {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.summary-card {
  padding: 20px 22px;
  border-radius: 26px;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(244, 249, 255, 0.98)),
    radial-gradient(circle at top right, rgba(58, 191, 248, 0.12), transparent 45%);
  border: 1px solid rgba(204, 219, 236, 0.95);
  box-shadow: 0 18px 42px rgba(55, 94, 138, 0.08);
}

.summary-card span,
.summary-card small {
  display: block;
  color: #6a839f;
}

.summary-card strong {
  display: block;
  margin: 12px 0 6px;
  font-size: 32px;
  color: #183452;
}

.monitor-layout {
  grid-template-columns: 320px 1fr;
}

.lot-button,
.device-card,
.airport-card,
.telemetry-row {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  padding: 16px;
  border-radius: 22px;
  border: 1px solid rgba(214, 225, 238, 0.95);
  background: #f8fbff;
}

.lot-button {
  text-align: left;
}

.lot-button.active {
  background: #eaf3ff;
}

.lot-button p,
.detail-head p,
.detail-head small,
.device-head p,
.airport-card p,
.telemetry-row p,
.device-stats span,
.telemetry-meta small {
  margin: 6px 0 0;
  color: #6a839f;
}

.detail-metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.detail-metrics div {
  padding: 12px;
  border-radius: 18px;
  background: #f6f9fd;
}

.detail-metrics span,
.space-cell span,
.sub-row span {
  color: #6a839f;
  font-size: 12px;
}

.detail-metrics strong {
  display: block;
  margin-top: 6px;
}

.space-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.space-cell {
  padding: 14px;
  border-radius: 18px;
  background: #ffffff;
  border: 1px solid rgba(214, 225, 238, 0.95);
}

.space-cell[data-status="free"] {
  border-color: rgba(94, 234, 212, 0.26);
}

.space-cell[data-status="occupied"] {
  border-color: rgba(251, 146, 60, 0.24);
}

.detail-columns {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.sub-row {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  padding: 12px 0;
  border-bottom: 1px solid rgba(220, 229, 239, 0.95);
}

.monitor-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.device-grid,
.airport-grid {
  grid-template-columns: 1fr;
}

.device-card {
  display: grid;
}

.device-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
}

.device-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.telemetry-meta {
  display: grid;
  justify-items: end;
  gap: 8px;
}

@media (max-width: 1180px) {
  .summary-strip,
  .monitor-layout,
  .monitor-grid,
  .detail-columns {
    grid-template-columns: 1fr;
  }

  .space-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
