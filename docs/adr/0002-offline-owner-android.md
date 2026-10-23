# ADR 0002: Offline Android application for the owner

Status: accepted scope; initial implementation in `apps/mobile/`.
Date: 2026-10-22 (America/Sao_Paulo).

## Context

The product direction is an Android version that works offline with only the
owner actor. ADR 0001 previously required Android to use the cloud API. The
existing API/web prototype also includes veterinarian collaboration.

## Decision

Android owns a local, persistent store as its source of truth. Core workflows
must work from first launch in airplane mode, without registration, sign-in,
an API service, or a network connection. Successful writes must survive app
closure and device restart. Cloud synchronization is outside this version.

The owner manages multiple cats and their care on the device. Include:

- Cat profiles with name, birth/adoption dates and explicit cat selection.
- Today across cats, showing cat identity, due dates and scheduling uncertainty.
- Responsibility creation, editing, completion, cancellation, deferral,
  recurrence and history.
- Typed direct care records, measurements and non-diagnosis observations.
- Deliberate local export and deletion with the affected scope shown first.
- PT-BR presentation and accessible interactions.

There is no veterinarian actor, role switch, invitation, assignment, review
workspace, information-request exchange, or reviewed follow-up workflow in
Android. Veterinary appointments may still be owner-managed responsibilities;
the veterinary care category does not introduce another actor. Clinical triage
and AI diagnosis are outside scope.

Preserve the existing care rules where applicable: notification delivery does
not complete care, observations do not become diagnoses, and completing a
recurring responsibility creates exactly one successor. Android must implement
these transitions atomically in local storage. Local reminders, if included,
must work without a remote provider and describe Android permission and delivery
limitations before any delivery promise.

Export and deletion refer to local cats or all local app data, not a cloud
account. Define the export format, restore behavior, backup policy and deletion
coverage before claiming data portability or recovery. App removal/device loss
and any OS-managed backup behavior must be explained by that policy.

## Consequences

This supersedes ADR 0001's Android cloud-client requirement. Cloud identity,
server persistence, network retry and veterinarian acceptance gates do not gate
this Android release. Existing API/web behavior remains separately scoped.

The simulation and existing domain rules provide semantic references; Android
does not require the Go API to run. The initial implementation uses native Java
Android views and SQLite, with Android 8.0/API 26 as its minimum and API 35 as
its compile/target level. Java 17 language features are built using JDK 21 and a
pinned Gradle wrapper. The app declares no internet permission. Reminders,
import/restore and cloud sync are excluded from version 0.1. Implementation
details and data policies are in [the mobile contract](../../contracts/mobile.md).
The presentation implementation is subsequently migrated to Kotlin and Compose
by [ADR 0003](0003-kotlin-compose-android.md); the offline boundary is retained.
This ADR does not establish production release readiness.

Delivery scenarios are defined in the
[Android acceptance contract](../../contracts/acceptance/android-owner-offline.md).
