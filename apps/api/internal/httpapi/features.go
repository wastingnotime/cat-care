package httpapi

import (
	"encoding/json"
	"errors"
	"net/http"
	"time"

	"github.com/wastingnotime/cat-care/apps/api/internal/domain"
)

func decode(writer http.ResponseWriter, request *http.Request, target any) bool {
	decoder := json.NewDecoder(request.Body)
	decoder.DisallowUnknownFields()
	if err := decoder.Decode(target); err != nil {
		writeError(writer, http.StatusBadRequest, "invalid_request", "invalid request payload")
		return false
	}
	return true
}
func domainError(writer http.ResponseWriter, err error) {
	switch {
	case errors.Is(err, domain.ErrResponsibilityNotFound), errors.Is(err, domain.ErrRecordNotFound):
		writeError(writer, http.StatusNotFound, "not_found", err.Error())
	case errors.Is(err, domain.ErrResponsibilityNotPlanned), errors.Is(err, domain.ErrInvalidTransition), errors.Is(err, domain.ErrDataDeleted):
		writeError(writer, http.StatusConflict, "conflict", err.Error())
	case errors.Is(err, domain.ErrInvalidResponsibility):
		writeError(writer, http.StatusBadRequest, "invalid_request", err.Error())
	default:
		internalError(writer)
	}
}

func (server *Server) updateProfile(w http.ResponseWriter, r *http.Request) {
	var command domain.Profile
	if !decode(w, r, &command) {
		return
	}
	item, e := server.service.UpdateProfile(r.Context(), command)
	if e != nil {
		domainError(w, e)
		return
	}
	writeJSON(w, http.StatusOK, item)
}

type editCommand struct {
	Title            string     `json:"title"`
	Category         string     `json:"category"`
	DueAt            *time.Time `json:"due_at"`
	RecurrenceDays   int        `json:"recurrence_days"`
	RecurrenceMonths int        `json:"recurrence_months"`
}

func (server *Server) editResponsibility(w http.ResponseWriter, r *http.Request) {
	var c editCommand
	if !decode(w, r, &c) {
		return
	}
	item, e := server.service.EditResponsibility(r.Context(), r.PathValue("id"), c.Title, c.Category, c.DueAt, c.RecurrenceDays, c.RecurrenceMonths)
	if e != nil {
		domainError(w, e)
		return
	}
	writeJSON(w, http.StatusOK, item)
}
func (server *Server) cancelResponsibility(w http.ResponseWriter, r *http.Request) {
	item, e := server.service.CancelResponsibility(r.Context(), r.PathValue("id"))
	if e != nil {
		domainError(w, e)
		return
	}
	writeJSON(w, http.StatusOK, item)
}
func (server *Server) deferResponsibility(w http.ResponseWriter, r *http.Request) {
	var c struct {
		DueAt time.Time `json:"due_at"`
	}
	if !decode(w, r, &c) {
		return
	}
	item, e := server.service.DeferResponsibility(r.Context(), r.PathValue("id"), c.DueAt)
	if e != nil {
		domainError(w, e)
		return
	}
	writeJSON(w, http.StatusOK, item)
}

func (server *Server) notes(w http.ResponseWriter, r *http.Request) {
	items, e := server.service.Notes(r.Context())
	if e != nil {
		internalError(w)
		return
	}
	writeJSON(w, http.StatusOK, items)
}
func (server *Server) recordNote(w http.ResponseWriter, r *http.Request) {
	var c struct {
		Description string `json:"description"`
	}
	if !decode(w, r, &c) {
		return
	}
	item, e := server.service.RecordNote(r.Context(), c.Description)
	if e != nil {
		domainError(w, e)
		return
	}
	writeJSON(w, http.StatusCreated, item)
}
func (server *Server) directCare(w http.ResponseWriter, r *http.Request) {
	items, e := server.service.DirectCare(r.Context())
	if e != nil {
		internalError(w)
		return
	}
	writeJSON(w, http.StatusOK, items)
}
func (server *Server) recordDirectCare(w http.ResponseWriter, r *http.Request) {
	var c struct {
		Type             string `json:"type"`
		Description      string `json:"description"`
		ResponsibilityID string `json:"responsibility_id"`
	}
	if !decode(w, r, &c) {
		return
	}
	item, e := server.service.RecordDirectCare(r.Context(), c.Type, c.Description, c.ResponsibilityID)
	if e != nil {
		domainError(w, e)
		return
	}
	writeJSON(w, http.StatusCreated, item)
}
func (server *Server) notifications(w http.ResponseWriter, r *http.Request) {
	items, e := server.service.Notifications(r.Context())
	if e != nil {
		internalError(w)
		return
	}
	writeJSON(w, http.StatusOK, items)
}
func (server *Server) recordNotification(w http.ResponseWriter, r *http.Request) {
	var c struct {
		ResponsibilityID string `json:"responsibility_id"`
		Outcome          string `json:"outcome"`
	}
	if !decode(w, r, &c) {
		return
	}
	item, e := server.service.RecordNotification(r.Context(), c.ResponsibilityID, c.Outcome)
	if e != nil {
		domainError(w, e)
		return
	}
	writeJSON(w, http.StatusCreated, item)
}

func (server *Server) triage(w http.ResponseWriter, r *http.Request) {
	items, e := server.service.Triage(r.Context())
	if e != nil {
		internalError(w)
		return
	}
	writeJSON(w, http.StatusOK, items)
}
func (server *Server) triageReviews(w http.ResponseWriter, r *http.Request) {
	items, e := server.service.VeterinarianReviews(r.Context())
	if e != nil {
		internalError(w)
		return
	}
	writeJSON(w, http.StatusOK, items)
}
func (server *Server) requestTriage(w http.ResponseWriter, r *http.Request) {
	var c struct {
		NoteIDs []string `json:"note_ids"`
	}
	if !decode(w, r, &c) {
		return
	}
	item, e := server.service.RequestTriage(r.Context(), c.NoteIDs)
	if e != nil {
		domainError(w, e)
		return
	}
	writeJSON(w, http.StatusCreated, item)
}
func (server *Server) reviewTriage(w http.ResponseWriter, r *http.Request) {
	var c struct {
		VeterinarianID string `json:"veterinarian_id"`
		Decision       string `json:"decision"`
		FinalUrgency   string `json:"final_urgency"`
		Rationale      string `json:"rationale"`
	}
	if !decode(w, r, &c) {
		return
	}
	item, e := server.service.ReviewTriage(r.Context(), r.PathValue("id"), c.VeterinarianID, c.Decision, c.FinalUrgency, c.Rationale)
	if e != nil {
		domainError(w, e)
		return
	}
	writeJSON(w, http.StatusOK, item)
}
func (server *Server) requestTriageInformation(w http.ResponseWriter, r *http.Request) {
	var c struct {
		VeterinarianID string `json:"veterinarian_id"`
		Question       string `json:"question"`
	}
	if !decode(w, r, &c) {
		return
	}
	item, e := server.service.RequestTriageInformation(r.Context(), r.PathValue("id"), c.VeterinarianID, c.Question)
	if e != nil {
		domainError(w, e)
		return
	}
	writeJSON(w, http.StatusCreated, item)
}
func (server *Server) defineTriageFollowUp(w http.ResponseWriter, r *http.Request) {
	var c struct {
		Title          string    `json:"title"`
		VeterinarianID string    `json:"veterinarian_id"`
		DueAt          time.Time `json:"due_at"`
	}
	if !decode(w, r, &c) {
		return
	}
	item, e := server.service.DefineTriageFollowUp(r.Context(), r.PathValue("id"), c.Title, c.VeterinarianID, c.DueAt)
	if e != nil {
		domainError(w, e)
		return
	}
	writeJSON(w, http.StatusCreated, item)
}
func (server *Server) exportData(w http.ResponseWriter, r *http.Request) {
	state, e := server.service.Export(r.Context())
	if e != nil {
		internalError(w)
		return
	}
	writeJSON(w, http.StatusOK, state)
}
func (server *Server) deleteData(w http.ResponseWriter, r *http.Request) {
	receipt, e := server.service.Delete(r.Context())
	if e != nil {
		domainError(w, e)
		return
	}
	writeJSON(w, http.StatusOK, receipt)
}
