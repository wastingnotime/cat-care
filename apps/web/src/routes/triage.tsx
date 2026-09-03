import { For, Show, createResource, createSignal } from "solid-js";
import { Shell } from "../components/Shell";
import { api } from "../lib/api";
import type { Session, TriageAssessment } from "../lib/contracts";

export default function Triage() {
  const [session] = createResource(() => api<Session>("session"));
  const [triage, { refetch }] = createResource(() => session()?.user.mode === "veterinarian", enabled => enabled ? api<TriageAssessment[]>("triage") : []);
  const [error, setError] = createSignal(""); const [notice, setNotice] = createSignal("");
  const command = async(path:string,body?:unknown)=>{setError("");setNotice("");try{await api(path,{method:"POST",body:body===undefined?undefined:JSON.stringify(body)});await refetch();}catch(failure){setError(failure instanceof Error?failure.message:"Triage command failed.");throw failure;}};
  const review = (id:string,decision:string,finalUrgency="") => command(`triage/${id}/review`,{veterinarian_id:"vet-local",decision,final_urgency:finalUrgency,rationale:decision==="modified"?"Prompt examination is appropriate.":"Reviewed in the local veterinarian queue."}).then(()=>setNotice("Veterinarian review recorded.")).catch(()=>{});

  return <Shell><Show when={session()?.user.mode === "veterinarian"} fallback={<section class="panel narrow-panel"><p class="eyebrow">Veterinarian mode</p><h1>Clinical review is restricted.</h1><p>Log on with a veterinarian account to access this workspace.</p></section>}><header class="page-heading"><p class="eyebrow">Care triage</p><h1>Veterinarian review</h1><p>AI urgency is provisional. It helps organize review and is not a diagnosis.</p></header>
    <div class="role-boundary"><strong>Local veterinarian workspace</strong><span>These controls belong to a clinical reviewer, not the caregiver account.</span></div>
    <Show when={error()}><div class="error" role="alert">{error()}</div></Show><Show when={notice()}><div class="notice" role="status">{notice()}</div></Show>
    <section class="panel"><div class="triage-list"><Show when={(triage()?.length??0)>0} fallback={<p class="empty">No assessments waiting.</p>}><For each={(triage()??[]).slice().reverse()}>{item=><article class="triage-card"><span class={`urgency ${item.urgency}`}>{item.urgency.replace("_"," ")}</span><p>{item.rationale}</p><small>{item.uncertainty}</small><p><strong>Status:</strong> {item.review_status}{item.final_urgency?` · ${item.final_urgency}`:""}</p><Show when={item.review_status==="pending"}><div class="compact-actions"><button class="secondary" onClick={()=>review(item.id,"accepted")}>Accept</button><button class="secondary" onClick={()=>review(item.id,"modified","urgent")}>Mark urgent</button><button class="text-button" onClick={()=>command(`triage/${item.id}/information-requests`,{veterinarian_id:"vet-local",question:"Please share appetite and energy changes."}).then(()=>setNotice("Information requested from the owner.")).catch(()=>{})}>Ask owner</button><button class="danger-link" onClick={()=>review(item.id,"rejected")}>Reject</button></div></Show><Show when={item.review_status==="accepted"||item.review_status==="modified"}><button class="secondary" onClick={()=>command(`triage/${item.id}/follow-up`,{veterinarian_id:"vet-local",title:"Veterinarian follow-up",due_at:new Date(Date.now()+3*86400000).toISOString()}).then(()=>setNotice("Follow-up responsibility added.")).catch(()=>{})}>Add follow-up responsibility</button></Show></article>}</For></Show></div></section></Show>
  </Shell>;
}
