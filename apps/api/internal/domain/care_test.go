package domain

import (
	"testing"
	"time"
)

func TestStatusPreservesUncertaintyAndCompletion(t *testing.T) {
	now := time.Date(2026, 9, 1, 12, 0, 0, 0, time.UTC)
	responsibility, err := NewResponsibility("r1", "Annual exam", "veterinary", nil, now)
	if err != nil {
		t.Fatal(err)
	}
	status := DeriveStatus([]Responsibility{responsibility}, now, 2)
	if status.Kind != "unknown" || status.NearestResponsibilityID != "r1" {
		t.Fatalf("unexpected status: %#v", status)
	}
	completed, err := responsibility.Complete(now)
	if err != nil {
		t.Fatal(err)
	}
	if status := DeriveStatus([]Responsibility{completed}, now, 2); status.Kind != "clear" {
		t.Fatalf("expected clear, got %#v", status)
	}
}

func TestStatusDerivesDueSoonAndOverdue(t *testing.T) {
	now := time.Date(2026, 9, 1, 12, 0, 0, 0, time.UTC)
	due := now.Add(24 * time.Hour)
	responsibility, _ := NewResponsibility("r1", "Vaccination", "preventive", &due, now)
	if status := DeriveStatus([]Responsibility{responsibility}, now, 2); status.Kind != "due_soon" {
		t.Fatalf("expected due soon, got %#v", status)
	}
	if status := DeriveStatus([]Responsibility{responsibility}, now.Add(48*time.Hour), 2); status.Kind != "overdue" {
		t.Fatalf("expected overdue, got %#v", status)
	}
}

func TestCompletionIsTerminal(t *testing.T) {
	now := time.Date(2026, 9, 1, 12, 0, 0, 0, time.UTC)
	responsibility, _ := NewResponsibility("r1", "Vaccination", "preventive", nil, now)
	completed, _ := responsibility.Complete(now)
	if _, err := completed.Complete(now); err != ErrResponsibilityNotPlanned {
		t.Fatalf("expected terminal error, got %v", err)
	}
}
