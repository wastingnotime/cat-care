from __future__ import annotations

import json
from dataclasses import dataclass, field
from datetime import date, datetime, timedelta
from enum import Enum


def _require_timezone_aware(value: datetime, field_name: str) -> None:
    if value.tzinfo is None or value.utcoffset() is None:
        raise ValueError(f"{field_name} must be timezone-aware")


class ResponsibilityState(str, Enum):
    PLANNED = "planned"
    COMPLETED = "completed"
    CANCELLED = "cancelled"


class NotificationOutcome(str, Enum):
    DELIVERED = "delivered"
    FAILED = "failed"


@dataclass(frozen=True)
class CareEvent:
    event_type: str
    occurred_at: datetime
    description: str
    responsibility_id: str | None = None
    action_key: str | None = None
    details: tuple[tuple[str, str], ...] = ()

    def __post_init__(self) -> None:
        _require_timezone_aware(self.occurred_at, "care event time")


@dataclass(frozen=True)
class RecurrencePolicy:
    interval_days: int

    def __post_init__(self) -> None:
        if self.interval_days <= 0:
            raise ValueError("recurrence interval must be positive")

    def next_due_at(self, due_at: datetime) -> datetime:
        return due_at + timedelta(days=self.interval_days)


@dataclass
class Responsibility:
    id: str
    title: str
    due_at: datetime | None
    category: str
    state: ResponsibilityState = ResponsibilityState.PLANNED
    recurrence: RecurrencePolicy | None = None
    completed_at: datetime | None = None
    action_key: str | None = None

    def __post_init__(self) -> None:
        if not self.category.strip():
            raise ValueError("responsibility category cannot be empty")
        if self.due_at is not None:
            _require_timezone_aware(self.due_at, "responsibility due time")
        if self.completed_at is not None:
            _require_timezone_aware(self.completed_at, "responsibility completion time")

    def derived_state(self, now: datetime, due_soon_threshold: timedelta) -> str:
        _require_timezone_aware(now, "current time")
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
        _require_timezone_aware(now, "completion time")
        if current_time is not None:
            _require_timezone_aware(current_time, "current time")
        if current_time is not None and now > current_time:
            raise ValueError("a care event cannot be recorded in the future")
        if self.state != ResponsibilityState.PLANNED:
            raise ValueError(f"responsibility {self.id} is not completable")
        self.state = ResponsibilityState.COMPLETED
        self.completed_at = now
        return CareEvent("responsibility_completed", now, self.title, self.id, self.action_key)

    def cancel(
        self,
        now: datetime | None = None,
        *,
        current_time: datetime | None = None,
    ) -> CareEvent | None:
        if now is not None:
            _require_timezone_aware(now, "cancellation time")
        if current_time is not None:
            _require_timezone_aware(current_time, "current time")
        if now is not None and current_time is not None and now > current_time:
            raise ValueError("a care event cannot be recorded in the future")
        if self.state != ResponsibilityState.PLANNED:
            raise ValueError(f"responsibility {self.id} is not cancellable")
        self.state = ResponsibilityState.CANCELLED
        if now is None:
            return None
        return CareEvent("responsibility_cancelled", now, self.title, self.id)


@dataclass(frozen=True)
class StatusSnapshot:
    kind: str
    sentence: str
    nearest_responsibility_id: str | None = None


@dataclass(frozen=True)
class DeletionReceipt:
    deleted_at: datetime
    responsibilities_removed: int
    events_removed: int


@dataclass
class CatCareState:
    cat_name: str
    responsibilities: list[Responsibility] = field(default_factory=list)
    events: list[CareEvent] = field(default_factory=list)
    future_information_known: bool = True
    deleted: bool = False
    birth_date: date | None = None
    adoption_date: date | None = None
    photo_ref: str | None = None

    def __post_init__(self) -> None:
        if not self.cat_name.strip():
            raise ValueError("cat name cannot be empty")
        if self.birth_date is not None and self.adoption_date is not None and self.adoption_date < self.birth_date:
            raise ValueError("adoption date cannot be before birth date")
        if self.photo_ref is not None and not self.photo_ref.strip():
            raise ValueError("photo reference cannot be empty")

    def _ensure_active(self) -> None:
        if self.deleted:
            raise ValueError("cat data has been deleted")

    def status(self, now: datetime, due_soon_threshold: timedelta) -> str:
        return self.status_snapshot(now, due_soon_threshold).sentence

    def status_snapshot(self, now: datetime, due_soon_threshold: timedelta) -> StatusSnapshot:
        self._ensure_active()
        _require_timezone_aware(now, "current time")
        active = [item for item in self.responsibilities if item.state == ResponsibilityState.PLANNED]
        overdue = [item for item in active if item.derived_state(now, due_soon_threshold) == "overdue"]
        if overdue:
            nearest = min(overdue, key=lambda item: item.due_at)
            return StatusSnapshot("overdue", "Something important is overdue.", nearest.id)
        unknown = next((item for item in active if item.due_at is None), None)
        if unknown is not None:
            return StatusSnapshot("unknown", "Some future care information is unknown.", unknown.id)
        nearest = min(
            (item for item in active if item.due_at is not None),
            key=lambda item: item.due_at,
            default=None,
        )
        if nearest is not None:
            state = nearest.derived_state(now, due_soon_threshold)
            if state == "due_soon":
                return StatusSnapshot("due_soon", f"Next: {nearest.title} soon.", nearest.id)
            return StatusSnapshot("planned", f"Nothing important is due soon. Next: {nearest.title}.", nearest.id)
        if not self.future_information_known:
            return StatusSnapshot("unknown", "Some future care information is unknown.")
        return StatusSnapshot("clear", "Nothing important is pending.")

    def add_responsibility(self, responsibility: Responsibility, now: datetime) -> CareEvent:
        self._ensure_active()
        _require_timezone_aware(now, "creation time")
        if any(item.id == responsibility.id for item in self.responsibilities):
            raise ValueError(f"responsibility {responsibility.id} already exists")
        self.responsibilities.append(responsibility)
        event = CareEvent("responsibility_created", now, responsibility.title, responsibility.id)
        self.events.append(event)
        return event

    def record_notification(
        self,
        responsibility_id: str,
        attempted_at: datetime,
        outcome: NotificationOutcome,
        *,
        current_time: datetime | None = None,
    ) -> CareEvent:
        self._ensure_active()
        if not isinstance(outcome, NotificationOutcome):
            raise ValueError("notification outcome must be explicit")
        _require_timezone_aware(attempted_at, "notification time")
        if current_time is not None:
            _require_timezone_aware(current_time, "current time")
        if current_time is not None and attempted_at > current_time:
            raise ValueError("a notification cannot be recorded in the future")
        responsibility = next(item for item in self.responsibilities if item.id == responsibility_id)
        event = CareEvent(
            "notification_recorded",
            attempted_at,
            f"notification for {responsibility.title}",
            responsibility_id,
            details=(("outcome", outcome.value),),
        )
        self.events.append(event)
        return event

    def defer_responsibility(
        self,
        responsibility_id: str,
        now: datetime,
        new_due_at: datetime,
        *,
        current_time: datetime | None = None,
    ) -> CareEvent:
        self._ensure_active()
        _require_timezone_aware(now, "deferral time")
        _require_timezone_aware(new_due_at, "new responsibility due time")
        if current_time is not None:
            _require_timezone_aware(current_time, "current time")
        if current_time is not None and now > current_time:
            raise ValueError("a deferral cannot be recorded in the future")
        if new_due_at <= now:
            raise ValueError("deferred due time must be in the future")
        responsibility = next(item for item in self.responsibilities if item.id == responsibility_id)
        if responsibility.state != ResponsibilityState.PLANNED:
            raise ValueError(f"responsibility {responsibility.id} is not deferrable")
        previous_due_at = responsibility.due_at
        responsibility.due_at = new_due_at
        event = CareEvent(
            "responsibility_deferred",
            now,
            responsibility.title,
            responsibility.id,
            details=(
                ("previous_due_at", previous_due_at.isoformat() if previous_due_at else "unknown"),
                ("new_due_at", new_due_at.isoformat()),
            ),
        )
        self.events.append(event)
        return event

    def edit_responsibility(
        self,
        responsibility_id: str,
        now: datetime,
        *,
        title: str,
        due_at: datetime | None,
        category: str | None = None,
    ) -> CareEvent:
        self._ensure_active()
        _require_timezone_aware(now, "edit time")
        responsibility = next(item for item in self.responsibilities if item.id == responsibility_id)
        if responsibility.state != ResponsibilityState.PLANNED:
            raise ValueError(f"responsibility {responsibility.id} is not editable")
        if due_at is not None:
            _require_timezone_aware(due_at, "responsibility due time")
        if category is not None and not category.strip():
            raise ValueError("responsibility category cannot be empty")
        previous_title = responsibility.title
        previous_due_at = responsibility.due_at
        previous_category = responsibility.category
        responsibility.title = title
        responsibility.due_at = due_at
        if category is not None:
            responsibility.category = category
        event = CareEvent(
            "responsibility_edited",
            now,
            title,
            responsibility.id,
            details=(
                ("previous_title", previous_title),
                ("new_title", title),
                ("previous_due_at", previous_due_at.isoformat() if previous_due_at else "unknown"),
                ("new_due_at", due_at.isoformat() if due_at else "unknown"),
                ("previous_category", previous_category),
                ("new_category", responsibility.category),
            ),
        )
        self.events.append(event)
        return event

    def complete(
        self,
        responsibility_id: str,
        now: datetime,
        *,
        current_time: datetime | None = None,
    ) -> CareEvent:
        self._ensure_active()
        responsibility = next(item for item in self.responsibilities if item.id == responsibility_id)
        if responsibility.action_key is not None and any(
            event.event_type == "responsibility_completed" and event.action_key == responsibility.action_key
            for event in self.events
        ):
            raise ValueError(f"care action {responsibility.action_key} was already completed")
        event = responsibility.complete(now, current_time=current_time)
        self.events.append(event)
        if responsibility.recurrence is not None and responsibility.due_at is not None:
            self.responsibilities.append(
                Responsibility(
                    id=f"{responsibility.id}-next",
                    title=responsibility.title,
                    due_at=responsibility.recurrence.next_due_at(responsibility.due_at),
                    category=responsibility.category,
                    recurrence=responsibility.recurrence,
                    action_key=responsibility.action_key,
                )
            )
        return event

    def cancel(
        self,
        responsibility_id: str,
        now: datetime,
        *,
        current_time: datetime | None = None,
    ) -> CareEvent:
        self._ensure_active()
        responsibility = next(item for item in self.responsibilities if item.id == responsibility_id)
        event = responsibility.cancel(now, current_time=current_time)
        assert event is not None
        self.events.append(event)
        return event

    def record_care_event(
        self,
        event_type: str,
        description: str,
        occurred_at: datetime,
        *,
        current_time: datetime | None = None,
        responsibility_id: str | None = None,
    ) -> CareEvent:
        self._ensure_active()
        _require_timezone_aware(occurred_at, "care event time")
        if current_time is not None:
            _require_timezone_aware(current_time, "current time")
        if current_time is not None and occurred_at > current_time:
            raise ValueError("a care event cannot be recorded in the future")
        if responsibility_id is not None and not any(
            item.id == responsibility_id for item in self.responsibilities
        ):
            raise ValueError(f"responsibility {responsibility_id} does not exist")
        event = CareEvent(event_type, occurred_at, description, responsibility_id)
        self.events.append(event)
        return event

    def record_note(self, description: str, now: datetime, *, current_time: datetime | None = None) -> CareEvent:
        return self.record_care_event("note_recorded", description, now, current_time=current_time)

    def timeline(self) -> list[CareEvent]:
        return sorted(self.events, key=lambda event: event.occurred_at, reverse=True)

    def export_data(self) -> dict[str, object]:
        if self.deleted:
            return {
                "cat": {"name": None, "birth_date": None, "adoption_date": None, "photo_ref": None},
                "deleted": True,
                "future_information_known": None,
                "responsibilities": [],
                "events": [],
            }
        return {
            "cat": {
                "name": self.cat_name,
                "birth_date": self.birth_date.isoformat() if self.birth_date else None,
                "adoption_date": self.adoption_date.isoformat() if self.adoption_date else None,
                "photo_ref": self.photo_ref,
            },
            "deleted": False,
            "future_information_known": self.future_information_known,
            "responsibilities": [
                {
                    "id": item.id,
                    "title": item.title,
                    "category": item.category,
                    "due_at": item.due_at.isoformat() if item.due_at else None,
                    "state": item.state.value,
                    "completed_at": item.completed_at.isoformat() if item.completed_at else None,
                    "recurrence_interval_days": item.recurrence.interval_days if item.recurrence else None,
                    "action_key": item.action_key,
                }
                for item in self.responsibilities
            ],
            "events": [
                {
                    "type": event.event_type,
                    "occurred_at": event.occurred_at.isoformat(),
                    "description": event.description,
                    "responsibility_id": event.responsibility_id,
                    "action_key": event.action_key,
                    "details": dict(event.details),
                }
                for event in sorted(self.events, key=lambda event: event.occurred_at)
            ],
        }

    def export_json(self) -> str:
        return json.dumps(self.export_data(), sort_keys=True)

    def delete_cat(self, deleted_at: datetime, *, current_time: datetime | None = None) -> DeletionReceipt:
        self._ensure_active()
        _require_timezone_aware(deleted_at, "deletion time")
        if current_time is not None:
            _require_timezone_aware(current_time, "current time")
        if current_time is not None and deleted_at > current_time:
            raise ValueError("deletion cannot be recorded in the future")
        receipt = DeletionReceipt(deleted_at, len(self.responsibilities), len(self.events))
        self.responsibilities.clear()
        self.events.clear()
        self.cat_name = ""
        self.birth_date = None
        self.adoption_date = None
        self.photo_ref = None
        self.future_information_known = True
        self.deleted = True
        return receipt
