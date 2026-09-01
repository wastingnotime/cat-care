from datetime import datetime, timedelta, timezone

import pytest

from cat_care_api.store import CatCareStore


NOW = datetime(2026, 9, 1, 12, tzinfo=timezone.utc)


@pytest.fixture
def store():
    value = CatCareStore(":memory:")
    yield value
    value.close()


def test_status_preserves_uncertainty_and_completion_history(store):
    responsibility = store.add_responsibility("Annual exam", "veterinary", None, NOW)
    status = store.status(NOW, timedelta(days=2))
    assert status.kind == "unknown"
    assert status.nearest_responsibility_id == responsibility["id"]

    completed = store.complete_responsibility(responsibility["id"], NOW)
    assert completed["state"] == "completed"
    assert store.status(NOW, timedelta(days=2)).kind == "clear"
    assert [event["type"] for event in store.timeline()] == [
        "responsibility_created",
        "responsibility_completed",
    ]


def test_due_states_are_derived_from_injected_time(store):
    store.add_responsibility("Vaccination", "preventive", NOW + timedelta(days=1), NOW)
    assert store.status(NOW, timedelta(days=2)).kind == "due_soon"
    assert store.status(NOW + timedelta(days=2), timedelta(days=2)).kind == "overdue"


def test_completion_is_terminal(store):
    responsibility = store.add_responsibility("Vaccination", "preventive", NOW, NOW)
    store.complete_responsibility(responsibility["id"], NOW)
    with pytest.raises(ValueError, match="planned"):
        store.complete_responsibility(responsibility["id"], NOW)
