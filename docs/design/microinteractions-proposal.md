# Quiet playfulness: Android interaction review

Status: coat-selector prototype authorized and implemented on 2026-11-16;
broader opportunities remain proposals. Subjective feel review is pending.
Reviewed: 2026-11-16.
Input: `~/Downloads/cat-care-microinteractions-handoff.md`.

The best starting point is a responsive coat selector: immediate selection,
subtle press feedback and one blink from the chosen cat. The best subsequent
everyday improvements are consistent category selection and clearer visual
continuity when completing care. Warmth should accompany useful actions.

## Evidence and current foundation

This is a source review of the Android app, supported by existing visual evidence,
not a live usability or performance test. “Activities” here means user workflows:
the app has one main Android activity hosting Compose screens and dialogs.
Web, API and simulation interactions are outside this proposal.

- [CatCareApp](../../apps/mobile/app/src/main/kotlin/org/wastingnotime/catcare/CatCareApp.kt)
  owns navigation, editors, coat selection, cat chips and snackbars.
- [CareScreens](../../apps/mobile/app/src/main/kotlin/org/wastingnotime/catcare/CareScreens.kt)
  implements Today, Cats, Notes, History, entry details, Settings, About and Data.
- [CareTheme](../../apps/mobile/app/src/main/kotlin/org/wastingnotime/catcare/CareTheme.kt)
  supplies semantic light/dark tokens: Paper, Ink, Forest, Muted, Sage, Line and
  Rust. Preserve these tokens, existing typography, spacing and touch targets.
- [CatArtwork](../../apps/mobile/app/src/main/kotlin/org/wastingnotime/catcare/CatArtwork.kt)
  draws avatars and the larger resting cat with Compose Canvas. Avatar eyes are
  two separate ovals; their height can be varied without replacing assets.
  The resting illustration already has closed eyes: the avatar blink should not
  be generalized to that artwork.
- The coat grid uses radio selection semantics, a Sage background and a visible
  checkmark embedded in the localized label. Its border currently stays Line.
  It uses two columns, or one above 120% font scale. Preserve these behaviors.
- [CareCategoryPicker](../../apps/mobile/app/src/main/kotlin/org/wastingnotime/catcare/CareCategoryPicker.kt)
  already changes background, border and label weight on selection.
  History and cat filters use Material 3 FilterChip; fields use OutlinedTextField.
  Inspect their built-in feedback before adding another animation layer.
- No explicit custom animation vocabulary was found in the reviewed UI files.
  The app uses Compose Material 3 with BOM `2025.04.01`; no animation package
  should be added solely for this work.
- [Existing warmth work](../../runs/android-cozy-ux-2026-11-11/evidence.md)
  already supplies personalized empty Notes, natural Today summaries, category
  prompts and cat-specific save/completion messages with recurring next dates.
  The completion screenshot confirms these are present, so they are the baseline,
  not new opportunities. Older profile screenshots show a superseded layout;
  current source governs this proposal.

## Workflow inventory and priorities

Impact, effort and risk are comparative design estimates, not measured outcomes.
The order balances usefulness with a small, reviewable first experiment.

| Order | Workflow and current interaction | Opportunity | Impact | Effort / risk |
| --- | --- | --- | --- | --- |
| 1 | Register/edit cat: select coat | Slight press compression, existing green selection transition, one selected-avatar blink | High emotional value; occasional use | Small / low–medium: gesture cancellation and motion settings |
| 2 | Plan care / add note: choose category | Reuse the coat selector's color/press language; keep category icons still | High consistency; frequent use | Small / low |
| 3 | Save an editor | Put a clear busy cue beside Save while the operation runs, preserving button width | High reassurance | Small / low–medium: dialog visibility and accessibility |
| 4 | Today: confirm completion | Briefly settle remaining cards into place after committed completion; retain named success and next date | High everyday value | Medium / medium–high: grouping, focus and recurring successor |
| 5 | Profile optional dates; Data explanation; Today Upcoming | Gentle disclosure transition after explicit expansion | Medium orientation | Small–medium / medium: scrolling, validation and large lists |
| 6 | Notes/History: change cat, category or period | Keep built-in chip feedback; consider a short result-area fade only if abrupt replacement is confusing in testing | Medium clarity; low extra delight | Medium / medium: old/new content and focus |
| 7 | Cats list: open a profile | Keep native card press feedback; evaluate avatar reaction only later | Low incremental value: navigation may hide reaction | Small / low, but easily redundant |
| 8 | Entry details: read/share/comment | Preserve native menus and save confirmation; optional restrained comment insertion | Low incremental value | Medium / medium: focus and list anchoring |
| 9 | Onboarding, clear Today, empty Notes | Preserve welcoming static artwork; do not add idle loops | Existing warmth is sufficient | No change |
| 10 | Settings, reminders, About, export/restore/delete | Keep native control feedback and explicit progress/results; no playful reactions | Clarity matters most | No decorative work |

The Save opportunity comes from code: busy disables editor actions, while the
linear progress indicator belongs to the underlying app scaffold. A local cue
would be easier to notice inside the full-screen editor. Confirm this on device
before implementation; never manufacture a delay to make progress visible.

Completion deserves its own later prototype. Today uses keyed task items within
deadline groups, and refreshing can remove groups or introduce a recurring
successor. Do not simply animate every list mutation: time-based regrouping,
filter changes and initial loading should not trigger completion effects.
Preserve confirmation, actual persistence timing and the existing success text.
Do not infer “all care is done” from no dated tasks: undated care can remain.

## Proposed motion vocabulary

| Meaning | Starting behavior | Limits |
| --- | --- | --- |
| Touch acknowledged | Scale selected tile surface toward 0.97 over roughly 80–120 ms; restrained spring return | Fixed layout/hit area; cancel cleanly when scrolling |
| Choice changed | Existing background/border colors transition over 150–200 ms | Semantic state and visible check update immediately |
| Cat responds | Eyes close and reopen once over 180–250 ms | Explicit new coat selection only; no head tilt in first experiment |
| Content disclosed | Short 150–200 ms reveal for bounded content | No whole-page entrance choreography; avoid animating a long Upcoming list as a block |
| Work confirmed | Existing readable result message; later, restrained list settling | Success follows committed work; no animation-controlled persistence/navigation |

Keep standard field focus, labels, navigation indicators and date/time picker
behavior. Animate date values only if testing reveals a comprehension problem.
No sound, confetti, streaks, shaking validation fields, pulsing overdue items,
random reactions or repeating cat animations. Haptics are outside the prototype.

## Minimal first prototype for approval

Scope: `AvatarGrid` in the cat editor plus an opt-in eye-openness input to
`CatAvatar`. No reactions on other screens. Preserve the current grid, palette,
labels and artwork at rest. Keep the existing checkmark immediate; a separate
checkmark animation would add complexity without helping this experiment.

1. Press a coat card: compress its visual surface slightly while retaining its
   layout and touch target. Release or cancel returns it to normal.
2. Activate a different coat by touch, keyboard or accessibility action: update
   the draft and selected semantics immediately; transition the existing surface
   color; blink only the newly selected avatar.
3. Rapidly choose another coat: newest selection wins; cancel the previous blink
   and leave its eyes open. Never queue reactions. Re-selecting the active coat
   gives normal press feedback without another blink.
4. Opening/recreating the editor, restoring a draft, scrolling or changing theme
   must not trigger a blink. Reaction state is transient and local, separate from
   the saved coat identifier.
5. With animations disabled, skip compression/blink and snap decorative state
   to its resting value. Selection and saving remain immediate. Verify the
   effective system motion setting rather than assuming custom drawing respects it.

Prefer Compose animation primitives and the existing selectable interaction
source. Animate eye geometry around its current center, preserving colors and
an identifiable closed-eye shape. Keep animation reads close to drawing and
avoid recomposing the whole form for each frame. A neutral default keeps all
other avatar usages static.

Compose documents built-in property/visibility animations in its
[animation guide](https://developer.android.com/develop/ui/compose/animation/quick-guide).
Its [MotionDurationScale contract](https://developer.android.com/reference/kotlin/androidx/compose/ui/MotionDurationScale)
defines zero-scale completion on the next frame. Verify behavior with this
repository's pinned versions; do not use raw delay loops for eye animation or
upgrade dependencies as part of the experiment.

## Evaluation and acceptance

Compare the current app with the prototype on the same disposable emulator or
test device. Ask the owner to choose a coat, change it several times, scroll,
save, reopen, and cancel an edit. Alternate which version is tried first.
Compare “responsive,” “calm” and “pleasant” ratings on a simple 1–5 scale, plus
whether the selected coat is immediately identifiable. This is a qualitative
decision aid, not a statistically validated study.

Acceptance checks before adoption:

- All eight coats remain recognizable, including closed eyes on black, tuxedo
  and Siamese; checkmark, label and selected semantics agree throughout.
- Rapid taps and a press that becomes a scroll cause no stuck scale, closed eyes,
  extra selection, queued blinks or delayed input. Only the final coat saves.
- Keyboard and TalkBack selection work without animation-dependent cues or
  redundant announcements. Focus stays stable.
- Test light/dark, English/Portuguese, normal/200% font size, narrow screens,
  recreation during a blink and system animations off. The one-column grid,
  draft restoration, validation and Save/Cancel remain usable.
- No idle animation work after the reaction. Inspect a short frame trace during
  rapid selections for regressions against baseline; do not claim performance
  from screenshots alone.
- Extend the existing coat persistence/recreation coverage in
  [CareComposeTest](../../apps/mobile/app/src/androidTest/kotlin/org/wastingnotime/catcare/CareComposeTest.kt)
  with cancellation, rapid selection and disabled-motion checks. Keep checks
  focused on state and usability rather than exact animation curves.
- Ship only if the owner prefers the prototype's feel and functional checks pass.
  If it feels busy, remove compression first and compare color plus blink; if the
  blink is distracting or illegible, retain only useful selection feedback.

## Shared versus screen-specific behavior

Share small duration/easing constants, motion-setting handling and eventually
selection feedback between coat and category tiles. Keep existing Material
components responsible for their own built-in feedback. Avoid introducing a
generic animation framework for one prototype.

Keep coat-selection triggers, eye geometry and cancellation local to the coat
picker/artwork. Keep completion events and agenda placement rules in the Today
flow; preserve the ViewModel's committed-result semantics. Keep disclosure state
with each screen. No database, care rules, backup format or shared WNT policy
changes are needed.

Recommended review decision: approve only the coat-selector experiment first.
Category feedback and editor-local busy feedback are the next small increments;
completion transitions require separate review after observing the prototype.
