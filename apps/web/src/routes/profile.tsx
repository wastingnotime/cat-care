import { Show, createEffect, createResource, createSignal } from "solid-js";
import { Shell } from "../components/Shell";
import { api } from "../lib/api";
import type { Cat } from "../lib/contracts";

export default function Profile() {
  const [cat, { refetch }] = createResource(() => api<Cat>("cat"));
  const [name, setName] = createSignal(""); const [birthDate, setBirthDate] = createSignal(""); const [adoptionDate, setAdoptionDate] = createSignal(""); const [photoRef, setPhotoRef] = createSignal("");
  const [error, setError] = createSignal(""); const [notice, setNotice] = createSignal("");
  createEffect(() => { const value=cat(); if(value){setName(value.name);setBirthDate(value.birth_date??"");setAdoptionDate(value.adoption_date??"");setPhotoRef(value.photo_ref??"");} });
  async function save(event:SubmitEvent){event.preventDefault();setError("");setNotice("");try{await api("cat",{method:"PUT",body:JSON.stringify({name:name(),birth_date:birthDate()||null,adoption_date:adoptionDate()||null,photo_ref:photoRef()})});await refetch();setNotice("Profile saved.");}catch(failure){setError(failure instanceof Error?failure.message:"Profile could not be saved.")}}

  return <Shell><header class="page-heading"><p class="eyebrow">Cat profile</p><h1>About {cat()?.name ?? "your cat"}</h1><p>Keep the stable details that help identify and care for your companion.</p></header>
    <Show when={error()}><div class="error" role="alert">{error()}</div></Show><Show when={notice()}><div class="notice" role="status">{notice()}</div></Show>
    <section class="panel narrow-panel"><form class="stack-form" onSubmit={save}><label>Name<input value={name()} onInput={e=>setName(e.currentTarget.value)} required/></label><label>Birth date<input type="date" value={birthDate()} onInput={e=>setBirthDate(e.currentTarget.value)}/></label><label>Adoption date<input type="date" value={adoptionDate()} onInput={e=>setAdoptionDate(e.currentTarget.value)}/></label><label>Photo reference<input value={photoRef()} onInput={e=>setPhotoRef(e.currentTarget.value)} placeholder="mimi.jpg"/></label><button class="primary">Save profile</button></form></section>
  </Shell>;
}
