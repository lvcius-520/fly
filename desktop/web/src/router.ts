import { createRouter, createWebHistory } from "vue-router";
import LandingPage from "./pages/LandingPage.vue";
import LoginPage from "./pages/LoginPage.vue";
import OpsOverviewPage from "./pages/OpsOverviewPage.vue";
import OpsDispatchPage from "./pages/OpsDispatchPage.vue";
import OpsMonitorPage from "./pages/OpsMonitorPage.vue";
import OpsAnalysisPage from "./pages/OpsAnalysisPage.vue";
import OpsUsersPage from "./pages/OpsUsersPage.vue";
import H5RecommendationPage from "./pages/H5RecommendationPage.vue";
import H5NavigationPage from "./pages/H5NavigationPage.vue";
import H5ParkingDetailPage from "./pages/H5ParkingDetailPage.vue";
import H5OpinionsPage from "./pages/H5OpinionsPage.vue";

const router = createRouter({
  history: createWebHistory(),
  scrollBehavior(to, _from, savedPosition) {
    if (savedPosition) {
      return savedPosition;
    }

    if (to.hash) {
      return {
        el: to.hash,
        top: 96,
        behavior: "smooth"
      };
    }

    return { top: 0 };
  },
  routes: [
    { path: "/", component: LandingPage, meta: { title: "首页", layout: "public" } },
    { path: "/login", component: LoginPage, meta: { title: "登录注册", layout: "auth" } },
    { path: "/ops/overview", component: OpsOverviewPage, meta: { title: "数据大屏", layout: "admin", requiresAdmin: true } },
    { path: "/ops/dispatch", component: OpsDispatchPage, meta: { title: "巡检调度", layout: "admin", requiresAdmin: true } },
    { path: "/ops/monitor", component: OpsMonitorPage, meta: { title: "动态监测", layout: "admin", requiresAdmin: true } },
    { path: "/ops/parking", redirect: "/ops/monitor" },
    { path: "/ops/device", redirect: "/ops/monitor" },
    { path: "/ops/analysis", component: OpsAnalysisPage, meta: { title: "舆情分析", layout: "admin", requiresAdmin: true } },
    { path: "/ops/users", component: OpsUsersPage, meta: { title: "用户管理", layout: "admin", requiresAdmin: true } },
    { path: "/h5/home", redirect: "/h5/recommendations" },
    { path: "/h5/recommendations", component: H5RecommendationPage, meta: { title: "推荐停车场", layout: "user" } },
    { path: "/h5/navigation", component: H5NavigationPage, meta: { title: "附近停车导航", layout: "user" } },
    { path: "/h5/opinions", component: H5OpinionsPage, meta: { title: "舆情评论", layout: "user" } },
    { path: "/h5/parking/:id", component: H5ParkingDetailPage, meta: { title: "停车场详情", layout: "user" } }
  ]
});

function readAuth() {
  const raw = window.localStorage.getItem("fly-ops-auth");
  if (!raw) return null;
  try {
    return JSON.parse(raw) as { user?: { role?: string } };
  } catch {
    window.localStorage.removeItem("fly-ops-auth");
    return null;
  }
}

function roleHome(role?: string) {
  return role === "ADMIN" ? "/ops/overview" : "/h5/recommendations";
}

router.beforeEach((to) => {
  const auth = readAuth();
  const role = auth?.user?.role;
  const isLoggedIn = Boolean(role);

  if (to.path === "/login" && isLoggedIn) {
    return roleHome(role);
  }

  if (to.meta.requiresAdmin && role !== "ADMIN") {
    return isLoggedIn ? roleHome(role) : "/login?mode=admin";
  }

  if (to.meta.layout === "user" && !isLoggedIn) {
    return true;
  }

  return true;
});

export default router;
