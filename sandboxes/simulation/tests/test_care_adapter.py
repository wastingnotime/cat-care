from datetime import date, datetime, timedelta, timezone

import pytest

from app.interfaces.care_adapter import CareAdapter
from app.interfaces.integration_ports import CatProfileData, NotificationDelivery, TriageSuggestion
from app.simulation.domain import (
    CatCareState,
    NotificationOutcome,
    RecurrencePolicy,
    Responsibility,
    TriageUrgency,
)


NOW = datetime(2026, 1, 1, 9, tzinfo=timezone.utc)


def test_adapter_exposes_status_contract_without_reimplementing_domain_rules():
    state = CatCareState(
        "Mimi",
        [Responsibility("r1", "vaccine", NOW + timedelta(days=1), "preventive care")],
    )
    adapter = CareAdapter(state)
    response = adapter.get_status(NOW, timedelta(days=2))
    assert response == {
        "cat": "Mimi",
        "kind": "due_soon",
        "status": "Next: vaccine soon.",
        "nearest_responsibility_id": "r1",
    }


def test_adapter_exposes_cat_profile_as_plain_data():
    state = CatCareState(
        "Mimi",
        birth_date=date(2021, 5, 1),
        adoption_date=date(2021, 7, 10),
        photo_ref="mimi-profile.jpg",
    )
    assert CareAdapter(state).get_cat_profile() == {
        "name": "Mimi",
        "birth_date": "2021-05-01",
        "adoption_date": "2021-07-10",
        "photo_ref": "mimi-profile.jpg",
    }


def test_adapter_edits_cat_profile_with_history_details():
    state = CatCareState("Mimi")
    event = CareAdapter(state).edit_cat_profile(
        "Mimi renamed",
        date(2021, 5, 1),
        date(2021, 7, 10),
        "mimi-profile.jpg",
        NOW,
        current_time=NOW,
    )
    assert event["type"] == "cat_profile_edited"
    assert event["details"]["previous_name"] == "Mimi"
    assert state.cat_name == "Mimi renamed"


def test_adapter_rejects_future_cat_profile_edit():
    state = CatCareState("Mimi")
    with pytest.raises(ValueError, match="future"):
        CareAdapter(state).edit_cat_profile(
            "Mimi later",
            None,
            None,
            None,
            NOW + timedelta(hours=1),
            current_time=NOW,
        )


def test_adapter_exposes_sorted_responsibility_views_with_derived_states():
    state = CatCareState(
        "Mimi",
        [
            Responsibility("unknown", "future treatment", None, "treatment"),
            Responsibility("later", "appointment", NOW + timedelta(days=4), "veterinary"),
            Responsibility("urgent", "vaccine", NOW - timedelta(days=1), "preventive care"),
        ],
    )
    views = CareAdapter(state).get_responsibilities(NOW, timedelta(days=2))
    assert [view["id"] for view in views] == ["urgent", "later", "unknown"]
    assert [view["state"] for view in views] == ["overdue", "planned", "unknown"]
    assert all(view["recurrence_interval_days"] is None for view in views)


def test_adapter_exposes_responsibility_recurrence_policy():
    state = CatCareState(
        "Mimi",
        [Responsibility("r1", "treatment", NOW, "treatment", recurrence=RecurrencePolicy(30))],
    )
    view = CareAdapter(state).get_responsibilities(NOW, timedelta(days=2))[0]
    assert view["recurrence_interval_days"] == 30
    assert view["action_key"] is None


def test_adapter_exposes_responsibility_action_key():
    state = CatCareState(
        "Mimi",
        [Responsibility("r1", "vaccine", NOW, "preventive care", action_key="vaccine-2026")],
    )
    view = CareAdapter(state).get_responsibilities(NOW, timedelta(days=2))[0]
    assert view["action_key"] == "vaccine-2026"


def test_adapter_exposes_completed_at_in_responsibility_views():
    state = CatCareState("Mimi", [Responsibility("r1", "vaccine", NOW, "preventive care")])
    adapter = CareAdapter(state)
    adapter.complete_responsibility("r1", NOW, current_time=NOW)
    view = adapter.get_responsibilities(NOW, timedelta(days=2))[0]
    assert view["state"] == "completed"
    assert view["completed_at"] == NOW.isoformat()
    assert view["cancelled_at"] is None


def test_adapter_exposes_cancelled_at_in_responsibility_views():
    state = CatCareState("Mimi", [Responsibility("r1", "appointment", NOW, "veterinary")])
    adapter = CareAdapter(state)
    adapter.cancel_responsibility("r1", NOW, current_time=NOW)
    view = adapter.get_responsibilities(NOW, timedelta(days=2))[0]
    assert view["state"] == "cancelled"
    assert view["cancelled_at"] == NOW.isoformat()


def test_adapter_uses_id_as_tie_breaker_for_equal_due_dates():
    state = CatCareState(
        "Mimi",
        [
            Responsibility("z-last", "later added", NOW + timedelta(days=1), "care"),
            Responsibility("a-first", "earlier id", NOW + timedelta(days=1), "care"),
        ],
    )
    views = CareAdapter(state).get_responsibilities(NOW, timedelta(days=2))
    assert [view["id"] for view in views] == ["a-first", "z-last"]


def test_adapter_commands_return_event_records_and_use_domain_transitions():
    state = CatCareState("Mimi")
    adapter = CareAdapter(state)
    created = adapter.create_responsibility(
        "r1", "vaccine", "preventive care", NOW, NOW, action_key="vaccine-2026"
    )
    edited = adapter.edit_responsibility(
        "r1", "annual vaccine", "preventive care", NOW, NOW
    )
    completed = adapter.complete_responsibility("r1", NOW, current_time=NOW)
    note = adapter.record_note("eating less", NOW, current_time=NOW)
    care_event = adapter.record_care_event(
        "weight_measured",
        "4.2 kg",
        NOW,
        current_time=NOW,
        responsibility_id="r1",
    )
    assert created["type"] == "responsibility_created"
    assert edited["type"] == "responsibility_edited"
    assert edited["details"]["previous_title"] == "vaccine"
    assert completed["type"] == "responsibility_completed"
    assert completed["action_key"] == "vaccine-2026"
    assert note["type"] == "note_recorded"
    assert care_event["type"] == "weight_measured"
    assert care_event["responsibility_id"] == "r1"
    assert len(state.events) == 5


def test_adapter_completion_exposes_next_recurrence_details():
    state = CatCareState(
        "Mimi",
        [Responsibility("r1", "treatment", NOW, "treatment", recurrence=RecurrencePolicy(30))],
    )
    completed = CareAdapter(state).complete_responsibility("r1", NOW, current_time=NOW)
    assert completed["details"] == {
        "next_responsibility_id": "r1-next",
        "next_due_at": (NOW + timedelta(days=30)).isoformat(),
    }


def test_adapter_exposes_newest_first_timeline_records():
    state = CatCareState("Mimi")
    adapter = CareAdapter(state)
    adapter.record_note("older", NOW, current_time=NOW)
    adapter.record_note("newer", NOW + timedelta(hours=1), current_time=NOW + timedelta(hours=1))
    timeline = adapter.get_timeline()
    assert [item["description"] for item in timeline] == ["newer", "older"]
    assert CareAdapter(state).get_timeline_summary() == {
        "event_count": 2,
        "newest_event_type": "note_recorded",
        "newest_event_at": (NOW + timedelta(hours=1)).isoformat(),
    }


def test_adapter_timeline_summary_is_stable_when_empty():
    assert CareAdapter(CatCareState("Mimi")).get_timeline_summary() == {
        "event_count": 0,
        "newest_event_type": None,
        "newest_event_at": None,
    }


def test_adapter_exposes_notification_deferral_export_and_delete_contracts():
    state = CatCareState(
        "Mimi",
        [Responsibility("r1", "vaccine", NOW, "preventive care")],
    )
    adapter = CareAdapter(state)
    notification = adapter.record_notification("r1", NOW, NotificationOutcome.FAILED)
    deferred = adapter.defer_responsibility("r1", NOW, NOW + timedelta(days=7), current_time=NOW)
    exported = adapter.export_data()
    deleted = adapter.delete_data(NOW, current_time=NOW)
    assert notification["type"] == "notification_recorded"
    assert notification["action_key"] is None
    assert deferred["type"] == "responsibility_deferred"
    assert exported["responsibilities"][0]["category"] == "preventive care"
    assert deleted["responsibilities_removed"] == 1
    assert deleted["notifications_removed"] == 1
    assert deleted["notes_removed"] == 0
    assert deleted["direct_care_removed"] == 0
    assert deleted["triage_assessments_removed"] == 0
    assert deleted["veterinarian_reviews_removed"] == 0
    assert adapter.export_data()["deleted"] is True
    assert adapter.export_data()["deleted_at"] == NOW.isoformat()


def test_adapter_uses_profile_store_without_giving_it_domain_state():
    class Store:
        def __init__(self):
            self.saved = None

        def load(self):
            return CatProfileData("Nina", None, None, "nina.jpg")

        def save(self, profile, changed_at):
            self.saved = (profile, changed_at)

    store = Store()
    adapter = CareAdapter(CatCareState("Mimi"))
    loaded = adapter.load_cat_profile(store, NOW)
    saved = adapter.save_cat_profile(store, NOW)
    assert loaded["type"] == "cat_profile_edited"
    assert saved["profile"]["name"] == "Nina"
    assert store.saved[0].name == "Nina"


def test_adapter_delivers_notification_through_gateway_and_records_outcome():
    class Gateway:
        def deliver(self, responsibility_id, title, due_at):
            return NotificationDelivery(NotificationOutcome.DELIVERED, "mail", "msg-1")

    state = CatCareState("Mimi", [Responsibility("r1", "vaccine", NOW, "care")])
    record = CareAdapter(state).deliver_notification(Gateway(), "r1", NOW, current_time=NOW)
    assert record["type"] == "notification_recorded"
    assert record["details"] == {
        "outcome": "delivered",
        "provider": "mail",
        "provider_message_id": "msg-1",
    }


def test_adapter_translates_triage_provider_suggestion_into_domain_assessment():
    state = CatCareState("Mimi")
    adapter = CareAdapter(state)
    adapter.record_note("eating less", NOW, current_time=NOW)

    class Provider:
        def assess(self, note_ids, note_text):
            assert note_ids == ("note-1",)
            assert note_text == ("eating less",)
            return TriageSuggestion(
                TriageUrgency.NEEDS_ATTENTION,
                "Needs prompt attention.",
                "No examination is available.",
                "triage-service",
                "model-1",
            )

    assessment = adapter.request_triage_from_provider(
        Provider(), ["note-1"], NOW, current_time=NOW
    )
    assert assessment["urgency"] == "needs_attention"
    assert assessment["provider"] == "triage-service"


def test_adapter_deletion_receipt_counts_all_typed_records():
    state = CatCareState("Mimi", [Responsibility("r1", "vaccine", NOW, "care")])
    adapter = CareAdapter(state)
    adapter.record_notification("r1", NOW, "failed")
    adapter.record_note("eating less", NOW, current_time=NOW)
    adapter.record_care_event("weight_measured", "4.2 kg", NOW, current_time=NOW, responsibility_id="r1")
    deleted = adapter.delete_data(NOW, current_time=NOW)
    assert deleted["notifications_removed"] == 1
    assert deleted["notes_removed"] == 1
    assert deleted["direct_care_removed"] == 1
    assert deleted["triage_assessments_removed"] == 0
    assert deleted["veterinarian_reviews_removed"] == 0
    with pytest.raises(ValueError, match="deleted"):
        adapter.get_notifications()
    with pytest.raises(ValueError, match="deleted"):
        adapter.get_notes()
    with pytest.raises(ValueError, match="deleted"):
        adapter.get_care_events()


def test_adapter_exposes_newest_first_notification_history():
    state = CatCareState("Mimi", [Responsibility("r1", "vaccine", NOW, "preventive care")])
    adapter = CareAdapter(state)
    adapter.record_notification("r1", NOW, "failed")
    adapter.record_notification("r1", NOW + timedelta(hours=1), "delivered")
    notifications = adapter.get_notifications()
    assert [item["details"]["outcome"] for item in notifications] == ["delivered", "failed"]
    assert all(item["type"] == "notification_recorded" for item in notifications)


def test_notification_history_preserves_action_key():
    state = CatCareState(
        "Mimi",
        [Responsibility("r1", "vaccine", NOW, "preventive care", action_key="vaccine-2026")],
    )
    adapter = CareAdapter(state)
    adapter.record_notification("r1", NOW, "failed")
    assert adapter.get_notifications()[0]["action_key"] == "vaccine-2026"


def test_adapter_exposes_newest_first_note_history():
    state = CatCareState("Mimi", [Responsibility("r1", "vaccine", NOW, "preventive care")])
    adapter = CareAdapter(state)
    adapter.record_note("older", NOW, current_time=NOW)
    adapter.record_note("newer", NOW + timedelta(hours=1), current_time=NOW + timedelta(hours=1))
    notes = adapter.get_notes()
    assert [item["description"] for item in notes] == ["newer", "older"]
    assert all(item["responsibility_id"] is None for item in notes)
    assert notes[0]["id"] == "note-2"


def test_adapter_exposes_newest_first_direct_care_history():
    state = CatCareState(
        "Mimi",
        [Responsibility("r1", "vaccine", NOW, "preventive care", action_key="vaccine-2026")],
    )
    adapter = CareAdapter(state)
    adapter.record_care_event("weight_measured", "4.1 kg", NOW, current_time=NOW, responsibility_id="r1")
    adapter.record_care_event(
        "weight_measured",
        "4.2 kg",
        NOW + timedelta(hours=1),
        current_time=NOW + timedelta(hours=1),
        responsibility_id="r1",
    )
    events = adapter.get_care_events()
    assert [item["description"] for item in events] == ["4.2 kg", "4.1 kg"]
    assert all(item["action_key"] == "vaccine-2026" for item in events)


def test_adapter_rejects_unknown_notification_outcome():
    state = CatCareState("Mimi", [Responsibility("r1", "vaccine", NOW, "preventive care")])
    with pytest.raises(ValueError, match="unsupported"):
        CareAdapter(state).record_notification("r1", NOW, "unknown")


def test_adapter_exposes_provisional_triage_and_veterinarian_review():
    state = CatCareState("Mimi")
    adapter = CareAdapter(state)
    adapter.record_note("eating less", NOW, current_time=NOW)
    assessment = adapter.request_triage(
        ["note-1"],
        "needs_attention",
        "Reduced appetite needs attention.",
        "No examination available.",
        NOW,
        "triage-service",
        "model-2026-01",
        current_time=NOW,
    )
    assert assessment["review_status"] == "pending"
    review = adapter.review_triage(
        assessment["id"],
        NOW + timedelta(hours=1),
        "vet-123",
        "accepted",
        None,
        "Reviewed and accepted.",
        current_time=NOW + timedelta(hours=1),
    )
    assert review["decision"] == "accepted"
    assert review["final_urgency"] == "needs_attention"
    assert adapter.get_triage_assessments()[0]["review_status"] == "accepted"
    assert adapter.get_veterinarian_reviews()[0]["veterinarian_id"] == "vet-123"


def test_adapter_rejects_unknown_triage_urgency():
    state = CatCareState("Mimi")
    adapter = CareAdapter(state)
    adapter.record_note("sneezing", NOW, current_time=NOW)
    with pytest.raises(ValueError, match="unsupported"):
        adapter.request_triage(
            ["note-1"], "diagnosis", "x", "y", NOW,
            "triage-service", "model-2026-01", current_time=NOW,
        )


def test_adapter_exposes_calendar_recurrence_without_inference():
    adapter = CareAdapter(CatCareState("Mimi"))
    adapter.create_responsibility(
        "r-monthly",
        "monthly weigh-in",
        "monitoring",
        NOW,
        NOW,
        recurrence_calendar_months=1,
    )
    assert adapter.get_responsibilities(NOW, timedelta(days=1))[0][
        "recurrence_calendar_months"
    ] == 1
