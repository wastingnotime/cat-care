package httpapi_test

import (
	"bytes"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/wastingnotime/cat-care/apps/api/internal/application"
	"github.com/wastingnotime/cat-care/apps/api/internal/httpapi"
	"github.com/wastingnotime/cat-care/apps/api/internal/infrastructure"
)

type fixedClock struct{ now time.Time }

func (clock fixedClock) Now() time.Time { return clock.now }

func testHandler() http.Handler {
	repository := infrastructure.NewMemoryRepository("Mimi")
	service := application.NewService(repository, fixedClock{time.Date(2026, 9, 1, 12, 0, 0, 0, time.UTC)}, &infrastructure.SequenceIDs{})
	return httpapi.NewServer(service).Handler()
}

func request(t *testing.T, handler http.Handler, method, path string, body any) *httptest.ResponseRecorder {
	t.Helper()
	var content []byte
	if body != nil {
		var err error
		content, err = json.Marshal(body)
		if err != nil {
			t.Fatal(err)
		}
	}
	recorder := httptest.NewRecorder()
	handler.ServeHTTP(recorder, httptest.NewRequest(method, path, bytes.NewReader(content)))
	return recorder
}

func TestCreateCompleteAndTimelineContract(t *testing.T) {
	handler := testHandler()
	created := request(t, handler, http.MethodPost, "/v1/responsibilities", map[string]any{"title": "Annual exam", "category": "veterinary", "due_at": nil})
	if created.Code != http.StatusCreated {
		t.Fatalf("create status %d: %s", created.Code, created.Body.String())
	}
	var responsibility map[string]any
	if err := json.Unmarshal(created.Body.Bytes(), &responsibility); err != nil {
		t.Fatal(err)
	}
	status := request(t, handler, http.MethodGet, "/v1/status", nil)
	if !bytes.Contains(status.Body.Bytes(), []byte(`"kind":"unknown"`)) {
		t.Fatalf("unexpected status: %s", status.Body.String())
	}
	completed := request(t, handler, http.MethodPost, "/v1/responsibilities/"+responsibility["id"].(string)+"/complete", nil)
	if completed.Code != http.StatusOK || !bytes.Contains(completed.Body.Bytes(), []byte(`"state":"completed"`)) {
		t.Fatalf("unexpected completion: %d %s", completed.Code, completed.Body.String())
	}
	if status := request(t, handler, http.MethodGet, "/v1/status", nil); !bytes.Contains(status.Body.Bytes(), []byte(`"kind":"clear"`)) {
		t.Fatalf("unexpected final status: %s", status.Body.String())
	}
	if timeline := request(t, handler, http.MethodGet, "/v1/timeline", nil); timeline.Code != http.StatusOK || !bytes.Contains(timeline.Body.Bytes(), []byte("responsibility_completed")) {
		t.Fatalf("unexpected timeline: %d %s", timeline.Code, timeline.Body.String())
	}
}

func TestInvalidPayloadAndTransitionsHaveStableErrors(t *testing.T) {
	handler := testHandler()
	invalid := request(t, handler, http.MethodPost, "/v1/responsibilities", map[string]string{"title": " ", "category": "veterinary"})
	if invalid.Code != http.StatusBadRequest || !bytes.Contains(invalid.Body.Bytes(), []byte(`"code":"invalid_request"`)) {
		t.Fatalf("unexpected invalid response: %d %s", invalid.Code, invalid.Body.String())
	}
	missing := request(t, handler, http.MethodPost, "/v1/responsibilities/missing/complete", nil)
	if missing.Code != http.StatusNotFound || !bytes.Contains(missing.Body.Bytes(), []byte(`"code":"not_found"`)) {
		t.Fatalf("unexpected missing response: %d %s", missing.Code, missing.Body.String())
	}
}

func TestRemainingReleasedSlicesThroughHTTP(t *testing.T) {
	handler := testHandler()
	profile := request(t, handler, http.MethodPut, "/v1/cat", map[string]any{"name": "Mimi", "birth_date": "2021-05-01", "adoption_date": "2021-07-10", "photo_ref": "mimi.jpg"})
	if profile.Code != http.StatusOK {
		t.Fatalf("profile: %d %s", profile.Code, profile.Body.String())
	}
	created := request(t, handler, http.MethodPost, "/v1/responsibilities", map[string]any{"title": "Vaccination", "category": "preventive", "due_at": "2026-09-03T12:00:00Z", "recurrence_days": 30})
	var responsibility map[string]any
	_ = json.Unmarshal(created.Body.Bytes(), &responsibility)
	id := responsibility["id"].(string)
	deferred := request(t, handler, http.MethodPost, "/v1/responsibilities/"+id+"/defer", map[string]any{"due_at": "2026-10-04T12:00:00Z"})
	if deferred.Code != http.StatusOK {
		t.Fatalf("defer: %d %s", deferred.Code, deferred.Body.String())
	}
	notification := request(t, handler, http.MethodPost, "/v1/notifications", map[string]any{"responsibility_id": id, "outcome": "failed"})
	if notification.Code != http.StatusCreated || !bytes.Contains(notification.Body.Bytes(), []byte(`"outcome":"failed"`)) {
		t.Fatalf("notification: %d %s", notification.Code, notification.Body.String())
	}
	noteResponse := request(t, handler, http.MethodPost, "/v1/notes", map[string]string{"description": "Eating less than usual"})
	var note map[string]any
	_ = json.Unmarshal(noteResponse.Body.Bytes(), &note)
	triageResponse := request(t, handler, http.MethodPost, "/v1/triage", map[string]any{"note_ids": []string{note["id"].(string)}})
	var triage map[string]any
	_ = json.Unmarshal(triageResponse.Body.Bytes(), &triage)
	assessmentID := triage["id"].(string)
	if triage["review_status"] != "pending" {
		t.Fatalf("triage must remain provisional: %s", triageResponse.Body.String())
	}
	review := request(t, handler, http.MethodPost, "/v1/triage/"+assessmentID+"/review", map[string]string{"veterinarian_id": "vet-local", "decision": "modified", "final_urgency": "urgent", "rationale": "Prompt examination is appropriate."})
	if review.Code != http.StatusOK || !bytes.Contains(review.Body.Bytes(), []byte(`"decision":"modified"`)) {
		t.Fatalf("review: %d %s", review.Code, review.Body.String())
	}
	followUp := request(t, handler, http.MethodPost, "/v1/triage/"+assessmentID+"/follow-up", map[string]any{"veterinarian_id": "vet-local", "title": "Veterinarian follow-up", "due_at": "2026-09-04T12:00:00Z"})
	if followUp.Code != http.StatusCreated {
		t.Fatalf("follow up: %d %s", followUp.Code, followUp.Body.String())
	}
	exported := request(t, handler, http.MethodGet, "/v1/export", nil)
	if exported.Code != http.StatusOK || !bytes.Contains(exported.Body.Bytes(), []byte("Eating less than usual")) {
		t.Fatalf("export: %d %s", exported.Code, exported.Body.String())
	}
	deleted := request(t, handler, http.MethodDelete, "/v1/data", nil)
	if deleted.Code != http.StatusOK || !bytes.Contains(deleted.Body.Bytes(), []byte(`"notifications_removed":1`)) {
		t.Fatalf("delete: %d %s", deleted.Code, deleted.Body.String())
	}
	rejected := request(t, handler, http.MethodPost, "/v1/notes", map[string]string{"description": "after delete"})
	if rejected.Code != http.StatusConflict {
		t.Fatalf("post deletion mutation: %d %s", rejected.Code, rejected.Body.String())
	}
}
