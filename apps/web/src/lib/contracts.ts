export type Cat = { name: string; birth_date: string | null; adoption_date: string | null; photo_ref?: string };
export type CareStatus = { kind: string; sentence: string; nearest_responsibility_id?: string; due_soon_days: number };
export type Responsibility = { id: string; title: string; category: string; due_at: string | null; state: string; derived_state: string; created_at: string; completed_at: string | null; cancelled_at: string | null; recurrence_days?: number; recurrence_months?: number };
export type CareEvent = { id: string; type: string; occurred_at: string; description: string; responsibility_id?: string; details: Record<string, unknown> };
export type Note = { id: string; description: string; occurred_at: string; is_diagnosis: false };
export type Notification = { id: string; responsibility_id: string; outcome: "delivered" | "failed"; attempted_at: string; provider?: string };
export type TriageAssessment = { id: string; note_ids: string[]; urgency: string; rationale: string; uncertainty: string; provider: string; model_version: string; assessed_at: string; review_status: string; final_urgency?: string };
