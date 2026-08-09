<script setup lang="ts">
import { computed } from "vue";
import type { AirportStation, CoverageLink, Device, ParkingLot } from "../types";
import { occupancyTone } from "../utils/parking";

const props = defineProps<{
  parkingLots: ParkingLot[];
  airports: AirportStation[];
  devices: Device[];
  coverageLinks?: CoverageLink[];
  compact?: boolean;
}>();

const width = 760;
const height = computed(() => (props.compact ? 260 : 380));

function normalize(value: number, min: number, max: number, size: number) {
  if (max === min) return size / 2;
  return ((value - min) / (max - min)) * size;
}

const extents = computed(() => {
  const latitudes = props.parkingLots.map((item) => item.latitude);
  const longitudes = props.parkingLots.map((item) => item.longitude);
  return {
    latMin: Math.min(...latitudes),
    latMax: Math.max(...latitudes),
    lonMin: Math.min(...longitudes),
    lonMax: Math.max(...longitudes)
  };
});

const parkingNodes = computed(() =>
  props.parkingLots.map((item) => ({
    ...item,
    x: normalize(item.longitude, extents.value.lonMin, extents.value.lonMax, width - 90) + 40,
    y: height.value - normalize(item.latitude, extents.value.latMin, extents.value.latMax, height.value - 80) - 30
  }))
);

const airportNodes = computed(() =>
  props.airports.map((item) => ({
    ...item,
    x: normalize(item.longitude, extents.value.lonMin, extents.value.lonMax, width - 90) + 40,
    y: height.value - normalize(item.latitude, extents.value.latMin, extents.value.latMax, height.value - 80) - 30
  }))
);
</script>

<template>
  <div class="scene">
    <svg :viewBox="`0 0 ${width} ${height}`" role="img" aria-label="GIS 示意图">
      <defs>
        <linearGradient id="coverage-line" x1="0%" y1="0%" x2="100%" y2="0%">
          <stop offset="0%" stop-color="#38bdf8" stop-opacity="0.22" />
          <stop offset="100%" stop-color="#5eead4" stop-opacity="0.78" />
        </linearGradient>
      </defs>

      <g class="grid">
        <line v-for="x in 8" :key="`x-${x}`" :x1="x * 95" y1="0" :x2="x * 95" :y2="height" />
        <line v-for="y in 5" :key="`y-${y}`" x1="0" :y1="y * 72" :x2="width" :y2="y * 72" />
      </g>

      <g v-if="coverageLinks?.length">
        <line
          v-for="link in coverageLinks"
          :key="`${link.airportStationId}-${link.parkingLotId}`"
          :x1="airportNodes.find((item) => item.id === link.airportStationId)?.x ?? 0"
          :y1="airportNodes.find((item) => item.id === link.airportStationId)?.y ?? 0"
          :x2="parkingNodes.find((item) => item.id === link.parkingLotId)?.x ?? 0"
          :y2="parkingNodes.find((item) => item.id === link.parkingLotId)?.y ?? 0"
          stroke="url(#coverage-line)"
          stroke-width="2"
          stroke-dasharray="6 10"
        />
      </g>

      <g v-for="airport in airportNodes" :key="airport.id">
        <circle class="coverage" :cx="airport.x" :cy="airport.y" r="38" />
        <circle class="airport-dot" :cx="airport.x" :cy="airport.y" r="10" />
      </g>

      <g v-for="lot in parkingNodes" :key="lot.id">
        <rect
          :x="lot.x - 36"
          :y="lot.y - 18"
          width="72"
          height="36"
          rx="12"
          class="parking-block"
          :data-tone="occupancyTone(lot.occupancyRate)"
        />
        <text :x="lot.x" :y="lot.y + 5" class="parking-label">{{ lot.availableSpaces }}</text>
      </g>

      <g v-for="device in devices" :key="device.id">
        <circle
          class="device-dot"
          :cx="airportNodes.find((item) => item.id === device.airportStationId)?.x ?? 0"
          :cy="(airportNodes.find((item) => item.id === device.airportStationId)?.y ?? 0) - 24"
          r="5"
        />
      </g>
    </svg>
  </div>
</template>

<style scoped>
.scene {
  overflow: hidden;
  border-radius: 26px;
  border: 1px solid rgba(208, 221, 236, 0.95);
  background:
    radial-gradient(circle at top right, rgba(56, 189, 248, 0.12), transparent 28%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(242, 248, 255, 0.98));
}

svg {
  width: 100%;
  height: auto;
  display: block;
}

.grid line {
  stroke: rgba(91, 121, 151, 0.16);
}

.coverage {
  fill: rgba(94, 234, 212, 0.06);
  stroke: rgba(94, 234, 212, 0.18);
}

.airport-dot {
  fill: #67e8f9;
}

.parking-block {
  fill: rgba(233, 243, 255, 0.95);
  stroke: rgba(95, 133, 173, 0.28);
}

.parking-block[data-tone="medium"] {
  fill: rgba(91, 58, 9, 0.72);
  stroke: rgba(251, 191, 36, 0.35);
}

.parking-block[data-tone="high"] {
  fill: rgba(82, 31, 12, 0.84);
  stroke: rgba(251, 146, 60, 0.45);
}

.parking-label {
  fill: #f8fbff;
  font-size: 14px;
  font-weight: 700;
  text-anchor: middle;
}

.device-dot {
  fill: #c4b5fd;
}
</style>
