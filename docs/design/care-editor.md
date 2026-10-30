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
