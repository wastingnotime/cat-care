from __future__ import annotations

import calendar
import json
from dataclasses import dataclass, field
from datetime import date, datetime, timedelta
from enum import Enum


def _require_timezone_aware(value: datetime, field_name: str) -> None:
    if value.tzinfo is None or value.utcoffset() is None:
        raise ValueError(f"{field_name} must be timezone-aware")


def _validate_cat_profile(
    name: str,
    birth_date: date | None,
    adoption_date: date | None,
    photo_ref: str | None,
) -> None:
    if not name.strip():
        raise ValueError("cat name cannot be empty")
    if birth_date is not None and adoption_date is not None and adoption_date < birth_date:
        raise ValueError("adoption date cannot be before birth date")
    if photo_ref is not None and not photo_ref.strip():
        raise ValueError("photo reference cannot be empty")


class ResponsibilityState(str, Enum):
    PLANNED = "planned"
    COMPLETED = "completed"
    CANCELLED = "cancelled"


class NotificationOutcome(str, Enum):
    DELIVERED = "delivered"
    FAILED = "failed"


class TriageUrgency(str, Enum):
    URGENT = "urgent"
    NEEDS_ATTENTION = "needs_attention"
    MONITOR = "monitor"
    INSUFFICIENT_INFORMATION = "insufficient_information"


class TriageReviewStatus(str, Enum):
    PENDING = "pending"
    ACCEPTED = "accepted"
    MODIFIED = "modified"
    REJECTED = "rejected"


@dataclass(frozen=True)
class NotificationRecord:
    responsibility_id: str
    attempted_at: datetime
    outcome: NotificationOutcome
    action_key: str | None = None
    provider: str | None = None
    provider_message_id: str | None = None

    def __post_init__(self) -> None:
        _require_timezone_aware(self.attempted_at, "notification time")
        if not isinstance(self.outcome, NotificationOutcome):
            raise ValueError("notification outcome must be explicit")


@dataclass(frozen=True)
class NoteRecord:
    description: str
    occurred_at: datetime
    id: str | None = None

    def __post_init__(self) -> None:
        if not self.description.strip():
            raise ValueError("note description cannot be empty")
        _require_timezone_aware(self.occurred_at, "note time")


@dataclass
class TriageAssessment:
    id: str
    note_ids: tuple[str, ...]
    urgency: TriageUrgency
    rationale: str
    uncertainty: str
    assessed_at: datetime
    provider: str
    model_version: str
    review_status: TriageReviewStatus = TriageReviewStatus.PENDING
    final_urgency: TriageUrgency | None = None

    def __post_init__(self) -> None:
        if not self.note_ids:
            raise ValueError("triage assessment requires at least one note")
        if not self.rationale.strip():
            raise ValueError("triage rationale cannot be empty")
        if not self.uncertainty.strip():
            raise ValueError("triage uncertainty cannot be empty")
        if not self.provider.strip():
            raise ValueError("triage provider cannot be empty")
        if not self.model_version.strip():
            raise ValueError("triage model version cannot be empty")
        if not isinstance(self.urgency, TriageUrgency):
            raise ValueError("triage urgency must be explicit")
        _require_timezone_aware(self.assessed_at, "triage assessment time")


@dataclass(frozen=True)
class VeterinarianReview:
    assessment_id: str
    reviewed_at: datetime
    veterinarian_id: str
    decision: TriageReviewStatus
    final_urgency: TriageUrgency | None
    rationale: str

    def __post_init__(self) -> None:
        if not isinstance(self.decision, TriageReviewStatus):
            raise ValueError("veterinarian review decision must be explicit")
        if not self.veterinarian_id.strip():
            raise ValueError("veterinarian identity cannot be empty")
        if self.decision == TriageReviewStatus.PENDING:
            raise ValueError("veterinarian review decision cannot be pending")
        if not self.rationale.strip():
            raise ValueError("veterinarian review rationale cannot be empty")
        if self.decision == TriageReviewStatus.MODIFIED and self.final_urgency is None:
            raise ValueError("modified triage review requires final urgency")
        if self.decision == TriageReviewStatus.REJECTED and self.final_urgency is not None:
            raise ValueError("rejected triage review cannot have final urgency")
        _require_timezone_aware(self.reviewed_at, "veterinarian review time")


@dataclass(frozen=True)
class TriageInformationRequest:
    id: str
    assessment_id: str
    requested_at: datetime
    veterinarian_id: str
    question: str

    def __post_init__(self) -> None:
        if not self.veterinarian_id.strip():
            raise ValueError("veterinarian identity cannot be empty")
        if not self.question.strip():
            raise ValueError("information request question cannot be empty")
        _require_timezone_aware(self.requested_at, "information request time")


@dataclass(frozen=True)
class DirectCareRecord:
    event_type: str
    description: str
    occurred_at: datetime
    responsibility_id: str | None = None
    action_key: str | None = None

    def __post_init__(self) -> None:
        if not self.event_type.strip():
            raise ValueError("care event type cannot be empty")
        if not self.description.strip():
            raise ValueError("care event description cannot be empty")
        _require_timezone_aware(self.occurred_at, "care event time")


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
    interval_days: int | None = None
    calendar_months: int | None = None

    def __post_init__(self) -> None:
        if (self.interval_days is None) == (self.calendar_months is None):
            raise ValueError("recurrence must define exactly one interval rule")
        if self.interval_days is not None and self.interval_days <= 0:
            raise ValueError("recurrence interval must be positive")
        if self.calendar_months is not None and self.calendar_months <= 0:
            raise ValueError("calendar recurrence must be positive")

    def next_due_at(self, due_at: datetime) -> datetime:
        _require_timezone_aware(due_at, "recurrence due time")
        if self.interval_days is not None:
            return due_at + timedelta(days=self.interval_days)
        month_index = due_at.month - 1 + self.calendar_months
        year = due_at.year + month_index // 12
        month = month_index % 12 + 1
        day = min(due_at.day, calendar.monthrange(year, month)[1])
        return due_at.replace(year=year, month=month, day=day)


@dataclass
class Responsibility:
    id: str
    title: str
    due_at: datetime | None
    category: str
    state: ResponsibilityState = ResponsibilityState.PLANNED
    recurrence: RecurrencePolicy | None = None
    completed_at: datetime | None = None
    cancelled_at: datetime | None = None
    action_key: str | None = None

    def __post_init__(self) -> None:
        if not self.id.strip():
            raise ValueError("responsibility id cannot be empty")
        if not self.title.strip():
            raise ValueError("responsibility title cannot be empty")
        if not self.category.strip():
            raise ValueError("responsibility category cannot be empty")
        if self.due_at is not None:
            _require_timezone_aware(self.due_at, "responsibility due time")
        if self.completed_at is not None:
            _require_timezone_aware(self.completed_at, "responsibility completion time")
        if self.cancelled_at is not None:
            _require_timezone_aware(self.cancelled_at, "responsibility cancellation time")

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
        self.cancelled_at = now
        if now is None:
            return None
        return CareEvent(
            "responsibility_cancelled",
            now,
            self.title,
            self.id,
            self.action_key,
        )


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
    notifications_removed: int
    notes_removed: int
    direct_care_removed: int
    triage_assessments_removed: int
    veterinarian_reviews_removed: int
    triage_information_requests_removed: int


@dataclass
class CatCareState:
    cat_name: str
    responsibilities: list[Responsibility] = field(default_factory=list)
    events: list[CareEvent] = field(default_factory=list)
    notifications: list[NotificationRecord] = field(default_factory=list)
    notes: list[NoteRecord] = field(default_factory=list)
    direct_care: list[DirectCareRecord] = field(default_factory=list)
    triage_assessments: list[TriageAssessment] = field(default_factory=list)
    veterinarian_reviews: list[VeterinarianReview] = field(default_factory=list)
    triage_information_requests: list[TriageInformationRequest] = field(default_factory=list)
    future_information_known: bool = True
    deleted: bool = False
    deleted_at: datetime | None = None
    birth_date: date | None = None
    adoption_date: date | None = None
    photo_ref: str | None = None

    def __post_init__(self) -> None:
        _validate_cat_profile(self.cat_name, self.birth_date, self.adoption_date, self.photo_ref)

    def _ensure_active(self) -> None:
        if self.deleted:
            raise ValueError("cat data has been deleted")

    def _responsibility(self, responsibility_id: str) -> Responsibility:
        responsibility = next(
            (item for item in self.responsibilities if item.id == responsibility_id),
            None,
        )
        if responsibility is None:
            raise ValueError(f"responsibility {responsibility_id} does not exist")
        return responsibility

    def status(self, now: datetime, due_soon_threshold: timedelta) -> str:
        return self.status_snapshot(now, due_soon_threshold).sentence

    def status_snapshot(self, now: datetime, due_soon_threshold: timedelta) -> StatusSnapshot:
        self._ensure_active()
        _require_timezone_aware(now, "current time")
        if due_soon_threshold < timedelta(0):
            raise ValueError("due-soon threshold cannot be negative")
        active = [item for item in self.responsibilities if item.state == ResponsibilityState.PLANNED]
        overdue = [item for item in active if item.derived_state(now, due_soon_threshold) == "overdue"]
        if overdue:
            nearest = min(overdue, key=lambda item: (item.due_at, item.id))
            return StatusSnapshot("overdue", "Something important is overdue.", nearest.id)
        unknown = next((item for item in active if item.due_at is None), None)
        if unknown is not None:
            return StatusSnapshot("unknown", "Some future care information is unknown.", unknown.id)
        nearest = min(
            (item for item in active if item.due_at is not None),
            key=lambda item: (item.due_at, item.id),
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

    def edit_cat_profile(
        self,
        now: datetime,
        *,
        name: str,
        birth_date: date | None,
        adoption_date: date | None,
        photo_ref: str | None,
        current_time: datetime | None = None,
    ) -> CareEvent:
        self._ensure_active()
        _require_timezone_aware(now, "profile edit time")
        if current_time is not None:
            _require_timezone_aware(current_time, "current time")
        if current_time is not None and now > current_time:
            raise ValueError("profile edit cannot be recorded in the future")
        _validate_cat_profile(name, birth_date, adoption_date, photo_ref)
        previous = {
            "name": self.cat_name,
            "birth_date": self.birth_date.isoformat() if self.birth_date else "unknown",
            "adoption_date": self.adoption_date.isoformat() if self.adoption_date else "unknown",
            "photo_ref": self.photo_ref or "unknown",
        }
        self.cat_name = name
        self.birth_date = birth_date
        self.adoption_date = adoption_date
        self.photo_ref = photo_ref
        new = {
            "name": name,
            "birth_date": birth_date.isoformat() if birth_date else "unknown",
            "adoption_date": adoption_date.isoformat() if adoption_date else "unknown",
            "photo_ref": photo_ref or "unknown",
        }
        event = CareEvent(
            "cat_profile_edited",
            now,
            name,
            details=tuple((f"previous_{key}", value) for key, value in previous.items())
            + tuple((f"new_{key}", value) for key, value in new.items()),
        )
        self.events.append(event)
        return event

    def add_responsibility(self, responsibility: Responsibility, now: datetime) -> CareEvent:
        self._ensure_active()
        _require_timezone_aware(now, "creation time")
        if any(item.id == responsibility.id for item in self.responsibilities):
            raise ValueError(f"responsibility {responsibility.id} already exists")
        self.responsibilities.append(responsibility)
        event = CareEvent(
            "responsibility_created",
            now,
            responsibility.title,
            responsibility.id,
            responsibility.action_key,
        )
        self.events.append(event)
        return event

    def record_notification(
        self,
        responsibility_id: str,
        attempted_at: datetime,
        outcome: NotificationOutcome,
        *,
        current_time: datetime | None = None,
        provider: str | None = None,
        provider_message_id: str | None = None,
    ) -> CareEvent:
        self._ensure_active()
        if not isinstance(outcome, NotificationOutcome):
            raise ValueError("notification outcome must be explicit")
        _require_timezone_aware(attempted_at, "notification time")
        if current_time is not None:
            _require_timezone_aware(current_time, "current time")
        if current_time is not None and attempted_at > current_time:
            raise ValueError("a notification cannot be recorded in the future")
        responsibility = self._responsibility(responsibility_id)
        notification = NotificationRecord(
            responsibility_id,
            attempted_at,
            outcome,
            responsibility.action_key,
            provider,
            provider_message_id,
        )
        self.notifications.append(notification)
        event = CareEvent(
            "notification_recorded",
            attempted_at,
            f"notification for {responsibility.title}",
            responsibility_id,
            responsibility.action_key,
            details=tuple(
                item
                for item in (
                    ("outcome", outcome.value),
                    *(((("provider", provider),) if provider else ())),
                    *(((("provider_message_id", provider_message_id),) if provider_message_id else ())),
                )
            ),
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
        responsibility = self._responsibility(responsibility_id)
        if responsibility.state != ResponsibilityState.PLANNED:
            raise ValueError(f"responsibility {responsibility.id} is not deferrable")
        previous_due_at = responsibility.due_at
        if previous_due_at is not None and new_due_at <= previous_due_at:
            raise ValueError("deferral must move the due time later")
        responsibility.due_at = new_due_at
        event = CareEvent(
            "responsibility_deferred",
            now,
            responsibility.title,
            responsibility.id,
            responsibility.action_key,
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
        responsibility = self._responsibility(responsibility_id)
        if responsibility.state != ResponsibilityState.PLANNED:
            raise ValueError(f"responsibility {responsibility.id} is not editable")
        if due_at is not None:
            _require_timezone_aware(due_at, "responsibility due time")
        if category is not None and not category.strip():
            raise ValueError("responsibility category cannot be empty")
        if not title.strip():
            raise ValueError("responsibility title cannot be empty")
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
            responsibility.action_key,
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
        responsibility = self._responsibility(responsibility_id)
        if responsibility.action_key is not None and any(
            event.event_type == "responsibility_completed" and event.action_key == responsibility.action_key
            for event in self.events
        ):
            raise ValueError(f"care action {responsibility.action_key} was already completed")
        event = responsibility.complete(now, current_time=current_time)
        if responsibility.recurrence is not None and responsibility.due_at is not None:
            next_responsibility_id = f"{responsibility.id}-next"
            next_due_at = responsibility.recurrence.next_due_at(responsibility.due_at)
            self.responsibilities.append(
                Responsibility(
                    id=next_responsibility_id,
                    title=responsibility.title,
                    due_at=next_due_at,
                    category=responsibility.category,
                    recurrence=responsibility.recurrence,
                    action_key=responsibility.action_key,
                )
            )
            event = CareEvent(
                event.event_type,
                event.occurred_at,
                event.description,
                event.responsibility_id,
                event.action_key,
                details=(
                    ("next_responsibility_id", next_responsibility_id),
                    ("next_due_at", next_due_at.isoformat()),
                ),
            )
        self.events.append(event)
        return event

    def cancel(
        self,
        responsibility_id: str,
        now: datetime,
        *,
        current_time: datetime | None = None,
    ) -> CareEvent:
        self._ensure_active()
        responsibility = self._responsibility(responsibility_id)
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
        if not event_type.strip():
            raise ValueError("care event type cannot be empty")
        if not description.strip():
            raise ValueError("care event description cannot be empty")
        _require_timezone_aware(occurred_at, "care event time")
        if current_time is not None:
            _require_timezone_aware(current_time, "current time")
        if current_time is not None and occurred_at > current_time:
            raise ValueError("a care event cannot be recorded in the future")
        linked_responsibility = None
        if responsibility_id is not None:
            linked_responsibility = next(
                (item for item in self.responsibilities if item.id == responsibility_id),
                None,
            )
            if linked_responsibility is None:
                raise ValueError(f"responsibility {responsibility_id} does not exist")
        action_key = linked_responsibility.action_key if linked_responsibility else None
        event = CareEvent(event_type, occurred_at, description, responsibility_id, action_key)
        if event_type != "note_recorded":
            self.direct_care.append(
                DirectCareRecord(event_type, description, occurred_at, responsibility_id, action_key)
            )
        self.events.append(event)
        return event

    def record_note(self, description: str, now: datetime, *, current_time: datetime | None = None) -> CareEvent:
        note = NoteRecord(description, now, f"note-{len(self.notes) + 1}")
        event = self.record_care_event("note_recorded", description, now, current_time=current_time)
        self.notes.append(note)
        return event

    def request_triage(
        self,
        note_ids: tuple[str, ...] | list[str],
        urgency: TriageUrgency,
        rationale: str,
        uncertainty: str,
        assessed_at: datetime,
        provider: str,
        model_version: str,
        *,
        current_time: datetime | None = None,
    ) -> TriageAssessment:
        self._ensure_active()
        _require_timezone_aware(assessed_at, "triage assessment time")
        if current_time is not None:
            _require_timezone_aware(current_time, "current time")
            if assessed_at > current_time:
                raise ValueError("a triage assessment cannot be recorded in the future")
        normalized_note_ids = tuple(note_ids)
        known_note_ids = {note.id for note in self.notes}
        if any(note_id not in known_note_ids for note_id in normalized_note_ids):
            raise ValueError("triage assessment references an unknown note")
        assessment = TriageAssessment(
            f"triage-{len(self.triage_assessments) + 1}",
            normalized_note_ids,
            urgency,
            rationale,
            uncertainty,
            assessed_at,
            provider,
            model_version,
        )
        self.triage_assessments.append(assessment)
        self.events.append(
            CareEvent(
                "triage_assessed",
                assessed_at,
                f"AI triage assessment {assessment.id}",
                details=(
                    ("urgency", urgency.value),
                    ("review_status", assessment.review_status.value),
                    ("provider", provider),
                    ("model_version", model_version),
                ),
            )
        )
        return assessment

    def review_triage(
        self,
        assessment_id: str,
        reviewed_at: datetime,
        veterinarian_id: str,
        decision: TriageReviewStatus,
        final_urgency: TriageUrgency | None,
        rationale: str,
        *,
        current_time: datetime | None = None,
    ) -> VeterinarianReview:
        self._ensure_active()
        _require_timezone_aware(reviewed_at, "veterinarian review time")
        if current_time is not None:
            _require_timezone_aware(current_time, "current time")
            if reviewed_at > current_time:
                raise ValueError("a veterinarian review cannot be recorded in the future")
        assessment = next(
            (item for item in self.triage_assessments if item.id == assessment_id),
            None,
        )
        if assessment is None:
            raise ValueError(f"triage assessment {assessment_id} does not exist")
        if assessment.review_status != TriageReviewStatus.PENDING:
            raise ValueError(f"triage assessment {assessment_id} has already been reviewed")
        if decision == TriageReviewStatus.ACCEPTED and final_urgency not in (None, assessment.urgency):
            raise ValueError("accepted triage review cannot change urgency")
        if decision == TriageReviewStatus.REJECTED and final_urgency is not None:
            raise ValueError("rejected triage review cannot have final urgency")
        effective_urgency = (
            assessment.urgency
            if decision == TriageReviewStatus.ACCEPTED and final_urgency is None
            else final_urgency
        )
        review = VeterinarianReview(
            assessment_id,
            reviewed_at,
            veterinarian_id,
            decision,
            effective_urgency,
            rationale,
        )
        assessment.review_status = decision
        assessment.final_urgency = effective_urgency or assessment.urgency
        self.veterinarian_reviews.append(review)
        self.events.append(
            CareEvent(
                "triage_reviewed",
                reviewed_at,
                f"Veterinarian reviewed {assessment_id}",
                details=(
                    ("decision", decision.value),
                    ("final_urgency", assessment.final_urgency.value),
                    ("veterinarian_id", veterinarian_id),
                ),
            )
        )
        return review

    def pending_triage_assessments(self) -> list[TriageAssessment]:
        self._ensure_active()
        return sorted(
            (
                assessment
                for assessment in self.triage_assessments
                if assessment.review_status == TriageReviewStatus.PENDING
            ),
            key=lambda assessment: (assessment.assessed_at, assessment.id),
        )

    def request_triage_information(
        self,
        assessment_id: str,
        requested_at: datetime,
        veterinarian_id: str,
        question: str,
        *,
        current_time: datetime | None = None,
    ) -> TriageInformationRequest:
        self._ensure_active()
        _require_timezone_aware(requested_at, "information request time")
        if current_time is not None:
            _require_timezone_aware(current_time, "current time")
            if requested_at > current_time:
                raise ValueError("an information request cannot be recorded in the future")
        assessment = next(
            (item for item in self.triage_assessments if item.id == assessment_id),
            None,
        )
        if assessment is None:
            raise ValueError(f"triage assessment {assessment_id} does not exist")
        if assessment.review_status != TriageReviewStatus.PENDING:
            raise ValueError("information can only be requested for pending triage")
        request = TriageInformationRequest(
            f"triage-info-{len(self.triage_information_requests) + 1}",
            assessment_id,
            requested_at,
            veterinarian_id,
            question,
        )
        self.triage_information_requests.append(request)
        self.events.append(
            CareEvent(
                "triage_information_requested",
                requested_at,
                question,
                details=(
                    ("assessment_id", assessment_id),
                    ("veterinarian_id", veterinarian_id),
                ),
            )
        )
        return request

    def define_triage_follow_up(
        self,
        assessment_id: str,
        responsibility_id: str,
        title: str,
        due_at: datetime,
        created_at: datetime,
        veterinarian_id: str,
        *,
        current_time: datetime | None = None,
    ) -> Responsibility:
        self._ensure_active()
        _require_timezone_aware(created_at, "follow-up creation time")
        _require_timezone_aware(due_at, "follow-up due time")
        if current_time is not None:
            _require_timezone_aware(current_time, "current time")
            if created_at > current_time:
                raise ValueError("a follow-up cannot be created in the future")
        if due_at <= created_at:
            raise ValueError("follow-up due time must be after creation")
        assessment = next(
            (item for item in self.triage_assessments if item.id == assessment_id),
            None,
        )
        if assessment is None or assessment.review_status == TriageReviewStatus.PENDING:
            raise ValueError("follow-up requires reviewed triage")
        if assessment.review_status == TriageReviewStatus.REJECTED:
            raise ValueError("rejected triage cannot define a follow-up")
        if not veterinarian_id.strip():
            raise ValueError("veterinarian identity cannot be empty")
        if any(item.id == responsibility_id for item in self.responsibilities):
            raise ValueError(f"responsibility {responsibility_id} already exists")
        responsibility = Responsibility(
            responsibility_id,
            title,
            due_at,
            "veterinary",
            action_key=f"triage:{assessment_id}:follow-up",
        )
        self.responsibilities.append(responsibility)
        self.events.append(
            CareEvent(
                "triage_follow_up_defined",
                created_at,
                title,
                responsibility_id,
                responsibility.action_key,
                details=(
                    ("assessment_id", assessment_id),
                    ("veterinarian_id", veterinarian_id),
                    ("due_at", due_at.isoformat()),
                ),
            )
        )
        return responsibility

    def timeline(self) -> list[CareEvent]:
        return sorted(self.events, key=self._event_sort_key, reverse=True)

    @staticmethod
    def _event_sort_key(event: CareEvent) -> tuple[datetime, str, str, str, str]:
        return (
            event.occurred_at,
            event.event_type,
            event.responsibility_id or "",
            event.action_key or "",
            event.description,
        )

    def export_data(self) -> dict[str, object]:
        if self.deleted:
            return {
                "cat": {"name": None, "birth_date": None, "adoption_date": None, "photo_ref": None},
                "deleted": True,
                "deleted_at": self.deleted_at.isoformat() if self.deleted_at else None,
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
        return {
            "cat": {
                "name": self.cat_name,
                "birth_date": self.birth_date.isoformat() if self.birth_date else None,
                "adoption_date": self.adoption_date.isoformat() if self.adoption_date else None,
                "photo_ref": self.photo_ref,
            },
            "deleted": False,
            "deleted_at": None,
            "future_information_known": self.future_information_known,
            "responsibilities": [
                {
                    "id": item.id,
                    "title": item.title,
                    "category": item.category,
                    "due_at": item.due_at.isoformat() if item.due_at else None,
                    "state": item.state.value,
                    "completed_at": item.completed_at.isoformat() if item.completed_at else None,
                    "cancelled_at": item.cancelled_at.isoformat() if item.cancelled_at else None,
                    "recurrence_interval_days": item.recurrence.interval_days if item.recurrence else None,
                    "recurrence_calendar_months": (
                        item.recurrence.calendar_months if item.recurrence else None
                    ),
                    "action_key": item.action_key,
                }
                for item in sorted(self.responsibilities, key=lambda item: item.id)
            ],
            "notifications": [
                {
                    "responsibility_id": notification.responsibility_id,
                    "attempted_at": notification.attempted_at.isoformat(),
                    "outcome": notification.outcome.value,
                    "action_key": notification.action_key,
                    "provider": notification.provider,
                    "provider_message_id": notification.provider_message_id,
                }
                for notification in sorted(
                    self.notifications,
                    key=lambda item: (
                        item.attempted_at,
                        item.responsibility_id,
                        item.outcome.value,
                        item.provider_message_id or "",
                    ),
                )
            ],
            "notes": [
                {
                    "id": note.id,
                    "description": note.description,
                    "occurred_at": note.occurred_at.isoformat(),
                }
                for note in sorted(self.notes, key=lambda item: (item.occurred_at, item.id or ""))
            ],
            "direct_care": [
                {
                    "type": care.event_type,
                    "description": care.description,
                    "occurred_at": care.occurred_at.isoformat(),
                    "responsibility_id": care.responsibility_id,
                    "action_key": care.action_key,
                }
                for care in sorted(
                    self.direct_care,
                    key=lambda item: (item.occurred_at, item.event_type, item.description),
                )
            ],
            "triage_assessments": [
                {
                    "id": assessment.id,
                    "note_ids": list(assessment.note_ids),
                    "urgency": assessment.urgency.value,
                    "rationale": assessment.rationale,
                    "uncertainty": assessment.uncertainty,
                    "assessed_at": assessment.assessed_at.isoformat(),
                    "provider": assessment.provider,
                    "model_version": assessment.model_version,
                    "review_status": assessment.review_status.value,
                    "final_urgency": assessment.final_urgency.value if assessment.final_urgency else None,
                }
                for assessment in sorted(self.triage_assessments, key=lambda item: item.id)
            ],
            "veterinarian_reviews": [
                {
                    "assessment_id": review.assessment_id,
                    "reviewed_at": review.reviewed_at.isoformat(),
                    "veterinarian_id": review.veterinarian_id,
                    "decision": review.decision.value,
                    "final_urgency": review.final_urgency.value if review.final_urgency else None,
                    "rationale": review.rationale,
                }
                for review in sorted(
                    self.veterinarian_reviews,
                    key=lambda item: (item.reviewed_at, item.assessment_id),
                )
            ],
            "triage_information_requests": [
                {
                    "id": request.id,
                    "assessment_id": request.assessment_id,
                    "requested_at": request.requested_at.isoformat(),
                    "veterinarian_id": request.veterinarian_id,
                    "question": request.question,
                }
                for request in sorted(self.triage_information_requests, key=lambda item: item.id)
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
                for event in sorted(self.events, key=self._event_sort_key)
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
        receipt = DeletionReceipt(
            deleted_at,
            len(self.responsibilities),
            len(self.events),
            len(self.notifications),
            len(self.notes),
            len(self.direct_care),
            len(self.triage_assessments),
            len(self.veterinarian_reviews),
            len(self.triage_information_requests),
        )
        self.responsibilities.clear()
        self.notifications.clear()
        self.notes.clear()
        self.direct_care.clear()
        self.triage_assessments.clear()
        self.veterinarian_reviews.clear()
        self.triage_information_requests.clear()
        self.events.clear()
        self.cat_name = ""
        self.birth_date = None
        self.adoption_date = None
        self.photo_ref = None
        self.future_information_known = True
        self.deleted = True
        self.deleted_at = deleted_at
        return receipt
