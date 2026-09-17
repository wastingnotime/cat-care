export const catCoats = [
  { id:"orange-tabby", label:"Orange tabby" },
  { id:"brown-tabby", label:"Brown tabby" },
  { id:"gray", label:"Gray" },
  { id:"black", label:"Black" },
  { id:"white", label:"White" },
  { id:"tuxedo", label:"Tuxedo" },
  { id:"calico", label:"Calico" },
  { id:"siamese", label:"Siamese" },
] as const;

export type CatCoat = typeof catCoats[number]["id"];

export function catCoat(value?:string):CatCoat {
  return catCoats.some(coat=>coat.id===value) ? value as CatCoat : "gray";
}

export function CatFace(props:{coat?:string;size?:"small"|"large";label?:string}) {
  const selected=()=>catCoat(props.coat);
  const label=()=>props.label??catCoats.find(coat=>coat.id===selected())?.label??"Cat";
  return <span class={`cat-face ${props.size??"small"}`} data-coat={selected()} role="img" aria-label={label()}><span class="cat-ear left"/><span class="cat-ear right"/><span class="cat-head"><span class="cat-patch"/><span class="cat-stripes"/><span class="cat-eye left"/><span class="cat-eye right"/><span class="cat-nose"/><span class="cat-muzzle"/></span></span>;
}
