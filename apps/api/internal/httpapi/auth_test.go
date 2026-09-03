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

func authenticatedHandler() http.Handler {
	repository := infrastructure.NewMultiCatMemoryRepository("owner-local", "Mimi")
	service := application.NewService(repository, fixedClock{time.Date(2026, 9, 1, 12, 0, 0, 0, time.UTC)}, &infrastructure.SequenceIDs{})
	return httpapi.NewLocalServer(service).Handler()
}

func authRequest(t *testing.T, handler http.Handler, cookie *http.Cookie, method, path string, body any) *httptest.ResponseRecorder {
	t.Helper()
	var content []byte
	if body != nil {
		var err error
		content, err = json.Marshal(body)
		if err != nil {
			t.Fatal(err)
		}
	}
	req := httptest.NewRequest(method, path, bytes.NewReader(content))
	if cookie != nil {
		req.AddCookie(cookie)
	}
	recorder := httptest.NewRecorder()
	handler.ServeHTTP(recorder, req)
	return recorder
}

func login(t *testing.T, handler http.Handler, email, password string) *http.Cookie {
	t.Helper()
	response := authRequest(t, handler, nil, http.MethodPost, "/v1/session", map[string]string{"email": email, "password": password})
	if response.Code != http.StatusOK || len(response.Result().Cookies()) != 1 {
		t.Fatalf("login: %d %s", response.Code, response.Body.String())
	}
	return response.Result().Cookies()[0]
}

func TestAuthenticationCatIsolationAndModes(t *testing.T) {
	handler := authenticatedHandler()
	if response := authRequest(t, handler, nil, http.MethodGet, "/v1/cat", nil); response.Code != http.StatusUnauthorized {
		t.Fatalf("anonymous status: %d", response.Code)
	}
	owner := login(t, handler, "owner@cat.care", "owner")
	created := authRequest(t, handler, owner, http.MethodPost, "/v1/cats", map[string]any{"name": "Nina", "birth_date": nil, "adoption_date": nil})
	if created.Code != http.StatusCreated {
		t.Fatalf("create cat: %d %s", created.Code, created.Body.String())
	}
	var cat application.Cat
	if err := json.Unmarshal(created.Body.Bytes(), &cat); err != nil {
		t.Fatal(err)
	}
	if selected := authRequest(t, handler, owner, http.MethodPost, "/v1/cats/"+cat.ID+"/select", nil); selected.Code != http.StatusOK {
		t.Fatalf("select: %d %s", selected.Code, selected.Body.String())
	}
	if profile := authRequest(t, handler, owner, http.MethodGet, "/v1/cat", nil); !bytes.Contains(profile.Body.Bytes(), []byte(`"name":"Nina"`)) {
		t.Fatalf("selected profile: %s", profile.Body.String())
	}
	if review := authRequest(t, handler, owner, http.MethodPost, "/v1/triage/missing/review", map[string]string{"decision": "accepted"}); review.Code != http.StatusForbidden {
		t.Fatalf("owner review status: %d", review.Code)
	}
	vet := login(t, handler, "vet@cat.care", "vet")
	if mutation := authRequest(t, handler, vet, http.MethodPost, "/v1/notes", map[string]string{"description": "not allowed"}); mutation.Code != http.StatusForbidden {
		t.Fatalf("vet owner mutation status: %d", mutation.Code)
	}
	if cats := authRequest(t, handler, vet, http.MethodGet, "/v1/cats", nil); cats.Code != http.StatusOK || !bytes.Contains(cats.Body.Bytes(), []byte("Nina")) {
		t.Fatalf("vet cats: %d %s", cats.Code, cats.Body.String())
	}
}
