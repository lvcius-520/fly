<script setup lang="ts">
defineProps<{
  label: string;
  value: string | number;
  hint?: string;
  accent?: "cyan" | "lime" | "amber" | "violet";
}>();
</script>

<template>
  <article class="metric-card" :data-accent="accent ?? 'cyan'">
    <span class="metric-label">{{ label }}</span>
    <strong class="metric-value">{{ value }}</strong>
    <small v-if="hint" class="metric-hint">{{ hint }}</small>
  </article>
</template>

<style scoped>
.metric-card {
  position: relative;
  overflow: hidden;
  border: 1px solid rgba(204, 219, 236, 0.95);
  border-radius: 28px;
  padding: 20px;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(244, 249, 255, 0.98)),
    radial-gradient(circle at top right, rgba(58, 191, 248, 0.12), transparent 45%);
  box-shadow: 0 18px 42px rgba(55, 94, 138, 0.08);
}

.metric-card::after {
  content: "";
  position: absolute;
  inset: auto -12% -30% auto;
  width: 120px;
  height: 120px;
  border-radius: 50%;
  background: rgba(58, 191, 248, 0.12);
  filter: blur(8px);
}

.metric-card[data-accent="lime"]::after {
  background: rgba(94, 234, 212, 0.14);
}

.metric-card[data-accent="amber"]::after {
  background: rgba(251, 191, 36, 0.16);
}

.metric-card[data-accent="violet"]::after {
  background: rgba(167, 139, 250, 0.16);
}

.metric-label,
.metric-hint {
  display: block;
  position: relative;
  z-index: 1;
}

.metric-label {
  color: #6f89a5;
  font-size: 13px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.metric-value {
  position: relative;
  z-index: 1;
  display: block;
  margin: 14px 0 8px;
  font-size: 34px;
  line-height: 1;
  color: #173451;
}

.metric-hint {
  color: #67829f;
  font-size: 12px;
}
</style>
