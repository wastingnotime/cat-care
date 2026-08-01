from datetime import datetime, timezone

from app.interfaces.integration_ports import (
    CatProfileData,
    NotificationDelivery,
    TriageSuggestion,
)
from app.simulation.domain import NotificationOutcome, TriageUrgency


NOW = datetime(2026, 1, 1, 12, tzinfo=timezone.utc)


def test_integration_result_contracts_are_plain_typed_values():
    profile = CatProfileData("Mimi", None, None, None)
    delivery = NotificationDelivery(NotificationOutcome.DELIVERED, "mail", "msg-1")
    suggestion = TriageSuggestion(
        TriageUrgency.NEEDS_ATTENTION,
        "Reduced appetite needs prompt attention.",
        "No examination is available.",
        "triage-service",
        "model-2026-01",
    )

    assert profile.name == "Mimi"
    assert delivery.outcome is NotificationOutcome.DELIVERED
    assert delivery.provider_message_id == "msg-1"
    assert suggestion.urgency is TriageUrgency.NEEDS_ATTENTION
    assert suggestion.provider == "triage-service"
    assert NOW.tzinfo is not None
