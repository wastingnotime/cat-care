export type Cat = { id: string; name: string };
export type CareStatus = { kind: string; sentence: string; nearest_responsibility_id?: string; due_soon_days: number };
export type Responsibility = { id: string; title: string; category: string; due_at: string | null; state: string; derived_state: string; created_at: string; completed_at: string | null };
export type CareEvent = { id: string; type: string; occurred_at: string; description: string; responsibility_id?: string; details: Record<string, unknown> };
