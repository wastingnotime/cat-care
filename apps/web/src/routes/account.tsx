import { Show, createSignal } from "solid-js";
import { Shell } from "../components/Shell";
import { api } from "../lib/api";

export default function Account() {
  const [error, setError] = createSignal(""); const [notice, setNotice] = createSignal(""); const [deleted, setDeleted] = createSignal(false);
  async function exportData(){setError("");try{const data=await api<unknown>("export");const url=URL.createObjectURL(new Blob([JSON.stringify(data,null,2)],{type:"application/json"}));const anchor=document.createElement("a");anchor.href=url;anchor.download="cat-care-export.json";anchor.click();URL.revokeObjectURL(url);setNotice("Owner export downloaded.");}catch(failure){setError(failure instanceof Error?failure.message:"Export failed.")}}
  async function deleteData(){if(!window.confirm("Permanently delete all local care data? This includes the cat profile, responsibilities, observations, care history, and triage records. This cannot be undone."))return;setError("");try{await api("data",{method:"DELETE"});setDeleted(true);setNotice("Local cat-care data deleted.");}catch(failure){setError(failure instanceof Error?failure.message:"Deletion failed.")}}

  return <Shell><header class="page-heading"><p class="eyebrow">Account & data</p><h1>Data stewardship</h1><p>Your local care record remains under your control.</p></header>
    <Show when={error()}><div class="error" role="alert">{error()}</div></Show><Show when={notice()}><div class="notice" role="status">{notice()}</div></Show>
    <Show when={!deleted()} fallback={<section class="panel narrow-panel"><h2>Local care data deleted</h2><p>Restart the development API to begin with a fresh in-memory environment.</p></section>}>
      <section class="panel stewardship"><div><p class="eyebrow">Your copy</p><h2>Download your complete record</h2><p>The JSON export includes profile, responsibilities, observations, care activity, and triage records.</p></div><button class="secondary" onClick={exportData}>Download export</button></section>
      <section class="panel danger-zone"><div><p class="eyebrow">Danger zone</p><h2>Delete all local data</h2><p>This permanently removes the complete local record. Download an export first if you may need it later.</p></div><button class="danger-button" onClick={deleteData}>Delete local data</button></section>
    </Show>
  </Shell>;
}
