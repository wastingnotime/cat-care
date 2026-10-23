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
| A18 | Comment on an observation, rotate before saving, reopen history | Draft and parent remain correct; replies persist oldest-first with timestamps; all history records offer a comment action; blank replies rejected |
| A19 | Upgrade schema 2, export/delete one cat with comments | Existing data survives upgrade; scoped export includes only that cat's comments; deletion cascades to comments and preserves other cats; observation counters exclude comments |

| A20 | Install Kotlin/Compose APK over the Java schema-3 app | Existing cats, tasks, history and comments retain all IDs and values; core owner flows work offline; no reset or schema upgrade occurs |

| A21 | Plan/edit/defer with calendar and numeric/clock time entry; rotate a weekly draft | ISO date/time and target survive; date without time rejected; UTC instant and recurrence zone persisted; daily/weekly/monthly choices and custom interval agree and next-date preview |
| A22 | Switch PT-BR/EN-US offline; inspect cards/forms/errors/history | App labels, plural counts, calendar/time controls and new system events translate; notes/comments and legacy prose stay unchanged; cat badges identify every care/history card |
| A23 | Display one instant in PT-BR/EN-US and different device zones including DST | Date order/time convention follow locale/device preference; conversion preserves the instant; no zone ID or UTC offset is appended; profile dates do not shift |
| A24 | Upgrade schema 3 with existing observations and replies | All old values/IDs survive; nullable metadata added; new automatic events translate across language changes and appear in export version 3 |

| A25 | Switch device/light/dark theme and recreate the activity | Preference persists; forms, calendars, cards, status colours and system bars remain legible |
| A26 | Open Today with a past-due exam; leave app open across a due time | Summary uses overdue label and error colour, updates within 30 seconds or immediately on resume |
| A27 | Comment on a measurement/profile/lifecycle card; share it | Reply persists under correct card; Android chooser gets only that card, cat, localized timestamps and comments |
| A28 | Allow notifications; schedule, edit, complete/cancel/delete care | Advance/due alerts post once per stage; changed tasks remove stale alerts and alarms; no automatic completion |
| A29 | Deny notifications, disable reminders, reboot, change timezone | Disabled/denied UI remains truthful; no false sent marker; restart reconciles schedules and preserves UTC instants |
| A30 | Upgrade schema 4 task across New York spring DST, then complete it | Due instant unchanged by UTC migration; next daily occurrence remains at original local hour with new offset |

| A31 | Open the ellipsis menu with and without cats | Only three icon tabs remain; localized accessibility names and activity titles identify them; Data, Settings and About are reachable |
| A32 | Choose backup folder, back up repeatedly, disable automatic saving | Initial/manual copies are valid full snapshots; daily persisted job exists; only newest seven app copies remain; disabling keeps files and cancels job |
| A33 | Restore a full backup, cancel, recreate preview, then confirm | Counts match file; cancel changes nothing; confirmation survives recreation; confirmed replacement preserves IDs/comments/recurrence and installation settings |
| A34 | Restore malformed/scoped/new-version/orphaned data or fail insertion | File rejected or transaction rolled back; existing local data remains intact |
| A35 | Fail publishing a backup or revoke folder access | Failure is visible; prior completed copies and last-success timestamp remain; no partial file is published as completed |
| A36 | Read About/privacy in both languages | WNT attribution and actual storage, backup/provider, sharing and deletion behavior are explained |

Record build revision, device/Android version, clock/timezone, network state,
actions and observed local state for release evidence. Document timezone and
calendar recurrence behavior, export format, restore support and backup policy
before release. Existing web/cloud fixtures remain evidence for their own
surface; veterinarian and server lifecycle scenarios do not apply to Android.
