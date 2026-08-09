<script setup lang="ts">
defineProps<{
  text: string;
  tone?: "low" | "medium" | "high" | "neutral";
}>();
</script>

<template>
  <span class="status-pill" :data-tone="tone ?? 'neutral'">{{ text }}</span>
</template>

<style scoped>
.status-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 28px;
  padding: 0 12px;
  border-radius: 999px;
  border: 1px solid #d9e4ef;
  background: #f6f9fd;
  color: #587593;
  font-size: 12px;
}

.status-pill[data-tone="low"] {
  color: #23855f;
  background: #ecfbf4;
}

.status-pill[data-tone="medium"] {
  color: #9a7311;
  background: #fff8e7;
}

.status-pill[data-tone="high"] {
  color: #c2673b;
  background: #fff1ea;
}
</style>
