<script setup lang="ts">
defineProps<{
  title: string;
  subtitle?: string;
}>();
</script>

<template>
  <section class="panel-card">
    <header class="panel-head">
      <div>
        <h3>{{ title }}</h3>
        <p v-if="subtitle">{{ subtitle }}</p>
      </div>
      <slot name="actions" />
    </header>
    <div class="panel-body">
      <slot />
    </div>
  </section>
</template>

<style scoped>
.panel-card {
  border: 1px solid rgba(207, 220, 236, 0.95);
  border-radius: 28px;
  padding: 20px;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(246, 250, 255, 0.98)),
    radial-gradient(circle at top left, rgba(56, 189, 248, 0.08), transparent 40%);
  box-shadow:
    0 18px 42px rgba(55, 94, 138, 0.08);
}

.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 18px;
}

.panel-head h3 {
  margin: 0;
  font-size: 18px;
  color: #183452;
}

.panel-head p {
  margin: 8px 0 0;
  font-size: 13px;
  color: #6a839f;
}

.panel-body {
  display: grid;
  gap: 16px;
}
</style>
