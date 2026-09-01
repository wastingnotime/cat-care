package application

import (
	"context"
	"fmt"
	"strings"
	"time"

	"github.com/wastingnotime/cat-care/apps/api/internal/domain"
)

func (service *Service) UpdateProfile(ctx context.Context, profile domain.Profile) (domain.Profile, error) {
	service.mu.Lock()
	defer service.mu.Unlock()
	state, err := service.repository.Load(ctx)
	if err != nil {
		return domain.Profile{}, err
	}
	if err := service.ensureActive(state); err != nil {
		return domain.Profile{}, err
	}
	if err := domain.ValidateProfile(profile); err != nil {
		return domain.Profile{}, err
	}
	now := service.clock.Now()
	previous := state.Profile
	state.Profile = profile
	state.Events = append(state.Events, domain.Event{ID: service.ids.Next("event"), Type: "cat_profile_edited", OccurredAt: now, Description: "Cat profile updated", Details: map[string]any{"previous_name": previous.Name, "name": profile.Name}})
	return profile, service.repository.Save(ctx, state)
}

func (service *Service) CreateResponsibilityWithPolicy(ctx context.Context, title, category string, dueAt *time.Time, recurrenceDays, recurrenceMonths int) (domain.ResponsibilityView, error) {
	item, e := service.CreateResponsibility(ctx, title, category, dueAt)
	if e != nil {
		return domain.ResponsibilityView{}, e
	}
	if recurrenceDays == 0 && recurrenceMonths == 0 {
		return item, nil
	}
	return service.EditResponsibility(ctx, item.ID, title, category, dueAt, recurrenceDays, recurrenceMonths)
}

func (service *Service) EditResponsibility(ctx context.Context, id, title, category string, dueAt *time.Time, recurrenceDays, recurrenceMonths int) (domain.ResponsibilityView, error) {
	service.mu.Lock()
	defer service.mu.Unlock()
	state, err := service.repository.Load(ctx)
	if err != nil {
		return domain.ResponsibilityView{}, err
	}
	if err := service.ensureActive(state); err != nil {
		return domain.ResponsibilityView{}, err
	}
	if recurrenceDays < 0 || recurrenceMonths < 0 || (recurrenceDays > 0 && recurrenceMonths > 0) {
		return domain.ResponsibilityView{}, domain.ErrInvalidTransition
	}
	for index, item := range state.Responsibilities {
		if item.ID == id {
			if item.State != "planned" {
				return domain.ResponsibilityView{}, domain.ErrResponsibilityNotPlanned
			}
			updated, validationErr := domain.NewResponsibility(item.ID, title, category, dueAt, item.CreatedAt)
			if validationErr != nil {
				return domain.ResponsibilityView{}, validationErr
			}
			updated.RecurrenceDays = recurrenceDays
			updated.RecurrenceMonths = recurrenceMonths
			updated.ActionKey = item.ActionKey
			state.Responsibilities[index] = updated
			now := service.clock.Now()
			state.Events = append(state.Events, domain.Event{ID: service.ids.Next("event"), Type: "responsibility_edited", OccurredAt: now, Description: updated.Title, ResponsibilityID: id, Details: map[string]any{"previous_title": item.Title, "previous_due_at": item.DueAt, "due_at": dueAt}})
			return domain.ResponsibilityView{Responsibility: updated, DerivedState: domain.DerivedState(updated, now, 48*time.Hour)}, service.repository.Save(ctx, state)
		}
	}
	return domain.ResponsibilityView{}, domain.ErrResponsibilityNotFound
}

func (service *Service) CancelResponsibility(ctx context.Context, id string) (domain.ResponsibilityView, error) {
	service.mu.Lock()
	defer service.mu.Unlock()
	state, err := service.repository.Load(ctx)
	if err != nil {
		return domain.ResponsibilityView{}, err
	}
	if err := service.ensureActive(state); err != nil {
		return domain.ResponsibilityView{}, err
	}
	now := service.clock.Now()
	for i, item := range state.Responsibilities {
		if item.ID == id {
			cancelled, e := item.Cancel(now)
			if e != nil {
				return domain.ResponsibilityView{}, e
			}
			state.Responsibilities[i] = cancelled
			state.Events = append(state.Events, domain.Event{ID: service.ids.Next("event"), Type: "responsibility_cancelled", OccurredAt: now, Description: item.Title, ResponsibilityID: id, Details: map[string]any{}})
			return domain.ResponsibilityView{Responsibility: cancelled, DerivedState: "cancelled"}, service.repository.Save(ctx, state)
		}
	}
	return domain.ResponsibilityView{}, domain.ErrResponsibilityNotFound
}
func (service *Service) DeferResponsibility(ctx context.Context, id string, dueAt time.Time) (domain.ResponsibilityView, error) {
	service.mu.Lock()
	defer service.mu.Unlock()
	state, err := service.repository.Load(ctx)
	if err != nil {
		return domain.ResponsibilityView{}, err
	}
	if err := service.ensureActive(state); err != nil {
		return domain.ResponsibilityView{}, err
	}
	now := service.clock.Now()
	for i, item := range state.Responsibilities {
		if item.ID == id {
			updated, e := item.Defer(dueAt, now)
			if e != nil {
				return domain.ResponsibilityView{}, e
			}
			state.Responsibilities[i] = updated
			state.Events = append(state.Events, domain.Event{ID: service.ids.Next("event"), Type: "responsibility_deferred", OccurredAt: now, Description: item.Title, ResponsibilityID: id, Details: map[string]any{"previous_due_at": item.DueAt, "due_at": dueAt}})
			return domain.ResponsibilityView{Responsibility: updated, DerivedState: domain.DerivedState(updated, now, 48*time.Hour)}, service.repository.Save(ctx, state)
		}
	}
	return domain.ResponsibilityView{}, domain.ErrResponsibilityNotFound
}

func (service *Service) Notes(ctx context.Context) ([]domain.Note, error) {
	state, e := service.repository.Load(ctx)
	items := append([]domain.Note(nil), state.Notes...)
	reverse(items)
	return items, e
}
func (service *Service) RecordNote(ctx context.Context, description string) (domain.Note, error) {
	service.mu.Lock()
	defer service.mu.Unlock()
	state, e := service.repository.Load(ctx)
	if e != nil {
		return domain.Note{}, e
	}
	if e := service.ensureActive(state); e != nil {
		return domain.Note{}, e
	}
	description = strings.TrimSpace(description)
	if description == "" {
		return domain.Note{}, domain.ErrInvalidTransition
	}
	now := service.clock.Now()
	note := domain.Note{ID: service.ids.Next("note"), Description: description, OccurredAt: now, IsDiagnosis: false}
	state.Notes = append(state.Notes, note)
	state.Events = append(state.Events, domain.Event{ID: service.ids.Next("event"), Type: "note_recorded", OccurredAt: now, Description: description, Details: map[string]any{"note_id": note.ID}})
	return note, service.repository.Save(ctx, state)
}
func (service *Service) DirectCare(ctx context.Context) ([]domain.DirectCare, error) {
	state, e := service.repository.Load(ctx)
	items := append([]domain.DirectCare(nil), state.DirectCare...)
	reverse(items)
	return items, e
}
func (service *Service) RecordDirectCare(ctx context.Context, kind, description, responsibilityID string) (domain.DirectCare, error) {
	service.mu.Lock()
	defer service.mu.Unlock()
	state, e := service.repository.Load(ctx)
	if e != nil {
		return domain.DirectCare{}, e
	}
	if e := service.ensureActive(state); e != nil {
		return domain.DirectCare{}, e
	}
	kind = strings.TrimSpace(kind)
	description = strings.TrimSpace(description)
	if kind == "" || description == "" {
		return domain.DirectCare{}, domain.ErrInvalidTransition
	}
	if responsibilityID != "" && !hasResponsibility(state, responsibilityID) {
		return domain.DirectCare{}, domain.ErrResponsibilityNotFound
	}
	now := service.clock.Now()
	record := domain.DirectCare{ID: service.ids.Next("care"), Type: kind, Description: description, OccurredAt: now, ResponsibilityID: responsibilityID}
	state.DirectCare = append(state.DirectCare, record)
	state.Events = append(state.Events, domain.Event{ID: service.ids.Next("event"), Type: kind, OccurredAt: now, Description: description, ResponsibilityID: responsibilityID, Details: map[string]any{"care_id": record.ID}})
	return record, service.repository.Save(ctx, state)
}

func (service *Service) Notifications(ctx context.Context) ([]domain.Notification, error) {
	state, e := service.repository.Load(ctx)
	items := append([]domain.Notification(nil), state.Notifications...)
	reverse(items)
	return items, e
}
func (service *Service) RecordNotification(ctx context.Context, responsibilityID, outcome string) (domain.Notification, error) {
	service.mu.Lock()
	defer service.mu.Unlock()
	state, e := service.repository.Load(ctx)
	if e != nil {
		return domain.Notification{}, e
	}
	if e := service.ensureActive(state); e != nil {
		return domain.Notification{}, e
	}
	if !hasResponsibility(state, responsibilityID) {
		return domain.Notification{}, domain.ErrResponsibilityNotFound
	}
	if outcome != "delivered" && outcome != "failed" {
		return domain.Notification{}, domain.ErrInvalidTransition
	}
	now := service.clock.Now()
	record := domain.Notification{ID: service.ids.Next("notification"), ResponsibilityID: responsibilityID, Outcome: outcome, AttemptedAt: now, Provider: "local-fake"}
	state.Notifications = append(state.Notifications, record)
	state.Events = append(state.Events, domain.Event{ID: service.ids.Next("event"), Type: "notification_recorded", OccurredAt: now, Description: "Notification " + outcome, ResponsibilityID: responsibilityID, Details: map[string]any{"outcome": outcome, "provider": record.Provider}})
	return record, service.repository.Save(ctx, state)
}

func (service *Service) Triage(ctx context.Context) ([]domain.TriageAssessment, error) {
	state, e := service.repository.Load(ctx)
	return append([]domain.TriageAssessment(nil), state.TriageAssessments...), e
}
func (service *Service) VeterinarianReviews(ctx context.Context) ([]domain.VeterinarianReview, error) {
	state, e := service.repository.Load(ctx)
	items := append([]domain.VeterinarianReview(nil), state.VeterinarianReviews...)
	reverse(items)
	return items, e
}
func (service *Service) RequestTriage(ctx context.Context, noteIDs []string) (domain.TriageAssessment, error) {
	service.mu.Lock()
	defer service.mu.Unlock()
	state, e := service.repository.Load(ctx)
	if e != nil {
		return domain.TriageAssessment{}, e
	}
	if e := service.ensureActive(state); e != nil {
		return domain.TriageAssessment{}, e
	}
	if len(noteIDs) == 0 {
		return domain.TriageAssessment{}, domain.ErrInvalidTransition
	}
	for _, id := range noteIDs {
		found := false
		for _, note := range state.Notes {
			if note.ID == id {
				found = true
			}
		}
		if !found {
			return domain.TriageAssessment{}, domain.ErrRecordNotFound
		}
	}
	now := service.clock.Now()
	item := domain.TriageAssessment{ID: service.ids.Next("triage"), NoteIDs: noteIDs, Urgency: "needs_attention", Rationale: "The observation merits timely professional review.", Uncertainty: "No physical examination or vital signs are available.", Provider: "local-triage-fake", ModelVersion: "development-1", AssessedAt: now, ReviewStatus: "pending"}
	state.TriageAssessments = append(state.TriageAssessments, item)
	state.Events = append(state.Events, domain.Event{ID: service.ids.Next("event"), Type: "triage_assessed", OccurredAt: now, Description: "Provisional care triage", Details: map[string]any{"assessment_id": item.ID, "urgency": item.Urgency}})
	return item, service.repository.Save(ctx, state)
}
func (service *Service) ReviewTriage(ctx context.Context, id, veterinarianID, decision, finalUrgency, rationale string) (domain.VeterinarianReview, error) {
	service.mu.Lock()
	defer service.mu.Unlock()
	state, e := service.repository.Load(ctx)
	if e != nil {
		return domain.VeterinarianReview{}, e
	}
	if e := service.ensureActive(state); e != nil {
		return domain.VeterinarianReview{}, e
	}
	for i, item := range state.TriageAssessments {
		if item.ID == id {
			if item.ReviewStatus != "pending" || (decision != "accepted" && decision != "modified" && decision != "rejected") || (decision == "modified" && !domain.ValidUrgency(finalUrgency)) || (decision == "rejected" && finalUrgency != "") {
				return domain.VeterinarianReview{}, domain.ErrInvalidTransition
			}
			if decision == "accepted" {
				finalUrgency = item.Urgency
			}
			now := service.clock.Now()
			review := domain.VeterinarianReview{AssessmentID: id, VeterinarianID: veterinarianID, Decision: decision, FinalUrgency: finalUrgency, Rationale: rationale, ReviewedAt: now}
			item.ReviewStatus = decision
			item.FinalUrgency = finalUrgency
			state.TriageAssessments[i] = item
			state.VeterinarianReviews = append(state.VeterinarianReviews, review)
			state.Events = append(state.Events, domain.Event{ID: service.ids.Next("event"), Type: "triage_reviewed", OccurredAt: now, Description: "Veterinarian reviewed " + id, Details: map[string]any{"decision": decision, "final_urgency": finalUrgency}})
			return review, service.repository.Save(ctx, state)
		}
	}
	return domain.VeterinarianReview{}, domain.ErrRecordNotFound
}
func (service *Service) RequestTriageInformation(ctx context.Context, id, veterinarianID, question string) (domain.InformationRequest, error) {
	service.mu.Lock()
	defer service.mu.Unlock()
	state, e := service.repository.Load(ctx)
	if e != nil {
		return domain.InformationRequest{}, e
	}
	if e := service.ensureActive(state); e != nil {
		return domain.InformationRequest{}, e
	}
	pending := false
	for _, item := range state.TriageAssessments {
		if item.ID == id && item.ReviewStatus == "pending" {
			pending = true
		}
	}
	if !pending || strings.TrimSpace(question) == "" {
		return domain.InformationRequest{}, domain.ErrInvalidTransition
	}
	now := service.clock.Now()
	request := domain.InformationRequest{ID: service.ids.Next("information"), AssessmentID: id, VeterinarianID: veterinarianID, Question: question, RequestedAt: now}
	state.InformationRequests = append(state.InformationRequests, request)
	state.Events = append(state.Events, domain.Event{ID: service.ids.Next("event"), Type: "triage_information_requested", OccurredAt: now, Description: question, Details: map[string]any{"assessment_id": id}})
	return request, service.repository.Save(ctx, state)
}

func (service *Service) DefineTriageFollowUp(ctx context.Context, id, title, veterinarianID string, dueAt time.Time) (domain.ResponsibilityView, error) {
	service.mu.Lock()
	defer service.mu.Unlock()
	state, e := service.repository.Load(ctx)
	if e != nil {
		return domain.ResponsibilityView{}, e
	}
	reviewed := false
	for _, item := range state.TriageAssessments {
		if item.ID == id && (item.ReviewStatus == "accepted" || item.ReviewStatus == "modified") {
			reviewed = true
		}
	}
	if !reviewed || !dueAt.After(service.clock.Now()) {
		return domain.ResponsibilityView{}, domain.ErrInvalidTransition
	}
	now := service.clock.Now()
	item, e := domain.NewResponsibility(service.ids.Next("responsibility"), title, "veterinary", &dueAt, now)
	if e != nil {
		return domain.ResponsibilityView{}, e
	}
	item.ActionKey = "triage:" + id + ":follow-up"
	state.Responsibilities = append(state.Responsibilities, item)
	state.Events = append(state.Events, domain.Event{ID: service.ids.Next("event"), Type: "triage_follow_up_defined", OccurredAt: now, Description: title, ResponsibilityID: item.ID, Details: map[string]any{"assessment_id": id, "veterinarian_id": veterinarianID, "due_at": dueAt}})
	return domain.ResponsibilityView{Responsibility: item, DerivedState: domain.DerivedState(item, now, 48*time.Hour)}, service.repository.Save(ctx, state)
}

func (service *Service) Export(ctx context.Context) (State, error) {
	return service.repository.Load(ctx)
}
func (service *Service) Delete(ctx context.Context) (domain.DeletionReceipt, error) {
	service.mu.Lock()
	defer service.mu.Unlock()
	state, e := service.repository.Load(ctx)
	if e != nil {
		return domain.DeletionReceipt{}, e
	}
	if state.Deleted {
		return domain.DeletionReceipt{}, domain.ErrDataDeleted
	}
	now := service.clock.Now()
	receipt := domain.DeletionReceipt{DeletedAt: now, ResponsibilitiesRemoved: len(state.Responsibilities), EventsRemoved: len(state.Events), NotificationsRemoved: len(state.Notifications), NotesRemoved: len(state.Notes), DirectCareRemoved: len(state.DirectCare), TriageAssessmentsRemoved: len(state.TriageAssessments), VeterinarianReviewsRemoved: len(state.VeterinarianReviews), InformationRequestsRemoved: len(state.InformationRequests)}
	state = State{Profile: domain.Profile{}, Deleted: true, DeletedAt: &now}
	return receipt, service.repository.Save(ctx, state)
}

func hasResponsibility(state State, id string) bool {
	for _, item := range state.Responsibilities {
		if item.ID == id {
			return true
		}
	}
	return false
}
func nextDue(item domain.Responsibility) (*time.Time, bool) {
	if item.DueAt == nil {
		return nil, false
	}
	due := *item.DueAt
	if item.RecurrenceDays > 0 {
		due = due.AddDate(0, 0, item.RecurrenceDays)
		return &due, true
	}
	if item.RecurrenceMonths > 0 {
		due = addCalendarMonths(due, item.RecurrenceMonths)
		return &due, true
	}
	return nil, false
}
func recurrenceTitle(item domain.Responsibility) string { return fmt.Sprintf("%s", item.Title) }
func reverse[T any](items []T) {
	for left, right := 0, len(items)-1; left < right; left, right = left+1, right-1 {
		items[left], items[right] = items[right], items[left]
	}
}
func addCalendarMonths(value time.Time, months int) time.Time {
	target := int(value.Month()) - 1 + months
	year := value.Year() + target/12
	month := time.Month(target%12 + 1)
	last := time.Date(year, month+1, 0, value.Hour(), value.Minute(), value.Second(), value.Nanosecond(), value.Location()).Day()
	day := value.Day()
	if day > last {
		day = last
	}
	return time.Date(year, month, day, value.Hour(), value.Minute(), value.Second(), value.Nanosecond(), value.Location())
}
