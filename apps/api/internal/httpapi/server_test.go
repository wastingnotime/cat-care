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
