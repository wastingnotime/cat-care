import { getRequestEvent } from "solid-js/web";

export class ApiError extends Error {
  constructor(message:string, readonly status:number) { super(message); this.name="ApiError"; }
}

export async function api<T>(path: string, init?: RequestInit): Promise<T> {
  const event = getRequestEvent();
  const requestOrigin = typeof window === "undefined"
    ? new URL(event?.request.url ?? "http://127.0.0.1:5173").origin
    : "";
  const headers = new Headers(init?.headers);
  if (!headers.has("content-type") && init?.body !== undefined) headers.set("content-type", "application/json");
  const cookie = event?.request.headers.get("cookie");
  if (cookie && !headers.has("cookie")) headers.set("cookie", cookie);
  const response = await fetch(`${requestOrigin}/api/${path}`, {
    ...init,
    headers,
  });
  if (!response.ok) {
    const body = await response.json().catch(() => ({})) as { message?: string };
    throw new ApiError(body.message ?? `Request failed (${response.status})`, response.status);
  }
  if (response.status === 204 || response.headers.get("content-length") === "0") return undefined as T;
  return response.json() as Promise<T>;
}
