from __future__ import annotations

from datetime import date, datetime, timedelta, timezone

from mrl_simulation_runtime.actors import Actor
from mrl_simulation_runtime.invariants import Invariant
from mrl_simulation_runtime.scenario import InitialScheduledAction, ObservatoryEdge, ObservatoryNode, Scenario

from app.simulation.domain import (
    CatCareState,
    NotificationOutcome,
    RecurrencePolicy,
    Responsibility,
    TriageReviewStatus,
    TriageUrgency,
)


INITIAL_TIME = datetime(2026, 1, 1, 9, tzinfo=timezone.utc)

USE_CASE_NODE_IDS = {
    "review_care_status": "review-status",
    "review_care_history": "review-history",
    "review_notification_history": "review-notifications",
    "review_care_events": "review-care",
    "review_notes": "review-notes",
    "review_cat_profile": "review-profile",
    "request_triage": "triage-care",
    "review_triage_queue": "review-triage-queue",
    "request_triage_information": "request-triage-information",
    "review_triage": "review-triage",
    "define_triage_follow_up": "define-triage-follow-up",
    "edit_cat_profile": "manage-cat-profile",
    "create_responsibility": "manage-responsibility",
    "edit_responsibility": "manage-responsibility",
    "complete_responsibility": "manage-responsibility",
    "record_notification": "deliver-notification",
    "defer_responsibility": "manage-responsibility",
    "cancel_responsibility": "manage-responsibility",
    "record_care_event": "record-care",
    "record_note": "record-care",
    "export_data": "manage-data",
    "delete_data": "manage-data",
}

USE_CASE_COMMAND_TARGETS = {
    "edit_cat_profile": "cat-profile",
    "create_responsibility": "responsibility",
    "edit_responsibility": "responsibility",
    "complete_responsibility": "responsibility",
    "record_notification": "notification",
    "defer_responsibility": "responsibility",
    "cancel_responsibility": "responsibility",
    "record_care_event": "care-event",
    "record_note": "note",
    "request_triage_information": "triage-assessment",
    "define_triage_follow_up": "responsibility",
    "export_data": "data-lifecycle",
    "delete_data": "data-lifecycle",
}

USE_CASE_ADAPTER_TARGETS = {
    "review_cat_profile": "profile-adapter",
    "edit_cat_profile": "profile-adapter",
    "review_notification_history": "notification-adapter",
    "record_notification": "notification-adapter",
    "request_triage": "triage-adapter",
    "review_triage": "triage-adapter",
    "create_responsibility": "recurrence-adapter",
    "complete_responsibility": "recurrence-adapter",
}


class OwnerBehavior:
    def on_start(self, context: object) -> None:
        context.emit("actor_intention", "review_cat_status", actor="owner", source="Owner")


class VeterinarianBehavior:
    def on_start(self, context: object) -> None:
        context.emit("actor_intention", "review_triage", actor="veterinarian", source="Veterinarian")


def create_simulation() -> Scenario:
    state = CatCareState(
        cat_name="Mimi",
        birth_date=date(2021, 5, 1),
        adoption_date=date(2021, 7, 10),
        photo_ref="mimi-profile.jpg",
        responsibilities=[
            Responsibility(
                "mimi-vaccine-1",
                "vaccine",
                INITIAL_TIME + timedelta(days=3),
                category="preventive care",
                recurrence=RecurrencePolicy(30),
                action_key="mimi-vaccine-2026",
            ),
            Responsibility(
                "mimi-appointment-1",
                "vet appointment",
                INITIAL_TIME + timedelta(days=5),
                category="veterinary",
                action_key="mimi-appointment-2026",
            ),
        ],
    )
    threshold = timedelta(days=2)
    last_status_kind: str | None = None
    last_nearest_responsibility_id: str | None = None

    def invoke_use_case(context: object, name: str, *, actor: str = "owner") -> str:
        correlation_id = f"use-case:{name}"
        context.emit(
            "use_case_invoked",
            name,
            source="Application",
            actor=actor,
            correlation_id=correlation_id,
        )
        context.emit(
            "command",
            USE_CASE_NODE_IDS[name],
            source=actor,
            actor=actor,
            correlation_id=correlation_id,
            payload={"use_case": name},
        )
        target = USE_CASE_COMMAND_TARGETS.get(name)
        if target is not None:
            context.emit(
                "command",
                target,
                source=USE_CASE_NODE_IDS[name],
                actor=actor,
                correlation_id=correlation_id,
                payload={"use_case": name},
            )
        adapter_target = USE_CASE_ADAPTER_TARGETS.get(name)
        if adapter_target is not None:
            context.emit(
                "command",
                adapter_target,
                source=USE_CASE_NODE_IDS[name],
                actor=actor,
                correlation_id=correlation_id,
                payload={"use_case": name, "boundary": "integration_adapter"},
            )
        return correlation_id

    def emit_status_projection_event(context: object, event: object) -> None:
        context.emit(
            "event",
            "status",
            source="responsibility",
            actor="owner",
            correlation_id=event.responsibility_id,
            payload={
                "domain_event": event.event_type,
                "responsibility_id": event.responsibility_id,
                "description": event.description,
            },
        )

    def observe_status(context: object) -> None:
        nonlocal last_nearest_responsibility_id, last_status_kind
        correlation_id = invoke_use_case(context, "review_care_status")
        snapshot = state.status_snapshot(context.clock.now(), threshold)
        context.emit(
            "semantic_observation",
            "care_status_derived",
            source="CareStatus",
            actor="owner",
            payload={
                "cat": state.cat_name,
                "status": snapshot.sentence,
                "kind": snapshot.kind,
                "nearest_responsibility_id": snapshot.nearest_responsibility_id,
            },
        )
        context.emit(
            "query",
            "status",
            source="review-status",
            actor="owner",
            correlation_id=correlation_id,
            payload={
                "kind": snapshot.kind,
                "sentence": snapshot.sentence,
                "nearest_responsibility_id": snapshot.nearest_responsibility_id,
            },
        )
        if last_status_kind is not None and last_status_kind != snapshot.kind:
            context.emit(
                "semantic_observation",
                "care_status_transition",
                source="CareStatus",
                actor="owner",
                payload={
                    "from": last_status_kind,
                    "to": snapshot.kind,
                    "previous_nearest_responsibility_id": last_nearest_responsibility_id,
                    "nearest_responsibility_id": snapshot.nearest_responsibility_id,
                    "sentence": snapshot.sentence,
                },
            )
        last_status_kind = snapshot.kind
        last_nearest_responsibility_id = snapshot.nearest_responsibility_id

    def review_history(context: object) -> None:
        correlation_id = invoke_use_case(context, "review_care_history")
        timeline = state.timeline()
        newest = timeline[0] if timeline else None
        context.emit(
            "query",
            "timeline",
            source="review-history",
            actor="owner",
            correlation_id=correlation_id,
            payload={
                "event_count": len(timeline),
                "newest_event_type": newest.event_type if newest else None,
                "newest_event_at": newest.occurred_at.isoformat() if newest else None,
            },
        )

    def review_notification_history(context: object) -> None:
        correlation_id = invoke_use_case(context, "review_notification_history")
        notifications = sorted(
            state.notifications,
            key=lambda item: item.attempted_at,
            reverse=True,
        )
        newest = notifications[0] if notifications else None
        context.emit(
            "query",
            "notification",
            source="review-notifications",
            actor="owner",
            correlation_id=correlation_id,
            payload={
                "notification_count": len(notifications),
                "outcomes": [item.outcome.value for item in notifications],
                "newest_outcome": newest.outcome.value if newest else None,
                "newest_attempted_at": newest.attempted_at.isoformat() if newest else None,
            },
        )

    def review_care_events(context: object) -> None:
        correlation_id = invoke_use_case(context, "review_care_events")
        care_events = sorted(
            state.direct_care,
            key=lambda item: item.occurred_at,
            reverse=True,
        )
        newest = care_events[0] if care_events else None
        context.emit(
            "query",
            "care-event",
            source="review-care",
            actor="owner",
            correlation_id=correlation_id,
            payload={
                "care_event_count": len(care_events),
                "responsibility_ids": [item.responsibility_id for item in care_events],
                "action_keys": [item.action_key for item in care_events],
                "newest_event_type": newest.event_type if newest else None,
                "newest_description": newest.description if newest else None,
                "newest_occurred_at": newest.occurred_at.isoformat() if newest else None,
            },
        )

    def review_notes(context: object) -> None:
        correlation_id = invoke_use_case(context, "review_notes")
        notes = sorted(state.notes, key=lambda item: item.occurred_at, reverse=True)
        newest = notes[0] if notes else None
        context.emit(
            "query",
            "note",
            source="review-notes",
            actor="owner",
            correlation_id=correlation_id,
            payload={
                "note_count": len(notes),
                "newest_description": newest.description if newest else None,
                "newest_occurred_at": newest.occurred_at.isoformat() if newest else None,
                "is_diagnosis": False,
            },
        )

    def review_cat_profile(context: object) -> None:
        correlation_id = invoke_use_case(context, "review_cat_profile")
        context.emit(
            "query",
            "cat-profile",
            source="review-profile",
            actor="owner",
            correlation_id=correlation_id,
            payload={
                "name": state.cat_name,
                "birth_date": state.birth_date.isoformat() if state.birth_date else None,
                "adoption_date": state.adoption_date.isoformat() if state.adoption_date else None,
                "photo_ref": state.photo_ref,
            },
        )

    def request_triage(context: object) -> None:
        invoke_use_case(context, "request_triage", actor="system")
        assessment = state.request_triage(
            ["note-1"],
            TriageUrgency.NEEDS_ATTENTION,
            "Reduced appetite needs prompt attention.",
            "No examination or vital signs are available.",
            context.clock.now(),
            "triage-service",
            "model-2026-01",
            current_time=context.clock.now(),
        )
        context.emit(
            "domain_event",
            "triage_assessed",
            source="TriageAssessment",
            actor="system",
            correlation_id=assessment.id,
            payload={
                "assessment_id": assessment.id,
                "urgency": assessment.urgency.value,
                "review_status": assessment.review_status.value,
                "provider": assessment.provider,
                "model_version": assessment.model_version,
                "rationale": assessment.rationale,
                "uncertainty": assessment.uncertainty,
            },
        )

    def review_triage(context: object) -> None:
        invoke_use_case(context, "review_triage", actor="veterinarian")
        review = state.review_triage(
            "triage-1",
            context.clock.now(),
            "vet-123",
            TriageReviewStatus.MODIFIED,
            TriageUrgency.URGENT,
            "Escalate after reviewing the history.",
            current_time=context.clock.now(),
        )
        context.emit(
            "domain_event",
            "triage_reviewed",
            source="TriageAssessment",
            actor="veterinarian",
            correlation_id=review.assessment_id,
            payload={
                "assessment_id": review.assessment_id,
                "decision": review.decision.value,
                "final_urgency": review.final_urgency.value,
                "veterinarian_id": review.veterinarian_id,
                "rationale": review.rationale,
            },
        )

    def review_triage_queue(context: object) -> None:
        correlation_id = invoke_use_case(
            context,
            "review_triage_queue",
            actor="veterinarian",
        )
        queue = state.pending_triage_assessments()
        context.emit(
            "query",
            "triage-assessment",
            source="review-triage-queue",
            actor="veterinarian",
            correlation_id=correlation_id,
            payload={
                "pending_count": len(queue),
                "assessment_ids": [assessment.id for assessment in queue],
                "urgencies": [assessment.urgency.value for assessment in queue],
            },
        )

    def request_more_triage_information(context: object) -> None:
        correlation_id = invoke_use_case(
            context,
            "request_triage_information",
            actor="veterinarian",
        )
        request = state.request_triage_information(
            "triage-1",
            context.clock.now(),
            "vet-123",
            "Has Mimi eaten or drunk anything since this observation?",
            current_time=context.clock.now(),
        )
        context.emit(
            "domain_event",
            "triage_information_requested",
            source="TriageAssessment",
            actor="veterinarian",
            correlation_id=correlation_id,
            payload={
                "request_id": request.id,
                "assessment_id": request.assessment_id,
                "veterinarian_id": request.veterinarian_id,
                "question": request.question,
            },
        )

    def define_triage_follow_up(context: object) -> None:
        correlation_id = invoke_use_case(
            context,
            "define_triage_follow_up",
            actor="veterinarian",
        )
        responsibility = state.define_triage_follow_up(
            "triage-1",
            "mimi-triage-follow-up-1",
            "urgent veterinary consultation",
            context.clock.now() + timedelta(hours=2),
            context.clock.now(),
            "vet-123",
            current_time=context.clock.now(),
        )
        context.emit(
            "domain_event",
            "triage_follow_up_defined",
            source="Responsibility",
            actor="veterinarian",
            correlation_id=correlation_id,
            payload={
                "assessment_id": "triage-1",
                "responsibility_id": responsibility.id,
                "due_at": responsibility.due_at.isoformat(),
                "action_key": responsibility.action_key,
            },
        )
    def edit_profile(context: object) -> None:
        invoke_use_case(context, "edit_cat_profile")
        event = state.edit_cat_profile(
            context.clock.now(),
            name="Mimi",
            birth_date=date(2021, 5, 1),
            adoption_date=date(2021, 7, 10),
            photo_ref="mimi-profile-updated.jpg",
            current_time=context.clock.now(),
        )
        context.emit("domain_event", event.event_type, source="CatProfile", actor="owner", payload={"details": dict(event.details)})

    def add_food_responsibility(context: object) -> None:
        invoke_use_case(context, "create_responsibility")
        event = state.add_responsibility(
            Responsibility(
                "mimi-food-1",
                "buy food",
                INITIAL_TIME + timedelta(days=7),
                category="supplies",
                action_key="mimi-food-2026",
            ),
            context.clock.now(),
        )
        context.emit(
            "domain_event",
            event.event_type,
            source="Responsibility",
            actor="owner",
            correlation_id=event.responsibility_id,
            payload={"description": event.description, "action_key": event.action_key},
        )

    def edit_food_responsibility(context: object) -> None:
        invoke_use_case(context, "edit_responsibility")
        event = state.edit_responsibility(
            "mimi-food-1",
            context.clock.now(),
            title="buy essential food",
            due_at=INITIAL_TIME + timedelta(days=6),
            category="supplies",
        )
        context.emit(
            "domain_event",
            event.event_type,
            source="Responsibility",
            actor="owner",
            correlation_id=event.responsibility_id,
            payload={
                "description": event.description,
                "history_preserved": True,
                "details": dict(event.details),
                "action_key": event.action_key,
            },
        )

    def complete_vaccine(context: object) -> None:
        invoke_use_case(context, "complete_responsibility")
        event = state.complete(
            "mimi-vaccine-1",
            context.clock.now(),
            current_time=context.clock.now(),
        )
        context.emit(
            "domain_event",
            event.event_type,
            source="Responsibility",
            actor="owner",
            correlation_id=event.responsibility_id,
            payload={
                "description": event.description,
                "details": dict(event.details),
                "action_key": event.action_key,
            },
        )
        emit_status_projection_event(context, event)
        observe_status(context)

    def record_failed_notification(context: object) -> None:
        invoke_use_case(context, "record_notification", actor="system")
        event = state.record_notification(
            "mimi-vaccine-1",
            context.clock.now(),
            NotificationOutcome.FAILED,
            current_time=context.clock.now(),
        )
        context.emit(
            "domain_event",
            event.event_type,
            source="Notification",
            actor="system",
            correlation_id=event.responsibility_id,
            payload={
                "outcome": NotificationOutcome.FAILED.value,
                "responsibility_state": state.responsibilities[0].derived_state(context.clock.now(), threshold),
                "action_key": event.action_key,
            },
        )

    def record_delivered_notification(context: object) -> None:
        invoke_use_case(context, "record_notification", actor="system")
        event = state.record_notification(
            "mimi-appointment-1",
            context.clock.now(),
            NotificationOutcome.DELIVERED,
            current_time=context.clock.now(),
        )
        context.emit(
            "domain_event",
            event.event_type,
            source="Notification",
            actor="system",
            correlation_id=event.responsibility_id,
            payload={
                "outcome": NotificationOutcome.DELIVERED.value,
                "responsibility_state": next(
                    item for item in state.responsibilities if item.id == "mimi-appointment-1"
                ).derived_state(context.clock.now(), threshold),
                "action_key": event.action_key,
            },
        )

    def defer_vaccine(context: object) -> None:
        invoke_use_case(context, "defer_responsibility")
        event = state.defer_responsibility(
            "mimi-vaccine-1",
            context.clock.now(),
            INITIAL_TIME + timedelta(days=10),
            current_time=context.clock.now(),
        )
        context.emit(
            "domain_event",
            event.event_type,
            source="Responsibility",
            actor="owner",
            correlation_id=event.responsibility_id,
            payload={
                "details": dict(event.details),
                "owner_decision": True,
                "action_key": event.action_key,
            },
        )
        emit_status_projection_event(context, event)

    def cancel_appointment(context: object) -> None:
        invoke_use_case(context, "cancel_responsibility")
        event = state.cancel(
            "mimi-appointment-1",
            context.clock.now(),
            current_time=context.clock.now(),
        )
        context.emit(
            "domain_event",
            event.event_type,
            source="Responsibility",
            actor="owner",
            correlation_id=event.responsibility_id,
            payload={
                "description": event.description,
                "history_preserved": True,
                "action_key": event.action_key,
            },
        )
        emit_status_projection_event(context, event)

    def record_weight_event(context: object) -> None:
        invoke_use_case(context, "record_care_event")
        event = state.record_care_event(
            "weight_measured",
            "4.2 kg",
            context.clock.now(),
            current_time=context.clock.now(),
            responsibility_id="mimi-vaccine-1",
        )
        context.emit(
            "domain_event",
            event.event_type,
            source="CareEvent",
            actor="owner",
            correlation_id=event.responsibility_id,
            payload={
                "description": event.description,
                "responsibility_id": event.responsibility_id,
                "action_key": event.action_key,
            },
        )

    def attempt_duplicate_completion(context: object) -> None:
        correlation_id = invoke_use_case(context, "complete_responsibility")
        action_key = next(
            item.action_key
            for item in state.responsibilities
            if item.id == "mimi-vaccine-1"
        )
        try:
            state.complete(
                "mimi-vaccine-1",
                context.clock.now(),
                current_time=context.clock.now(),
            )
        except ValueError as error:
            context.emit(
                "command_rejected",
                "complete_responsibility",
                source="responsibility",
                actor="owner",
                correlation_id="mimi-vaccine-1",
                payload={
                    "reason": str(error),
                    "action_key": action_key,
                    "attempted_at": context.clock.now().isoformat(),
                    "responsibility_state": state.responsibilities[0].state.value,
                },
            )
            context.emit(
                "command_rejected",
                "responsibility",
                source="manage-responsibility",
                actor="owner",
                correlation_id=correlation_id,
                payload={
                    "operation": "complete_responsibility",
                    "reason": str(error),
                    "action_key": action_key,
                },
            )
    def record_note(context: object) -> None:
        invoke_use_case(context, "record_note")
        event = state.record_note(
            "eating less",
            context.clock.now(),
            current_time=context.clock.now(),
        )
        context.emit(
            "domain_event",
            event.event_type,
            source="Note",
            actor="owner",
            payload={"description": event.description, "is_diagnosis": False},
        )
        context.emit(
            "semantic_observation",
            "timeline_ordered",
            source="Timeline",
            actor="owner",
            payload={"event_types": [item.event_type for item in state.timeline()]},
        )

    def export_data(context: object) -> None:
        invoke_use_case(context, "export_data")
        exported = state.export_data()
        context.emit(
            "semantic_observation",
            "data_exported",
            source="DataExport",
            actor="owner",
            payload={
                "cat_name": exported["cat"]["name"],
                "responsibility_count": len(exported["responsibilities"]),
                "event_count": len(exported["events"]),
            },
        )

    def delete_data(context: object) -> None:
        invoke_use_case(context, "delete_data")
        receipt = state.delete_cat(context.clock.now(), current_time=context.clock.now())
        context.emit(
            "semantic_observation",
            "data_deleted",
            source="DataLifecycle",
            actor="owner",
            payload={
                "deleted_at": receipt.deleted_at.isoformat(),
                "responsibilities_removed": receipt.responsibilities_removed,
                "events_removed": receipt.events_removed,
                "notifications_removed": receipt.notifications_removed,
                "notes_removed": receipt.notes_removed,
                "direct_care_removed": receipt.direct_care_removed,
                "triage_assessments_removed": receipt.triage_assessments_removed,
                "veterinarian_reviews_removed": receipt.veterinarian_reviews_removed,
                "triage_information_requests_removed": receipt.triage_information_requests_removed,
                "orphaned_records": 0,
            },
        )

    def no_overdue_completed_responsibility(context: object) -> bool:
        return all(
            item.state.value != "completed" or item.derived_state(context.clock.now(), threshold) != "overdue"
            for item in state.responsibilities
        )

    return Scenario(
        name="cat-care-responsibility-status",
        seed=7,
        initial_time=INITIAL_TIME,
        run_id="cat-care-first-slice",
        actors=[
            Actor("owner", OwnerBehavior()),
            Actor("veterinarian", VeterinarianBehavior()),
        ],
        scheduled_actions=[
            InitialScheduledAction(INITIAL_TIME, observe_status, "observe_initial_status", "CareStatus"),
            InitialScheduledAction(INITIAL_TIME + timedelta(hours=2), add_food_responsibility, "add_food_responsibility", "Responsibility"),
            InitialScheduledAction(INITIAL_TIME + timedelta(hours=4), edit_food_responsibility, "edit_food_responsibility", "Responsibility", "mimi-food-1"),
            InitialScheduledAction(INITIAL_TIME + timedelta(hours=5), edit_profile, "edit_profile", "CatProfile"),
            InitialScheduledAction(INITIAL_TIME + timedelta(hours=6), review_cat_profile, "review_cat_profile", "CatProfile"),
            InitialScheduledAction(INITIAL_TIME + timedelta(hours=6), review_history, "review_history", "Timeline"),
            InitialScheduledAction(INITIAL_TIME + timedelta(days=1), observe_status, "observe_due_soon_status", "CareStatus"),
            InitialScheduledAction(INITIAL_TIME + timedelta(days=3, hours=1), observe_status, "observe_overdue_status", "CareStatus"),
            InitialScheduledAction(INITIAL_TIME + timedelta(days=3, hours=1), record_failed_notification, "record_failed_notification", "Notification", "mimi-vaccine-1"),
            InitialScheduledAction(INITIAL_TIME + timedelta(days=4, hours=1), record_delivered_notification, "record_delivered_notification", "Notification", "mimi-appointment-1"),
            InitialScheduledAction(INITIAL_TIME + timedelta(days=4, hours=2), review_notification_history, "review_notification_history", "Notification"),
            InitialScheduledAction(INITIAL_TIME + timedelta(days=4), review_care_events, "review_care_events", "CareEvent"),
            InitialScheduledAction(INITIAL_TIME + timedelta(days=4, hours=2), review_notes, "review_notes", "Note"),
            InitialScheduledAction(INITIAL_TIME + timedelta(days=4, hours=1, minutes=30), request_triage, "request_triage", "TriageAssessment"),
            InitialScheduledAction(INITIAL_TIME + timedelta(days=4, hours=1, minutes=40), review_triage_queue, "review_triage_queue", "TriageAssessment"),
            InitialScheduledAction(INITIAL_TIME + timedelta(days=4, hours=1, minutes=50), request_more_triage_information, "request_triage_information", "TriageAssessment"),
            InitialScheduledAction(INITIAL_TIME + timedelta(days=4, hours=2, minutes=30), review_triage, "review_triage", "TriageAssessment"),
            InitialScheduledAction(INITIAL_TIME + timedelta(days=4, hours=2, minutes=45), define_triage_follow_up, "define_triage_follow_up", "Responsibility", "mimi-triage-follow-up-1"),
            InitialScheduledAction(INITIAL_TIME + timedelta(days=3, hours=1), defer_vaccine, "defer_vaccine", "Responsibility", "mimi-vaccine-1"),
            InitialScheduledAction(INITIAL_TIME + timedelta(days=3, hours=2), complete_vaccine, "complete_vaccine", "Responsibility", "mimi-vaccine-1"),
            InitialScheduledAction(INITIAL_TIME + timedelta(days=3, hours=3), record_weight_event, "record_weight_event", "CareEvent", "mimi-vaccine-1"),
            InitialScheduledAction(INITIAL_TIME + timedelta(days=3, hours=4), attempt_duplicate_completion, "attempt_duplicate_completion", "Responsibility", "mimi-vaccine-1"),
            InitialScheduledAction(INITIAL_TIME + timedelta(days=4), cancel_appointment, "cancel_appointment", "Responsibility", "mimi-appointment-1"),
            InitialScheduledAction(INITIAL_TIME + timedelta(days=4, hours=1), record_note, "record_note", "Note"),
            InitialScheduledAction(INITIAL_TIME + timedelta(days=4, hours=2), export_data, "export_data", "DataExport"),
            InitialScheduledAction(INITIAL_TIME + timedelta(days=4, hours=3), delete_data, "delete_data", "DataLifecycle"),
        ],
        invariants=[
            Invariant("completed_responsibility_is_not_overdue", no_overdue_completed_responsibility),
            Invariant("timeline_is_newest_first", lambda context: state.timeline() == sorted(state.events, key=lambda event: event.occurred_at, reverse=True)),
        ],
        observatory_nodes=[
            ObservatoryNode("owner", "Owner", "actor", "domain"),
            ObservatoryNode("veterinarian", "Veterinarian", "actor", "domain"),
            ObservatoryNode("review-status", "Review care status", "use_case", "use_cases"),
            ObservatoryNode("review-history", "Review care history", "use_case", "use_cases"),
            ObservatoryNode("review-notifications", "Review notification history", "use_case", "use_cases"),
            ObservatoryNode("review-care", "Review care events", "use_case", "use_cases"),
            ObservatoryNode("review-notes", "Review notes", "use_case", "use_cases"),
            ObservatoryNode("review-profile", "Review cat profile", "use_case", "use_cases"),
            ObservatoryNode("triage-care", "Request care triage", "use_case", "use_cases"),
            ObservatoryNode("review-triage-queue", "Review triage queue", "use_case", "use_cases"),
            ObservatoryNode("request-triage-information", "Request more information", "use_case", "use_cases"),
            ObservatoryNode("review-triage", "Review care triage", "use_case", "use_cases"),
            ObservatoryNode("define-triage-follow-up", "Define triage follow-up", "use_case", "use_cases"),
            ObservatoryNode("manage-responsibility", "Manage responsibility", "use_case", "use_cases"),
            ObservatoryNode("manage-cat-profile", "Edit cat profile", "use_case", "use_cases"),
            ObservatoryNode("record-care", "Record care history", "use_case", "use_cases"),
            ObservatoryNode("deliver-notification", "Record notification", "use_case", "use_cases"),
            ObservatoryNode("manage-data", "Manage owner data", "use_case", "use_cases"),
            ObservatoryNode("profile-adapter", "Profile gateway", "outbound_adapter", "external_providers"),
            ObservatoryNode("notification-adapter", "Notification gateway", "outbound_adapter", "external_providers"),
            ObservatoryNode("triage-adapter", "Triage service", "outbound_adapter", "external_providers"),
            ObservatoryNode("recurrence-adapter", "Recurrence resolver", "outbound_adapter", "external_providers"),
            ObservatoryNode("cat-profile", "Cat profile", "aggregate", "domain"),
            ObservatoryNode("triage-assessment", "Triage assessment", "entity", "domain"),
            ObservatoryNode("notification", "Notification", "aggregate", "domain"),
            ObservatoryNode("data-lifecycle", "Data lifecycle", "aggregate", "domain"),
            ObservatoryNode("care-event", "Care event", "entity", "domain"),
            ObservatoryNode("note", "Note", "entity", "domain"),
            ObservatoryNode("responsibility", "Responsibility", "aggregate", "domain"),
            ObservatoryNode("status", "Calm status", "projection", "projections"),
            ObservatoryNode("timeline", "Timeline", "projection", "projections"),
        ],
        observatory_edges=[
            ObservatoryEdge("owner", "review-status", "invokes"),
            ObservatoryEdge("owner", "review-history", "invokes"),
            ObservatoryEdge("owner", "review-notifications", "invokes"),
            ObservatoryEdge("owner", "review-care", "invokes"),
            ObservatoryEdge("owner", "review-notes", "invokes"),
            ObservatoryEdge("owner", "review-profile", "invokes"),
            ObservatoryEdge("owner", "triage-care", "invokes"),
            ObservatoryEdge("veterinarian", "review-triage-queue", "reviews"),
            ObservatoryEdge("veterinarian", "request-triage-information", "requests"),
            ObservatoryEdge("veterinarian", "review-triage", "reviews"),
            ObservatoryEdge("veterinarian", "define-triage-follow-up", "defines"),
            ObservatoryEdge("triage-care", "triage-assessment", "creates"),
            ObservatoryEdge("review-triage-queue", "triage-assessment", "queries"),
            ObservatoryEdge("request-triage-information", "triage-assessment", "updates"),
            ObservatoryEdge("review-triage", "triage-assessment", "reviews"),
            ObservatoryEdge("define-triage-follow-up", "responsibility", "creates"),
            ObservatoryEdge("owner", "manage-responsibility", "invokes"),
            ObservatoryEdge("owner", "manage-cat-profile", "invokes"),
            ObservatoryEdge("owner", "record-care", "invokes"),
            ObservatoryEdge("owner", "manage-data", "invokes"),
            ObservatoryEdge("manage-responsibility", "responsibility", "commands"),
            ObservatoryEdge("manage-cat-profile", "cat-profile", "updates"),
            ObservatoryEdge("record-care", "responsibility", "records"),
            ObservatoryEdge("deliver-notification", "notification", "records"),
            ObservatoryEdge("manage-data", "data-lifecycle", "manages"),
            ObservatoryEdge("profile-adapter", "cat-profile", "translates-to"),
            ObservatoryEdge("notification-adapter", "notification", "translates-to"),
            ObservatoryEdge("triage-adapter", "triage-assessment", "translates-to"),
            ObservatoryEdge("recurrence-adapter", "responsibility", "translates-to"),
            ObservatoryEdge("record-care", "care-event", "records"),
            ObservatoryEdge("record-care", "note", "records"),
            ObservatoryEdge("owner", "deliver-notification", "invokes"),
            ObservatoryEdge("responsibility", "status", "derives"),
        ],
    )
