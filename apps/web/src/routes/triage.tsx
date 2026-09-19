import { For, Show, createResource, createSignal } from "solid-js";
import { Shell } from "../components/Shell";
import { api } from "../lib/api";
import type { CareEvent, CatAccount, Note, Responsibility, Session, TriageAssessment } from "../lib/contracts";
import { exactDateTime, relativeTime } from "../lib/time";

const withCat = (path:string, catID:string) => `${path}${path.includes("?")?"&":"?"}cat_id=${encodeURIComponent(catID)}`;
type CatTriage = { cat:CatAccount; assessments:TriageAssessment[]; notes:Note[]; timeline:CareEvent[]; responsibilities:Responsibility[] };

export default function Triage() {
  const [session] = createResource(() => api<Session>("session"));
  const [queues, { refetch }] = createResource(
    () => session()?.user.mode === "veterinarian" ? session()?.cats : undefined,
    async cats => Promise.all((cats??[]).map(async cat => {
      const [assessments,notes,timeline,responsibilities] = await Promise.all([
        api<TriageAssessment[]>(withCat("triage",cat.id)),
        api<Note[]>(withCat("notes",cat.id)),
        api<CareEvent[]>(withCat("timeline",cat.id)),
        api<Responsibility[]>(withCat("responsibilities",cat.id)),
      ]);
      return {cat,assessments:assessments??[],notes:notes??[],timeline:timeline??[],responsibilities:responsibilities??[]} satisfies CatTriage;
    })),
  );
  const [error,setError]=createSignal(""); const [notice,setNotice]=createSignal("");
  const [askingOwnerOn,setAskingOwnerOn]=createSignal<string|null>(null); const [ownerQuestion,setOwnerQuestion]=createSignal("");
  const command=async(catID:string,path:string,body?:unknown)=>{setError("");setNotice("");try{await api(withCat(path,catID),{method:"POST",body:body===undefined?undefined:JSON.stringify(body)});await refetch()}catch(failure){setError(failure instanceof Error?failure.message:"Triage command failed.");throw failure}};
  const review=(catID:string,id:string,decision:string,finalUrgency="")=>command(catID,`triage/${id}/review`,{decision,final_urgency:finalUrgency,rationale:decision==="modified"?"Prompt examination is appropriate.":"Reviewed in the local veterinarian queue."}).then(()=>setNotice("Veterinarian review recorded.")).catch(()=>{});
  const cards=()=>(queues()??[]).flatMap(queue=>queue.assessments.map(assessment=>({queue,assessment}))).sort((a,b)=>new Date(b.assessment.assessed_at).getTime()-new Date(a.assessment.assessed_at).getTime());
  const observations=(queue:CatTriage,item:TriageAssessment)=>queue.notes.filter(note=>item.note_ids.includes(note.id));
  const thread=(queue:CatTriage,id:string)=>queue.timeline.filter(event=>event.type.startsWith("triage_")&&event.type!=="triage_requested"&&event.details.assessment_id===id).slice().reverse();
  const threadLabel=(event:CareEvent)=>event.type==="triage_owner_commented"?String(event.details.author_name??"Owner"):event.type.replace("triage_","triage ").replaceAll("_"," ");
  const hasFollowUp=(queue:CatTriage)=>queue.responsibilities.some(item=>item.title.toLowerCase()==="veterinarian follow-up");
  const openOwnerQuestion=(assessmentID:string)=>{setAskingOwnerOn(assessmentID);setOwnerQuestion("Please share appetite and energy changes.")};
  const askOwner=async(event:SubmitEvent,catID:string,assessmentID:string)=>{event.preventDefault();const question=ownerQuestion().trim();if(!question)return;try{await command(catID,`triage/${assessmentID}/information-requests`,{question});setAskingOwnerOn(null);setOwnerQuestion("");setNotice("Information requested from the owner.")}catch{}};

  return <Shell><Show when={session()?.user.mode==="veterinarian"} fallback={<section class="panel narrow-panel"><p class="eyebrow">Veterinarian mode</p><h1>Clinical review is restricted.</h1><p>Log on with a veterinarian account to access this workspace.</p></section>}><header class="page-heading"><p class="eyebrow">Care triage</p><h1>Veterinarian review</h1><p>AI urgency is provisional. It helps organize review and is not a diagnosis.</p></header>
    <div class="role-boundary"><strong>Local veterinarian workspace</strong><span>Every cat needing review appears here; actions are scoped by the triage card.</span></div>
    <Show when={error()}><div class="error" role="alert">{error()}</div></Show><Show when={notice()}><div class="notice" role="status">{notice()}</div></Show>
    <section class="panel"><div class="triage-list"><Show when={cards().length>0} fallback={<p class="empty">No assessments waiting.</p>}><For each={cards()}>{entry=>{const item=entry.assessment;const queue=entry.queue;return <article class="triage-card"><p class="item-cat">{queue.cat.profile.name}</p><span class={`urgency ${item.urgency}`}>{item.urgency.replace("_"," ")}</span><div class="cat-observations"><strong>Cat observations</strong><For each={observations(queue,item)}>{note=><p>{note.description}</p>}</For></div><p>{item.rationale}</p><small>{item.uncertainty}</small><p><strong>Status:</strong> {item.review_status}{item.final_urgency?` · ${item.final_urgency}`:""}</p><div class="triage-thread"><For each={thread(queue,item.id)}>{event=><p class="feed-comment"><strong>{threadLabel(event)}</strong><time dateTime={event.occurred_at} title={exactDateTime(event.occurred_at)}>{relativeTime(event.occurred_at)}</time>{event.description}</p>}</For></div><Show when={item.review_status==="pending"}><div class="compact-actions"><button class="secondary" onClick={()=>review(queue.cat.id,item.id,"accepted")}>Accept</button><button class="secondary" onClick={()=>review(queue.cat.id,item.id,"modified","urgent")}>Mark urgent</button><button class="text-button" aria-expanded={askingOwnerOn()===item.id} onClick={()=>openOwnerQuestion(item.id)}>Ask owner</button><button class="danger-link" onClick={()=>review(queue.cat.id,item.id,"rejected")}>Reject</button></div><Show when={askingOwnerOn()===item.id}><form class="triage-question-form" onSubmit={event=>askOwner(event,queue.cat.id,item.id)}><label for={`owner-question-${item.id}`}>Question for the owner<textarea id={`owner-question-${item.id}`} value={ownerQuestion()} onInput={event=>setOwnerQuestion(event.currentTarget.value)} required autofocus/></label><div class="form-actions"><button class="primary">Send question</button><button class="text-button" type="button" onClick={()=>{setAskingOwnerOn(null);setOwnerQuestion("")}}>Cancel</button></div></form></Show></Show><Show when={(item.review_status==="accepted"||item.review_status==="modified")&&!hasFollowUp(queue)}><button class="secondary" onClick={()=>command(queue.cat.id,`triage/${item.id}/follow-up`,{title:"Veterinarian follow-up",due_at:new Date(Date.now()+3*86400000).toISOString()}).then(()=>setNotice("Follow-up responsibility added.")).catch(()=>{})}>Add follow-up responsibility</button></Show></article>}}</For></Show></div></section></Show>
  </Shell>;
}
