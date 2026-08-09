<script setup lang="ts">
import type { H5ParkingCard } from "../types";
import { formatDistance, formatRate } from "../utils/parking";

defineProps<{
  parking: H5ParkingCard;
}>();
</script>

<template>
  <article class="parking-card">
    <div class="parking-card__head">
      <div>
        <h3>{{ parking.name }}</h3>
        <p>{{ parking.address }}</p>
      </div>
      <RouterLink class="parking-card__link" :to="`/h5/parking/${parking.id}`">详情</RouterLink>
    </div>

    <div class="parking-card__stats">
      <div>
        <span>空位</span>
        <strong>{{ parking.availableSpaces }}</strong>
      </div>
      <div>
        <span>总车位</span>
        <strong>{{ parking.totalSpaces }}</strong>
      </div>
      <div>
        <span>距离</span>
        <strong>{{ formatDistance(parking.distanceKm) }}</strong>
      </div>
      <div>
        <span>占用率</span>
        <strong>{{ formatRate(parking.occupancyRate) }}</strong>
      </div>
    </div>

    <div class="parking-card__tags">
      <span v-for="tag in parking.tags" :key="tag">{{ tag }}</span>
    </div>
  </article>
</template>

<style scoped>
.parking-card {
  display: grid;
  gap: 16px;
  border-radius: 24px;
  padding: 18px;
  background: rgba(251, 252, 255, 0.94);
  border: 1px solid rgba(12, 28, 49, 0.08);
  box-shadow: 0 18px 34px rgba(32, 56, 85, 0.08);
}

.parking-card__head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.parking-card__head h3 {
  margin: 0;
  font-size: 18px;
  color: #10233f;
}

.parking-card__head p {
  margin: 8px 0 0;
  color: #5f738e;
  font-size: 13px;
}

.parking-card__link {
  align-self: start;
  padding: 10px 14px;
  border-radius: 999px;
  background: #103455;
  color: #fff;
  text-decoration: none;
  font-size: 13px;
}

.parking-card__stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.parking-card__stats div {
  padding: 12px;
  border-radius: 18px;
  background: #eff5fb;
}

.parking-card__stats span,
.parking-card__tags span {
  color: #5f738e;
  font-size: 12px;
}

.parking-card__stats strong {
  display: block;
  margin-top: 8px;
  color: #10233f;
}

.parking-card__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.parking-card__tags span {
  padding: 6px 10px;
  border-radius: 999px;
  background: rgba(16, 52, 85, 0.08);
}

@media (max-width: 768px) {
  .parking-card__stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
