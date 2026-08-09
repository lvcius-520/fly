<script setup lang="ts">
import { computed } from "vue";
import { RouterLink, RouterView, useRoute, useRouter } from "vue-router";
import brandLogo from "./assets/智巡停GIS系统 (1).png";
import { apiFetch } from "./composables/useApi";
import { useAuth } from "./composables/useAuth";

const route = useRoute();
const router = useRouter();
const { currentUser, isAdmin, logoutLocal } = useAuth();

type AdminNavItem = {
  to: string;
  label: string;
};

const adminNavItems: AdminNavItem[] = [
  { to: "/ops/overview", label: "数据大屏" },
  { to: "/ops/dispatch", label: "巡检调度" },
  { to: "/ops/monitor", label: "动态监测" },
  { to: "/ops/analysis#analysis-demo", label: "舆情分析" },
  { to: "/ops/users", label: "用户管理" }
];

const userModules = [
  { to: "/h5/recommendations", label: "推荐停车场" },
  { to: "/h5/navigation", label: "附近停车导航" },
  { to: "/h5/opinions", label: "舆情评论" }
];

const layout = computed(() => String(route.meta.layout ?? "public"));
const isPublicHome = computed(() => layout.value === "public" && route.path === "/");

async function logout() {
  try {
    await apiFetch("/api/auth/logout", { method: "POST" });
  } catch {
    // ignore network errors on logout
  } finally {
    logoutLocal();
    await router.push("/");
  }
}
</script>

<template>
  <div class="app-shell" :class="`layout-${layout}`">
    <template v-if="layout === 'admin'">
      <main class="admin-main">
        <header class="admin-topbar">
          <RouterLink class="public-brand" to="/">
            <img class="brand-logo brand-logo--admin" :src="brandLogo" alt="遥翼智图" />
          </RouterLink>

          <nav class="admin-topnav">
            <RouterLink
              v-for="item in adminNavItems"
              :key="item.to"
              :to="item.to"
              class="admin-topnav__link"
            >
              {{ item.label }}
            </RouterLink>
          </nav>

          <div class="admin-topbar__actions">
            <div v-if="currentUser" class="profile-card">
              <strong>{{ currentUser.displayName }}</strong>
              <span>{{ currentUser.role === "ADMIN" ? "管理员" : "普通用户" }}</span>
            </div>
            <RouterLink class="ghost-link" to="/">返回首页</RouterLink>
            <button class="secondary-button" @click="logout">退出登录</button>
          </div>
        </header>
        <RouterView />
      </main>
    </template>

    <template v-else-if="layout === 'auth'">
      <main class="auth-main auth-main--immersive">
        <RouterView />
      </main>
    </template>

    <template v-else-if="layout === 'user'">
      <main class="admin-main user-main">
        <header class="admin-topbar">
          <RouterLink class="public-brand" to="/">
            <img class="brand-logo brand-logo--admin" :src="brandLogo" alt="遥翼智图" />
          </RouterLink>

          <nav class="admin-topnav">
            <RouterLink v-for="item in userModules" :key="item.to" :to="item.to" class="admin-topnav__link">
              {{ item.label }}
            </RouterLink>
          </nav>

          <div class="admin-topbar__actions">
            <div v-if="currentUser" class="profile-card">
              <strong>{{ currentUser.displayName }}</strong>
              <span>普通用户</span>
            </div>
            <RouterLink class="ghost-link" to="/">产品首页</RouterLink>
            <RouterLink v-if="isAdmin" class="ghost-link" to="/ops/overview">进入后台</RouterLink>
            <button v-if="currentUser" class="secondary-button" @click="logout">退出登录</button>
            <RouterLink v-else class="button-link" to="/login?mode=user&tab=login">登录</RouterLink>
          </div>
        </header>
        <section class="user-content">
          <RouterView />
        </section>
      </main>
    </template>

    <template v-else>
      <main class="public-main" :class="{ 'public-main--home': isPublicHome }">
        <header v-if="isPublicHome" class="home-floating-header">
          <RouterLink class="public-brand public-brand--home" to="/">
            <img class="brand-logo brand-logo--home" :src="brandLogo" alt="遥翼智图" />
          </RouterLink>
          <RouterLink class="auth-avatar-link auth-avatar-link--home" to="/login?mode=user&tab=login" aria-label="进入登录注册页" title="登录 / 注册">
            <span class="auth-avatar-icon">
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <path
                  d="M12 12a4.25 4.25 0 1 0-4.25-4.25A4.25 4.25 0 0 0 12 12Zm0 2c-4.11 0-7.5 2.32-7.5 5.18 0 .46.37.82.82.82h13.36a.82.82 0 0 0 .82-.82C19.5 16.32 16.11 14 12 14Z"
                  fill="currentColor"
                />
              </svg>
            </span>
            <span class="auth-avatar-text">登录 / 注册</span>
          </RouterLink>
        </header>
        <header v-else class="public-topbar">
          <RouterLink class="public-brand" to="/">
            <img class="brand-logo" :src="brandLogo" alt="遥翼智图" />
          </RouterLink>
          <div class="public-actions">
            <RouterLink class="auth-avatar-link" to="/login?mode=user&tab=login" aria-label="进入登录注册页" title="登录 / 注册">
              <span class="auth-avatar-icon">
                <svg viewBox="0 0 24 24" aria-hidden="true">
                  <path
                    d="M12 12a4.25 4.25 0 1 0-4.25-4.25A4.25 4.25 0 0 0 12 12Zm0 2c-4.11 0-7.5 2.32-7.5 5.18 0 .46.37.82.82.82h13.36a.82.82 0 0 0 .82-.82C19.5 16.32 16.11 14 12 14Z"
                  fill="currentColor"
                />
              </svg>
            </span>
            <span class="auth-avatar-text">登录 / 注册</span>
          </RouterLink>
          </div>
        </header>
        <RouterView />
      </main>
    </template>
  </div>
</template>
