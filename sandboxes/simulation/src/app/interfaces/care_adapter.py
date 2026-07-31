from __future__ import annotations

from datetime import datetime, timedelta

from app.simulation.domain import CatCareState, Responsibility


class CareAdapter:
    """Thin technology-facing contract around the repository domain model."""

    def __init__(self, state: CatCareState) -> None:
        self.state = state

    def get_status(self, now: datetime, due_soon_threshold: timedelta) -> dict[str, object]:
        snapshot = self.state.status_snapshot(now, due_soon_threshold)
        return {
            "cat": self.state.cat_name,
            "kind": snapshot.kind,
            "status": snapshot.sentence,
            "nearest_responsibility_id": snapshot.nearest_responsibility_id,
        }

    def create_responsibility(
        self,
        responsibility: Responsibility,
        now: datetime,
    ) -> dict[str, object]:
        return self._event_record(self.state.add_responsibility(responsibility, now))

    def complete_responsibility(
        self,
        responsibility_id: str,
        now: datetime,
        *,
        current_time: datetime | None = None,
    ) -> dict[str, object]:
        return self._event_record(
            self.state.complete(responsibility_id, now, current_time=current_time)
        )

    def cancel_responsibility(
        self,
        responsibility_id: str,
        now: datetime,
        *,
        current_time: datetime | None = None,
    ) -> dict[str, object]:
        return self._event_record(
            self.state.cancel(responsibility_id, now, current_time=current_time)
        )

    def record_note(
        self,
        description: str,
        now: datetime,
        *,
        current_time: datetime | None = None,
    ) -> dict[str, object]:
        return self._event_record(
            self.state.record_note(description, now, current_time=current_time)
        )

    @staticmethod
    def _event_record(event: object) -> dict[str, object]:
        return {
            "type": event.event_type,
            "occurred_at": event.occurred_at.isoformat(),
            "description": event.description,
            "responsibility_id": event.responsibility_id,
            "details": dict(event.details),
        }

