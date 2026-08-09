const apiBase = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8787";

function readStoredAuth() {
  const raw = window.localStorage.getItem("fly-ops-auth");
  if (!raw) return null;
  try {
    return JSON.parse(raw) as { token?: string };
  } catch {
    window.localStorage.removeItem("fly-ops-auth");
    return null;
  }
}

export async function apiFetch<T>(path: string, init?: RequestInit): Promise<T> {
  const storedAuth = readStoredAuth();
  const headers = new Headers(init?.headers ?? {});
  if (!headers.has("Content-Type") && init?.body) {
    headers.set("Content-Type", "application/json");
  }
  if (storedAuth?.token) {
    headers.set("Authorization", `Bearer ${storedAuth.token}`);
  }

  const response = await fetch(`${apiBase}${path}`, {
    headers,
    ...init
  });

  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `请求失败：${response.status}`);
  }

  return response.json() as Promise<T>;
}

export { apiBase };
