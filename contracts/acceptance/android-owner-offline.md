# Offline owner Android acceptance contract

Scope: [ADR 0002](../../docs/adr/0002-offline-owner-android.md).
These are delivery requirements, not passing implementation evidence.

Use synthetic cats Mimi and Luna, a controlled clock and airplane mode throughout
unless a scenario explicitly says otherwise. The owner is the sole actor.

| ID | Scenario | Required result |
| --- | --- | --- |
| A01 | Install and launch with no network; create both cats | No sign-in or server required; both profiles usable locally |
| A02 | Edit Mimi's dates; select Luna; reopen the app | Valid changes persist for Mimi only; adoption before birth rejected |
| A03 | Add dated and undated responsibilities across both cats; open Today | Explicit cat identity, due status and scheduling uncertainty; no clinical urgency inferred |
| A04 | Create, edit, defer and cancel responsibilities | Valid transitions and history persist; invalid deferrals leave no partial changes |
| A05 | Complete a monthly task due January 31 at 09:00 in a non-leap year | One completion and one successor on February 28 at 09:00, with recurrence policy retained; atomic persistence |
| A06 | Record weight and an appetite observation for Mimi | Typed care and non-diagnosis observation appear in Mimi's history; Luna and planned tasks unchanged |
| A07 | Close the app and restart the device after successful writes | Profiles, responsibilities, recurrence and care history survive without network access |
| A08 | Export or delete Mimi while Luna has history | Scope preview names Mimi; export contains Mimi only; deletion removes Mimi's covered local data and preserves Luna |
| A09 | Export or delete all local app data | Scope preview enumerates affected cats; export covers them; confirmed deletion follows documented local storage and backup coverage |
| A10 | Navigate every owner flow | No veterinarian login, workspace, invitation, assignment or review dependency; owner can record a veterinary appointment |
| A11 | Use PT-BR and Android accessibility services for Today and care entry | Cat, date, type, confirmation and error states are understandable and operable |
| A12 | If reminders are shipped, deny permission and later trigger a local reminder offline | Permission/delivery limits visible; no false delivery claim; task remains planned until owner completes it |
| A13 | Open Gatos with multiple cats and mixed task states | One avatar/name card per cat; total responsibility and observation counts are scoped correctly; only planned tasks count as pending; no badge for zero pending |
| A14 | Create/edit a cat with dates and each available coat | Labeled visual choices match card avatars; saving updates the correct cat and reveals its card; dates and coat persist after restart and appear in export |
| A15 | Choose a coat and type a name, then rotate before saving | Name/date/coat draft survives; saving applies the selected coat without creating duplicate cats |
| A16 | Open Hoje with two cats, create a plan by avatar, rotate the draft | No page cat selector or Registrar action; an explicit form cat choice is required and survives rotation; only that cat receives the task |
| A17 | Register from Histórico, changing the cat by avatar | Record belongs to the chosen cat and that cat's history is shown after save; draft selection survives rotation |
| A18 | Comment on an observation, rotate before saving, reopen history | Draft and parent remain correct; replies persist oldest-first with timestamps; non-observation records have no comment action; blank replies rejected |
| A19 | Upgrade schema 2, export/delete one cat with comments | Existing data survives upgrade; scoped export includes only that cat's comments; deletion cascades to comments and preserves other cats; observation counters exclude comments |

Record build revision, device/Android version, clock/timezone, network state,
actions and observed local state for release evidence. Document timezone and
calendar recurrence behavior, export format, restore support and backup policy
before release. Existing web/cloud fixtures remain evidence for their own
surface; veterinarian and server lifecycle scenarios do not apply to Android.
