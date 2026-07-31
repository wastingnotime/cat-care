from datetime import datetime, timedelta, timezone

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


def test_adapter_commands_return_event_records_and_use_domain_transitions():
    state = CatCareState("Mimi")
    adapter = CareAdapter(state)
    created = adapter.create_responsibility(
        Responsibility("r1", "vaccine", NOW, "preventive care"), NOW
    )
    completed = adapter.complete_responsibility("r1", NOW, current_time=NOW)
    note = adapter.record_note("eating less", NOW, current_time=NOW)
    assert created["type"] == "responsibility_created"
    assert completed["type"] == "responsibility_completed"
    assert note["type"] == "note_recorded"
    assert len(state.events) == 3


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
