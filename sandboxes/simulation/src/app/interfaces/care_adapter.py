from __future__ import annotations

from datetime import date, datetime, timedelta

from app.interfaces.integration_ports import (
    CatProfileData,
    CatProfileStore,
    NotificationGateway,
    TriageProvider,
)
from app.simulation.domain import (
    CatCareState,
    NotificationOutcome,
    RecurrencePolicy,
    Responsibility,
    TriageReviewStatus,
    TriageUrgency,
)


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

    def get_cat_profile(self) -> dict[str, object]:
        if self.state.deleted:
            raise ValueError("cat data has been deleted")
        return self.state.export_data()["cat"]

    def load_cat_profile(self, store: CatProfileStore, now: datetime) -> dict[str, object]:
        profile = store.load()
        event = self.state.edit_cat_profile(
            now,
            name=profile.name,
            birth_date=profile.birth_date,
            adoption_date=profile.adoption_date,
            photo_ref=profile.photo_ref,
            current_time=now,
        )
        return self._event_record(event)

    def save_cat_profile(self, store: CatProfileStore, now: datetime) -> dict[str, object]:
        profile = self.state.export_data()["cat"]
        store.save(
            CatProfileData(
                profile["name"],
                date.fromisoformat(profile["birth_date"]) if profile["birth_date"] else None,
                date.fromisoformat(profile["adoption_date"]) if profile["adoption_date"] else None,
                profile["photo_ref"],
            ),
            now,
        )
        return {"saved_at": now.isoformat(), "profile": profile}

    def edit_cat_profile(
        self,
        name: str,
        birth_date: date | None,
        adoption_date: date | None,
        photo_ref: str | None,
        now: datetime,
        *,
        current_time: datetime | None = None,
    ) -> dict[str, object]:
        return self._event_record(
            self.state.edit_cat_profile(
                now,
                name=name,
                birth_date=birth_date,
                adoption_date=adoption_date,
                photo_ref=photo_ref,
                current_time=current_time,
            )
        )

    def get_responsibilities(
        self,
        now: datetime,
        due_soon_threshold: timedelta,
    ) -> list[dict[str, object]]:
        if self.state.deleted:
            raise ValueError("cat data has been deleted")
        items = sorted(
            self.state.responsibilities,
            key=lambda item: (item.due_at is None, item.due_at or now, item.id),
        )
        return [
            {
                "id": item.id,
                "title": item.title,
                "category": item.category,
                "due_at": item.due_at.isoformat() if item.due_at else None,
                "state": item.derived_state(now, due_soon_threshold),
                "completed_at": item.completed_at.isoformat() if item.completed_at else None,
                "cancelled_at": item.cancelled_at.isoformat() if item.cancelled_at else None,
                "recurrence_interval_days": (
                    item.recurrence.interval_days if item.recurrence else None
                ),
                "action_key": item.action_key,
            }
            for item in items
        ]

    def create_responsibility(
        self,
        responsibility_id: str,
        title: str,
        category: str,
        due_at: datetime | None,
        now: datetime,
        *,
        recurrence_interval_days: int | None = None,
        action_key: str | None = None,
    ) -> dict[str, object]:
        responsibility = Responsibility(
            responsibility_id,
            title,
            due_at,
            category,
            recurrence=(
                RecurrencePolicy(recurrence_interval_days)
                if recurrence_interval_days is not None
                else None
            ),
            action_key=action_key,
        )
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

    def edit_responsibility(
        self,
        responsibility_id: str,
        title: str,
        category: str,
        due_at: datetime | None,
        now: datetime,
    ) -> dict[str, object]:
        return self._event_record(
            self.state.edit_responsibility(
                responsibility_id,
                now,
                title=title,
                category=category,
                due_at=due_at,
            )
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

    def record_care_event(
        self,
        event_type: str,
        description: str,
        occurred_at: datetime,
        *,
        current_time: datetime | None = None,
        responsibility_id: str | None = None,
    ) -> dict[str, object]:
        return self._event_record(
            self.state.record_care_event(
                event_type,
                description,
                occurred_at,
                current_time=current_time,
                responsibility_id=responsibility_id,
            )
        )

    def record_notification(
        self,
        responsibility_id: str,
        attempted_at: datetime,
        outcome: str | NotificationOutcome,
        *,
        current_time: datetime | None = None,
    ) -> dict[str, object]:
        if isinstance(outcome, str):
            try:
                outcome = NotificationOutcome(outcome)
            except ValueError as error:
                raise ValueError("unsupported notification outcome") from error
        return self._event_record(
            self.state.record_notification(
                responsibility_id,
                attempted_at,
                outcome,
                current_time=current_time,
            )
        )

    def deliver_notification(
        self,
        gateway: NotificationGateway,
        responsibility_id: str,
        attempted_at: datetime,
        *,
        current_time: datetime | None = None,
    ) -> dict[str, object]:
        responsibility = next(
            item for item in self.state.responsibilities if item.id == responsibility_id
        )
        delivery = gateway.deliver(responsibility.id, responsibility.title, responsibility.due_at)
        record = self.record_notification(
            responsibility_id,
            attempted_at,
            delivery.outcome,
            current_time=current_time,
        )
        record["details"].update(
            {
                "provider": delivery.provider,
                "provider_message_id": delivery.provider_message_id,
            }
        )
        return record

    def defer_responsibility(
        self,
        responsibility_id: str,
        now: datetime,
        new_due_at: datetime,
        *,
        current_time: datetime | None = None,
    ) -> dict[str, object]:
        return self._event_record(
            self.state.defer_responsibility(
                responsibility_id,
                now,
                new_due_at,
                current_time=current_time,
            )
        )

    def export_data(self) -> dict[str, object]:
        return self.state.export_data()

    def delete_data(
        self,
        deleted_at: datetime,
        *,
        current_time: datetime | None = None,
    ) -> dict[str, object]:
        receipt = self.state.delete_cat(deleted_at, current_time=current_time)
        return {
            "deleted_at": receipt.deleted_at.isoformat(),
            "responsibilities_removed": receipt.responsibilities_removed,
            "events_removed": receipt.events_removed,
            "notifications_removed": receipt.notifications_removed,
            "notes_removed": receipt.notes_removed,
            "direct_care_removed": receipt.direct_care_removed,
            "triage_assessments_removed": receipt.triage_assessments_removed,
            "veterinarian_reviews_removed": receipt.veterinarian_reviews_removed,
        }

    def get_timeline(self) -> list[dict[str, object]]:
        return [self._event_record(event) for event in self.state.timeline()]

    def get_timeline_summary(self) -> dict[str, object]:
        if self.state.deleted:
            raise ValueError("cat data has been deleted")
        timeline = self.state.timeline()
        newest = timeline[0] if timeline else None
        return {
            "event_count": len(timeline),
            "newest_event_type": newest.event_type if newest else None,
            "newest_event_at": newest.occurred_at.isoformat() if newest else None,
        }

    def request_triage(
        self,
        note_ids: list[str],
        urgency: str,
        rationale: str,
        uncertainty: str,
        assessed_at: datetime,
        provider: str,
        model_version: str,
        *,
        current_time: datetime | None = None,
    ) -> dict[str, object]:
        try:
            parsed_urgency = TriageUrgency(urgency)
        except ValueError as error:
            raise ValueError("unsupported triage urgency") from error
        assessment = self.state.request_triage(
            note_ids,
            parsed_urgency,
            rationale,
            uncertainty,
            assessed_at,
            provider,
            model_version,
            current_time=current_time,
        )
        return self._triage_record(assessment)

    def request_triage_from_provider(
        self,
        provider: TriageProvider,
        note_ids: list[str],
        assessed_at: datetime,
        *,
        current_time: datetime | None = None,
    ) -> dict[str, object]:
        notes = {note.id: note.description for note in self.state.notes}
        try:
            note_text = tuple(notes[note_id] for note_id in note_ids)
        except KeyError as error:
            raise ValueError("triage assessment references an unknown note") from error
        suggestion = provider.assess(tuple(note_ids), note_text)
        return self.request_triage(
            note_ids,
            suggestion.urgency.value,
            suggestion.rationale,
            suggestion.uncertainty,
            assessed_at,
            suggestion.provider,
            suggestion.model_version,
            current_time=current_time,
        )

    def review_triage(
        self,
        assessment_id: str,
        reviewed_at: datetime,
        veterinarian_id: str,
        decision: str,
        final_urgency: str | None,
        rationale: str,
        *,
        current_time: datetime | None = None,
    ) -> dict[str, object]:
        try:
            parsed_decision = TriageReviewStatus(decision)
        except ValueError as error:
            raise ValueError("unsupported triage review decision") from error
        parsed_urgency = TriageUrgency(final_urgency) if final_urgency else None
        review = self.state.review_triage(
            assessment_id,
            reviewed_at,
            veterinarian_id,
            parsed_decision,
            parsed_urgency,
            rationale,
            current_time=current_time,
        )
        return {
            "assessment_id": review.assessment_id,
            "reviewed_at": review.reviewed_at.isoformat(),
            "veterinarian_id": review.veterinarian_id,
            "decision": review.decision.value,
            "final_urgency": review.final_urgency.value if review.final_urgency else None,
            "rationale": review.rationale,
        }

    def get_triage_assessments(self) -> list[dict[str, object]]:
        if self.state.deleted:
            raise ValueError("cat data has been deleted")
        return [self._triage_record(assessment) for assessment in self.state.triage_assessments]

    def get_veterinarian_reviews(self) -> list[dict[str, object]]:
        if self.state.deleted:
            raise ValueError("cat data has been deleted")
        return [
            {
                "assessment_id": review.assessment_id,
                "reviewed_at": review.reviewed_at.isoformat(),
                "veterinarian_id": review.veterinarian_id,
                "decision": review.decision.value,
                "final_urgency": review.final_urgency.value if review.final_urgency else None,
                "rationale": review.rationale,
            }
            for review in self.state.veterinarian_reviews
        ]

    @staticmethod
    def _triage_record(assessment: object) -> dict[str, object]:
        return {
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

    def get_notifications(self) -> list[dict[str, object]]:
        if self.state.deleted:
            raise ValueError("cat data has been deleted")
        return [
            self._notification_record(notification)
            for notification in sorted(
                self.state.notifications,
                key=lambda item: item.attempted_at,
                reverse=True,
            )
        ]

    def get_notes(self) -> list[dict[str, object]]:
        if self.state.deleted:
            raise ValueError("cat data has been deleted")
        return [
            {
                "id": note.id,
                "type": "note_recorded",
                "occurred_at": note.occurred_at.isoformat(),
                "description": note.description,
                "responsibility_id": None,
                "action_key": None,
                "details": {},
            }
            for note in sorted(self.state.notes, key=lambda item: item.occurred_at, reverse=True)
        ]

    def get_care_events(self) -> list[dict[str, object]]:
        if self.state.deleted:
            raise ValueError("cat data has been deleted")
        return [
            {
                "type": care.event_type,
                "occurred_at": care.occurred_at.isoformat(),
                "description": care.description,
                "responsibility_id": care.responsibility_id,
                "action_key": care.action_key,
                "details": {},
            }
            for care in sorted(self.state.direct_care, key=lambda item: item.occurred_at, reverse=True)
        ]

    def _notification_record(self, notification: object) -> dict[str, object]:
        responsibility = next(
            item
            for item in self.state.responsibilities
            if item.id == notification.responsibility_id
        )
        return {
            "type": "notification_recorded",
            "occurred_at": notification.attempted_at.isoformat(),
            "description": f"notification for {responsibility.title}",
            "responsibility_id": notification.responsibility_id,
            "action_key": notification.action_key,
            "details": {"outcome": notification.outcome.value},
        }

    @staticmethod
    def _event_record(event: object) -> dict[str, object]:
        return {
            "type": event.event_type,
            "occurred_at": event.occurred_at.isoformat(),
            "description": event.description,
            "responsibility_id": event.responsibility_id,
            "action_key": event.action_key,
            "details": dict(event.details),
        }
