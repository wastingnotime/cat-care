import { getRequestEvent } from "solid-js/web";

export async function api<T>(path: string, init?: RequestInit): Promise<T> {
  const requestOrigin = typeof window === "undefined"
    ? new URL(getRequestEvent()?.request.url ?? "http://127.0.0.1:5173").origin
    : "";
  const response = await fetch(`${requestOrigin}/api/${path}`, {
    headers: { "content-type": "application/json", ...(init?.headers ?? {}) },
    ...init,
  });
  if (!response.ok) {
    const body = await response.json().catch(() => ({})) as { message?: string };
    throw new Error(body.message ?? `Request failed (${response.status})`);
  }
  return response.json() as Promise<T>;
}
