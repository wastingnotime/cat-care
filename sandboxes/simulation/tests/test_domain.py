from datetime import datetime, timedelta, timezone

import pytest

from app.simulation.domain import CatCareState, RecurrencePolicy, Responsibility, ResponsibilityState


NOW = datetime(2026, 1, 1, 9, tzinfo=timezone.utc)
THRESHOLD = timedelta(days=2)


def test_due_soon_and_overdue_are_derived_from_time():
    responsibility = Responsibility("r1", "vaccine", NOW + timedelta(days=1))
    assert responsibility.derived_state(NOW, THRESHOLD) == "due_soon"
    assert responsibility.derived_state(NOW + timedelta(days=2), THRESHOLD) == "overdue"


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
