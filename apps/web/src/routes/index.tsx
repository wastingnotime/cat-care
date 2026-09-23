import { For, Show, createEffect, createResource, createSignal, onCleanup, onMount } from "solid-js";
import { Shell } from "../components/Shell";
import { CatFace } from "../components/CatFace";
import { api } from "../lib/api";
import { exactDateTime, relativeTime } from "../lib/time";
import type { CareEvent, CareStatus, CatAccount, Note, Responsibility, Session, TriageAssessment } from "../lib/contracts";

const statusLevels = [
  { kind: "urgent", label: "Urgent", more: "need urgent care" },
  { kind: "overdue", label: "Needs attention", more: "need attention" },
  { kind: "review_pending", label: "Review pending", more: "are waiting for review" },
  { kind: "due_soon", label: "Coming up", more: "have care coming up" },
  { kind: "unknown", label: "Needs scheduling", more: "need scheduling" },
] as const;
const formatDate = (value?: string | null) => value ? relativeTime(value) : "No due date yet";
const withCat = (path:string, catID:string) => `${path}${path.includes("?")?"&":"?"}cat_id=${encodeURIComponent(catID)}`;

type CatCare = { cat:CatAccount; status:CareStatus; responsibilities:Responsibility[]; timeline:CareEvent[]; notes:Note[]; triage:TriageAssessment[] };

export default function Today() {
  const [session, { refetch: refetchSession }] = createResource(() => api<Session>("session"));
  const [care, { refetch: refetchCare }] = createResource(() => session()?.cats, async cats => Promise.all(cats.map(async cat => {
    const [status,responsibilities,timeline,notes,triage] = await Promise.all([
      api<CareStatus>(withCat("status",cat.id)), api<Responsibility[]>(withCat("responsibilities",cat.id)), api<CareEvent[]>(withCat("timeline",cat.id)), api<Note[]>(withCat("notes",cat.id)), api<TriageAssessment[]>(withCat("triage",cat.id)),
    ]);
    return {cat,status,responsibilities:responsibilities??[],timeline:timeline??[],notes:notes??[],triage:triage??[]} satisfies CatCare;
  })));
  const [actionCat,setActionCat]=createSignal("");
  const [formVisible,setFormVisible]=createSignal(false); const [quickAction,setQuickAction]=createSignal<"observation"|"care"|null>(null);
  const [commentingOn,setCommentingOn]=createSignal<string|null>(null); const [commentText,setCommentText]=createSignal(""); const [comments,setComments]=createSignal<Record<string,{text:string; occurred_at:string}[]>>({});
  const [title,setTitle]=createSignal(""); const [category,setCategory]=createSignal("veterinary"); const [dueAt,setDueAt]=createSignal(""); const [recurrenceDays,setRecurrenceDays]=createSignal(0);
  const [noteText,setNoteText]=createSignal(""); const [careType,setCareType]=createSignal("measurement"); const [careDescription,setCareDescription]=createSignal("");
  const [scheduleFor,setScheduleFor]=createSignal<string|null>(null); const [scheduleAt,setScheduleAt]=createSignal("");
  const [localDay,setLocalDay]=createSignal("");
  const [error,setError]=createSignal(""); const [notice,setNotice]=createSignal(""); const [saving,setSaving]=createSignal(false);
  onMount(()=>{
    let dayTimer:number;
    const updateDay=()=>{
      const now=new Date();
      setLocalDay(now.toDateString());
      dayTimer=window.setTimeout(updateDay,new Date(now.getFullYear(),now.getMonth(),now.getDate()+1).getTime()-now.getTime()+1000);
    };
    updateDay();
    onCleanup(()=>window.clearTimeout(dayTimer));
  });
  createEffect(()=>{if(!actionCat()&&session()?.active_cat_id)setActionCat(session()!.active_cat_id)});

  const selectedCatID=()=>actionCat()||session()?.active_cat_id||session()?.cats[0]?.id||"";
  const catName=(catID:string)=>session()?.cats.find(cat=>cat.id===catID)?.profile.name??"Cat";
  const rememberCat=async(catID:string)=>{if(catID&&catID!==session()?.active_cat_id)await api(`cats/${catID}/select`,{method:"POST"});await refetchSession()};
  const command=async(path:string,body?:unknown,catID=selectedCatID())=>{setError("");setNotice("");try{await api(withCat(path,catID),{method:"POST",body:body===undefined?undefined:JSON.stringify(body)});setActionCat(catID);await rememberCat(catID);await refetchCare()}catch(failure){setError(failure instanceof Error?failure.message:"The care command failed");throw failure}};
  async function createResponsibility(event:SubmitEvent){event.preventDefault();setSaving(true);const id=selectedCatID();try{await command("responsibilities",{title:title(),category:category(),due_at:dueAt()?new Date(dueAt()).toISOString():null,recurrence_days:recurrenceDays(),recurrence_months:0},id);setTitle("");setDueAt("");setRecurrenceDays(0);setFormVisible(false);setNotice(`Responsibility added for ${catName(id)}.`)}catch{}finally{setSaving(false)}}
  async function recordNote(event:SubmitEvent){event.preventDefault();const id=selectedCatID();try{await command("notes",{description:noteText()},id);setNoteText("");setQuickAction(null);setNotice(`Observation added to ${catName(id)}'s care history. It remains a note, not a diagnosis.`)}catch{}}
  async function recordCare(event:SubmitEvent){event.preventDefault();const id=selectedCatID();try{await command("care-events",{type:careType(),description:careDescription(),responsibility_id:""},id);setCareDescription("");setQuickAction(null);setNotice(`Care event added to ${catName(id)}'s history.`)}catch{}}
  const notify=(catID:string,id:string)=>command("notifications",{responsibility_id:id,outcome:"delivered"},catID).then(()=>setNotice("Notification delivered; responsibility state was not changed.")).catch(()=>{});
  const deferSevenDays=(catID:string,id:string,currentDue:string|null)=>command(`responsibilities/${id}/defer`,{due_at:new Date(Math.max(Date.now(),currentDue?new Date(currentDue).getTime():0)+7*86400000).toISOString()},catID).then(()=>setNotice("Responsibility deferred by seven days.")).catch(()=>{});
  const updateSchedule=async(event:SubmitEvent,catID:string,item:Responsibility)=>{
    event.preventDefault();
    setSaving(true);setError("");setNotice("");
    try{
      await api(withCat(`responsibilities/${item.id}`,catID),{method:"PUT",body:JSON.stringify({title:item.title,category:item.category,due_at:new Date(scheduleAt()).toISOString(),recurrence_days:item.recurrence_days??0,recurrence_months:item.recurrence_months??0})});
      await refetchCare();
      setScheduleFor(null);setScheduleAt("");
      setNotice(`${item.title} scheduled for ${catName(catID)}.`);
    }catch(failure){setError(failure instanceof Error?failure.message:"Schedule could not be saved.")}
    finally{setSaving(false)}
  };
  const requestTriage=(catID:string,noteID:string)=>command("triage",{note_ids:[noteID]},catID).then(()=>setNotice("Provisional triage requested; veterinarian review is still required.")).catch(()=>{});
  const statusSentence=(item:CatCare,kind:string)=>{
    if(kind==="review_pending")return "Waiting for veterinarian review.";
    if(kind==="overdue"||kind==="unknown"){
      const nearest=item.responsibilities.find(responsibility=>responsibility.id===item.status.nearest_responsibility_id);
      if(nearest)return kind==="overdue"?`${nearest.title} is overdue.`:`Set a due date for ${nearest.title}.`;
    }
    return item.status.sentence;
  };
  const statusGroups=()=>statusLevels.map(level=>({
    ...level,
    cats:(care()??[]).filter(item=>level.kind==="review_pending"?item.triage.some(assessment=>assessment.review_status==="pending"):item.status.kind===level.kind),
  })).filter(group=>group.cats.length>0);
  const openQuickAction=(action:"observation"|"care")=>{
    const next=quickAction()===action?null:action;
    setFormVisible(false);
    setQuickAction(next);
    if(next)window.setTimeout(()=>document.getElementById(action==="observation"?"observation-input":"care-description-input")?.focus(),0);
  };
  const openResponsibility=()=>{setQuickAction(null);setFormVisible(value=>!value)};
  const addComment=(event:SubmitEvent,eventID:string)=>{event.preventDefault();const value=commentText().trim();if(!value)return;setComments(current=>({...current,[eventID]:[...(current[eventID]??[]),{text:value,occurred_at:new Date().toISOString()}]}));setCommentText("");setCommentingOn(null)};
  const addTriageComment=async(event:SubmitEvent,catID:string,assessmentID:string)=>{event.preventDefault();const value=commentText().trim();if(!value)return;try{await command(`triage/${assessmentID}/comments`,{message:value},catID);setCommentText("");setCommentingOn(null);setNotice("Your reply was shared with the veterinarian.")}catch{}};
  const eventLabel=(type:string)=>type.startsWith("responsibility_")?"Responsibility":type==="note_recorded"?"Observation":type==="notification_recorded"?"Notification":type==="triage_requested"?"Triage":"Care update";
  const triageCommentLabel=(type:string)=>type.replace("triage_","triage ").replaceAll("_"," ");
  const allResponsibilities=()=>{
    const items=(care()??[]).flatMap(item=>item.responsibilities.map(responsibility=>({cat:item.cat,responsibility})));
    const active=items.filter(item=>item.responsibility.state!=="completed"&&item.responsibility.state!=="cancelled");
    const finishedToday=items.filter(item=>{
      const responsibility=item.responsibility;
      const finishedAt=responsibility.state==="completed"?responsibility.completed_at:responsibility.state==="cancelled"?responsibility.cancelled_at:null;
      return finishedAt&&new Date(finishedAt).toDateString()===localDay();
    });
    return [...active,...finishedToday];
  };
  const allTimeline=()=>(care()??[]).flatMap(item=>{const roots=new Set(item.timeline.filter(event=>event.type==="responsibility_created").map(event=>event.responsibility_id));return item.timeline.filter(event=>(!event.type.startsWith("triage_")||event.type==="triage_requested")&&(!event.responsibility_id||event.type==="responsibility_created"||!roots.has(event.responsibility_id))).map(event=>({cat:item.cat,event}))}).sort((a,b)=>new Date(b.event.occurred_at).getTime()-new Date(a.event.occurred_at).getTime());
  const triageForNote=(catID:string,noteID:unknown)=>care()?.find(item=>item.cat.id===catID)?.triage.find(item=>item.note_ids.includes(String(noteID)));
  const triageComments=(catID:string,assessmentID:unknown)=>(care()?.find(item=>item.cat.id===catID)?.timeline??[]).filter(item=>item.type.startsWith("triage_")&&item.type!=="triage_requested"&&item.details.assessment_id===assessmentID).slice().reverse();
  const responsibilityComments=(catID:string,responsibilityID:string)=>(care()?.find(item=>item.cat.id===catID)?.timeline??[]).filter(item=>item.responsibility_id===responsibilityID&&item.type!=="responsibility_created"&&!item.type.startsWith("triage_")).slice().reverse();
  const catPicker=(name:string)=><fieldset class="quick-cat-picker"><legend>Cat</legend><div><For each={session()?.cats}>{cat=><label data-selected={selectedCatID()===cat.id}><input type="radio" name={name} value={cat.id} checked={selectedCatID()===cat.id} onChange={()=>setActionCat(cat.id)}/><CatFace coat={cat.profile.photo_ref} label={`${cat.profile.name} face`}/><span>{cat.profile.name}</span></label>}</For></div></fieldset>;

  return <Shell>
    <section class="hero" aria-labelledby="greeting"><p class="eyebrow">Today</p><h1 id="greeting"><Show when={(session()?.cats.length??0)>1} fallback={<>How is <span>{session()?.cats[0]?.profile.name??"your cat"}</span> doing?</>}>How are <span>your cats</span> doing?</Show></h1><Show when={care()}>{items=><div class="cat-status-list" aria-live="polite"><Show when={items().length>0} fallback={<div class="status-card"><span class="status-dot" aria-hidden="true"/><div><p class="status-label">No cats yet</p><p class="status-copy">Add a cat to start tracking care.</p></div></div>}><Show when={statusGroups().length>0} fallback={<div class="status-card" data-kind="clear"><span class="status-dot" aria-hidden="true"/><div><p class="status-label">All calm</p><p class="status-copy">Nothing important is pending.</p></div></div>}><For each={statusGroups()}>{group=><div class="status-card" data-kind={group.kind}><span class="status-dot" aria-hidden="true"/><div class="status-card-content"><p class="status-label">{group.label}</p><p class="status-copy"><strong>{group.cats[0].cat.profile.name}</strong> · {statusSentence(group.cats[0],group.kind)}</p><Show when={group.cats.length>1}><details class="status-more"><summary>{group.cats.length-1} more {group.cats.length===2?"cat":"cats"} {group.more}</summary><ul><For each={group.cats.slice(1)}>{item=><li><strong>{item.cat.profile.name}</strong> · {statusSentence(item,group.kind)}</li>}</For></ul></details></Show></div></div>}</For></Show></Show></div>}</Show></section>
    <Show when={error()}><div class="error" role="alert">{error()}</div></Show><Show when={notice()}><div class="notice" role="status">{notice()}</div></Show>
    <section class="quick-actions" aria-labelledby="quick-actions-title"><div><p class="eyebrow">Quick actions</p><h2 id="quick-actions-title">Add to your cats' care</h2></div><div class="quick-action-links"><button class="secondary" aria-expanded={quickAction()==="observation"} onClick={()=>openQuickAction("observation")}>Add observation</button><button class="secondary" aria-expanded={quickAction()==="care"} onClick={()=>openQuickAction("care")}>Record direct care</button><button class="secondary" aria-expanded={formVisible()} onClick={openResponsibility}>Add responsibility</button></div></section>
    <Show when={quickAction()}><section class="panel quick-action-form" aria-label="Quick action form"><Show when={quickAction()==="observation"}><div><p class="eyebrow">New observation</p><h2>What did you notice?</h2><form class="stack-form" onSubmit={recordNote}>{catPicker("observation-cat")}<label class="sr-only" for="observation-input">What did you notice?</label><textarea id="observation-input" value={noteText()} onInput={e=>setNoteText(e.currentTarget.value)} required placeholder="Eating less than usual"/><div class="form-actions"><button class="primary">Add to history</button><button class="text-button" type="button" onClick={()=>setQuickAction(null)}>Cancel</button></div></form></div></Show><Show when={quickAction()==="care"}><div><p class="eyebrow">Direct care</p><h2>What happened?</h2><form class="stack-form" onSubmit={recordCare}>{catPicker("care-cat")}<label>Type<select value={careType()} onChange={e=>setCareType(e.currentTarget.value)}><option value="measurement">Measurement</option><option value="exam_performed">Exam</option><option value="medication_given">Medication given</option></select></label><label>Description<textarea id="care-description-input" value={careDescription()} onInput={e=>setCareDescription(e.currentTarget.value)} required placeholder="Weight 4.2 kg"/></label><div class="form-actions"><button class="primary">Add to history</button><button class="text-button" type="button" onClick={()=>setQuickAction(null)}>Cancel</button></div></form></div></Show></section></Show>
    <section class="workspace" aria-label="Care workspace"><article class="panel responsibilities-panel"><div class="panel-heading"><div><p class="eyebrow">Responsibilities</p><h2>What needs care</h2></div></div><Show when={formVisible()}><form class="responsibility-form" onSubmit={createResponsibility}>{catPicker("responsibility-cat")}<label>What needs to happen?<input value={title()} onInput={e=>setTitle(e.currentTarget.value)} required maxlength="160" placeholder="Annual exam"/></label><label>Category<select value={category()} onChange={e=>setCategory(e.currentTarget.value)}><option value="veterinary">Veterinary</option><option value="preventive">Preventive care</option><option value="nutrition">Nutrition</option><option value="grooming">Grooming</option><option value="other">Other</option></select></label><label>Due date <span class="optional">optional</span><input value={dueAt()} onInput={e=>setDueAt(e.currentTarget.value)} type="datetime-local"/></label><label>Repeat every <span class="optional">days</span><input value={recurrenceDays()} onInput={e=>setRecurrenceDays(Number(e.currentTarget.value))} type="number" min="0"/></label><div class="form-actions"><button class="primary" disabled={saving()}>{saving()?"Saving…":"Save responsibility"}</button><button class="text-button" type="button" onClick={()=>setFormVisible(false)}>Cancel</button></div></form></Show><div class="responsibility-list"><Show when={allResponsibilities().length>0} fallback={<p class="empty">No responsibilities yet.</p>}><For each={allResponsibilities()}>{entry=><article class={`responsibility ${entry.responsibility.state}`}><div><p class="item-cat">{entry.cat.profile.name}</p><h3>{entry.responsibility.title}</h3><p>{entry.responsibility.category} · <span title={entry.responsibility.due_at?exactDateTime(entry.responsibility.due_at):undefined}>{formatDate(entry.responsibility.due_at)}</span> · {entry.responsibility.derived_state.replace("_"," ")}{entry.responsibility.recurrence_days?` · every ${entry.responsibility.recurrence_days} days`:""}</p></div><Show when={entry.responsibility.state==="planned"}><div class="compact-actions"><button class="complete-button" aria-label={`Mark ${entry.responsibility.title} complete`} onClick={()=>command(`responsibilities/${entry.responsibility.id}/complete`,undefined,entry.cat.id).catch(()=>{})}>Complete</button><Show when={entry.responsibility.derived_state==="unknown"}><button class="secondary" aria-expanded={scheduleFor()===`${entry.cat.id}:${entry.responsibility.id}`} onClick={()=>{setScheduleFor(`${entry.cat.id}:${entry.responsibility.id}`);setScheduleAt("")}}>Set due date</button></Show><button class="text-button" onClick={()=>deferSevenDays(entry.cat.id,entry.responsibility.id,entry.responsibility.due_at)}>Defer 7d</button><button class="text-button" onClick={()=>notify(entry.cat.id,entry.responsibility.id)}>Notify</button><button class="danger-link" onClick={()=>command(`responsibilities/${entry.responsibility.id}/cancel`,undefined,entry.cat.id).catch(()=>{})}>Cancel</button></div></Show><Show when={scheduleFor()===`${entry.cat.id}:${entry.responsibility.id}`}><form class="schedule-form" onSubmit={event=>updateSchedule(event,entry.cat.id,entry.responsibility)}><label>Due date for {entry.responsibility.title}<input type="datetime-local" value={scheduleAt()} onInput={event=>setScheduleAt(event.currentTarget.value)} required/></label><div class="form-actions"><button class="primary" disabled={saving()}>{saving()?"Saving…":"Save due date"}</button><button class="text-button" type="button" onClick={()=>setScheduleFor(null)}>Keep unscheduled</button></div></form></Show></article>}</For></Show></div></article></section>
    <section class="history-section" aria-labelledby="history-title"><div class="section-heading"><div><p class="eyebrow">Care history</p><h2 id="history-title">Life with your cats</h2></div><p>Observations, care and responsibilities — newest first.</p></div><div class="history-feed"><Show when={allTimeline().length>0} fallback={<div class="panel empty">Care updates will appear here.</div>}><For each={allTimeline().slice(0,20)}>{entry=>{const item=entry.event;return <article class="history-card"><header><span class="feed-avatar"><CatFace coat={entry.cat.profile.photo_ref} label={`${entry.cat.profile.name} face`}/></span><div><strong>{entry.cat.profile.name}</strong><small>{eventLabel(item.type)}</small><time dateTime={item.occurred_at} title={exactDateTime(item.occurred_at)}>{relativeTime(item.occurred_at)}</time></div><span class="feed-kind">{item.type.replaceAll("_"," ")}</span></header><p class="feed-copy">{item.description}</p><div class="feed-actions"><button class="text-button" onClick={()=>{setCommentingOn(commentingOn()===item.id?null:item.id);setCommentText("")}}>Comment</button><Show when={item.type==="note_recorded"&&!triageForNote(entry.cat.id,item.details.note_id)}><button class="text-button" onClick={()=>requestTriage(entry.cat.id,String(item.details.note_id))}>Request triage</button></Show></div><Show when={item.type==="note_recorded"&&triageForNote(entry.cat.id,item.details.note_id)}><p class="feed-comment"><strong>Triage</strong>Triage was requested.</p></Show><Show when={item.type==="triage_requested"}><For each={triageComments(entry.cat.id,item.details.assessment_id)}>{comment=><p class="feed-comment"><strong>{comment.type==="triage_owner_commented"?String(comment.details.author_name??"Owner"):"Triage"}</strong><time dateTime={comment.occurred_at} title={exactDateTime(comment.occurred_at)}>{relativeTime(comment.occurred_at)}</time>{comment.type==="triage_owner_commented"?comment.description:`${triageCommentLabel(comment.type)}: ${comment.description}`}</p>}</For></Show><Show when={item.type==="responsibility_created"}><For each={responsibilityComments(entry.cat.id,item.responsibility_id??"")}>{comment=><p class="feed-comment"><strong>Responsibility</strong><time dateTime={comment.occurred_at} title={exactDateTime(comment.occurred_at)}>{relativeTime(comment.occurred_at)}</time>{comment.type.replaceAll("_"," ")}: {comment.description}</p>}</For></Show><For each={comments()[item.id]??[]}>{comment=><p class="feed-comment"><strong>You</strong><time dateTime={comment.occurred_at} title={exactDateTime(comment.occurred_at)}>{relativeTime(comment.occurred_at)}</time>{comment.text}</p>}</For><Show when={commentingOn()===item.id}><form class="comment-form" onSubmit={event=>item.type==="triage_requested"?addTriageComment(event,entry.cat.id,String(item.details.assessment_id)):addComment(event,item.id)}><label class="sr-only" for={`comment-${item.id}`}>Comment on {item.description}</label><input id={`comment-${item.id}`} value={commentText()} onInput={e=>setCommentText(e.currentTarget.value)} placeholder="Write a comment…" autofocus/><button class="primary">Post</button></form></Show></article>}}</For></Show></div></section>
  </Shell>;
}
