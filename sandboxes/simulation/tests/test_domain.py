from datetime import date, datetime, timedelta, timezone
import json

import pytest

from app.simulation.domain import CatCareState, NoteRecord, NotificationOutcome, NotificationRecord, RecurrencePolicy, Responsibility, ResponsibilityState


NOW = datetime(2026, 1, 1, 9, tzinfo=timezone.utc)
THRESHOLD = timedelta(days=2)


def test_due_soon_and_overdue_are_derived_from_time():
    responsibility = Responsibility("r1", "vaccine", NOW + timedelta(days=1), "preventive care")
    assert responsibility.derived_state(NOW, THRESHOLD) == "due_soon"
    assert responsibility.derived_state(NOW + timedelta(days=2), THRESHOLD) == "overdue"


def test_status_snapshot_exposes_stable_kind_and_nearest_responsibility():
    state = CatCareState("Mimi", [Responsibility("r1", "vaccine", NOW + timedelta(days=1), "preventive care")])
    snapshot = state.status_snapshot(NOW, THRESHOLD)
    assert snapshot.kind == "due_soon"
    assert snapshot.nearest_responsibility_id == "r1"
    assert snapshot.sentence == "Next: vaccine soon."


def test_responsibility_can_be_added_and_edited_with_history_events():
    state = CatCareState("Mimi")
    responsibility = Responsibility("r1", "buy food", NOW + timedelta(days=7), "supplies")
    created = state.add_responsibility(responsibility, NOW)
    edited = state.edit_responsibility("r1", NOW, title="buy essential food", due_at=NOW + timedelta(days=6), category="supplies")
    assert [event.event_type for event in state.events] == ["responsibility_created", "responsibility_edited"]
    assert created.responsibility_id == edited.responsibility_id == "r1"
    assert dict(edited.details) == {
        "previous_title": "buy food",
        "new_title": "buy essential food",
        "previous_due_at": (NOW + timedelta(days=7)).isoformat(),
        "new_due_at": (NOW + timedelta(days=6)).isoformat(),
        "previous_category": "supplies",
        "new_category": "supplies",
    }
    assert responsibility.title == "buy essential food"
    assert responsibility.due_at == NOW + timedelta(days=6)
    assert responsibility.category == "supplies"


def test_responsibility_category_is_required_and_exported():
    with pytest.raises(ValueError, match="category"):
        Responsibility("r1", "vaccine", NOW, category=" ")
    state = CatCareState("Mimi", [Responsibility("r1", "vaccine", NOW, category="preventive care")])
    assert state.export_data()["responsibilities"][0]["category"] == "preventive care"


def test_duplicate_responsibility_ids_and_editing_completed_items_are_rejected():
    state = CatCareState("Mimi")
    state.add_responsibility(Responsibility("r1", "vaccine", NOW, "preventive care"), NOW)
    with pytest.raises(ValueError, match="already exists"):
        state.add_responsibility(Responsibility("r1", "other", NOW, "other"), NOW)
    state.complete("r1", NOW)
    with pytest.raises(ValueError, match="not editable"):
        state.edit_responsibility("r1", NOW, title="corrected", due_at=NOW)


def test_completion_records_event_and_recurring_next_occurrence():
    state = CatCareState(
        "Mimi", [Responsibility("r1", "treatment", NOW, "treatment", recurrence=RecurrencePolicy(30))]
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
        "Mimi", [Responsibility("r1", "treatment", NOW, "treatment", recurrence=RecurrencePolicy(30))]
    )
    state.complete("r1", NOW + timedelta(days=3))
    assert state.responsibilities[1].due_at == NOW + timedelta(days=30)


def test_completed_responsibility_cannot_be_completed_twice():
    state = CatCareState("Mimi", [Responsibility("r1", "vaccine", NOW, "preventive care")])
    state.complete("r1", NOW)
    with pytest.raises(ValueError):
        state.complete("r1", NOW)


def test_same_real_world_action_cannot_complete_two_responsibilities():
    state = CatCareState(
        "Mimi",
        [
            Responsibility("r1", "vaccine record", NOW, "preventive care", action_key="vaccine-2026"),
            Responsibility("r2", "vaccine follow-up", NOW, "preventive care", action_key="vaccine-2026"),
        ],
    )
    event = state.complete("r1", NOW)
    with pytest.raises(ValueError, match="already completed"):
        state.complete("r2", NOW)
    assert event.action_key == "vaccine-2026"
    assert state.responsibilities[1].state == ResponsibilityState.PLANNED
    assert len(state.events) == 1


def test_cancelled_responsibility_is_not_urgent():
    responsibility = Responsibility("r1", "appointment", NOW - timedelta(days=1), "veterinary")
    event = responsibility.cancel(NOW)
    assert event.event_type == "responsibility_cancelled"
    assert responsibility.derived_state(NOW, THRESHOLD) == "cancelled"


def test_cancellation_cannot_be_future_dated():
    state = CatCareState("Mimi", [Responsibility("r1", "appointment", NOW, "veterinary")])
    with pytest.raises(ValueError, match="future"):
        state.cancel("r1", NOW + timedelta(days=1), current_time=NOW)


def test_cancellation_preserves_history_event():
    state = CatCareState("Mimi", [Responsibility("r1", "appointment", NOW, "veterinary")])
    state.cancel("r1", NOW)
    assert [event.event_type for event in state.events] == ["responsibility_cancelled"]


def test_unknown_future_information_does_not_claim_all_clear():
    state = CatCareState("Mimi", future_information_known=False)
    assert state.status(NOW, THRESHOLD) == "Some future care information is unknown."


def test_responsibility_without_due_date_is_explicitly_uncertain():
    state = CatCareState("Mimi", [Responsibility("r1", "future treatment", None, "treatment")])
    assert state.status(NOW, THRESHOLD) == "Some future care information is unknown."
    snapshot = state.status_snapshot(NOW, THRESHOLD)
    assert snapshot.kind == "unknown"
    assert snapshot.nearest_responsibility_id == "r1"


def test_care_event_cannot_be_future_dated():
    state = CatCareState("Mimi", [Responsibility("r1", "vaccine", NOW, "preventive care")])
    with pytest.raises(ValueError):
        state.complete("r1", NOW + timedelta(days=1), current_time=NOW)


def test_timeline_orders_events_newest_first_and_notes_remain_observations():
    state = CatCareState("Mimi", [Responsibility("r1", "vaccine", NOW, "preventive care")])
    state.complete("r1", NOW)
    note = state.record_note("eating less", NOW + timedelta(hours=1))
    assert state.timeline()[0] == note
    assert note.event_type == "note_recorded"
    assert note.responsibility_id is None


def test_care_event_can_be_logged_and_linked_to_a_responsibility():
    state = CatCareState("Mimi", [Responsibility("r1", "vaccine", NOW, "preventive care")])
    event = state.record_care_event("weight_measured", "4.2 kg", NOW, responsibility_id="r1")
    assert event in state.timeline()
    assert event.responsibility_id == "r1"


def test_care_event_requires_existing_link_and_cannot_be_future_dated():
    state = CatCareState("Mimi")
    with pytest.raises(ValueError, match="does not exist"):
        state.record_care_event("exam_performed", "exam", NOW, responsibility_id="missing")
    with pytest.raises(ValueError, match="future"):
        state.record_care_event("exam_performed", "exam", NOW + timedelta(days=1), current_time=NOW)


def test_care_event_type_and_description_cannot_be_empty():
    state = CatCareState("Mimi")
    with pytest.raises(ValueError, match="type"):
        state.record_care_event(" ", "exam", NOW)
    with pytest.raises(ValueError, match="description"):
        state.record_note(" ", NOW)


def test_failed_notification_does_not_change_owner_responsibility_state():
    state = CatCareState("Mimi", [Responsibility("r1", "vaccine", NOW, "preventive care")])
    event = state.record_notification("r1", NOW, NotificationOutcome.FAILED)
    assert event.details == (("outcome", "failed"),)
    assert state.responsibilities[0].state == ResponsibilityState.PLANNED
    assert state.notifications == [NotificationRecord("r1", NOW, NotificationOutcome.FAILED)]
    assert state.responsibilities[0].derived_state(NOW + timedelta(days=1), THRESHOLD) == "overdue"


def test_delivered_notification_also_does_not_complete_responsibility():
    state = CatCareState("Mimi", [Responsibility("r1", "vaccine", NOW, "preventive care")])
    event = state.record_notification("r1", NOW, NotificationOutcome.DELIVERED)
    assert dict(event.details) == {"outcome": "delivered"}
    assert state.responsibilities[0].state == ResponsibilityState.PLANNED


def test_notification_outcome_must_be_explicit():
    state = CatCareState("Mimi", [Responsibility("r1", "vaccine", NOW, "preventive care")])
    with pytest.raises(ValueError, match="explicit"):
        state.record_notification("r1", NOW, "failed")


def test_owner_deferral_records_decision_and_reschedules_responsibility():
    state = CatCareState("Mimi", [Responsibility("r1", "vaccine", NOW, "preventive care")])
    event = state.defer_responsibility("r1", NOW, NOW + timedelta(days=7), current_time=NOW)
    assert event.event_type == "responsibility_deferred"
    assert dict(event.details)["new_due_at"] == (NOW + timedelta(days=7)).isoformat()
    assert state.responsibilities[0].derived_state(NOW, THRESHOLD) == "planned"


def test_deferral_requires_a_future_date_and_planned_responsibility():
    state = CatCareState("Mimi", [Responsibility("r1", "vaccine", NOW, "preventive care")])
    with pytest.raises(ValueError, match="future"):
        state.defer_responsibility("r1", NOW, NOW, current_time=NOW)
    later_check = CatCareState(
        "Mimi", [Responsibility("r2", "appointment", NOW + timedelta(days=3), "veterinary")]
    )
    with pytest.raises(ValueError, match="later"):
        later_check.defer_responsibility("r2", NOW, NOW + timedelta(days=1), current_time=NOW)
    state.complete("r1", NOW)
    with pytest.raises(ValueError, match="not deferrable"):
        state.defer_responsibility("r1", NOW, NOW + timedelta(days=1), current_time=NOW)


def test_note_cannot_be_future_dated():
    state = CatCareState("Mimi")
    with pytest.raises(ValueError):
        state.record_note("vomiting", NOW + timedelta(days=1), current_time=NOW)


def test_domain_rejects_naive_timestamps():
    naive_now = datetime(2026, 1, 1, 9)
    with pytest.raises(ValueError, match="timezone-aware"):
        Responsibility("r1", "vaccine", naive_now, "preventive care")
    with pytest.raises(ValueError, match="timezone-aware"):
        CatCareState("Mimi").status(naive_now, THRESHOLD)


def test_export_contains_current_state_and_chronological_event_history():
    state = CatCareState("Mimi", [Responsibility("r1", "vaccine", NOW, "preventive care")], future_information_known=False)
    state.record_note("eating less", NOW + timedelta(hours=1))
    exported = state.export_data()
    assert exported["cat"] == {
        "name": "Mimi",
        "birth_date": None,
        "adoption_date": None,
        "photo_ref": None,
    }
    assert exported["future_information_known"] is False
    assert exported["responsibilities"][0]["id"] == "r1"
    assert exported["events"][0]["type"] == "note_recorded"
    note_time = NOW + timedelta(hours=1)
    assert exported["notes"] == [{"description": "eating less", "occurred_at": note_time.isoformat()}]
    assert state.notes == [NoteRecord("eating less", note_time)]
    assert json.loads(state.export_json()) == exported


def test_deleting_cat_removes_owned_records_and_leaves_no_orphans():
    state = CatCareState("Mimi", [Responsibility("r1", "vaccine", NOW, "preventive care")])
    state.record_note("eating less", NOW)
    receipt = state.delete_cat(NOW)
    assert receipt.responsibilities_removed == 1
    assert receipt.events_removed == 1
    assert state.export_data() == {
        "cat": {"name": None, "birth_date": None, "adoption_date": None, "photo_ref": None},
        "deleted": True,
        "future_information_known": None,
        "responsibilities": [],
        "notifications": [],
        "notes": [],
        "events": [],
    }
    with pytest.raises(ValueError, match="deleted"):
        state.record_note("after deletion", NOW)


def test_cat_profile_fields_are_optional_and_exported():
    state = CatCareState(
        "Mimi",
        birth_date=date(2021, 5, 1),
        adoption_date=date(2021, 7, 10),
        photo_ref="mimi-profile.jpg",
    )
    assert state.export_data()["cat"] == {
        "name": "Mimi",
        "birth_date": "2021-05-01",
        "adoption_date": "2021-07-10",
        "photo_ref": "mimi-profile.jpg",
    }
    with pytest.raises(ValueError, match="cat name"):
        CatCareState(" ")
    with pytest.raises(ValueError, match="before birth"):
        CatCareState("Mimi", birth_date=date(2021, 7, 10), adoption_date=date(2021, 5, 1))


def test_cat_profile_edit_is_traceable_and_validated():
    state = CatCareState("Mimi")
    event = state.edit_cat_profile(
        NOW,
        name="Mimi renamed",
        birth_date=date(2021, 5, 1),
        adoption_date=date(2021, 7, 10),
        photo_ref="mimi-profile.jpg",
        current_time=NOW,
    )
    assert event.event_type == "cat_profile_edited"
    assert dict(event.details)["previous_name"] == "Mimi"
    with pytest.raises(ValueError, match="before birth"):
        state.edit_cat_profile(
            NOW,
            name="Mimi",
            birth_date=date(2021, 7, 10),
            adoption_date=date(2021, 5, 1),
            photo_ref=None,
            current_time=NOW,
        )
    with pytest.raises(ValueError, match="future"):
        state.edit_cat_profile(
            NOW + timedelta(days=1),
            name="Future Mimi",
            birth_date=date(2021, 5, 1),
            adoption_date=date(2021, 7, 10),
            photo_ref=None,
            current_time=NOW,
        )
    assert state.cat_name == "Mimi renamed"


def test_cat_deletion_cannot_be_future_dated():
    with pytest.raises(ValueError, match="future"):
        CatCareState("Mimi").delete_cat(NOW + timedelta(days=1), current_time=NOW)
