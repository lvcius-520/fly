import { computed, ref } from "vue";
import type { AuthUser, LoginResponse } from "../types";

const storageKey = "fly-ops-auth";

interface StoredAuth {
  token: string;
  user: AuthUser;
}

const state = ref<StoredAuth | null>(readStoredAuth());

function readStoredAuth(): StoredAuth | null {
  const raw = window.localStorage.getItem(storageKey);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as StoredAuth;
  } catch {
    window.localStorage.removeItem(storageKey);
    return null;
  }
}

function persist(value: StoredAuth | null) {
  state.value = value;
  if (!value) {
    window.localStorage.removeItem(storageKey);
    return;
  }
  window.localStorage.setItem(storageKey, JSON.stringify(value));
}

export function useAuth() {
  const currentUser = computed(() => state.value?.user ?? null);
  const token = computed(() => state.value?.token ?? null);
  const isLoggedIn = computed(() => Boolean(state.value?.token));
  const isAdmin = computed(() => state.value?.user.role === "ADMIN");

  function applyLogin(payload: LoginResponse) {
    persist({
      token: payload.token,
      user: payload.user
    });
  }

  function logoutLocal() {
    persist(null);
  }

  return {
    currentUser,
    token,
    isLoggedIn,
    isAdmin,
    applyLogin,
    logoutLocal
  };
}
