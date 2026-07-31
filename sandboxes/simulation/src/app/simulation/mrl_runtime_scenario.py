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
            Responsibility("mimi-vaccine-1", "vaccine", INITIAL_TIME + timedelta(days=3))
        ],
    )
    threshold = timedelta(days=2)

    def observe_status(context: object) -> None:
        status = state.status(context.clock.now(), threshold)
        context.emit(
            "semantic_observation",
            "care_status_derived",
            source="CareStatus",
            actor="owner",
            payload={"cat": state.cat_name, "status": status},
        )

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
            InitialScheduledAction(INITIAL_TIME + timedelta(days=1), observe_status, "observe_due_soon_status", "CareStatus"),
            InitialScheduledAction(INITIAL_TIME + timedelta(days=3, hours=1), observe_status, "observe_overdue_status", "CareStatus"),
            InitialScheduledAction(INITIAL_TIME + timedelta(days=3, hours=2), complete_vaccine, "complete_vaccine", "Responsibility", "mimi-vaccine-1"),
        ],
        invariants=[Invariant("completed_responsibility_is_not_overdue", no_overdue_completed_responsibility)],
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
