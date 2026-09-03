import { A, useLocation } from "@solidjs/router";
import { createResource } from "solid-js";
import type { JSX } from "solid-js";
import { api } from "../lib/api";
import type { Session } from "../lib/contracts";

const navigation = [
  { href: "/", label: "Today" },
  { href: "/profile", label: "Cat profile" },
  { href: "/triage", label: "Triage" },
  { href: "/account", label: "Account & data" },
];

export function Shell(props: { children: JSX.Element }) {
  const location = useLocation();
  const [session] = createResource(() => api<Session>("session"));
  const isCurrent = (href: string) => href === "/" ? location.pathname === "/" : location.pathname.startsWith(href);

  return <>
    <header class="site-header">
      <A class="brand" href="/" aria-label="Cat Care home"><span aria-hidden="true">ᓚᘏᗢ</span> Cat Care</A>
      <nav class="primary-nav" aria-label="Primary navigation">
        {navigation.filter(item=>session()?.user.mode==="veterinarian" ? item.href==="/triage" : item.href!=="/triage").map(item => <A href={item.href} aria-current={isCurrent(item.href) ? "page" : undefined}>{item.label}</A>)}
      </nav>
      <div class="session-controls"><label><span class="sr-only">Active cat</span><select value={session()?.active_cat_id} onChange={async e=>{await api(`cats/${e.currentTarget.value}/select`,{method:"POST"});window.location.reload()}}>{session()?.cats.map(cat=><option value={cat.id}>{cat.profile.name}</option>)}</select></label><span class={`mode-badge ${session()?.user.mode}`}>{session()?.user.mode??"local"}</span><button class="text-button" onClick={async()=>{await api("session",{method:"DELETE"});window.location.href="/login"}}>Log out</button></div>
    </header>
    <main>{props.children}</main>
    <footer>Notes and provisional triage help organize care. They are not medical diagnoses or treatment advice.</footer>
  </>;
}
