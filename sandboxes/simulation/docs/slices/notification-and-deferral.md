# Notification and deferral

## Contract

Record notification delivery outcomes independently from an owner's decision to
complete, cancel, or defer a responsibility.

## Rules

- Notification outcome is explicitly `delivered` or `failed`.
- Delivery never changes responsibility state.
- Deferral is an explicit owner decision that moves a planned responsibility to
  a later due time and records history.
- Provider and action-key evidence survive typed history, timeline, export, and
  runtime observations.

## Evidence and done criteria

- Domain tests cover both delivery outcomes and deferral constraints.
- Adapter tests cover serialized outcomes, gateway results, typed history, and
  action keys.
- The runtime scenario observes failed and delivered notifications plus owner
  deferral without implicit completion.

## Out of scope

Channel selection, retry policy, notification preferences, message content, and
production provider integration.
