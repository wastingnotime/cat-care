package domain

import (
	"errors"
	"sort"
	"strings"
	"time"
)

var (
	ErrInvalidResponsibility    = errors.New("title and category are required")
	ErrResponsibilityNotFound   = errors.New("responsibility not found")
	ErrResponsibilityNotPlanned = errors.New("only a planned responsibility can be completed")
	ErrInvalidTransition        = errors.New("invalid care transition")
	ErrRecordNotFound           = errors.New("care record not found")
	ErrDataDeleted              = errors.New("cat data has been deleted")
)

type Responsibility struct {
	ID               string     `json:"id"`
	Title            string     `json:"title"`
	Category         string     `json:"category"`
	DueAt            *time.Time `json:"due_at"`
	State            string     `json:"state"`
	CreatedAt        time.Time  `json:"created_at"`
	CompletedAt      *time.Time `json:"completed_at"`
	CancelledAt      *time.Time `json:"cancelled_at"`
	RecurrenceDays   int        `json:"recurrence_days,omitempty"`
	RecurrenceMonths int        `json:"recurrence_months,omitempty"`
	ActionKey        string     `json:"action_key,omitempty"`
}

type ResponsibilityView struct {
	Responsibility
	DerivedState string `json:"derived_state"`
}

type Event struct {
	ID               string         `json:"id"`
	Type             string         `json:"type"`
	OccurredAt       time.Time      `json:"occurred_at"`
	Description      string         `json:"description"`
	ResponsibilityID string         `json:"responsibility_id,omitempty"`
	Details          map[string]any `json:"details"`
}

type Status struct {
	Kind                    string `json:"kind"`
	Sentence                string `json:"sentence"`
	NearestResponsibilityID string `json:"nearest_responsibility_id,omitempty"`
	DueSoonDays             int    `json:"due_soon_days"`
}

func NewResponsibility(id, title, category string, dueAt *time.Time, now time.Time) (Responsibility, error) {
	title = strings.TrimSpace(title)
	category = strings.TrimSpace(category)
	if title == "" || category == "" {
		return Responsibility{}, ErrInvalidResponsibility
	}
	return Responsibility{ID: id, Title: title, Category: category, DueAt: dueAt, State: "planned", CreatedAt: now}, nil
}

func (responsibility Responsibility) Complete(now time.Time) (Responsibility, error) {
	if responsibility.State != "planned" {
		return Responsibility{}, ErrResponsibilityNotPlanned
	}
	responsibility.State = "completed"
	responsibility.CompletedAt = &now
	return responsibility, nil
}

func (responsibility Responsibility) Cancel(now time.Time) (Responsibility, error) {
	if responsibility.State != "planned" {
		return Responsibility{}, ErrResponsibilityNotPlanned
	}
	responsibility.State = "cancelled"
	responsibility.CancelledAt = &now
	return responsibility, nil
}

func (responsibility Responsibility) Defer(dueAt, now time.Time) (Responsibility, error) {
	if responsibility.State != "planned" || !dueAt.After(now) || (responsibility.DueAt != nil && !dueAt.After(*responsibility.DueAt)) {
		return Responsibility{}, ErrInvalidTransition
	}
	responsibility.DueAt = &dueAt
	return responsibility, nil
}

func DerivedState(responsibility Responsibility, now time.Time, threshold time.Duration) string {
	if responsibility.State != "planned" {
		return responsibility.State
	}
	if responsibility.DueAt == nil {
		return "unknown"
	}
	if responsibility.DueAt.Before(now) {
		return "overdue"
	}
	if !responsibility.DueAt.After(now.Add(threshold)) {
		return "due_soon"
	}
	return "planned"
}

func DeriveStatus(responsibilities []Responsibility, now time.Time, dueSoonDays int) Status {
	threshold := time.Duration(dueSoonDays) * 24 * time.Hour
	views := Views(responsibilities, now, threshold)
	for _, responsibility := range views {
		if responsibility.DerivedState == "overdue" {
			return Status{Kind: "overdue", Sentence: "Something important is overdue.", NearestResponsibilityID: responsibility.ID, DueSoonDays: dueSoonDays}
		}
	}
	for _, responsibility := range views {
		if responsibility.DerivedState == "unknown" {
			return Status{Kind: "unknown", Sentence: "Some future care information is unknown.", NearestResponsibilityID: responsibility.ID, DueSoonDays: dueSoonDays}
		}
	}
	for _, responsibility := range views {
		if responsibility.State != "planned" {
			continue
		}
		if responsibility.DerivedState == "due_soon" {
			return Status{Kind: "due_soon", Sentence: "Next: " + responsibility.Title + " soon.", NearestResponsibilityID: responsibility.ID, DueSoonDays: dueSoonDays}
		}
		return Status{Kind: "planned", Sentence: "Nothing important is due soon. Next: " + responsibility.Title + ".", NearestResponsibilityID: responsibility.ID, DueSoonDays: dueSoonDays}
	}
	return Status{Kind: "clear", Sentence: "Nothing important is pending.", DueSoonDays: dueSoonDays}
}

func Views(responsibilities []Responsibility, now time.Time, threshold time.Duration) []ResponsibilityView {
	items := make([]ResponsibilityView, 0, len(responsibilities))
	for _, responsibility := range responsibilities {
		items = append(items, ResponsibilityView{Responsibility: responsibility, DerivedState: DerivedState(responsibility, now, threshold)})
	}
	sort.Slice(items, func(left, right int) bool {
		if items[left].DueAt == nil || items[right].DueAt == nil {
			if items[left].DueAt == nil && items[right].DueAt == nil {
				return items[left].ID < items[right].ID
			}
			return items[left].DueAt != nil
		}
		if items[left].DueAt.Equal(*items[right].DueAt) {
			return items[left].ID < items[right].ID
		}
		return items[left].DueAt.Before(*items[right].DueAt)
	})
	return items
}

func SortEvents(events []Event) []Event {
	items := append([]Event(nil), events...)
	sort.Slice(items, func(left, right int) bool {
		if items[left].OccurredAt.Equal(items[right].OccurredAt) {
			if items[left].Type == items[right].Type {
				return items[left].ID > items[right].ID
			}
			return items[left].Type > items[right].Type
		}
		return items[left].OccurredAt.After(items[right].OccurredAt)
	})
	return items
}
