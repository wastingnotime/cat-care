package infrastructure

import (
	"context"
	"fmt"
	"sync"
	"time"

	"github.com/wastingnotime/cat-care/apps/api/internal/application"
	"github.com/wastingnotime/cat-care/apps/api/internal/domain"
)

type MemoryRepository struct {
	mu    sync.RWMutex
	state application.State
}

func NewMemoryRepository(catName string) *MemoryRepository {
	return &MemoryRepository{state: application.State{Profile: domain.Profile{Name: catName}, Responsibilities: []domain.Responsibility{}, Events: []domain.Event{}, Notes: []domain.Note{}, DirectCare: []domain.DirectCare{}, Notifications: []domain.Notification{}, TriageAssessments: []domain.TriageAssessment{}, VeterinarianReviews: []domain.VeterinarianReview{}, InformationRequests: []domain.InformationRequest{}}}
}
func (repository *MemoryRepository) Load(_ context.Context) (application.State, error) {
	repository.mu.RLock()
	defer repository.mu.RUnlock()
	state := repository.state
	state.Responsibilities = append(state.Responsibilities[:0:0], repository.state.Responsibilities...)
	state.Events = append(state.Events[:0:0], repository.state.Events...)
	state.Notes = append(state.Notes[:0:0], repository.state.Notes...)
	state.DirectCare = append(state.DirectCare[:0:0], repository.state.DirectCare...)
	state.Notifications = append(state.Notifications[:0:0], repository.state.Notifications...)
	state.TriageAssessments = append(state.TriageAssessments[:0:0], repository.state.TriageAssessments...)
	state.VeterinarianReviews = append(state.VeterinarianReviews[:0:0], repository.state.VeterinarianReviews...)
	state.InformationRequests = append(state.InformationRequests[:0:0], repository.state.InformationRequests...)
	return state, nil
}
func (repository *MemoryRepository) Save(_ context.Context, state application.State) error {
	repository.mu.Lock()
	defer repository.mu.Unlock()
	repository.state = state
	return nil
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
