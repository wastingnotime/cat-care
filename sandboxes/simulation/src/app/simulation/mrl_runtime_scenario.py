from __future__ import annotations

from datetime import date, datetime, timedelta, timezone

from mrl_simulation_runtime.actors import Actor
from mrl_simulation_runtime.invariants import Invariant
from mrl_simulation_runtime.scenario import InitialScheduledAction, ObservatoryEdge, ObservatoryNode, Scenario

from app.simulation.domain import CatCareState, NotificationOutcome, Responsibility


INITIAL_TIME = datetime(2026, 1, 1, 9, tzinfo=timezone.utc)


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
            Responsibility("mimi-vaccine-1", "vaccine", INITIAL_TIME + timedelta(days=3), category="preventive care"),
            Responsibility("mimi-appointment-1", "vet appointment", INITIAL_TIME + timedelta(days=5), category="veterinary"),
        ],
    )
    threshold = timedelta(days=2)

    def invoke_use_case(context: object, name: str, *, actor: str = "owner") -> None:
        context.emit("use_case_invoked", name, source="Application", actor=actor)

    def observe_status(context: object) -> None:
        invoke_use_case(context, "review_care_status")
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
            Responsibility("mimi-food-1", "buy food", INITIAL_TIME + timedelta(days=7), category="supplies"),
            context.clock.now(),
        )
        context.emit("domain_event", event.event_type, source="Responsibility", actor="owner", correlation_id=event.responsibility_id, payload={"description": event.description})

    def edit_food_responsibility(context: object) -> None:
        invoke_use_case(context, "edit_responsibility")
        event = state.edit_responsibility(
            "mimi-food-1",
            context.clock.now(),
            title="buy essential food",
            due_at=INITIAL_TIME + timedelta(days=6),
            category="supplies",
        )
        context.emit("domain_event", event.event_type, source="Responsibility", actor="owner", correlation_id=event.responsibility_id, payload={"description": event.description, "history_preserved": True, "details": dict(event.details)})

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
            payload={"description": event.description},
        )
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
            payload={"outcome": NotificationOutcome.FAILED.value, "responsibility_state": state.responsibilities[0].derived_state(context.clock.now(), threshold)},
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
            payload={"details": dict(event.details), "owner_decision": True},
        )

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
            payload={"description": event.description, "history_preserved": True},
        )

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
            payload={"description": event.description, "responsibility_id": event.responsibility_id},
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
                "responsibilities_removed": receipt.responsibilities_removed,
                "events_removed": receipt.events_removed,
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
            InitialScheduledAction(INITIAL_TIME + timedelta(days=1), observe_status, "observe_due_soon_status", "CareStatus"),
            InitialScheduledAction(INITIAL_TIME + timedelta(days=3, hours=1), observe_status, "observe_overdue_status", "CareStatus"),
            InitialScheduledAction(INITIAL_TIME + timedelta(days=3, hours=1), record_failed_notification, "record_failed_notification", "Notification", "mimi-vaccine-1"),
            InitialScheduledAction(INITIAL_TIME + timedelta(days=3, hours=1), defer_vaccine, "defer_vaccine", "Responsibility", "mimi-vaccine-1"),
            InitialScheduledAction(INITIAL_TIME + timedelta(days=3, hours=2), complete_vaccine, "complete_vaccine", "Responsibility", "mimi-vaccine-1"),
            InitialScheduledAction(INITIAL_TIME + timedelta(days=3, hours=3), record_weight_event, "record_weight_event", "CareEvent", "mimi-vaccine-1"),
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
            ObservatoryNode("manage-responsibility", "Manage responsibility", "use_case", "use_cases"),
            ObservatoryNode("manage-cat-profile", "Edit cat profile", "use_case", "use_cases"),
            ObservatoryNode("record-care", "Record care history", "use_case", "use_cases"),
            ObservatoryNode("deliver-notification", "Record notification", "use_case", "use_cases"),
            ObservatoryNode("manage-data", "Manage owner data", "use_case", "use_cases"),
            ObservatoryNode("responsibility", "Responsibility", "aggregate", "domain"),
            ObservatoryNode("status", "Calm status", "projection", "domain"),
        ],
        observatory_edges=[
            ObservatoryEdge("owner", "review-status", "invokes"),
            ObservatoryEdge("owner", "manage-responsibility", "invokes"),
            ObservatoryEdge("owner", "manage-cat-profile", "invokes"),
            ObservatoryEdge("owner", "record-care", "invokes"),
            ObservatoryEdge("owner", "manage-data", "invokes"),
            ObservatoryEdge("manage-responsibility", "responsibility", "commands"),
            ObservatoryEdge("manage-cat-profile", "responsibility", "updates"),
            ObservatoryEdge("record-care", "responsibility", "records"),
            ObservatoryEdge("deliver-notification", "responsibility", "notifies"),
            ObservatoryEdge("review-status", "status", "derives"),
            ObservatoryEdge("owner", "deliver-notification", "invokes"),
            ObservatoryEdge("responsibility", "status", "derives"),
        ],
    )
