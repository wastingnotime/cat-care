from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime, timedelta
from enum import Enum


class ResponsibilityState(str, Enum):
    PLANNED = "planned"
    COMPLETED = "completed"
    CANCELLED = "cancelled"


@dataclass(frozen=True)
class CareEvent:
    event_type: str
    occurred_at: datetime
    description: str
    responsibility_id: str | None = None


@dataclass
class Responsibility:
    id: str
    title: str
    due_at: datetime | None
    state: ResponsibilityState = ResponsibilityState.PLANNED
    recurrence_days: int | None = None
    completed_at: datetime | None = None

    def derived_state(self, now: datetime, due_soon_threshold: timedelta) -> str:
        if self.state != ResponsibilityState.PLANNED:
            return self.state.value
        if self.due_at is None:
            return "unknown"
        if self.due_at < now:
            return "overdue"
        if self.due_at <= now + due_soon_threshold:
            return "due_soon"
        return "planned"

    def complete(self, now: datetime, *, current_time: datetime | None = None) -> CareEvent:
        if current_time is not None and now > current_time:
            raise ValueError("a care event cannot be recorded in the future")
        if self.state != ResponsibilityState.PLANNED:
            raise ValueError(f"responsibility {self.id} is not completable")
        self.state = ResponsibilityState.COMPLETED
        self.completed_at = now
        return CareEvent("responsibility_completed", now, self.title, self.id)

    def cancel(self, now: datetime | None = None) -> CareEvent | None:
        if self.state != ResponsibilityState.PLANNED:
            raise ValueError(f"responsibility {self.id} is not cancellable")
        self.state = ResponsibilityState.CANCELLED
        if now is None:
            return None
        return CareEvent("responsibility_cancelled", now, self.title, self.id)


@dataclass
class CatCareState:
    cat_name: str
    responsibilities: list[Responsibility] = field(default_factory=list)
    events: list[CareEvent] = field(default_factory=list)
    future_information_known: bool = True

    def status(self, now: datetime, due_soon_threshold: timedelta) -> str:
        active = [item for item in self.responsibilities if item.state == ResponsibilityState.PLANNED]
        if any(item.derived_state(now, due_soon_threshold) == "overdue" for item in active):
            return "Something important is overdue."
        if any(item.due_at is None for item in active):
            return "Some future care information is unknown."
        nearest = min(
            (item for item in active if item.due_at is not None),
            key=lambda item: item.due_at,
            default=None,
        )
        if nearest is not None:
            state = nearest.derived_state(now, due_soon_threshold)
            if state == "due_soon":
                return f"Next: {nearest.title} soon."
            return f"Nothing important is due soon. Next: {nearest.title}."
        if not self.future_information_known:
            return "Some future care information is unknown."
        return "Nothing important is pending."

    def complete(
        self,
        responsibility_id: str,
        now: datetime,
        *,
        current_time: datetime | None = None,
    ) -> CareEvent:
        responsibility = next(item for item in self.responsibilities if item.id == responsibility_id)
        event = responsibility.complete(now, current_time=current_time)
        self.events.append(event)
        if responsibility.recurrence_days is not None and responsibility.due_at is not None:
            self.responsibilities.append(
                Responsibility(
                    id=f"{responsibility.id}-next",
                    title=responsibility.title,
                    due_at=responsibility.due_at + timedelta(days=responsibility.recurrence_days),
                )
            )
        return event

    def cancel(self, responsibility_id: str, now: datetime) -> CareEvent:
        responsibility = next(item for item in self.responsibilities if item.id == responsibility_id)
        event = responsibility.cancel(now)
        assert event is not None
        self.events.append(event)
        return event

    def record_note(self, description: str, now: datetime, *, current_time: datetime | None = None) -> CareEvent:
        if current_time is not None and now > current_time:
            raise ValueError("a note cannot be recorded in the future")
        event = CareEvent("note_recorded", now, description)
        self.events.append(event)
        return event

    def timeline(self) -> list[CareEvent]:
        return sorted(self.events, key=lambda event: event.occurred_at, reverse=True)
