import { createSignal, Show } from "solid-js";
import { api } from "../lib/api";
import type { Session } from "../lib/contracts";

export default function Login(){
  const [email,setEmail]=createSignal("owner@cat.care"); const [password,setPassword]=createSignal("owner"); const [error,setError]=createSignal(""); const [busy,setBusy]=createSignal(false);
  const persona=(mode:"owner"|"veterinarian")=>{setEmail(mode==="owner"?"owner@cat.care":"vet@cat.care");setPassword(mode==="owner"?"owner":"vet")};
  async function submit(event:SubmitEvent){event.preventDefault();setBusy(true);setError("");try{const session=await api<Session>("session",{method:"POST",body:JSON.stringify({email:email(),password:password()})});window.location.href=session.user.mode==="veterinarian"?"/triage":"/"}catch(failure){setError(failure instanceof Error?failure.message:"Logon failed")}finally{setBusy(false)}}
  return <main class="login-page"><section class="login-card"><a class="brand" href="/"><span>ᓚᘏᗢ</span> Cat Care</a><p class="eyebrow">Welcome back</p><h1>Care starts with context.</h1><p>Log on as an owner or veterinarian. Each mode has its own responsibilities and permissions.</p><div class="persona-picker"><button class="secondary" type="button" onClick={()=>persona("owner")}>Use owner demo</button><button class="secondary" type="button" onClick={()=>persona("veterinarian")}>Use veterinarian demo</button></div><Show when={error()}><div class="error" role="alert">{error()}</div></Show><form class="stack-form" onSubmit={submit}><label>Email<input type="email" value={email()} onInput={e=>setEmail(e.currentTarget.value)} required/></label><label>Password<input type="password" value={password()} onInput={e=>setPassword(e.currentTarget.value)} required/></label><button class="primary" disabled={busy()}>{busy()?"Logging on…":"Log on"}</button></form><small>Local demo only. Credentials and sessions reset with the API.</small></section></main>
}
