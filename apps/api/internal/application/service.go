package application

import (
	"context"
	"sync"
	"time"

	"github.com/wastingnotime/cat-care/apps/api/internal/domain"
)

type State struct {
	Profile             domain.Profile              `json:"cat"`
	Responsibilities    []domain.Responsibility     `json:"responsibilities"`
	Events              []domain.Event              `json:"events"`
	Notes               []domain.Note               `json:"notes"`
	DirectCare          []domain.DirectCare         `json:"direct_care"`
	Notifications       []domain.Notification       `json:"notifications"`
	TriageAssessments   []domain.TriageAssessment   `json:"triage_assessments"`
	VeterinarianReviews []domain.VeterinarianReview `json:"veterinarian_reviews"`
	InformationRequests []domain.InformationRequest `json:"information_requests"`
	Deleted             bool                        `json:"deleted"`
	DeletedAt           *time.Time                  `json:"deleted_at"`
}

type Repository interface {
	Load(context.Context) (State, error)
	Save(context.Context, State) error
}

type Cat struct {
	ID      string         `json:"id"`
	OwnerID string         `json:"owner_id"`
	Profile domain.Profile `json:"profile"`
}

type CatRepository interface {
	Repository
	Cats(context.Context, string, bool) ([]Cat, error)
	CreateCat(context.Context, string, domain.Profile) (Cat, error)
}

type catContextKey struct{}

func WithCat(ctx context.Context, catID string) context.Context {
	return context.WithValue(ctx, catContextKey{}, catID)
}

func CatFromContext(ctx context.Context) string {
	value, _ := ctx.Value(catContextKey{}).(string)
	return value
}

type Clock interface{ Now() time.Time }
type IDs interface{ Next(string) string }

type Service struct {
	repository Repository
	clock      Clock
	ids        IDs
	mu         sync.Mutex
}

func NewService(repository Repository, clock Clock, ids IDs) *Service {
	return &Service{repository: repository, clock: clock, ids: ids}
}

func (service *Service) Cats(ctx context.Context, ownerID string, all bool) ([]Cat, error) {
	repository, ok := service.repository.(CatRepository)
	if !ok {
		return nil, domain.ErrInvalidTransition
	}
	return repository.Cats(ctx, ownerID, all)
}

func (service *Service) CreateCat(ctx context.Context, ownerID string, profile domain.Profile) (Cat, error) {
	if err := domain.ValidateProfile(profile); err != nil {
		return Cat{}, err
	}
	repository, ok := service.repository.(CatRepository)
	if !ok {
		return Cat{}, domain.ErrInvalidTransition
	}
	return repository.CreateCat(ctx, ownerID, profile)
}

func (service *Service) Cat(ctx context.Context) (string, error) {
	state, err := service.repository.Load(ctx)
	return state.Profile.Name, err
}

func (service *Service) Profile(ctx context.Context) (domain.Profile, error) {
	state, err := service.repository.Load(ctx)
	return state.Profile, err
}

func (service *Service) ensureActive(state State) error {
	if state.Deleted {
		return domain.ErrDataDeleted
	}
	return nil
}

func (service *Service) Responsibilities(ctx context.Context) ([]domain.ResponsibilityView, error) {
	state, err := service.repository.Load(ctx)
	if err != nil {
		return nil, err
	}
	return domain.Views(state.Responsibilities, service.clock.Now(), 48*time.Hour), nil
}

func (service *Service) Status(ctx context.Context, dueSoonDays int) (domain.Status, error) {
	state, err := service.repository.Load(ctx)
	if err != nil {
		return domain.Status{}, err
	}
	return domain.DeriveStatus(state.Responsibilities, service.clock.Now(), dueSoonDays), nil
}

func (service *Service) Timeline(ctx context.Context) ([]domain.Event, error) {
	state, err := service.repository.Load(ctx)
	if err != nil {
		return nil, err
	}
	return domain.SortEvents(state.Events), nil
}

func (service *Service) CreateResponsibility(ctx context.Context, title, category string, dueAt *time.Time) (domain.ResponsibilityView, error) {
	service.mu.Lock()
	defer service.mu.Unlock()
	state, err := service.repository.Load(ctx)
	if err != nil {
		return domain.ResponsibilityView{}, err
	}
	now := service.clock.Now()
	if err := service.ensureActive(state); err != nil {
		return domain.ResponsibilityView{}, err
	}
	responsibility, err := domain.NewResponsibility(service.ids.Next("responsibility"), title, category, dueAt, now)
	if err != nil {
		return domain.ResponsibilityView{}, err
	}
	state.Responsibilities = append(state.Responsibilities, responsibility)
	state.Events = append(state.Events, domain.Event{ID: service.ids.Next("event"), Type: "responsibility_created", OccurredAt: now, Description: responsibility.Title, ResponsibilityID: responsibility.ID, Details: map[string]any{"category": responsibility.Category, "due_at": responsibility.DueAt}})
	if err := service.repository.Save(ctx, state); err != nil {
		return domain.ResponsibilityView{}, err
	}
	return domain.ResponsibilityView{Responsibility: responsibility, DerivedState: domain.DerivedState(responsibility, now, 48*time.Hour)}, nil
}

func (service *Service) CompleteResponsibility(ctx context.Context, id string) (domain.ResponsibilityView, error) {
	service.mu.Lock()
	defer service.mu.Unlock()
	state, err := service.repository.Load(ctx)
	if err != nil {
		return domain.ResponsibilityView{}, err
	}
	now := service.clock.Now()
	if err := service.ensureActive(state); err != nil {
		return domain.ResponsibilityView{}, err
	}
	for index, responsibility := range state.Responsibilities {
		if responsibility.ID != id {
			continue
		}
		completed, completeErr := responsibility.Complete(now)
		if completeErr != nil {
			return domain.ResponsibilityView{}, completeErr
		}
		state.Responsibilities[index] = completed
		details := map[string]any{}
		if dueAt, recurring := nextDue(completed); recurring {
			next, createErr := domain.NewResponsibility(service.ids.Next("responsibility"), recurrenceTitle(completed), completed.Category, dueAt, now)
			if createErr != nil {
				return domain.ResponsibilityView{}, createErr
			}
			next.RecurrenceDays = completed.RecurrenceDays
			next.RecurrenceMonths = completed.RecurrenceMonths
			next.ActionKey = completed.ActionKey
			state.Responsibilities = append(state.Responsibilities, next)
			details["next_responsibility_id"] = next.ID
			details["next_due_at"] = dueAt
		}
		state.Events = append(state.Events, domain.Event{ID: service.ids.Next("event"), Type: "responsibility_completed", OccurredAt: now, Description: completed.Title, ResponsibilityID: completed.ID, Details: details})
		if err := service.repository.Save(ctx, state); err != nil {
			return domain.ResponsibilityView{}, err
		}
		return domain.ResponsibilityView{Responsibility: completed, DerivedState: "completed"}, nil
	}
	return domain.ResponsibilityView{}, domain.ErrResponsibilityNotFound
}
