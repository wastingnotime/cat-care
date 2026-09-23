package httpapi

import (
	"net/http"
	"strings"

	"github.com/wastingnotime/cat-care/apps/api/internal/application"
)

func ownerMode(w http.ResponseWriter, r *http.Request) (Session, bool) {
	session, ok := principal(r.Context())
	if !ok || session.User.Mode != "owner" {
		writeError(w, http.StatusForbidden, "forbidden", "Owner mode is required")
		return Session{}, false
	}
	return session, true
}

func veterinarianMode(w http.ResponseWriter, r *http.Request) (Session, bool) {
	session, ok := principal(r.Context())
	if !ok || session.User.Mode != "veterinarian" {
		writeError(w, http.StatusForbidden, "forbidden", "Veterinarian mode is required")
		return Session{}, false
	}
	return session, true
}

func (server *Server) veterinarians(w http.ResponseWriter, r *http.Request) {
	session, ok := ownerMode(w, r)
	if !ok {
		return
	}
	items, err := server.service.Invitations(r.Context(), session.User.ID, false)
	if err != nil {
		internalError(w)
		return
	}
	writeJSON(w, http.StatusOK, items)
}

func (server *Server) inviteVeterinarian(w http.ResponseWriter, r *http.Request) {
	session, ok := ownerMode(w, r)
	if !ok {
		return
	}
	var command struct {
		Email string `json:"email"`
	}
	if !decode(w, r, &command) {
		return
	}
	if !strings.EqualFold(strings.TrimSpace(command.Email), "vet@cat.care") {
		writeError(w, http.StatusBadRequest, "invalid_request", "No veterinarian account exists for that email in the local demo")
		return
	}
	invitation, err := server.service.InviteVeterinarian(r.Context(), application.VetInvitation{OwnerID: session.User.ID, OwnerName: session.User.Name, VeterinarianID: "vet-local", VeterinarianName: "Dr. Silva"})
	if err != nil {
		domainError(w, err)
		return
	}
	writeJSON(w, http.StatusCreated, invitation)
}

func (server *Server) invitations(w http.ResponseWriter, r *http.Request) {
	session, ok := veterinarianMode(w, r)
	if !ok {
		return
	}
	items, err := server.service.Invitations(r.Context(), session.User.ID, true)
	if err != nil {
		internalError(w)
		return
	}
	writeJSON(w, http.StatusOK, items)
}

func (server *Server) acceptInvitation(w http.ResponseWriter, r *http.Request) {
	session, ok := veterinarianMode(w, r)
	if !ok {
		return
	}
	invitation, err := server.service.AcceptInvitation(r.Context(), r.PathValue("ownerID"), session.User.ID)
	if err != nil {
		domainError(w, err)
		return
	}
	writeJSON(w, http.StatusOK, invitation)
}

func (server *Server) assignVeterinarian(w http.ResponseWriter, r *http.Request) {
	session, ok := ownerMode(w, r)
	if !ok {
		return
	}
	var command struct {
		VeterinarianID string `json:"veterinarian_id"`
	}
	if !decode(w, r, &command) {
		return
	}
	cat, err := server.service.AssignVeterinarian(r.Context(), session.User.ID, r.PathValue("id"), command.VeterinarianID)
	if err != nil {
		domainError(w, err)
		return
	}
	writeJSON(w, http.StatusOK, cat)
}
