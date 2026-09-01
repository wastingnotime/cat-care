package domain

import (
	"strings"
	"time"
)

type Profile struct {
	Name         string  `json:"name"`
	BirthDate    *string `json:"birth_date"`
	AdoptionDate *string `json:"adoption_date"`
	PhotoRef     string  `json:"photo_ref,omitempty"`
}
type Note struct {
	ID          string    `json:"id"`
	Description string    `json:"description"`
	OccurredAt  time.Time `json:"occurred_at"`
	IsDiagnosis bool      `json:"is_diagnosis"`
}
type DirectCare struct {
	ID               string    `json:"id"`
	Type             string    `json:"type"`
	Description      string    `json:"description"`
	OccurredAt       time.Time `json:"occurred_at"`
	ResponsibilityID string    `json:"responsibility_id,omitempty"`
}
type Notification struct {
	ID               string    `json:"id"`
	ResponsibilityID string    `json:"responsibility_id"`
	Outcome          string    `json:"outcome"`
	AttemptedAt      time.Time `json:"attempted_at"`
	Provider         string    `json:"provider,omitempty"`
}
type TriageAssessment struct {
	ID           string    `json:"id"`
	NoteIDs      []string  `json:"note_ids"`
	Urgency      string    `json:"urgency"`
	Rationale    string    `json:"rationale"`
	Uncertainty  string    `json:"uncertainty"`
	Provider     string    `json:"provider"`
	ModelVersion string    `json:"model_version"`
	AssessedAt   time.Time `json:"assessed_at"`
	ReviewStatus string    `json:"review_status"`
	FinalUrgency string    `json:"final_urgency,omitempty"`
}
type VeterinarianReview struct {
	AssessmentID   string    `json:"assessment_id"`
	VeterinarianID string    `json:"veterinarian_id"`
	Decision       string    `json:"decision"`
	FinalUrgency   string    `json:"final_urgency,omitempty"`
	Rationale      string    `json:"rationale"`
	ReviewedAt     time.Time `json:"reviewed_at"`
}
type InformationRequest struct {
	ID             string    `json:"id"`
	AssessmentID   string    `json:"assessment_id"`
	VeterinarianID string    `json:"veterinarian_id"`
	Question       string    `json:"question"`
	RequestedAt    time.Time `json:"requested_at"`
}
type DeletionReceipt struct {
	DeletedAt                  time.Time `json:"deleted_at"`
	ResponsibilitiesRemoved    int       `json:"responsibilities_removed"`
	EventsRemoved              int       `json:"events_removed"`
	NotificationsRemoved       int       `json:"notifications_removed"`
	NotesRemoved               int       `json:"notes_removed"`
	DirectCareRemoved          int       `json:"direct_care_removed"`
	TriageAssessmentsRemoved   int       `json:"triage_assessments_removed"`
	VeterinarianReviewsRemoved int       `json:"veterinarian_reviews_removed"`
	InformationRequestsRemoved int       `json:"information_requests_removed"`
}

func ValidateProfile(profile Profile) error {
	if strings.TrimSpace(profile.Name) == "" {
		return ErrInvalidTransition
	}
	for _, value := range []*string{profile.BirthDate, profile.AdoptionDate} {
		if value != nil {
			if _, err := time.Parse("2006-01-02", *value); err != nil {
				return ErrInvalidTransition
			}
		}
	}
	if profile.BirthDate != nil && profile.AdoptionDate != nil && *profile.AdoptionDate < *profile.BirthDate {
		return ErrInvalidTransition
	}
	return nil
}
func ValidUrgency(value string) bool {
	return value == "urgent" || value == "needs_attention" || value == "monitor" || value == "insufficient_information"
}
