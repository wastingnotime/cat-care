import { A, useLocation } from "@solidjs/router";
import type { JSX } from "solid-js";

const navigation = [
  { href: "/", label: "Today" },
  { href: "/profile", label: "Cat profile" },
  { href: "/triage", label: "Triage" },
  { href: "/account", label: "Account & data" },
];

export function Shell(props: { children: JSX.Element }) {
  const location = useLocation();
  const isCurrent = (href: string) => href === "/" ? location.pathname === "/" : location.pathname.startsWith(href);

  return <>
    <header class="site-header">
      <A class="brand" href="/" aria-label="Cat Care home"><span aria-hidden="true">ᓚᘏᗢ</span> Cat Care</A>
      <nav class="primary-nav" aria-label="Primary navigation">
        {navigation.map(item => <A href={item.href} aria-current={isCurrent(item.href) ? "page" : undefined}>{item.label}</A>)}
      </nav>
      <span class="local-badge">Local companion</span>
    </header>
    <main>{props.children}</main>
    <footer>Notes and provisional triage help organize care. They are not medical diagnoses or treatment advice.</footer>
  </>;
}
