<script setup lang="ts">
import heroImage from "../assets/door1.jpg";

const moduleCards = [
  {
    title: "推荐停车场",
    text: "面向普通用户推荐更合适的停车场，综合空位、距离和便利性给出决策参考。",
    hint: "快速找到更值得前往的停车场"
  },
  {
    title: "附近停车导航",
    text: "围绕当前位置查看附近停车场，并直接规划路线跳转导航，缩短找位时间。",
    hint: "查看路线并一键导航"
  },
  {
    title: "巡检调度管理",
    text: "面向管理员查看停车资源、巡检任务、设备运行和全域管理状态。",
    hint: "支撑后台管理与调度决策"
  }
];

const featureCards = [
  {
    tag: "组网巡检",
    title: "无人机机场分区覆盖",
    text: "根据停车场密度布局机场，一套组网方案服务多个露天停车场。"
  },
  {
    tag: "边缘识别",
    title: "车位空占本地判断",
    text: "无人机到点悬停拍摄，现场完成空位识别，减少持续回传大图。"
  },
  {
    tag: "GIS 联动",
    title: "空间要素统一可视化",
    text: "停车场、车位、机场、航线与态势信息汇聚到一张地图中。"
  }
];
</script>

<template>
  <div class="landing-page">
    <section class="hero-banner" :style="{ backgroundImage: `linear-gradient(180deg, rgba(7, 27, 50, 0.16), rgba(7, 27, 50, 0.52)), url('${heroImage}')` }">
      <div class="hero-banner__inner">
        <div class="hero-title-wall">
          <span class="hero-title-wall__eyebrow">智慧停车 GIS 管理系统</span>
          <strong>遥翼智图</strong>
        </div>

        <div class="hero-copy">
          <h1>以空间信息与边缘智能为底座，构建露天停车场全域巡检与智慧停车服务入口</h1>
        </div>
      </div>
    </section>

    <section class="modules-section">
      <div class="section-copy">
        <p class="section-kicker">功能模块</p>
        <h2>围绕用户查询与后台管理拆分模块，先把核心能力讲清楚</h2>
        <p>
          首页下方只负责介绍系统功能，不打断首屏视觉。普通用户侧聚焦找车位和导航，管理端聚焦巡检、资源和态势管控。
        </p>
      </div>

      <div class="module-grid">
        <article v-for="item in moduleCards" :key="item.title" class="module-card">
          <strong>{{ item.title }}</strong>
          <p>{{ item.text }}</p>
          <span>{{ item.hint }}</span>
        </article>
      </div>
    </section>

    <section class="feature-grid">
      <article v-for="item in featureCards" :key="item.title" class="feature-card">
        <span class="feature-card__tag">{{ item.tag }}</span>
        <h3>{{ item.title }}</h3>
        <p>{{ item.text }}</p>
      </article>
    </section>
  </div>
</template>

<style scoped>
.landing-page {
  display: grid;
  gap: 28px;
}

.hero-banner {
  position: relative;
  width: 100vw;
  margin-left: calc(50% - 50vw);
  min-height: 58vh;
  background-position: center;
  background-size: cover;
}

.hero-banner__inner,
.modules-section,
.feature-grid {
  width: min(1240px, calc(100% - 48px));
  margin: 0 auto;
}

.hero-banner__inner {
  min-height: 58vh;
  display: grid;
  align-content: end;
  gap: 28px;
  padding: 164px 0 66px;
}

.hero-title-wall {
  display: grid;
  gap: 10px;
}

.hero-title-wall__eyebrow,
.section-kicker,
.feature-card__tag {
  display: inline-flex;
  width: fit-content;
  padding: 8px 14px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
}

.hero-title-wall__eyebrow {
  color: rgba(247, 250, 255, 0.95);
  background: rgba(255, 255, 255, 0.12);
  backdrop-filter: blur(10px);
}

.hero-title-wall strong {
  font-size: clamp(72px, 11vw, 144px);
  line-height: 0.95;
  color: rgba(255, 255, 255, 0.78);
  text-shadow: 0 18px 38px rgba(16, 34, 58, 0.28);
  letter-spacing: 0.08em;
}

.hero-copy {
  max-width: 920px;
  display: grid;
  gap: 18px;
}

.hero-copy h1 {
  margin: 0;
  color: #ffffff;
  font-size: clamp(18px, 1.95vw, 26px);
  line-height: 1.35;
  letter-spacing: 0.02em;
  max-width: none;
  white-space: nowrap;
}

.hero-copy p {
  margin: 0;
  color: rgba(245, 249, 255, 0.92);
  line-height: 2;
  font-size: 16px;
  letter-spacing: 0.03em;
  max-width: 760px;
}

.modules-section {
  display: grid;
  grid-template-columns: 0.95fr 1.05fr;
  gap: 18px;
}

.section-copy,
.module-card,
.feature-card {
  background: rgba(255, 255, 255, 0.96);
  border: 1px solid rgba(211, 223, 236, 0.95);
  box-shadow: 0 18px 42px rgba(55, 94, 138, 0.08);
}

.section-copy {
  padding: 30px;
  border-radius: 28px;
}

.section-kicker {
  margin-bottom: 10px;
  background: #edf6ff;
  color: #2c6ab4;
}

.section-copy h2 {
  margin: 0 0 14px;
  color: #18324d;
  font-size: clamp(28px, 3vw, 40px);
  line-height: 1.18;
}

.section-copy p,
.module-card p,
.feature-card p {
  margin: 0;
  color: #68829d;
  line-height: 1.85;
}

.module-grid {
  display: grid;
  gap: 14px;
}

.module-card {
  display: grid;
  gap: 10px;
  padding: 22px;
  border-radius: 24px;
}

.module-card strong,
.feature-card h3 {
  color: #18324d;
  font-size: 21px;
}

.module-card strong,
.module-card span,
.feature-card h3 {
  margin: 0;
}

.module-card span {
  color: #2f6fd3;
  font-weight: 700;
}

.feature-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 10px;
}

.feature-card {
  padding: 24px;
  border-radius: 26px;
}

.feature-card__tag {
  margin-bottom: 14px;
  background: #f0f7ff;
  color: #2f6fd3;
}

@media (max-width: 1080px) {
  .modules-section,
  .feature-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .hero-banner__inner,
  .modules-section,
  .feature-grid {
    width: calc(100% - 24px);
  }

  .hero-banner,
  .hero-banner__inner {
    min-height: 52vh;
  }

  .hero-banner__inner {
    gap: 18px;
    padding: 128px 0 36px;
  }

  .hero-title-wall strong {
    font-size: clamp(52px, 18vw, 88px);
  }
}
</style>
