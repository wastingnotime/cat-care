from datetime import datetime, timedelta, timezone

import pytest

from app.simulation.domain import CatCareState, RecurrencePolicy, Responsibility, ResponsibilityState


NOW = datetime(2026, 1, 1, 9, tzinfo=timezone.utc)
THRESHOLD = timedelta(days=2)


def test_due_soon_and_overdue_are_derived_from_time():
    responsibility = Responsibility("r1", "vaccine", NOW + timedelta(days=1))
    assert responsibility.derived_state(NOW, THRESHOLD) == "due_soon"
    assert responsibility.derived_state(NOW + timedelta(days=2), THRESHOLD) == "overdue"


def test_status_snapshot_exposes_stable_kind_and_nearest_responsibility():
    state = CatCareState("Mimi", [Responsibility("r1", "vaccine", NOW + timedelta(days=1))])
    snapshot = state.status_snapshot(NOW, THRESHOLD)
    assert snapshot.kind == "due_soon"
    assert snapshot.nearest_responsibility_id == "r1"
    assert snapshot.sentence == "Next: vaccine soon."


def test_responsibility_can_be_added_and_edited_with_history_events():
    state = CatCareState("Mimi")
    responsibility = Responsibility("r1", "buy food", NOW + timedelta(days=7))
    created = state.add_responsibility(responsibility, NOW)
    edited = state.edit_responsibility("r1", NOW, title="buy essential food", due_at=NOW + timedelta(days=6))
    assert [event.event_type for event in state.events] == ["responsibility_created", "responsibility_edited"]
    assert created.responsibility_id == edited.responsibility_id == "r1"
    assert responsibility.title == "buy essential food"
    assert responsibility.due_at == NOW + timedelta(days=6)


def test_duplicate_responsibility_ids_and_editing_completed_items_are_rejected():
    state = CatCareState("Mimi")
    state.add_responsibility(Responsibility("r1", "vaccine", NOW), NOW)
    with pytest.raises(ValueError, match="already exists"):
        state.add_responsibility(Responsibility("r1", "other", NOW), NOW)
    state.complete("r1", NOW)
    with pytest.raises(ValueError, match="not editable"):
        state.edit_responsibility("r1", NOW, title="corrected", due_at=NOW)


def test_completion_records_event_and_recurring_next_occurrence():
    state = CatCareState(
        "Mimi", [Responsibility("r1", "treatment", NOW, recurrence=RecurrencePolicy(30))]
    )
    event = state.complete("r1", NOW)
    assert event.event_type == "responsibility_completed"
    assert state.responsibilities[0].state == ResponsibilityState.COMPLETED
    assert state.responsibilities[1].due_at == NOW + timedelta(days=30)
    assert state.responsibilities[1].recurrence == RecurrencePolicy(30)


def test_recurrence_policy_rejects_implicit_or_invalid_intervals():
    with pytest.raises(ValueError):
        RecurrencePolicy(0)
    with pytest.raises(ValueError):
        RecurrencePolicy(-7)


def test_recurrence_is_anchored_to_the_planned_due_date_when_completed_late():
    state = CatCareState(
        "Mimi", [Responsibility("r1", "treatment", NOW, recurrence=RecurrencePolicy(30))]
    )
    state.complete("r1", NOW + timedelta(days=3))
    assert state.responsibilities[1].due_at == NOW + timedelta(days=30)


def test_completed_responsibility_cannot_be_completed_twice():
    state = CatCareState("Mimi", [Responsibility("r1", "vaccine", NOW)])
    state.complete("r1", NOW)
    with pytest.raises(ValueError):
        state.complete("r1", NOW)


def test_same_real_world_action_cannot_complete_two_responsibilities():
    state = CatCareState(
        "Mimi",
        [
            Responsibility("r1", "vaccine record", NOW, action_key="vaccine-2026"),
            Responsibility("r2", "vaccine follow-up", NOW, action_key="vaccine-2026"),
        ],
    )
    event = state.complete("r1", NOW)
    with pytest.raises(ValueError, match="already completed"):
        state.complete("r2", NOW)
    assert event.action_key == "vaccine-2026"
    assert state.responsibilities[1].state == ResponsibilityState.PLANNED
    assert len(state.events) == 1


def test_cancelled_responsibility_is_not_urgent():
    responsibility = Responsibility("r1", "appointment", NOW - timedelta(days=1))
    event = responsibility.cancel(NOW)
    assert event.event_type == "responsibility_cancelled"
    assert responsibility.derived_state(NOW, THRESHOLD) == "cancelled"


def test_cancellation_preserves_history_event():
    state = CatCareState("Mimi", [Responsibility("r1", "appointment", NOW)])
    state.cancel("r1", NOW)
    assert [event.event_type for event in state.events] == ["responsibility_cancelled"]


def test_unknown_future_information_does_not_claim_all_clear():
    state = CatCareState("Mimi", future_information_known=False)
    assert state.status(NOW, THRESHOLD) == "Some future care information is unknown."


def test_responsibility_without_due_date_is_explicitly_uncertain():
    state = CatCareState("Mimi", [Responsibility("r1", "future treatment", None)])
    assert state.status(NOW, THRESHOLD) == "Some future care information is unknown."


def test_care_event_cannot_be_future_dated():
    state = CatCareState("Mimi", [Responsibility("r1", "vaccine", NOW)])
    with pytest.raises(ValueError):
        state.complete("r1", NOW + timedelta(days=1), current_time=NOW)


def test_timeline_orders_events_newest_first_and_notes_remain_observations():
    state = CatCareState("Mimi", [Responsibility("r1", "vaccine", NOW)])
    state.complete("r1", NOW)
    note = state.record_note("eating less", NOW + timedelta(hours=1))
    assert state.timeline()[0] == note
    assert note.event_type == "note_recorded"
    assert note.responsibility_id is None


def test_note_cannot_be_future_dated():
    state = CatCareState("Mimi")
    with pytest.raises(ValueError):
        state.record_note("vomiting", NOW + timedelta(days=1), current_time=NOW)


def test_domain_rejects_naive_timestamps():
    naive_now = datetime(2026, 1, 1, 9)
    with pytest.raises(ValueError, match="timezone-aware"):
        Responsibility("r1", "vaccine", naive_now)
    with pytest.raises(ValueError, match="timezone-aware"):
        CatCareState("Mimi").status(naive_now, THRESHOLD)
