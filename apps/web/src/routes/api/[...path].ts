import type { APIEvent } from "@solidjs/start/server";

const allowed = new Set(["GET", "POST", "PUT", "DELETE"]);

async function proxy(event: APIEvent) {
  if (!allowed.has(event.request.method)) return Response.json({ code: "method_not_allowed", message: "method not allowed" }, { status: 405 });
  const serverEnv = (globalThis as typeof globalThis & { process?: { env?: Record<string, string | undefined> } }).process?.env;
  const upstreamBase = serverEnv?.CAT_CARE_API_URL ?? "http://127.0.0.1:8080";
  const incoming = new URL(event.request.url);
  const upstream = new URL(`/v1/${event.params.path ?? ""}${incoming.search}`, upstreamBase);
  try {
    const response = await fetch(upstream, {
      method: event.request.method,
      headers: event.request.headers.get("content-type") ? { "content-type": event.request.headers.get("content-type")! } : undefined,
      body: event.request.method === "GET" ? undefined : await event.request.arrayBuffer(),
    });
    return new Response(response.body, { status: response.status, headers: { "content-type": response.headers.get("content-type") ?? "application/json" } });
  } catch {
    return Response.json({ code: "api_unavailable", message: "The Cat Care API is unavailable." }, { status: 503 });
  }
}

export const GET = proxy;
export const POST = proxy;
export const PUT = proxy;
export const DELETE = proxy;
