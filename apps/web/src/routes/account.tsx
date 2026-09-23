import { For, Show, createResource, createSignal } from "solid-js";
import { Shell } from "../components/Shell";
import { api } from "../lib/api";
import type { Session, VetInvitation } from "../lib/contracts";

export default function Account() {
  const [error, setError] = createSignal(""); const [notice, setNotice] = createSignal(""); const [deleted, setDeleted] = createSignal(false);
  const [session]=createResource(()=>api<Session>("session"));
  const [veterinarians,{refetch:refetchVeterinarians}]=createResource(()=>session()?.user.mode==="owner"?true:undefined,()=>api<VetInvitation[]>("veterinarians"));
  async function inviteVet(event:SubmitEvent){event.preventDefault();setError("");setNotice("");const form=event.currentTarget as HTMLFormElement;const email=String(new FormData(form).get("email")??"");try{await api("veterinarians/invitations",{method:"POST",body:JSON.stringify({email})});form.reset();await refetchVeterinarians();setNotice("Invitation sent. The veterinarian must accept before you can assign a cat.")}catch(failure){setError(failure instanceof Error?failure.message:"Invitation could not be sent.")}}
  async function exportData(){setError("");try{const data=await api<unknown>("export");const url=URL.createObjectURL(new Blob([JSON.stringify(data,null,2)],{type:"application/json"}));const anchor=document.createElement("a");anchor.href=url;anchor.download="cat-care-export.json";anchor.click();URL.revokeObjectURL(url);setNotice("Owner export downloaded.");}catch(failure){setError(failure instanceof Error?failure.message:"Export failed.")}}
  async function deleteData(){if(!window.confirm("Permanently delete all local care data? This includes the cat profile, responsibilities, observations, care history, and triage records. This cannot be undone."))return;setError("");try{await api("data",{method:"DELETE"});setDeleted(true);setNotice("Local cat-care data deleted.");}catch(failure){setError(failure instanceof Error?failure.message:"Deletion failed.")}}

  return <Shell><header class="page-heading"><p class="eyebrow">Account & data</p><h1>Data stewardship</h1><p>Your local care record remains under your control.</p></header>
    <Show when={error()}><div class="error" role="alert">{error()}</div></Show><Show when={notice()}><div class="notice" role="status">{notice()}</div></Show>
    <Show when={!deleted()} fallback={<section class="panel narrow-panel"><h2>Local care data deleted</h2><p>Restart the development API to begin with a fresh in-memory environment.</p></section>}>
      <section class="panel stewardship"><div><p class="eyebrow">Veterinarians</p><h2>Invite a veterinarian</h2><p>After they accept, you can assign them to an individual cat.</p><form class="inline-form" onSubmit={inviteVet}><label>Veterinarian email<input name="email" type="email" placeholder="vet@cat.care" required/></label><button class="primary">Send invitation</button></form><ul class="record-list"><For each={veterinarians()}>{item=><li>{item.veterinarian_name} · {item.status}</li>}</For></ul></div></section>
      <section class="panel stewardship"><div><p class="eyebrow">Your copy</p><h2>Download your complete record</h2><p>The JSON export includes profile, responsibilities, observations, care activity, and triage records.</p></div><button class="secondary" onClick={exportData}>Download export</button></section>
      <section class="panel danger-zone"><div><p class="eyebrow">Danger zone</p><h2>Delete all local data</h2><p>This permanently removes the complete local record. Download an export first if you may need it later.</p></div><button class="danger-button" onClick={deleteData}>Delete local data</button></section>
    </Show>
  </Shell>;
}
