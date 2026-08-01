"""Technology-facing ports for integrations around the Cat Care domain.

These protocols describe what the domain adapter needs from external systems.
They intentionally return plain, validated values that the domain can accept;
providers do not receive permission to mutate CatCareState directly.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date, datetime
from typing import Protocol

from app.simulation.domain import NotificationOutcome, TriageUrgency


@dataclass(frozen=True)
class CatProfileData:
    name: str
    birth_date: date | None
    adoption_date: date | None
    photo_ref: str | None


class CatProfileStore(Protocol):
    def load(self) -> CatProfileData: ...

    def save(self, profile: CatProfileData, changed_at: datetime) -> None: ...


@dataclass(frozen=True)
class NotificationDelivery:
    outcome: NotificationOutcome
    provider: str
    provider_message_id: str | None = None


class NotificationGateway(Protocol):
    def deliver(
        self,
        responsibility_id: str,
        title: str,
        due_at: datetime | None,
    ) -> NotificationDelivery: ...


@dataclass(frozen=True)
class TriageSuggestion:
    urgency: TriageUrgency
    rationale: str
    uncertainty: str
    provider: str
    model_version: str


class TriageProvider(Protocol):
    def assess(self, note_ids: tuple[str, ...], note_text: tuple[str, ...]) -> TriageSuggestion: ...


class RecurrencePolicyProvider(Protocol):
    def next_due_at(self, due_at: datetime, action_key: str | None) -> datetime | None: ...
