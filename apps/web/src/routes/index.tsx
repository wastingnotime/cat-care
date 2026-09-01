import { For, Show, createResource, createSignal } from "solid-js";
import { api } from "../lib/api";
import type { CareEvent, CareStatus, Cat, Responsibility } from "../lib/contracts";

const labels: Record<string, string> = { clear: "All calm", planned: "Care planned", due_soon: "Coming up", overdue: "Needs attention", unknown: "Needs scheduling" };

export default function Home() {
  const [cat] = createResource(() => api<Cat>("cat"));
  const [status, { refetch: refetchStatus }] = createResource(() => api<CareStatus>("status"));
  const [responsibilities, { refetch: refetchResponsibilities }] = createResource(() => api<Responsibility[]>("responsibilities"));
  const [timeline, { refetch: refetchTimeline }] = createResource(() => api<CareEvent[]>("timeline"));
  const [formVisible, setFormVisible] = createSignal(false);
  const [title, setTitle] = createSignal("");
  const [category, setCategory] = createSignal("veterinary");
  const [dueAt, setDueAt] = createSignal("");
  const [error, setError] = createSignal("");
  const [saving, setSaving] = createSignal(false);

  const refresh = async () => { await Promise.all([refetchStatus(), refetchResponsibilities(), refetchTimeline()]); };
  const formatDate = (value?: string | null) => value ? new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "short" }).format(new Date(value)) : "No due date yet";

  async function createResponsibility(event: SubmitEvent) {
    event.preventDefault(); setSaving(true); setError("");
    try {
      await api("responsibilities", { method: "POST", body: JSON.stringify({ title: title(), category: category(), due_at: dueAt() ? new Date(dueAt()).toISOString() : null }) });
      setTitle(""); setCategory("veterinary"); setDueAt(""); setFormVisible(false); await refresh();
    } catch (failure) { setError(failure instanceof Error ? failure.message : "Could not save responsibility"); }
    finally { setSaving(false); }
  }

  async function completeResponsibility(id: string) {
    setError("");
    try { await api(`responsibilities/${id}/complete`, { method: "POST" }); await refresh(); }
    catch (failure) { setError(failure instanceof Error ? failure.message : "Could not complete responsibility"); }
  }

  return <>
    <header class="site-header"><a class="brand" href="/" aria-label="Cat Care home"><span aria-hidden="true">◡̈</span> Cat Care</a><span class="local-badge">Local companion</span></header>
    <main>
      <section class="hero" aria-labelledby="greeting"><p class="eyebrow">A calm view of today</p><h1 id="greeting">How is <span>{cat()?.name ?? "your cat"}</span> doing?</h1>
        <div class="status-card" data-kind={status()?.kind ?? "loading"} aria-live="polite"><span class="status-dot" aria-hidden="true"/><div><p class="status-label">{labels[status()?.kind ?? ""] ?? "Checking care status"}</p><p class="status-copy">{status()?.sentence ?? "Connecting to your local care record…"}</p></div></div>
      </section>
      <Show when={error()}><div class="error" role="alert">{error()}</div></Show>
      <section class="workspace" aria-label="Care workspace">
        <article class="panel responsibilities-panel"><div class="panel-heading"><div><p class="eyebrow">Responsibilities</p><h2>What needs care</h2></div><Show when={!formVisible()}><button class="secondary" type="button" onClick={() => setFormVisible(true)}>Add responsibility</button></Show></div>
          <Show when={formVisible()}><form class="responsibility-form" onSubmit={createResponsibility}><label>What needs to happen?<input value={title()} onInput={(event) => setTitle(event.currentTarget.value)} required maxlength="160" placeholder="Annual exam" /></label><label>Category<select value={category()} onChange={(event) => setCategory(event.currentTarget.value)}><option value="veterinary">Veterinary</option><option value="preventive">Preventive care</option><option value="nutrition">Nutrition</option><option value="grooming">Grooming</option><option value="other">Other</option></select></label><label>Due date <span class="optional">optional</span><input value={dueAt()} onInput={(event) => setDueAt(event.currentTarget.value)} type="datetime-local" /></label><div class="form-actions"><button class="primary" type="submit" disabled={saving()}>{saving() ? "Saving…" : "Save responsibility"}</button><button class="text-button" type="button" onClick={() => setFormVisible(false)}>Cancel</button></div></form></Show>
          <div class="responsibility-list" aria-live="polite"><Show when={(responsibilities()?.length ?? 0) > 0} fallback={<p class="empty">No responsibilities yet. Add one when something needs care.</p>}><For each={responsibilities()}>{(item) => <article class={`responsibility ${item.state}`} data-responsibility-id={item.id}><div><h3>{item.title}</h3><p>{item.category} · {formatDate(item.due_at)} · {item.derived_state.replace("_", " ")}</p></div><Show when={item.state === "planned"}><button class="complete-button" type="button" aria-label={`Mark ${item.title} complete`} onClick={() => completeResponsibility(item.id)}>Mark complete</button></Show></article>}</For></Show></div>
        </article>
        <aside class="panel history-panel"><div class="panel-heading"><div><p class="eyebrow">History</p><h2>Recent care</h2></div></div><ol class="timeline"><Show when={(timeline()?.length ?? 0) > 0} fallback={<li class="empty">Care history will appear here.</li>}><For each={(timeline() ?? []).slice(0, 8)}>{(item) => <li><strong>{item.type === "responsibility_completed" ? `Completed ${item.description}` : `Added ${item.description}`}</strong><time dateTime={item.occurred_at}>{formatDate(item.occurred_at)}</time></li>}</For></Show></ol></aside>
      </section>
    </main>
    <footer>Notes and status help organize care. They are not medical diagnoses.</footer>
  </>;
}
