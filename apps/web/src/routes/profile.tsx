import { Show, createEffect, createResource } from "solid-js";
import { Shell } from "../components/Shell";
import { api } from "../lib/api";
import type { Session } from "../lib/contracts";

export default function LegacyProfile() {
  const [session] = createResource(() => api<Session>("session"));
  createEffect(() => {
    const catID = session()?.active_cat_id || session()?.cats[0]?.id;
    if (catID && typeof window !== "undefined") window.location.replace(`/cats/${catID}`);
  });
  return <Shell><section class="panel narrow-panel"><Show when={session()} fallback={<p>Loading cats…</p>}><p>Opening cat profile…</p></Show></section></Shell>;
}
