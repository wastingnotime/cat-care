import { A, useLocation } from "@solidjs/router";
import { createResource } from "solid-js";
import type { JSX } from "solid-js";
import { api } from "../lib/api";
import type { Session } from "../lib/contracts";
import type { Workspace } from "../lib/contracts";

const navigation = [
  { href: "/", label: "Today" },
  { href: "/cats", label: "Cats" },
  { href: "/triage", label: "Triage" },
  { href: "/account", label: "Account & data" },
];

export function Shell(props: { children: JSX.Element }) {
  const location = useLocation();
  const [session] = createResource(() => api<Session>("session"));
  const isCurrent = (href: string) => href === "/" ? location.pathname === "/" : location.pathname.startsWith(href);
  const switchWorkspace=async(mode:Workspace)=>{if(mode===session()?.user.mode)return;await api<Session>("session/workspace",{method:"POST",body:JSON.stringify({mode})});window.location.href=mode==="veterinarian"?"/triage":"/"};

  return <>
    <header class="site-header">
      <A class="brand" href="/" aria-label="Cat Care home"><span aria-hidden="true">ᓚᘏᗢ</span> Cat Care</A>
      <nav class="primary-nav" aria-label="Primary navigation">
        {navigation.filter(item=>session()?.user.mode==="veterinarian" ? item.href==="/triage" : item.href!=="/triage").map(item => <A href={item.href} aria-current={isCurrent(item.href) ? "page" : undefined}>{item.label}</A>)}
      </nav>
      <div class="session-controls">{(session()?.user.roles.length??0)>1?<label class="workspace-switcher"><span class="sr-only">Active workspace</span><select value={session()?.user.mode} onChange={event=>switchWorkspace(event.currentTarget.value as Workspace)}><option value="owner">Owner workspace</option><option value="veterinarian">Veterinarian workspace</option></select></label>:<span class={`mode-badge ${session()?.user.mode}`}>{session()?.user.mode??"local"}</span>}<button class="text-button" onClick={async()=>{await api("session",{method:"DELETE"});window.location.href="/login"}}>Log out</button></div>
    </header>
    <main>{props.children}</main>
    <footer>Notes and provisional triage help organize care. They are not medical diagnoses or treatment advice. The Notify action is included for test purposes only.</footer>
  </>;
}
