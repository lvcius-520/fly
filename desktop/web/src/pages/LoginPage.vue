<script setup lang="ts">
import { computed, reactive, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import authBackground from "../assets/background.jpeg";
import brandLogo from "../assets/智巡停GIS系统 (1).png";
import { apiFetch } from "../composables/useApi";
import { useAuth } from "../composables/useAuth";
import type { LoginResponse } from "../types";

const route = useRoute();
const router = useRouter();
const { applyLogin } = useAuth();

const loading = ref(false);
const error = ref("");
const successMessage = ref("");
const entryMode = ref<"user" | "admin">(route.query.mode === "admin" ? "admin" : "user");
const activeTab = ref<"login" | "register">(route.query.tab === "register" ? "register" : "login");

const loginForm = reactive({
  username: "",
  password: ""
});

const registerForm = reactive({
  username: "",
  password: "",
  confirmPassword: ""
});

watch(
  () => route.query,
  (query) => {
    entryMode.value = query.mode === "admin" ? "admin" : "user";
    activeTab.value = query.tab === "register" ? "register" : "login";
  },
  { immediate: true }
);

watch(entryMode, (mode) => {
  if (mode === "admin" && activeTab.value === "register") {
    activeTab.value = "login";
  }
});

const entryTitle = computed(() =>
  entryMode.value === "admin" ? "管理员入口" : "普通用户入口"
);

const entryDescription = computed(() =>
  entryMode.value === "admin"
    ? "管理员登录后进入后台工作台，通过侧边栏管理巡检调度、设备和用户。"
    : "普通用户可以登录后查看停车场信息与空位推荐，也可以直接注册新账号。"
);

async function switchMode(mode: "user" | "admin", tab: "login" | "register" = activeTab.value) {
  await router.replace({ path: "/login", query: { mode, tab } });
}

async function switchTab(tab: "login" | "register") {
  await router.replace({ path: "/login", query: { mode: entryMode.value, tab } });
}

async function submitLogin() {
  loading.value = true;
  error.value = "";
  successMessage.value = "";
  try {
    const response = await apiFetch<LoginResponse>("/api/auth/login", {
      method: "POST",
      body: JSON.stringify({
        username: loginForm.username,
        password: loginForm.password,
        loginAs: entryMode.value === "admin" ? "ADMIN" : "OPERATOR"
      })
    });
    applyLogin(response);
    await router.push(response.user.role === "ADMIN" ? "/ops/overview" : "/h5/recommendations");
  } catch (exception) {
    error.value = exception instanceof Error ? exception.message : "登录失败";
  } finally {
    loading.value = false;
  }
}

async function submitRegister() {
  if (registerForm.password !== registerForm.confirmPassword) {
    error.value = "两次输入的密码不一致";
    return;
  }

  loading.value = true;
  error.value = "";
  successMessage.value = "";
  try {
    const response = await apiFetch<LoginResponse>("/api/auth/register", {
      method: "POST",
      body: JSON.stringify({
        username: registerForm.username,
        password: registerForm.password
      })
    });
    applyLogin(response);
    successMessage.value = "注册成功，已自动登录";
    await router.push("/h5/recommendations");
  } catch (exception) {
    error.value = exception instanceof Error ? exception.message : "注册失败";
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <section class="auth-page" :style="{ backgroundImage: `linear-gradient(135deg, rgba(239, 246, 255, 0.42), rgba(247, 250, 255, 0.28)), url('${authBackground}')` }">
    <div class="auth-page__overlay">
      <div class="auth-layout">
        <div class="auth-intro">
          <RouterLink class="auth-logo-link" to="/">
            <img class="auth-logo" :src="brandLogo" alt="遥翼智图" />
          </RouterLink>
          <span class="auth-badge">{{ entryTitle }}</span>
          <h1>连接智慧停车服务与全域巡检管理</h1>
        </div>

        <div class="auth-card">
          <div class="mode-switch">
            <button class="mode-switch__item" :class="{ active: entryMode === 'user' }" @click="switchMode('user')">
              普通用户
            </button>
            <button class="mode-switch__item" :class="{ active: entryMode === 'admin' }" @click="switchMode('admin', 'login')">
              管理员
            </button>
          </div>

          <div class="tab-switch">
            <button class="tab-switch__item" :class="{ active: activeTab === 'login' }" @click="switchTab('login')">
              登录
            </button>
            <button
              class="tab-switch__item"
              :class="{ active: activeTab === 'register' }"
              :disabled="entryMode === 'admin'"
              @click="switchTab('register')"
            >
              注册
            </button>
          </div>

          <div v-if="activeTab === 'login'" class="auth-form">
            <label>
              用户名
              <input v-model="loginForm.username" autocomplete="username" placeholder="请输入用户名" />
            </label>
            <label>
              密码
              <input v-model="loginForm.password" type="password" autocomplete="current-password" placeholder="请输入密码" />
            </label>
            <button :disabled="loading" @click="submitLogin">
              {{ loading ? "登录中..." : entryMode === "admin" ? "进入管理员后台" : "进入普通用户中心" }}
            </button>
          </div>

          <div v-else class="auth-form">
            <label>
              用户名
              <input v-model="registerForm.username" autocomplete="username" placeholder="至少 3 位字符" />
            </label>
            <label>
              密码
              <input v-model="registerForm.password" type="password" autocomplete="new-password" placeholder="至少 6 位字符" />
            </label>
            <label>
              确认密码
              <input v-model="registerForm.confirmPassword" type="password" autocomplete="new-password" placeholder="请再次输入密码" />
            </label>
            <button :disabled="loading" @click="submitRegister">
              {{ loading ? "注册中..." : "注册普通用户" }}
            </button>
          </div>

          <p class="tips">
            管理员默认账号：<code>admin</code>，默认密码：<code>admin123</code>。管理员账号不能通过公开注册获得。
          </p>
          <p v-if="error" class="error">{{ error }}</p>
          <p v-if="successMessage" class="success">{{ successMessage }}</p>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.auth-page {
  min-height: 100vh;
  background-position: center;
  background-size: cover;
  background-repeat: no-repeat;
}

.auth-page__overlay {
  min-height: 100vh;
  padding: 40px 24px;
}

.auth-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 440px;
  gap: 48px;
  align-items: center;
  width: min(1280px, 100%);
  min-height: calc(100vh - 80px);
  margin: 0 auto;
}

.auth-intro {
  display: grid;
  gap: 18px;
  align-content: center;
  padding: 28px 12px 28px 8px;
  max-width: 620px;
}

.auth-logo-link {
  display: inline-flex;
  width: fit-content;
}

.auth-logo {
  width: auto;
  height: 96px;
  object-fit: contain;
  filter: drop-shadow(0 18px 36px rgba(44, 87, 137, 0.12));
}

.auth-card {
  padding: 32px;
  border-radius: 30px;
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid rgba(208, 221, 236, 0.98);
  box-shadow: 0 26px 60px rgba(40, 84, 130, 0.14);
  backdrop-filter: blur(16px);
}

.auth-badge {
  display: inline-flex;
  padding: 8px 14px;
  border-radius: 999px;
  background: #eef5ff;
  color: #2866b1;
  font-size: 13px;
  font-weight: 700;
}

.auth-intro h1 {
  margin: 0;
  font-size: clamp(36px, 4.6vw, 58px);
  line-height: 1.08;
  color: #173452;
}

.auth-intro p,
.auth-intro li,
.tips {
  color: #6a839f;
  line-height: 1.8;
}

.auth-intro ul {
  margin: 8px 0 0;
  padding-left: 20px;
}

.mode-switch,
.tab-switch {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.tab-switch {
  margin-top: 14px;
}

.mode-switch__item,
.tab-switch__item {
  min-height: 44px;
  background: #f6f9fd;
  color: #466a91;
  box-shadow: none;
  border: 1px solid #d8e3ef;
}

.mode-switch__item.active,
.tab-switch__item.active {
  background: linear-gradient(135deg, #3b82f6, #2f6fd3);
  color: #ffffff;
}

.auth-form {
  display: grid;
  gap: 14px;
  margin-top: 18px;
}

label {
  display: grid;
  gap: 8px;
  color: #4d6b8c;
}

.tips,
.error,
.success {
  margin: 16px 0 0;
}

.error {
  color: #d26a3e;
}

.success {
  color: #228b62;
}

@media (max-width: 980px) {
  .auth-page__overlay {
    padding: 18px;
  }

  .auth-layout {
    grid-template-columns: 1fr;
    gap: 18px;
    min-height: auto;
  }

  .auth-intro,
  .auth-card {
    padding: 24px;
  }

  .auth-logo {
    height: 72px;
  }
}
</style>
