package httpapi

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"net/http"
	"strings"
	"sync"

	"github.com/wastingnotime/cat-care/apps/api/internal/application"
	"github.com/wastingnotime/cat-care/apps/api/internal/domain"
)

const sessionCookie = "cat_care_session"

type User struct {
	ID    string `json:"id"`
	Name  string `json:"name"`
	Email string `json:"email"`
	Mode  string `json:"mode"`
}
type Session struct {
	User        User              `json:"user"`
	Cats        []application.Cat `json:"cats"`
	ActiveCatID string            `json:"active_cat_id"`
}
type localIdentity struct {
	mu       sync.RWMutex
	sessions map[string]Session
}
type principalKey struct{}

func newLocalIdentity() *localIdentity { return &localIdentity{sessions: map[string]Session{}} }

func (identity *localIdentity) login(email, password string, service *application.Service) (string, Session, bool) {
	var user User
	switch {
	case email == "owner@cat.care" && password == "owner":
		user = User{ID: "owner-local", Name: "Alex", Email: email, Mode: "owner"}
	case email == "vet@cat.care" && password == "vet":
		user = User{ID: "vet-local", Name: "Dr. Silva", Email: email, Mode: "veterinarian"}
	default:
		return "", Session{}, false
	}
	cats, _ := service.Cats(context.Background(), user.ID, user.Mode == "veterinarian")
	session := Session{User: user, Cats: cats}
	if len(cats) > 0 {
		session.ActiveCatID = cats[0].ID
	}
	bytes := make([]byte, 24)
	_, _ = rand.Read(bytes)
	token := hex.EncodeToString(bytes)
	identity.mu.Lock()
	identity.sessions[token] = session
	identity.mu.Unlock()
	return token, session, true
}

func (identity *localIdentity) get(r *http.Request) (string, Session, bool) {
	cookie, err := r.Cookie(sessionCookie)
	if err != nil {
		return "", Session{}, false
	}
	identity.mu.RLock()
	session, ok := identity.sessions[cookie.Value]
	identity.mu.RUnlock()
	return cookie.Value, session, ok
}
func (identity *localIdentity) save(token string, session Session) {
	identity.mu.Lock()
	identity.sessions[token] = session
	identity.mu.Unlock()
}
func principal(ctx context.Context) (Session, bool) {
	value, ok := ctx.Value(principalKey{}).(Session)
	return value, ok
}

func (server *Server) createSession(w http.ResponseWriter, r *http.Request) {
	var command struct {
		Email    string `json:"email"`
		Password string `json:"password"`
	}
	if !decode(w, r, &command) {
		return
	}
	token, session, ok := server.identity.login(strings.TrimSpace(command.Email), command.Password, server.service)
	if !ok {
		writeError(w, http.StatusUnauthorized, "invalid_credentials", "Email or password is incorrect")
		return
	}
	http.SetCookie(w, &http.Cookie{Name: sessionCookie, Value: token, Path: "/", HttpOnly: true, SameSite: http.SameSiteLaxMode})
	writeJSON(w, http.StatusOK, session)
}
func (server *Server) currentSession(w http.ResponseWriter, r *http.Request) {
	_, session, ok := server.identity.get(r)
	if !ok {
		writeError(w, 401, "unauthenticated", "Log on to continue")
		return
	}
	writeJSON(w, 200, session)
}
func (server *Server) deleteSession(w http.ResponseWriter, r *http.Request) {
	token, _, ok := server.identity.get(r)
	if ok {
		server.identity.mu.Lock()
		delete(server.identity.sessions, token)
		server.identity.mu.Unlock()
	}
	http.SetCookie(w, &http.Cookie{Name: sessionCookie, Path: "/", MaxAge: -1, HttpOnly: true})
	w.WriteHeader(http.StatusNoContent)
}

func (server *Server) cats(w http.ResponseWriter, r *http.Request) {
	session, _ := principal(r.Context())
	items, e := server.service.Cats(r.Context(), session.User.ID, session.User.Mode == "veterinarian")
	if e != nil {
		internalError(w)
		return
	}
	writeJSON(w, 200, items)
}
func (server *Server) createCat(w http.ResponseWriter, r *http.Request) {
	token, session, _ := server.identity.get(r)
	if session.User.Mode != "owner" {
		writeError(w, 403, "forbidden", "Owner mode is required")
		return
	}
	var profile domain.Profile
	if !decode(w, r, &profile) {
		return
	}
	item, e := server.service.CreateCat(r.Context(), session.User.ID, profile)
	if e != nil {
		domainError(w, e)
		return
	}
	session.Cats = append(session.Cats, item)
	server.identity.save(token, session)
	writeJSON(w, 201, item)
}
func (server *Server) selectCat(w http.ResponseWriter, r *http.Request) {
	token, session, _ := server.identity.get(r)
	id := r.PathValue("id")
	cats, _ := server.service.Cats(r.Context(), session.User.ID, session.User.Mode == "veterinarian")
	allowed := false
	for _, cat := range cats {
		if cat.ID == id {
			allowed = true
		}
	}
	if !allowed {
		writeError(w, 404, "not_found", "Cat is not available to this account")
		return
	}
	session.Cats = cats
	session.ActiveCatID = id
	server.identity.save(token, session)
	writeJSON(w, 200, session)
}

func (server *Server) authenticate(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path == "/healthz" || (r.URL.Path == "/v1/session" && r.Method == http.MethodPost) {
			next.ServeHTTP(w, r)
			return
		}
		_, session, ok := server.identity.get(r)
		if !ok {
			writeError(w, 401, "unauthenticated", "Log on to continue")
			return
		}
		if session.ActiveCatID == "" && !strings.HasPrefix(r.URL.Path, "/v1/cats") && r.URL.Path != "/v1/session" {
			writeError(w, 409, "cat_required", "Select a cat to continue")
			return
		}
		if session.User.Mode == "veterinarian" && r.Method != "GET" && !strings.Contains(r.URL.Path, "/review") && !strings.Contains(r.URL.Path, "/information-requests") && !strings.Contains(r.URL.Path, "/follow-up") && r.URL.Path != "/v1/session" && !strings.HasSuffix(r.URL.Path, "/select") {
			writeError(w, 403, "forbidden", "This action belongs to owner mode")
			return
		}
		if session.User.Mode == "owner" && (strings.Contains(r.URL.Path, "/review") || strings.Contains(r.URL.Path, "/information-requests") || strings.Contains(r.URL.Path, "/follow-up")) {
			writeError(w, 403, "forbidden", "Veterinarian mode is required")
			return
		}
		ctx := context.WithValue(r.Context(), principalKey{}, session)
		ctx = application.WithCat(ctx, session.ActiveCatID)
		next.ServeHTTP(w, r.WithContext(ctx))
	})
}
