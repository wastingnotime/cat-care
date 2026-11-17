# Care card creation

The Android Today planning editor orders inputs as cat, visual category, editable
title, schedule and recurrence. Categories use icon tiles with short localized
labels and radio selection semantics. Large text switches the tiles to one column.

New titles start with the selected category's localized name; Other suggests
“Care” / “Cuidado”. Category changes update the suggestion until the owner edits
the title. Manual edits and all existing task titles are preserved when changing
category. The draft remembers this distinction across recreation. Empty manually
edited titles retain required-field validation.

Time selection opens the clock face, with a toggle for keyboard entry. The picker
starts from the saved time or the current time and follows the device's 12/24-hour
preference. Opening or dismissing it does not change the stored time.

Care and note category tiles share the coat selector's restrained press feedback
and 180 ms background transition. The selected border, bold label and radio state
update immediately. Touch targets retain their size during compression; cancelled
presses do not select. System-disabled animations show the final state directly.
Category icons remain static. Shared behavior lives in `SelectionCard`, while the
coat-only blink remains in `CoatSelectionCard`.

Editable forms show a small progress indicator beside Save while the ViewModel
reports actual work. The indicator has a reserved slot, keeping button width and
action placement stable. Save and Cancel remain disabled during work; completion
or validation errors immediately release the busy state without a minimum display
duration. The Save label stays visible, with localized “Saving…” / “Salvando…”
accessibility state and a polite live announcement. Disabled system animations
use a static open ring without exposing a percentage. Confirmation dialogs keep
their existing action controls.
