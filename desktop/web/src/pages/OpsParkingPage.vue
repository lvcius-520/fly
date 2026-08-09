<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import PanelCard from "../components/PanelCard.vue";
import StatusPill from "../components/StatusPill.vue";
import { apiFetch } from "../composables/useApi";
import type { ParkingLot, ParkingLotDetailResponse } from "../types";
import { formatRate, formatTime, occupancyTone } from "../utils/parking";

const parkingLots = ref<ParkingLot[]>([]);
const selectedLotId = ref("");
const detail = ref<ParkingLotDetailResponse | null>(null);

const selectedLot = computed(() => parkingLots.value.find((item) => item.id === selectedLotId.value) ?? null);

async function loadLots() {
  parkingLots.value = await apiFetch<ParkingLot[]>("/api/parking-lots");
  if (!selectedLotId.value && parkingLots.value[0]) {
    selectedLotId.value = parkingLots.value[0].id;
  }
  if (selectedLotId.value) {
    detail.value = await apiFetch<ParkingLotDetailResponse>(`/api/parking-lots/${selectedLotId.value}`);
  }
}

async function selectLot(id: string) {
  selectedLotId.value = id;
  detail.value = await apiFetch<ParkingLotDetailResponse>(`/api/parking-lots/${id}`);
}

onMounted(loadLots);
</script>

<template>
  <div class="page-grid">
    <div class="layout-grid">
      <PanelCard title="停车场清单" subtitle="按巡检片区与资源负载管理停车场">
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
      </PanelCard>

      <PanelCard
        v-if="detail && selectedLot"
        :title="selectedLot.name"
        subtitle="停车场档案、车位状态与最近巡检结果"
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
          <article
            v-for="space in detail.spaces"
            :key="space.id"
            class="space-cell"
            :data-status="space.status"
          >
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
  </div>
</template>

<style scoped>
.page-grid,
.layout-grid {
  display: grid;
  gap: 18px;
}

.layout-grid {
  grid-template-columns: 340px 1fr;
}

.lot-button {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  text-align: left;
  padding: 16px;
  border-radius: 22px;
  border: 1px solid rgba(214, 225, 238, 0.95);
  background: #f8fbff;
}

.lot-button.active {
  background: #eaf3ff;
}

.lot-button p,
.detail-head p,
.detail-head small {
  margin: 6px 0 0;
  color: #6a839f;
}

.detail-head,
.detail-columns {
  display: grid;
  gap: 18px;
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
  border-color: rgba(94, 234, 212, 0.2);
}

.space-cell[data-status="occupied"] {
  border-color: rgba(251, 146, 60, 0.2);
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

@media (max-width: 1100px) {
  .layout-grid,
  .detail-columns {
    grid-template-columns: 1fr;
  }

  .space-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
