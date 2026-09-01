package application

import (
	"context"
	"sync"
	"time"

	"github.com/wastingnotime/cat-care/apps/api/internal/domain"
)

type State struct {
	CatName          string
	Responsibilities []domain.Responsibility
	Events           []domain.Event
}

type Repository interface {
	Load(context.Context) (State, error)
	Save(context.Context, State) error
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

func (service *Service) Cat(ctx context.Context) (string, error) {
	state, err := service.repository.Load(ctx)
	return state.CatName, err
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
	for index, responsibility := range state.Responsibilities {
		if responsibility.ID != id {
			continue
		}
		completed, completeErr := responsibility.Complete(now)
		if completeErr != nil {
			return domain.ResponsibilityView{}, completeErr
		}
		state.Responsibilities[index] = completed
		state.Events = append(state.Events, domain.Event{ID: service.ids.Next("event"), Type: "responsibility_completed", OccurredAt: now, Description: completed.Title, ResponsibilityID: completed.ID, Details: map[string]any{}})
		if err := service.repository.Save(ctx, state); err != nil {
			return domain.ResponsibilityView{}, err
		}
		return domain.ResponsibilityView{Responsibility: completed, DerivedState: "completed"}, nil
	}
	return domain.ResponsibilityView{}, domain.ErrResponsibilityNotFound
}
