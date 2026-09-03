import type { APIEvent } from "@solidjs/start/server";

const allowed = new Set(["GET", "POST", "PUT", "DELETE"]);

async function proxy(event: APIEvent) {
  if (!allowed.has(event.request.method)) return Response.json({ code: "method_not_allowed", message: "method not allowed" }, { status: 405 });
  const serverEnv = (globalThis as typeof globalThis & { process?: { env?: Record<string, string | undefined> } }).process?.env;
  const upstreamBase = serverEnv?.CAT_CARE_API_URL ?? "http://127.0.0.1:8080";
  const incoming = new URL(event.request.url);
  const upstream = new URL(`/v1/${event.params.path ?? ""}${incoming.search}`, upstreamBase);
  try {
    const headers: Record<string,string> = {};
    for (const name of ["content-type", "cookie"]) { const value=event.request.headers.get(name); if(value) headers[name]=value; }
    const response = await fetch(upstream, {
      method: event.request.method,
      headers,
      body: event.request.method === "GET" ? undefined : await event.request.arrayBuffer(),
    });
    const outgoing = new Headers({ "content-type": response.headers.get("content-type") ?? "application/json" });
    const cookie=response.headers.get("set-cookie"); if(cookie) outgoing.set("set-cookie",cookie);
    return new Response(response.body, { status: response.status, headers: outgoing });
  } catch {
    return Response.json({ code: "api_unavailable", message: "The Cat Care API is unavailable." }, { status: 503 });
  }
}

export const GET = proxy;
export const POST = proxy;
export const PUT = proxy;
export const DELETE = proxy;
