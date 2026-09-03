import { A, Router, useLocation } from "@solidjs/router";
import { FileRoutes } from "@solidjs/start/router";
import { Show, Suspense, createResource } from "solid-js";
import { api } from "./lib/api";
import type { Session } from "./lib/contracts";
import "./app.css";

export default function App() {
  return <Router root={(props) => <AuthBoundary>{props.children}</AuthBoundary>}><FileRoutes /></Router>;
}

function AuthBoundary(props:{children:unknown}) {
  const location=useLocation(); const [session]=createResource(()=>location.pathname!=="/login", enabled=>enabled?api<Session>("session"):undefined);
  return <Suspense><Show when={location.pathname==="/login" || session()} fallback={<main class="signed-out"><section class="login-card"><p class="eyebrow">Session required</p><h1>Know who is caring.</h1><p>Log on to open an owner or veterinarian workspace.</p><A class="primary" href="/login">Go to logon</A></section></main>}>{props.children as any}</Show></Suspense>;
}
