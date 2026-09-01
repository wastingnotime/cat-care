package httpapi

import (
	"encoding/json"
	"errors"
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/wastingnotime/cat-care/apps/api/internal/application"
	"github.com/wastingnotime/cat-care/apps/api/internal/domain"
)

type Server struct{ service *application.Service }

func NewServer(service *application.Service) *Server { return &Server{service: service} }

func (server *Server) Handler() http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("GET /healthz", func(writer http.ResponseWriter, _ *http.Request) {
		writeJSON(writer, http.StatusOK, map[string]string{"status": "ok"})
	})
	mux.HandleFunc("GET /v1/cat", server.cat)
	mux.HandleFunc("PUT /v1/cat", server.updateProfile)
	mux.HandleFunc("GET /v1/status", server.status)
	mux.HandleFunc("GET /v1/responsibilities", server.responsibilities)
	mux.HandleFunc("POST /v1/responsibilities", server.createResponsibility)
	mux.HandleFunc("POST /v1/responsibilities/{id}/complete", server.completeResponsibility)
	mux.HandleFunc("PUT /v1/responsibilities/{id}", server.editResponsibility)
	mux.HandleFunc("POST /v1/responsibilities/{id}/cancel", server.cancelResponsibility)
	mux.HandleFunc("POST /v1/responsibilities/{id}/defer", server.deferResponsibility)
	mux.HandleFunc("GET /v1/timeline", server.timeline)
	mux.HandleFunc("GET /v1/notes", server.notes)
	mux.HandleFunc("POST /v1/notes", server.recordNote)
	mux.HandleFunc("GET /v1/care-events", server.directCare)
	mux.HandleFunc("POST /v1/care-events", server.recordDirectCare)
	mux.HandleFunc("GET /v1/notifications", server.notifications)
	mux.HandleFunc("POST /v1/notifications", server.recordNotification)
	mux.HandleFunc("GET /v1/triage", server.triage)
	mux.HandleFunc("GET /v1/triage-reviews", server.triageReviews)
	mux.HandleFunc("POST /v1/triage", server.requestTriage)
	mux.HandleFunc("POST /v1/triage/{id}/review", server.reviewTriage)
	mux.HandleFunc("POST /v1/triage/{id}/information-requests", server.requestTriageInformation)
	mux.HandleFunc("POST /v1/triage/{id}/follow-up", server.defineTriageFollowUp)
	mux.HandleFunc("GET /v1/export", server.exportData)
	mux.HandleFunc("DELETE /v1/data", server.deleteData)
	return mux
}

func (server *Server) cat(writer http.ResponseWriter, request *http.Request) {
	profile, err := server.service.Profile(request.Context())
	if err != nil {
		internalError(writer)
		return
	}
	writeJSON(writer, http.StatusOK, profile)
}
func (server *Server) status(writer http.ResponseWriter, request *http.Request) {
	days := 2
	if raw := request.URL.Query().Get("due_soon_days"); raw != "" {
		parsed, err := strconv.Atoi(raw)
		if err != nil || parsed < 0 || parsed > 365 {
			writeError(writer, http.StatusBadRequest, "invalid_request", "due_soon_days must be between 0 and 365")
			return
		}
		days = parsed
	}
	result, err := server.service.Status(request.Context(), days)
	if err != nil {
		internalError(writer)
		return
	}
	writeJSON(writer, http.StatusOK, result)
}
func (server *Server) responsibilities(writer http.ResponseWriter, request *http.Request) {
	items, err := server.service.Responsibilities(request.Context())
	if err != nil {
		internalError(writer)
		return
	}
	writeJSON(writer, http.StatusOK, items)
}
func (server *Server) timeline(writer http.ResponseWriter, request *http.Request) {
	items, err := server.service.Timeline(request.Context())
	if err != nil {
		internalError(writer)
		return
	}
	writeJSON(writer, http.StatusOK, items)
}

type createCommand struct {
	Title            string     `json:"title"`
	Category         string     `json:"category"`
	DueAt            *time.Time `json:"due_at"`
	RecurrenceDays   int        `json:"recurrence_days"`
	RecurrenceMonths int        `json:"recurrence_months"`
}

func (server *Server) createResponsibility(writer http.ResponseWriter, request *http.Request) {
	var command createCommand
	decoder := json.NewDecoder(request.Body)
	decoder.DisallowUnknownFields()
	if err := decoder.Decode(&command); err != nil {
		writeError(writer, http.StatusBadRequest, "invalid_request", "invalid responsibility payload")
		return
	}
	item, err := server.service.CreateResponsibilityWithPolicy(request.Context(), command.Title, command.Category, command.DueAt, command.RecurrenceDays, command.RecurrenceMonths)
	if errors.Is(err, domain.ErrInvalidResponsibility) {
		writeError(writer, http.StatusBadRequest, "invalid_request", err.Error())
		return
	}
	if err != nil {
		internalError(writer)
		return
	}
	writeJSON(writer, http.StatusCreated, item)
}
func (server *Server) completeResponsibility(writer http.ResponseWriter, request *http.Request) {
	item, err := server.service.CompleteResponsibility(request.Context(), strings.TrimSpace(request.PathValue("id")))
	if errors.Is(err, domain.ErrResponsibilityNotFound) {
		writeError(writer, http.StatusNotFound, "not_found", err.Error())
		return
	}
	if errors.Is(err, domain.ErrResponsibilityNotPlanned) {
		writeError(writer, http.StatusConflict, "conflict", err.Error())
		return
	}
	if err != nil {
		internalError(writer)
		return
	}
	writeJSON(writer, http.StatusOK, item)
}

func internalError(writer http.ResponseWriter) {
	writeError(writer, http.StatusInternalServerError, "internal_error", "internal server error")
}
func writeError(writer http.ResponseWriter, status int, code, message string) {
	writeJSONStatus(writer, status, map[string]string{"code": code, "message": message})
}
func writeJSON(writer http.ResponseWriter, status int, value any) {
	writeJSONStatus(writer, status, value)
}
func writeJSONStatus(writer http.ResponseWriter, status int, value any) {
	writer.Header().Set("Content-Type", "application/json")
	writer.WriteHeader(status)
	_ = json.NewEncoder(writer).Encode(value)
}
