<script setup lang="ts">
import { onMounted, ref } from "vue";
import PanelCard from "../components/PanelCard.vue";
import StatusPill from "../components/StatusPill.vue";
import { apiFetch } from "../composables/useApi";
import type { AirportStation, DeviceView, MediaFileItem, TelemetryPoint } from "../types";
import { formatTime } from "../utils/parking";

const airports = ref<AirportStation[]>([]);
const devices = ref<DeviceView[]>([]);
const telemetry = ref<TelemetryPoint[]>([]);
const media = ref<MediaFileItem[]>([]);

async function loadData() {
  const [airportData, deviceData, telemetryData, mediaData] = await Promise.all([
    apiFetch<AirportStation[]>("/api/airports"),
    apiFetch<DeviceView[]>("/api/devices"),
    apiFetch<TelemetryPoint[]>("/api/telemetry?limit=6"),
    apiFetch<MediaFileItem[]>("/api/media")
  ]);

  airports.value = airportData;
  devices.value = deviceData;
  telemetry.value = telemetryData;
  media.value = mediaData;
}

async function toggleSimulator(device: DeviceView) {
  const path = device.simulatorRunning
    ? `/api/devices/${device.id}/simulator/stop`
    : `/api/devices/${device.id}/simulator/start`;
  await apiFetch(path, { method: "POST" });
  await loadData();
}

onMounted(loadData);
</script>

<template>
  <div class="page-grid">
    <PanelCard title="机场健康状态" subtitle="无人机机场作为巡检任务的区域起点">
      <div class="airport-grid">
        <article v-for="airport in airports" :key="airport.id" class="airport-card">
          <div>
            <strong>{{ airport.name }}</strong>
            <p>覆盖半径 {{ airport.coverageRadiusMeters }} 米</p>
          </div>
          <StatusPill :text="airport.status" :tone="airport.status === 'online' ? 'low' : 'medium'" />
        </article>
      </div>
    </PanelCard>

    <PanelCard title="无人机监控" subtitle="查看设备在线情况、电量、卫星数与模拟执行能力">
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

    <div class="dual-grid">
      <PanelCard title="最新遥测" subtitle="用于展示飞行轨迹与设备健康">
        <article v-for="item in telemetry" :key="item.id" class="data-row">
          <div>
            <strong>{{ item.deviceId }}</strong>
            <p>{{ item.lat.toFixed(4) }}, {{ item.lon.toFixed(4) }}</p>
          </div>
          <div class="data-meta">
            <span>{{ item.altitudeMeters.toFixed(1) }} m</span>
            <small>{{ formatTime(item.timestamp) }}</small>
          </div>
        </article>
      </PanelCard>

      <PanelCard title="最近媒体" subtitle="巡检拍摄影像与识别源图">
        <article v-for="item in media" :key="item.id" class="data-row">
          <div>
            <strong>{{ item.name }}</strong>
            <p>{{ item.deviceId }}</p>
          </div>
          <div class="data-meta">
            <span>{{ item.type }}</span>
            <small>{{ formatTime(item.createdAt) }}</small>
          </div>
        </article>
      </PanelCard>
    </div>
  </div>
</template>

<style scoped>
.page-grid,
.airport-grid,
.device-grid,
.dual-grid {
  display: grid;
  gap: 18px;
}

.airport-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.device-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.dual-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.airport-card,
.device-card,
.data-row {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 16px;
  border-radius: 22px;
  background: #f8fbff;
  border: 1px solid rgba(214, 225, 238, 0.95);
}

.airport-card p,
.device-head p,
.data-row p,
.device-stats span,
.data-meta small {
  margin: 6px 0 0;
  color: #6a839f;
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

.data-meta {
  display: grid;
  justify-items: end;
  gap: 8px;
}

@media (max-width: 1100px) {
  .airport-grid,
  .device-grid,
  .dual-grid {
    grid-template-columns: 1fr;
  }
}
</style>
