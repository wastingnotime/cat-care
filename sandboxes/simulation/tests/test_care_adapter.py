from datetime import date, datetime, timedelta, timezone

from app.interfaces.care_adapter import CareAdapter
from app.simulation.domain import CatCareState, NotificationOutcome, Responsibility


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


def test_adapter_exposes_newest_first_timeline_records():
    state = CatCareState("Mimi")
    adapter = CareAdapter(state)
    adapter.record_note("older", NOW, current_time=NOW)
    adapter.record_note("newer", NOW + timedelta(hours=1), current_time=NOW + timedelta(hours=1))
    timeline = adapter.get_timeline()
    assert [item["description"] for item in timeline] == ["newer", "older"]


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
    assert deferred["type"] == "responsibility_deferred"
    assert exported["responsibilities"][0]["category"] == "preventive care"
    assert deleted["responsibilities_removed"] == 1
    assert adapter.export_data()["deleted"] is True
