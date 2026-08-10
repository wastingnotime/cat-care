from datetime import date, datetime, timedelta, timezone
import json

import pytest

from app.simulation.domain import CareEvent, CatCareState, DeletionReceipt, DirectCareRecord, NoteRecord, NotificationOutcome, NotificationRecord, RecurrencePolicy, Responsibility, ResponsibilityState, TriageAssessment, TriageReviewStatus, TriageUrgency, VeterinarianReview


NOW = datetime(2026, 1, 1, 9, tzinfo=timezone.utc)
THRESHOLD = timedelta(days=2)


def test_due_soon_and_overdue_are_derived_from_time():
    responsibility = Responsibility("r1", "vaccine", NOW + timedelta(days=1), "preventive care")
    assert responsibility.derived_state(NOW, THRESHOLD) == "due_soon"
    assert responsibility.derived_state(NOW + timedelta(days=2), THRESHOLD) == "overdue"


def test_status_rejects_negative_due_soon_threshold():
    with pytest.raises(ValueError, match="cannot be negative"):
        CatCareState("Mimi").status_snapshot(NOW, timedelta(days=-1))
    with pytest.raises(ValueError, match="cannot be negative"):
        Responsibility("r1", "vaccine", NOW, "care").derived_state(NOW, timedelta(days=-1))


def test_status_snapshot_exposes_stable_kind_and_nearest_responsibility():
    state = CatCareState("Mimi", [Responsibility("r1", "vaccine", NOW + timedelta(days=1), "preventive care")])
    snapshot = state.status_snapshot(NOW, THRESHOLD)
    assert snapshot.kind == "due_soon"
    assert snapshot.nearest_responsibility_id == "r1"
    assert snapshot.sentence == "Next: vaccine soon."


def test_equal_due_dates_choose_responsibility_id_for_status():
    state = CatCareState(
        "Mimi",
        [
            Responsibility("z-last", "later label", NOW, "care"),
            Responsibility("a-first", "earlier label", NOW, "care"),
        ],
    )
    assert state.status_snapshot(NOW, THRESHOLD).nearest_responsibility_id == "a-first"


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


def test_responsibility_identity_and_title_are_required():
    with pytest.raises(ValueError, match="id"):
        Responsibility(" ", "vaccine", NOW, "preventive care")
    with pytest.raises(ValueError, match="title"):
        Responsibility("r1", " ", NOW, "preventive care")
    state = CatCareState("Mimi", [Responsibility("r1", "vaccine", NOW, "preventive care")])
    with pytest.raises(ValueError, match="title"):
        state.edit_responsibility("r1", NOW, title=" ", due_at=NOW)
    with pytest.raises(ValueError, match="action key"):
        Responsibility("r2", "vaccine", NOW, "care", action_key=" ")
    with pytest.raises(ValueError, match="state"):
        Responsibility("r3", "vaccine", NOW, "care", state="planned")
    with pytest.raises(ValueError, match="recurrence"):
        Responsibility("r4", "vaccine", NOW, "care", recurrence="daily")


def test_duplicate_responsibility_ids_and_editing_completed_items_are_rejected():
    state = CatCareState("Mimi")
    state.add_responsibility(Responsibility("r1", "vaccine", NOW, "preventive care"), NOW)
    with pytest.raises(ValueError, match="already exists"):
        state.add_responsibility(Responsibility("r1", "other", NOW, "other"), NOW)
    state.complete("r1", NOW)
    with pytest.raises(ValueError, match="not editable"):
        state.edit_responsibility("r1", NOW, title="corrected", due_at=NOW)


def test_unknown_responsibility_commands_have_domain_errors():
    state = CatCareState("Mimi")
    with pytest.raises(ValueError, match="responsibility missing does not exist"):
        state.complete("missing", NOW)
    with pytest.raises(ValueError, match="responsibility missing does not exist"):
        state.cancel("missing", NOW)
    with pytest.raises(ValueError, match="responsibility missing does not exist"):
        state.record_notification("missing", NOW, NotificationOutcome.DELIVERED)


def test_completion_records_event_and_recurring_next_occurrence():
    state = CatCareState(
        "Mimi", [Responsibility("r1", "treatment", NOW, "treatment", recurrence=RecurrencePolicy(30))]
    )
    event = state.complete("r1", NOW)
    assert event.event_type == "responsibility_completed"
    assert dict(event.details) == {
        "next_responsibility_id": "r1-next",
        "next_due_at": (NOW + timedelta(days=30)).isoformat(),
    }
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


def test_equal_time_events_export_in_deterministic_order():
    state = CatCareState("Mimi")
    state.record_care_event("z_event", "z", NOW)
    state.record_care_event("a_event", "a", NOW)
    assert [event.event_type for event in state.timeline()] == ["z_event", "a_event"]
    assert [event["type"] for event in state.export_data()["events"]] == ["a_event", "z_event"]


def test_export_orders_responsibilities_by_id():
    state = CatCareState("Mimi")
    state.add_responsibility(Responsibility("z-last", "later", NOW, "care"), NOW)
    state.add_responsibility(Responsibility("a-first", "earlier", NOW, "care"), NOW)
    assert [item["id"] for item in state.export_data()["responsibilities"]] == [
        "a-first", "z-last"
    ]


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


def test_care_record_identifiers_cannot_be_blank():
    with pytest.raises(ValueError, match="responsibility ID"):
        DirectCareRecord("exam", "completed", NOW, responsibility_id=" ")
    with pytest.raises(ValueError, match="action key"):
        DirectCareRecord("exam", "completed", NOW, action_key=" ")


def test_care_event_details_require_unique_non_empty_text():
    with pytest.raises(ValueError, match="detail key"):
        CareEvent("exam", NOW, "completed", details=((" ", "value"),))
    with pytest.raises(ValueError, match="detail keys"):
        CareEvent("exam", NOW, "completed", details=(("result", "ok"), ("result", "again")))


def test_failed_notification_does_not_change_owner_responsibility_state():
    state = CatCareState("Mimi", [Responsibility("r1", "vaccine", NOW, "preventive care")])
    event = state.record_notification("r1", NOW, NotificationOutcome.FAILED)
    assert event.details == (("outcome", "failed"),)
    assert state.responsibilities[0].state == ResponsibilityState.PLANNED
    assert state.notifications == [NotificationRecord("r1", NOW, NotificationOutcome.FAILED, None)]
    assert event.action_key is None
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


def test_notification_optional_provider_fields_cannot_be_blank():
    with pytest.raises(ValueError, match="provider cannot be empty"):
        NotificationRecord("r1", NOW, NotificationOutcome.DELIVERED, provider=" ")
    with pytest.raises(ValueError, match="message ID cannot be empty"):
        NotificationRecord("r1", NOW, NotificationOutcome.DELIVERED, provider_message_id=" ")
    with pytest.raises(ValueError, match="responsibility ID"):
        NotificationRecord(" ", NOW, NotificationOutcome.DELIVERED)
    with pytest.raises(ValueError, match="action key"):
        NotificationRecord("r1", NOW, NotificationOutcome.DELIVERED, action_key=" ")


def test_note_optional_id_cannot_be_blank():
    with pytest.raises(ValueError, match="note ID"):
        NoteRecord("eating less", NOW, " ")


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
    state.record_care_event("weight_measured", "4.2 kg", NOW, responsibility_id="r1")
    exported = state.export_data()
    assert exported["cat"] == {
        "name": "Mimi",
        "birth_date": None,
        "adoption_date": None,
        "photo_ref": None,
    }
    assert exported["future_information_known"] is False
    assert exported["responsibilities"][0]["id"] == "r1"
    assert [event["type"] for event in exported["events"]] == ["weight_measured", "note_recorded"]
    note_time = NOW + timedelta(hours=1)
    assert exported["notes"] == [{"id": "note-1", "description": "eating less", "occurred_at": note_time.isoformat()}]
    assert state.notes == [NoteRecord("eating less", note_time, "note-1")]
    assert state.direct_care == [DirectCareRecord("weight_measured", "4.2 kg", NOW, "r1", None)]
    assert json.loads(state.export_json()) == exported


def test_deleting_cat_removes_owned_records_and_leaves_no_orphans():
    state = CatCareState("Mimi", [Responsibility("r1", "vaccine", NOW, "preventive care")])
    state.record_note("eating less", NOW)
    receipt = state.delete_cat(NOW)
    assert receipt.responsibilities_removed == 1
    assert receipt.events_removed == 1
    assert receipt.notifications_removed == 0
    assert receipt.notes_removed == 1
    assert receipt.direct_care_removed == 0
    assert receipt.triage_assessments_removed == 0
    assert receipt.veterinarian_reviews_removed == 0
    assert receipt.triage_information_requests_removed == 0
    assert state.export_data() == {
        "cat": {"name": None, "birth_date": None, "adoption_date": None, "photo_ref": None},
        "deleted": True,
        "deleted_at": NOW.isoformat(),
        "future_information_known": None,
        "responsibilities": [],
        "notifications": [],
        "notes": [],
        "direct_care": [],
        "triage_assessments": [],
        "veterinarian_reviews": [],
        "triage_information_requests": [],
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


def test_deletion_receipt_validates_timestamp_and_counts():
    counts = [0] * 8
    with pytest.raises(ValueError, match="deletion time"):
        DeletionReceipt(datetime(2026, 1, 1, 9), *counts)
    with pytest.raises(ValueError, match="cannot be negative"):
        DeletionReceipt(NOW, -1, *counts[1:])


def test_cat_state_validates_uncertainty_and_deletion_metadata():
    with pytest.raises(ValueError, match="boolean"):
        CatCareState("Mimi", future_information_known="yes")
    with pytest.raises(ValueError, match="deletion time"):
        CatCareState("Mimi", deleted=True, deleted_at=datetime(2026, 1, 1, 9))
    with pytest.raises(ValueError, match="cat name must be text"):
        CatCareState(42)
    with pytest.raises(ValueError, match="birth date must be a date"):
        CatCareState("Mimi", birth_date=datetime(2021, 1, 1, 9))
    with pytest.raises(ValueError, match="deleted flag"):
        CatCareState("Mimi", deleted="yes")
    with pytest.raises(ValueError, match="match deleted state"):
        CatCareState("Mimi", deleted_at=NOW)


def test_ai_triage_is_provisional_until_veterinarian_review():
    state = CatCareState("Mimi")
    state.record_note("eating less", NOW)
    assessment = state.request_triage(
        ["note-1"], TriageUrgency.NEEDS_ATTENTION,
        "Reduced appetite may need prompt attention.",
        "No examination or vital signs available.", NOW,
        "triage-service", "model-2026-01", current_time=NOW,
    )
    assert assessment.review_status == TriageReviewStatus.PENDING
    assert assessment.final_urgency is None
    review = state.review_triage(
        assessment.id, NOW + timedelta(hours=1), "vet-123",
        TriageReviewStatus.MODIFIED, TriageUrgency.URGENT,
        "Escalate after reviewing the history.",
        current_time=NOW + timedelta(hours=1),
    )
    assert review.decision == TriageReviewStatus.MODIFIED
    assert assessment.review_status == TriageReviewStatus.MODIFIED
    assert assessment.final_urgency == TriageUrgency.URGENT
    assert state.export_data()["triage_assessments"][0]["final_urgency"] == "urgent"


def test_triage_review_decision_preserves_urgency_semantics():
    state = CatCareState("Mimi")
    state.record_note("sneezing", NOW)
    assessment = state.request_triage(
        ["note-1"], TriageUrgency.MONITOR, "Monitor.", "Limited history.",
        NOW, "triage-service", "model-2026-01", current_time=NOW,
    )
    with pytest.raises(ValueError, match="cannot change urgency"):
        state.review_triage(
            assessment.id, NOW, "vet-123", TriageReviewStatus.ACCEPTED,
            TriageUrgency.URGENT, "Reviewed.", current_time=NOW,
        )
    with pytest.raises(ValueError, match="cannot have final urgency"):
        state.review_triage(
            assessment.id, NOW, "vet-123", TriageReviewStatus.REJECTED,
            TriageUrgency.MONITOR, "Insufficient evidence.", current_time=NOW,
        )


def test_triage_rejects_duplicate_note_references():
    state = CatCareState("Mimi")
    state.record_note("sneezing", NOW)
    with pytest.raises(ValueError, match="repeat a note"):
        state.request_triage(
            ["note-1", "note-1"], TriageUrgency.MONITOR, "Monitor.",
            "Limited history.", NOW, "triage-service", "model-2026-01",
            current_time=NOW,
        )


def test_triage_record_constructors_validate_identity_and_types():
    with pytest.raises(ValueError, match="assessment id"):
        TriageAssessment(
            " ", ("note-1",), TriageUrgency.MONITOR, "Monitor.",
            "Limited history.", NOW, "provider", "model",
        )
    with pytest.raises(ValueError, match="note IDs"):
        TriageAssessment(
            "triage-1", ("",), TriageUrgency.MONITOR, "Monitor.",
            "Limited history.", NOW, "provider", "model",
        )
    with pytest.raises(ValueError, match="final urgency"):
        VeterinarianReview(
            "triage-1", NOW, "vet-1", TriageReviewStatus.MODIFIED,
            "urgent", "Reviewed.",
        )


def test_triage_rejects_unknown_notes_and_future_review_times():
    state = CatCareState("Mimi")
    with pytest.raises(ValueError, match="unknown note"):
        state.request_triage(
            ["note-404"], TriageUrgency.MONITOR, "Monitor.",
            "Limited history.", NOW, "triage-service", "model-2026-01",
            current_time=NOW,
        )
    state.record_note("sneezing", NOW)
    assessment = state.request_triage(
        ["note-1"], TriageUrgency.MONITOR, "Monitor.",
        "Limited history.", NOW, "triage-service", "model-2026-01",
        current_time=NOW,
    )
    with pytest.raises(ValueError, match="future"):
        state.review_triage(
            assessment.id, NOW + timedelta(hours=1), "vet-123",
            TriageReviewStatus.ACCEPTED, None, "Reviewed.", current_time=NOW,
        )


def test_calendar_recurrence_preserves_time_and_clamps_short_months():
    policy = RecurrencePolicy(calendar_months=1)
    due_at = datetime(2026, 1, 31, 9, tzinfo=timezone.utc)
    assert policy.next_due_at(due_at) == datetime(2026, 2, 28, 9, tzinfo=timezone.utc)


def test_recurrence_policy_requires_one_explicit_rule():
    with pytest.raises(ValueError, match="exactly one"):
        RecurrencePolicy()
    with pytest.raises(ValueError, match="exactly one"):
        RecurrencePolicy(30, 1)


def test_recurrence_policy_rejects_boolean_and_fractional_intervals():
    with pytest.raises(ValueError, match="integer"):
        RecurrencePolicy(True)
    with pytest.raises(ValueError, match="integer"):
        RecurrencePolicy(calendar_months=1.5)


def test_veterinarian_triage_workflow_creates_information_request_and_follow_up():
    state = CatCareState("Mimi")
    state.record_note("eating less", NOW)
    assessment = state.request_triage(
        ["note-1"], TriageUrgency.NEEDS_ATTENTION, "Needs review.",
        "No examination.", NOW, "triage-service", "model-1", current_time=NOW,
    )
    assert [item.id for item in state.pending_triage_assessments()] == [assessment.id]
    request = state.request_triage_information(
        assessment.id, NOW + timedelta(minutes=10), "vet-123",
        "Has Mimi eaten today?", current_time=NOW + timedelta(minutes=10),
    )
    state.review_triage(
        assessment.id, NOW + timedelta(minutes=20), "vet-123",
        TriageReviewStatus.MODIFIED, TriageUrgency.URGENT,
        "Urgent consultation needed.", current_time=NOW + timedelta(minutes=20),
    )
    follow_up = state.define_triage_follow_up(
        assessment.id, "follow-up-1", "urgent veterinary consultation",
        NOW + timedelta(hours=2), NOW + timedelta(minutes=30), "vet-123",
        current_time=NOW + timedelta(minutes=30),
    )
    assert request.id == "triage-info-1"
    assert state.pending_triage_assessments() == []
    assert follow_up.category == "veterinary"
    assert follow_up.action_key == "triage:triage-1:follow-up"
    assert state.export_data()["triage_information_requests"][0]["question"] == "Has Mimi eaten today?"


def test_triage_follow_up_requires_completed_veterinarian_review():
    state = CatCareState("Mimi")
    state.record_note("eating less", NOW)
    assessment = state.request_triage(
        ["note-1"], TriageUrgency.MONITOR, "Monitor.", "No examination.",
        NOW, "triage-service", "model-1", current_time=NOW,
    )
    with pytest.raises(ValueError, match="reviewed triage"):
        state.define_triage_follow_up(
            assessment.id, "follow-up-1", "consultation", NOW + timedelta(hours=2),
            NOW, "vet-123", current_time=NOW,
        )
