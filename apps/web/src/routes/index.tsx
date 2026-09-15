import { For, Show, createResource, createSignal, onMount } from "solid-js";
import { Shell } from "../components/Shell";
import { api } from "../lib/api";
import type { CareEvent, CareStatus, Cat, Note, Responsibility, TriageAssessment } from "../lib/contracts";

const labels: Record<string, string> = { clear: "All calm", planned: "Care planned", due_soon: "Coming up", overdue: "Needs attention", unknown: "Needs scheduling" };
const formatDate = (value?: string | null) => value ? new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "short" }).format(new Date(value)) : "No due date yet";

export default function Today() {
  const [cat] = createResource(() => api<Cat>("cat"));
  const [status, { refetch: refetchStatus }] = createResource(() => api<CareStatus>("status"));
  const [responsibilities, { refetch: refetchResponsibilities }] = createResource(() => api<Responsibility[]>("responsibilities"));
  const [timeline, { refetch: refetchTimeline }] = createResource(() => api<CareEvent[]>("timeline"));
  const [notes, { refetch: refetchNotes }] = createResource(() => api<Note[]>("notes"));
  const [triage, { refetch: refetchTriage }] = createResource(() => api<TriageAssessment[]>("triage"));
  const [formVisible, setFormVisible] = createSignal(false);
  const [quickAction, setQuickAction] = createSignal<"observation"|"care"|null>(null);
  const [commentingOn, setCommentingOn] = createSignal<string|null>(null);
  const [commentText, setCommentText] = createSignal("");
  const [comments, setComments] = createSignal<Record<string,string[]>>({});
  const [title, setTitle] = createSignal(""); const [category, setCategory] = createSignal("veterinary"); const [dueAt, setDueAt] = createSignal(""); const [recurrenceDays, setRecurrenceDays] = createSignal(0);
  const [noteText, setNoteText] = createSignal(""); const [careType, setCareType] = createSignal("measurement"); const [careDescription, setCareDescription] = createSignal("");
  const [error, setError] = createSignal(""); const [notice, setNotice] = createSignal(""); const [saving, setSaving] = createSignal(false); const [hydrated, setHydrated] = createSignal(false);
  onMount(() => setHydrated(true));

  const refresh = async () => Promise.all([refetchStatus(), refetchResponsibilities(), refetchTimeline(), refetchNotes(), refetchTriage()]);
  const command = async (path:string, body?:unknown) => { setError(""); setNotice(""); try { await api(path, { method:"POST", body: body === undefined ? undefined : JSON.stringify(body) }); await refresh(); } catch (failure) { setError(failure instanceof Error ? failure.message : "The care command failed"); throw failure; } };
  async function createResponsibility(event:SubmitEvent){event.preventDefault();setSaving(true);try{await command("responsibilities",{title:title(),category:category(),due_at:dueAt()?new Date(dueAt()).toISOString():null,recurrence_days:recurrenceDays(),recurrence_months:0});setTitle("");setDueAt("");setRecurrenceDays(0);setFormVisible(false);setNotice("Responsibility added.");}catch{}finally{setSaving(false)}}
  async function recordNote(event:SubmitEvent){event.preventDefault();try{await command("notes",{description:noteText()});setNoteText("");setQuickAction(null);setNotice("Observation added to care history. It remains a note, not a diagnosis.");}catch{}}
  async function recordCare(event:SubmitEvent){event.preventDefault();try{await command("care-events",{type:careType(),description:careDescription(),responsibility_id:""});setCareDescription("");setQuickAction(null);setNotice("Care event added to history.");}catch{}}
  const notify = (id:string) => command("notifications",{responsibility_id:id,outcome:"delivered"}).then(()=>setNotice("Notification delivered; responsibility state was not changed.")).catch(()=>{});
  const deferSevenDays = (id:string,currentDue:string|null) => command(`responsibilities/${id}/defer`,{due_at:new Date(Math.max(Date.now(),currentDue?new Date(currentDue).getTime():0)+7*86400000).toISOString()}).then(()=>setNotice("Responsibility deferred by seven days.")).catch(()=>{});
  const requestTriage = (noteID:string) => command("triage",{note_ids:[noteID]}).then(()=>setNotice("Provisional triage requested; veterinarian review is still required.")).catch(()=>{});
  const pendingTriage = () => (triage() ?? []).filter(item => item.review_status === "pending");
  const openQuickAction = (action:"observation"|"care") => {
    setQuickAction(current => current === action ? null : action);
    window.setTimeout(() => document.getElementById(action === "observation" ? "observation-input" : "care-description-input")?.focus(), 0);
  };
  const addComment = (event:SubmitEvent, eventID:string) => {
    event.preventDefault();
    const value=commentText().trim();
    if(!value)return;
    setComments(current=>({...current,[eventID]:[...(current[eventID]??[]),value]}));
    setCommentText("");setCommentingOn(null);
  };
  const eventLabel = (type:string) => type.startsWith("responsibility_") ? "Responsibility" : type === "note_recorded" ? "Observation" : type === "notification_recorded" ? "Notification" : "Care update";

  return <Shell>
    <section class="hero" aria-labelledby="greeting"><p class="eyebrow">Today</p><h1 id="greeting">How is <span>{cat()?.name??"your cat"}</span> doing?</h1><div class="status-card" data-kind={status()?.kind??"loading"} aria-live="polite"><span class="status-dot" aria-hidden="true"/><div><p class="status-label">{labels[status()?.kind??""]??"Checking care status"}</p><p class="status-copy">{status()?.sentence??"Connecting to your local care record…"}</p></div></div></section>
    <Show when={error()}><div class="error" role="alert">{error()}</div></Show><Show when={notice()}><div class="notice" role="status">{notice()}</div></Show>
    <Show when={pendingTriage().length > 0}><section class="notification-area" aria-label="Notifications"><span class="notification-mark" aria-hidden="true">!</span><div><strong>{pendingTriage().length} veterinarian review{pendingTriage().length === 1 ? " is" : "s are"} pending</strong><p>You’ll see the reviewed guidance here when it is ready.</p></div></section></Show>
    <section class="quick-actions" aria-labelledby="quick-actions-title"><div><p class="eyebrow">Quick actions</p><h2 id="quick-actions-title">Add to {cat()?.name??"your cat"}'s care</h2></div><div class="quick-action-links"><button class="secondary" aria-expanded={quickAction()==="observation"} onClick={()=>openQuickAction("observation")}>Add observation</button><button class="secondary" aria-expanded={quickAction()==="care"} onClick={()=>openQuickAction("care")}>Record direct care</button><button class="secondary" aria-expanded={formVisible()} onClick={()=>setFormVisible(value=>!value)}>Add responsibility</button></div></section>
    <Show when={quickAction()}><section class="panel quick-action-form" aria-label="Quick action form">
      <Show when={quickAction()==="observation"}><div><p class="eyebrow">New observation</p><h2>What did you notice?</h2><form class="stack-form" onSubmit={recordNote}><label class="sr-only" for="observation-input">What did you notice?</label><textarea id="observation-input" value={noteText()} onInput={e=>setNoteText(e.currentTarget.value)} required placeholder="Eating less than usual"/><div class="form-actions"><button class="primary">Add to history</button><button class="text-button" type="button" onClick={()=>setQuickAction(null)}>Cancel</button></div></form></div></Show>
      <Show when={quickAction()==="care"}><div><p class="eyebrow">Direct care</p><h2>What happened?</h2><form class="stack-form" onSubmit={recordCare}><label>Type<select value={careType()} onChange={e=>setCareType(e.currentTarget.value)}><option value="measurement">Measurement</option><option value="exam_performed">Exam</option><option value="medication_given">Medication given</option></select></label><label>Description<textarea id="care-description-input" value={careDescription()} onInput={e=>setCareDescription(e.currentTarget.value)} required placeholder="Weight 4.2 kg"/></label><div class="form-actions"><button class="primary">Add to history</button><button class="text-button" type="button" onClick={()=>setQuickAction(null)}>Cancel</button></div></form></div></Show>
    </section></Show>
    <section class="workspace" aria-label="Care workspace"><article class="panel responsibilities-panel"><div class="panel-heading"><div><p class="eyebrow">Responsibilities</p><h2>What needs care</h2></div></div>
      <Show when={formVisible()}><form class="responsibility-form" onSubmit={createResponsibility}><label>What needs to happen?<input value={title()} onInput={e=>setTitle(e.currentTarget.value)} required maxlength="160" placeholder="Annual exam"/></label><label>Category<select value={category()} onChange={e=>setCategory(e.currentTarget.value)}><option value="veterinary">Veterinary</option><option value="preventive">Preventive care</option><option value="nutrition">Nutrition</option><option value="grooming">Grooming</option><option value="other">Other</option></select></label><label>Due date <span class="optional">optional</span><input value={dueAt()} onInput={e=>setDueAt(e.currentTarget.value)} type="datetime-local"/></label><label>Repeat every <span class="optional">days</span><input value={recurrenceDays()} onInput={e=>setRecurrenceDays(Number(e.currentTarget.value))} type="number" min="0"/></label><div class="form-actions"><button class="primary" disabled={saving()}>{saving()?"Saving…":"Save responsibility"}</button><button class="text-button" type="button" onClick={()=>setFormVisible(false)}>Cancel</button></div></form></Show>
      <div class="responsibility-list"><Show when={(responsibilities()?.length??0)>0} fallback={<p class="empty">No responsibilities yet.</p>}><For each={responsibilities()}>{item=><article class={`responsibility ${item.state}`}><div><h3>{item.title}</h3><p>{item.category} · {formatDate(item.due_at)} · {item.derived_state.replace("_"," ")}{item.recurrence_days?` · every ${item.recurrence_days} days`:""}</p></div><Show when={item.state==="planned"}><div class="compact-actions"><button class="complete-button" aria-label={`Mark ${item.title} complete`} onClick={()=>command(`responsibilities/${item.id}/complete`).catch(()=>{})}>Complete</button><button class="text-button" onClick={()=>deferSevenDays(item.id,item.due_at)}>Defer 7d</button><button class="text-button" onClick={()=>notify(item.id)}>Notify</button><button class="danger-link" onClick={()=>command(`responsibilities/${item.id}/cancel`).catch(()=>{})}>Cancel</button></div></Show></article>}</For></Show></div>
    </article></section>
    <section class="history-section" aria-labelledby="history-title"><div class="section-heading"><div><p class="eyebrow">Care history</p><h2 id="history-title">Life with {cat()?.name??"your cat"}</h2></div><p>Observations, care and responsibilities — newest first.</p></div><div class="history-feed"><Show when={(timeline()?.length??0)>0} fallback={<div class="panel empty">Care updates will appear here.</div>}><For each={(timeline()??[]).slice(0,20)}>{item=><article class="history-card"><header><span class="feed-avatar" aria-hidden="true">{eventLabel(item.type).charAt(0)}</span><div><strong>{eventLabel(item.type)}</strong><time dateTime={item.occurred_at}>{formatDate(item.occurred_at)}</time></div><span class="feed-kind">{item.type.replaceAll("_"," ")}</span></header><p class="feed-copy">{item.description}</p><div class="feed-actions"><button class="text-button" onClick={()=>{setCommentingOn(commentingOn()===item.id?null:item.id);setCommentText("")}}>Comment</button><Show when={item.type==="note_recorded"}><button class="text-button" onClick={()=>requestTriage(String(item.details.note_id))}>Request triage</button></Show></div><For each={comments()[item.id]??[]}>{comment=><p class="feed-comment"><strong>You</strong>{comment}</p>}</For><Show when={commentingOn()===item.id}><form class="comment-form" onSubmit={event=>addComment(event,item.id)}><label class="sr-only" for={`comment-${item.id}`}>Comment on {item.description}</label><input id={`comment-${item.id}`} value={commentText()} onInput={e=>setCommentText(e.currentTarget.value)} placeholder="Write a comment…" autofocus/><button class="primary">Post</button></form></Show></article>}</For></Show></div></section>
  </Shell>;
}
