package infrastructure

import (
	"context"
	"fmt"
	"sort"
	"sync"
	"time"

	"github.com/wastingnotime/cat-care/apps/api/internal/application"
	"github.com/wastingnotime/cat-care/apps/api/internal/domain"
)

type MemoryRepository struct {
	mu      sync.RWMutex
	state   application.State
	states  map[string]application.State
	owners  map[string]string
	nextCat int
}

func NewMemoryRepository(catName string) *MemoryRepository {
	return &MemoryRepository{state: application.State{Profile: domain.Profile{Name: catName}, Responsibilities: []domain.Responsibility{}, Events: []domain.Event{}, Notes: []domain.Note{}, DirectCare: []domain.DirectCare{}, Notifications: []domain.Notification{}, TriageAssessments: []domain.TriageAssessment{}, VeterinarianReviews: []domain.VeterinarianReview{}, InformationRequests: []domain.InformationRequest{}}}
}

func NewMultiCatMemoryRepository(ownerID, catName string) *MemoryRepository {
	state := emptyState(domain.Profile{Name: catName})
	return &MemoryRepository{states: map[string]application.State{"cat-1": state}, owners: map[string]string{"cat-1": ownerID}, nextCat: 1}
}

func emptyState(profile domain.Profile) application.State {
	return application.State{Profile: profile, Responsibilities: []domain.Responsibility{}, Events: []domain.Event{}, Notes: []domain.Note{}, DirectCare: []domain.DirectCare{}, Notifications: []domain.Notification{}, TriageAssessments: []domain.TriageAssessment{}, VeterinarianReviews: []domain.VeterinarianReview{}, InformationRequests: []domain.InformationRequest{}}
}
func (repository *MemoryRepository) Load(ctx context.Context) (application.State, error) {
	return repository.loadFor(application.CatFromContext(ctx))
}
func (repository *MemoryRepository) loadFor(catID string) (application.State, error) {
	repository.mu.RLock()
	defer repository.mu.RUnlock()
	state := repository.state
	if repository.states != nil {
		state = repository.states[catID]
	}
	state.Responsibilities = append([]domain.Responsibility(nil), state.Responsibilities...)
	state.Events = append([]domain.Event(nil), state.Events...)
	state.Notes = append([]domain.Note(nil), state.Notes...)
	state.DirectCare = append([]domain.DirectCare(nil), state.DirectCare...)
	state.Notifications = append([]domain.Notification(nil), state.Notifications...)
	state.TriageAssessments = append([]domain.TriageAssessment(nil), state.TriageAssessments...)
	state.VeterinarianReviews = append([]domain.VeterinarianReview(nil), state.VeterinarianReviews...)
	state.InformationRequests = append([]domain.InformationRequest(nil), state.InformationRequests...)
	return state, nil
}
func (repository *MemoryRepository) Save(ctx context.Context, state application.State) error {
	repository.mu.Lock()
	defer repository.mu.Unlock()
	if repository.states != nil {
		repository.states[application.CatFromContext(ctx)] = state
		return nil
	}
	repository.state = state
	return nil
}

func (repository *MemoryRepository) Cats(_ context.Context, ownerID string, all bool) ([]application.Cat, error) {
	repository.mu.RLock()
	defer repository.mu.RUnlock()
	items := []application.Cat{}
	for id, state := range repository.states {
		if all || repository.owners[id] == ownerID {
			items = append(items, application.Cat{ID: id, OwnerID: repository.owners[id], Profile: state.Profile})
		}
	}
	sort.Slice(items, func(i, j int) bool { return items[i].ID < items[j].ID })
	return items, nil
}
func (repository *MemoryRepository) CreateCat(_ context.Context, ownerID string, profile domain.Profile) (application.Cat, error) {
	repository.mu.Lock()
	defer repository.mu.Unlock()
	repository.nextCat++
	id := fmt.Sprintf("cat-%d", repository.nextCat)
	repository.states[id] = emptyState(profile)
	repository.owners[id] = ownerID
	return application.Cat{ID: id, OwnerID: ownerID, Profile: profile}, nil
}

type SystemClock struct{}

func (SystemClock) Now() time.Time { return time.Now().UTC() }

type SequenceIDs struct {
	mu   sync.Mutex
	next int
}

func (ids *SequenceIDs) Next(kind string) string {
	ids.mu.Lock()
	defer ids.mu.Unlock()
	ids.next++
	return fmt.Sprintf("%s-%d", kind, ids.next)
}
