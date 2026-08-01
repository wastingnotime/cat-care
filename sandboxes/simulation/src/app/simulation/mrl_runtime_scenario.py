from __future__ import annotations

from datetime import date, datetime, timedelta, timezone

from mrl_simulation_runtime.actors import Actor
from mrl_simulation_runtime.invariants import Invariant
from mrl_simulation_runtime.scenario import InitialScheduledAction, ObservatoryEdge, ObservatoryNode, Scenario

from app.simulation.domain import CatCareState, NotificationOutcome, RecurrencePolicy, Responsibility


INITIAL_TIME = datetime(2026, 1, 1, 9, tzinfo=timezone.utc)

USE_CASE_NODE_IDS = {
    "review_care_status": "review-status",
    "review_care_history": "review-history",
    "review_notification_history": "review-notifications",
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
    "export_data": "data-lifecycle",
    "delete_data": "data-lifecycle",
}


class OwnerBehavior:
    def on_start(self, context: object) -> None:
        context.emit("actor_intention", "review_cat_status", actor="owner", source="Owner")


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
            source="owner",
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
        actors=[Actor("owner", OwnerBehavior())],
        scheduled_actions=[
            InitialScheduledAction(INITIAL_TIME, observe_status, "observe_initial_status", "CareStatus"),
            InitialScheduledAction(INITIAL_TIME + timedelta(hours=2), add_food_responsibility, "add_food_responsibility", "Responsibility"),
            InitialScheduledAction(INITIAL_TIME + timedelta(hours=4), edit_food_responsibility, "edit_food_responsibility", "Responsibility", "mimi-food-1"),
            InitialScheduledAction(INITIAL_TIME + timedelta(hours=5), edit_profile, "edit_profile", "CatProfile"),
            InitialScheduledAction(INITIAL_TIME + timedelta(hours=6), review_history, "review_history", "Timeline"),
            InitialScheduledAction(INITIAL_TIME + timedelta(days=1), observe_status, "observe_due_soon_status", "CareStatus"),
            InitialScheduledAction(INITIAL_TIME + timedelta(days=3, hours=1), observe_status, "observe_overdue_status", "CareStatus"),
            InitialScheduledAction(INITIAL_TIME + timedelta(days=3, hours=1), record_failed_notification, "record_failed_notification", "Notification", "mimi-vaccine-1"),
            InitialScheduledAction(INITIAL_TIME + timedelta(days=4, hours=1), record_delivered_notification, "record_delivered_notification", "Notification", "mimi-appointment-1"),
            InitialScheduledAction(INITIAL_TIME + timedelta(days=4, hours=2), review_notification_history, "review_notification_history", "Notification"),
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
            ObservatoryNode("review-status", "Review care status", "use_case", "use_cases"),
            ObservatoryNode("review-history", "Review care history", "use_case", "use_cases"),
            ObservatoryNode("review-notifications", "Review notification history", "use_case", "use_cases"),
            ObservatoryNode("manage-responsibility", "Manage responsibility", "use_case", "use_cases"),
            ObservatoryNode("manage-cat-profile", "Edit cat profile", "use_case", "use_cases"),
            ObservatoryNode("record-care", "Record care history", "use_case", "use_cases"),
            ObservatoryNode("deliver-notification", "Record notification", "use_case", "use_cases"),
            ObservatoryNode("manage-data", "Manage owner data", "use_case", "use_cases"),
            ObservatoryNode("cat-profile", "Cat profile", "aggregate", "domain"),
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
            ObservatoryEdge("owner", "manage-responsibility", "invokes"),
            ObservatoryEdge("owner", "manage-cat-profile", "invokes"),
            ObservatoryEdge("owner", "record-care", "invokes"),
            ObservatoryEdge("owner", "manage-data", "invokes"),
            ObservatoryEdge("manage-responsibility", "responsibility", "commands"),
            ObservatoryEdge("manage-cat-profile", "cat-profile", "updates"),
            ObservatoryEdge("record-care", "responsibility", "records"),
            ObservatoryEdge("deliver-notification", "notification", "records"),
            ObservatoryEdge("manage-data", "data-lifecycle", "manages"),
            ObservatoryEdge("record-care", "care-event", "records"),
            ObservatoryEdge("record-care", "note", "records"),
            ObservatoryEdge("owner", "deliver-notification", "invokes"),
            ObservatoryEdge("responsibility", "status", "derives"),
        ],
    )
