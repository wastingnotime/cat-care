from __future__ import annotations

from datetime import datetime, timedelta, timezone

from mrl_simulation_runtime.actors import Actor
from mrl_simulation_runtime.invariants import Invariant
from mrl_simulation_runtime.scenario import InitialScheduledAction, ObservatoryEdge, ObservatoryNode, Scenario

from app.simulation.domain import CatCareState, Responsibility


INITIAL_TIME = datetime(2026, 1, 1, 9, tzinfo=timezone.utc)


class OwnerBehavior:
    def on_start(self, context: object) -> None:
        context.emit("actor_intention", "review_cat_status", actor="owner", source="Owner")


def create_simulation() -> Scenario:
    state = CatCareState(
        cat_name="Mimi",
        responsibilities=[
            Responsibility("mimi-vaccine-1", "vaccine", INITIAL_TIME + timedelta(days=3)),
            Responsibility("mimi-appointment-1", "vet appointment", INITIAL_TIME + timedelta(days=5)),
        ],
    )
    threshold = timedelta(days=2)

    def observe_status(context: object) -> None:
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

    def add_food_responsibility(context: object) -> None:
        event = state.add_responsibility(
            Responsibility("mimi-food-1", "buy food", INITIAL_TIME + timedelta(days=7)),
            context.clock.now(),
        )
        context.emit("domain_event", event.event_type, source="Responsibility", actor="owner", correlation_id=event.responsibility_id, payload={"description": event.description})

    def edit_food_responsibility(context: object) -> None:
        event = state.edit_responsibility(
            "mimi-food-1",
            context.clock.now(),
            title="buy essential food",
            due_at=INITIAL_TIME + timedelta(days=6),
        )
        context.emit("domain_event", event.event_type, source="Responsibility", actor="owner", correlation_id=event.responsibility_id, payload={"description": event.description, "history_preserved": True, "details": dict(event.details)})

    def complete_vaccine(context: object) -> None:
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

    def cancel_appointment(context: object) -> None:
        event = state.cancel("mimi-appointment-1", context.clock.now())
        context.emit(
            "domain_event",
            event.event_type,
            source="Responsibility",
            actor="owner",
            correlation_id=event.responsibility_id,
            payload={"description": event.description, "history_preserved": True},
        )

    def record_weight_event(context: object) -> None:
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
            InitialScheduledAction(INITIAL_TIME + timedelta(days=1), observe_status, "observe_due_soon_status", "CareStatus"),
            InitialScheduledAction(INITIAL_TIME + timedelta(days=3, hours=1), observe_status, "observe_overdue_status", "CareStatus"),
            InitialScheduledAction(INITIAL_TIME + timedelta(days=3, hours=2), complete_vaccine, "complete_vaccine", "Responsibility", "mimi-vaccine-1"),
            InitialScheduledAction(INITIAL_TIME + timedelta(days=3, hours=3), record_weight_event, "record_weight_event", "CareEvent", "mimi-vaccine-1"),
            InitialScheduledAction(INITIAL_TIME + timedelta(days=4), cancel_appointment, "cancel_appointment", "Responsibility", "mimi-appointment-1"),
            InitialScheduledAction(INITIAL_TIME + timedelta(days=4, hours=1), record_note, "record_note", "Note"),
        ],
        invariants=[
            Invariant("completed_responsibility_is_not_overdue", no_overdue_completed_responsibility),
            Invariant("timeline_is_newest_first", lambda context: state.timeline() == sorted(state.events, key=lambda event: event.occurred_at, reverse=True)),
        ],
        observatory_nodes=[
            ObservatoryNode("owner", "Owner", "actor", "domain"),
            ObservatoryNode("responsibility", "Responsibility", "aggregate", "domain"),
            ObservatoryNode("status", "Calm status", "projection", "domain"),
        ],
        observatory_edges=[
            ObservatoryEdge("owner", "responsibility", "manages"),
            ObservatoryEdge("responsibility", "status", "derives"),
        ],
    )
