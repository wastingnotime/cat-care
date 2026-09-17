import { A } from "@solidjs/router";
import { For, Show, createResource, createSignal } from "solid-js";
import { Shell } from "../../components/Shell";
import { CatFace } from "../../components/CatFace";
import { api } from "../../lib/api";
import type { CareStatus, CatAccount, Responsibility, Session } from "../../lib/contracts";

const withCat=(path:string,catID:string)=>`${path}?cat_id=${encodeURIComponent(catID)}`;
type CatSummary={cat:CatAccount;status:CareStatus;responsibilities:Responsibility[]};

export default function Cats() {
  const [session,{refetch:refetchSession}]=createResource(()=>api<Session>("session"));
  const [summaries,{refetch}]=createResource(()=>session()?.cats,async cats=>Promise.all(cats.map(async cat=>{const [status,responsibilities]=await Promise.all([api<CareStatus>(withCat("status",cat.id)),api<Responsibility[]>(withCat("responsibilities",cat.id))]);return {cat,status,responsibilities:responsibilities??[]} satisfies CatSummary})));
  const [newCat,setNewCat]=createSignal(""); const [error,setError]=createSignal(""); const [saving,setSaving]=createSignal(false);
  const nextResponsibility=(item:CatSummary)=>item.responsibilities.find(responsibility=>responsibility.state==="planned");
  async function addCat(event:SubmitEvent){event.preventDefault();setError("");setSaving(true);try{const cat=await api<CatAccount>("cats",{method:"POST",body:JSON.stringify({name:newCat(),birth_date:null,adoption_date:null})});await api(`cats/${cat.id}/select`,{method:"POST"});await refetchSession();await refetch();window.location.href=`/cats/${cat.id}`}catch(failure){setError(failure instanceof Error?failure.message:"Cat could not be added")}finally{setSaving(false)}}

  return <Shell><Show when={session()?.user.mode==="owner"} fallback={<section class="panel narrow-panel"><h1>Cats are managed by their owner.</h1></section>}><header class="page-heading cats-heading"><div><p class="eyebrow">Your cats</p><h1>One home, every cat.</h1><p>Open a cat to update their stable details and review their care.</p></div><form class="inline-form add-cat-form" onSubmit={addCat}><label><span class="sr-only">Cat name</span><input value={newCat()} onInput={event=>setNewCat(event.currentTarget.value)} placeholder="Cat name" required/></label><button class="primary" disabled={saving()}>{saving()?"Adding…":"Add cat"}</button></form></header><Show when={error()}><div class="error" role="alert">{error()}</div></Show><section class="cat-grid"><For each={summaries()}>{item=><A class="cat-card" href={`/cats/${item.cat.id}`}><CatFace coat={item.cat.profile.photo_ref} label={`${item.cat.profile.name}, ${item.cat.profile.photo_ref||"gray"} cat`}/><div><p class="eyebrow">{item.status.kind.replaceAll("_"," ")}</p><h2>{item.cat.profile.name}</h2><p>{item.status.sentence}</p><Show when={nextResponsibility(item)}>{responsibility=><small>Next: {responsibility().title}</small>}</Show></div><span class="cat-card-action">View profile →</span></A>}</For></section></Show></Shell>;
}
