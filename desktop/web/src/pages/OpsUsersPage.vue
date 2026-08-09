<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import PanelCard from "../components/PanelCard.vue";
import StatusPill from "../components/StatusPill.vue";
import { apiFetch } from "../composables/useApi";
import type { AuthUser } from "../types";
import { formatTime } from "../utils/parking";

const users = ref<AuthUser[]>([]);
const loading = ref(false);
const saving = ref(false);
const editingId = ref<string | null>(null);
const message = ref("");
const createError = ref("");
const editMessage = ref("");
const editError = ref("");

const createForm = reactive({
  username: "",
  displayName: "",
  password: "",
  role: "OPERATOR" as AuthUser["role"],
  enabled: true
});

const editForm = reactive({
  displayName: "",
  password: "",
  role: "OPERATOR" as AuthUser["role"],
  enabled: true
});

const editingUser = computed(() => users.value.find((item) => item.id === editingId.value) ?? null);
const scrollingUsers = computed(() =>
  users.value.length > 1 ? [...users.value, ...users.value] : users.value
);

async function loadUsers() {
  loading.value = true;
  try {
    users.value = await apiFetch<AuthUser[]>("/api/admin/users");
  } finally {
    loading.value = false;
  }
}

async function createUser() {
  saving.value = true;
  message.value = "";
  createError.value = "";
  try {
    const username = createForm.username.trim();
    const displayName = createForm.displayName.trim();
    const password = createForm.password.trim();

    if (username.length < 3) {
      createError.value = "用户名至少需要 3 个字符";
      return;
    }

    if (displayName.length < 2) {
      createError.value = "显示名称至少需要 2 个字符";
      return;
    }

    if (password.length < 6) {
      createError.value = "密码至少需要 6 个字符";
      return;
    }

    await apiFetch<AuthUser>("/api/admin/users", {
      method: "POST",
      body: JSON.stringify({
        username,
        displayName,
        password,
        role: createForm.role,
        enabled: createForm.enabled
      })
    });
    message.value = "用户已创建";
    createForm.username = "";
    createForm.displayName = "";
    createForm.password = "";
    createForm.role = "OPERATOR";
    createForm.enabled = true;
    await loadUsers();
  } catch (exception) {
    createError.value = exception instanceof Error ? exception.message : "创建用户失败";
  } finally {
    saving.value = false;
  }
}

function beginEdit(user: AuthUser) {
  editingId.value = user.id;
  editMessage.value = "";
  editError.value = "";
  editForm.displayName = user.displayName;
  editForm.password = "";
  editForm.role = user.role;
  editForm.enabled = user.enabled;
}

async function saveEdit() {
  if (!editingId.value) return;
  saving.value = true;
  editMessage.value = "";
  editError.value = "";
  try {
    const displayName = editForm.displayName.trim();
    const password = editForm.password.trim();

    if (displayName.length < 2) {
      editError.value = "显示名称至少需要 2 个字符";
      return;
    }

    await apiFetch<AuthUser>(`/api/admin/users/${editingId.value}`, {
      method: "PATCH",
      body: JSON.stringify({
        displayName,
        password: password ? password : null,
        role: editForm.role,
        enabled: editForm.enabled
      })
    });
    editMessage.value = "用户已更新";
    editingId.value = null;
    await loadUsers();
  } catch (exception) {
    editError.value = exception instanceof Error ? exception.message : "保存修改失败";
  } finally {
    saving.value = false;
  }
}

onMounted(loadUsers);
</script>

<template>
  <div class="page-grid">
    <div class="layout-grid">
      <PanelCard title="新增管理端用户" subtitle="只需要账号、密码和角色即可完成配置">
        <label class="field">
          用户名
          <input v-model="createForm.username" placeholder="例如 ops_south_01" />
        </label>
        <label class="field">
          显示名称
          <input v-model="createForm.displayName" placeholder="例如 南区调度员" />
        </label>
        <label class="field">
          密码
          <input v-model="createForm.password" type="password" placeholder="至少 6 位" />
        </label>
        <label class="field">
          角色
          <select v-model="createForm.role">
            <option value="ADMIN">管理员</option>
            <option value="OPERATOR">运营用户</option>
          </select>
        </label>
        <label class="toggle-field">
          <input v-model="createForm.enabled" type="checkbox" />
          启用该账号
        </label>
        <button :disabled="saving" @click="createUser">{{ saving ? "保存中..." : "创建用户" }}</button>
        <p v-if="message" class="message">{{ message }}</p>
        <p v-if="createError" class="error">{{ createError }}</p>
      </PanelCard>

      <PanelCard title="管理端用户列表" subtitle="管理员可维护管理端账号、角色和密码">
        <div class="user-ticker">
          <div class="user-ticker__track" :class="{ 'user-ticker__track--static': users.length <= 1 }">
            <article v-for="(user, index) in scrollingUsers" :key="`${user.id}-${index}`" class="user-row">
              <div class="user-row__main">
                <div>
                  <strong>{{ user.displayName }}</strong>
                  <p>{{ user.username }}</p>
                </div>
                <div class="user-row__meta">
                  <StatusPill :text="user.role === 'ADMIN' ? '管理员' : '运营用户'" :tone="user.role === 'ADMIN' ? 'medium' : 'low'" />
                  <StatusPill :text="user.enabled ? '已启用' : '已停用'" :tone="user.enabled ? 'low' : 'high'" />
                </div>
              </div>

              <small>最近登录：{{ formatTime(user.lastLoginAt) }}</small>

              <button class="secondary-button" @click="beginEdit(user)">编辑账号</button>
            </article>
          </div>
        </div>
      </PanelCard>
    </div>

    <div v-if="editingId" class="edit-modal">
      <div class="edit-modal__backdrop" @click="editingId = null" />
      <div class="edit-modal__dialog">
        <PanelCard title="编辑用户" subtitle="可修改显示名称、角色、状态和密码">
          <template #actions>
            <button class="secondary-button" @click="editingId = null">关闭</button>
          </template>

          <div class="edit-modal__intro" v-if="editingUser">
            <strong>{{ editingUser.username }}</strong>
            <span>当前编辑账号</span>
          </div>

          <div class="edit-grid">
            <label class="field">
              显示名称
              <input v-model="editForm.displayName" />
            </label>
            <label class="field">
              新密码
              <input v-model="editForm.password" type="password" placeholder="留空则保持原密码" />
            </label>
            <label class="field">
              角色
              <select v-model="editForm.role">
                <option value="ADMIN">管理员</option>
                <option value="OPERATOR">运营用户</option>
              </select>
            </label>
            <label class="toggle-field">
              <input v-model="editForm.enabled" type="checkbox" />
              启用该账号
            </label>
          </div>

          <div class="edit-actions">
            <button class="secondary-button" @click="editingId = null">取消</button>
            <button :disabled="saving" @click="saveEdit">{{ saving ? "保存中..." : "保存修改" }}</button>
          </div>

          <p v-if="editMessage" class="message">{{ editMessage }}</p>
          <p v-if="editError" class="error">{{ editError }}</p>
        </PanelCard>
      </div>
    </div>

    <p v-if="loading" class="loading">正在加载用户列表...</p>
  </div>
</template>

<style scoped>
.page-grid,
.layout-grid,
.edit-grid {
  display: grid;
  gap: 18px;
}

.layout-grid {
  grid-template-columns: 360px 1fr;
}

.field {
  display: grid;
  gap: 8px;
}

.toggle-field {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #5d7894;
}

.toggle-field input {
  width: auto;
  min-height: auto;
}

.user-row {
  display: grid;
  gap: 12px;
  padding: 16px;
  border-radius: 22px;
  background: #f8fbff;
  border: 1px solid rgba(214, 225, 238, 0.95);
}

.user-ticker {
  position: relative;
  height: 640px;
  overflow: hidden;
}

.user-ticker__track {
  display: grid;
  gap: 16px;
  animation: userTickerScroll 22s linear infinite;
}

.user-ticker:hover .user-ticker__track {
  animation-play-state: paused;
}

.user-ticker__track--static {
  animation: none;
}

.user-row__main,
.user-row__meta,
.edit-actions {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}

.user-row p,
.user-row small,
.message,
.loading {
  margin: 0;
  color: #6a839f;
}

.error {
  margin: 0;
  color: #d26a3e;
}

.edit-modal {
  position: fixed;
  inset: 0;
  z-index: 30;
  display: grid;
  place-items: center;
  padding: 24px;
}

.edit-modal__backdrop {
  position: absolute;
  inset: 0;
  background: rgba(15, 31, 52, 0.24);
  backdrop-filter: blur(6px);
}

.edit-modal__dialog {
  position: relative;
  z-index: 1;
  width: min(560px, 100%);
}

.edit-modal__intro {
  display: grid;
  gap: 4px;
  padding: 14px 16px;
  border-radius: 18px;
  background: #f6f9fd;
  border: 1px solid rgba(214, 225, 238, 0.95);
}

.edit-modal__intro span {
  color: #6a839f;
  font-size: 12px;
}

.secondary-button {
  background: #ffffff;
  color: #315d92;
  border: 1px solid #d8e3ef;
  box-shadow: none;
}

@media (max-width: 1100px) {
  .layout-grid,
  .edit-grid {
    grid-template-columns: 1fr;
  }

  .user-row__main,
  .user-row__meta,
  .edit-actions {
    flex-direction: column;
    align-items: flex-start;
  }
}

@keyframes userTickerScroll {
  0% {
    transform: translateY(0);
  }
  100% {
    transform: translateY(calc(-50% - 8px));
  }
}
</style>
