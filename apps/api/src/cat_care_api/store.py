from __future__ import annotations

import json
import sqlite3
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from pathlib import Path
from uuid import uuid4


def utc_now() -> datetime:
    return datetime.now(timezone.utc)


def require_aware(value: datetime, field: str) -> datetime:
    if value.tzinfo is None or value.utcoffset() is None:
        raise ValueError(f"{field} must include a timezone")
    return value.astimezone(timezone.utc)


@dataclass(frozen=True)
class Status:
    kind: str
    sentence: str
    nearest_responsibility_id: str | None


class CatCareStore:
    def __init__(self, path: str) -> None:
        self.path = path
        if path != ":memory:":
            Path(path).parent.mkdir(parents=True, exist_ok=True)
        self.connection = sqlite3.connect(path, check_same_thread=False)
        self.connection.row_factory = sqlite3.Row
        self.connection.execute("PRAGMA foreign_keys = ON")
        self._migrate()

    def close(self) -> None:
        self.connection.close()

    def _migrate(self) -> None:
        self.connection.executescript(
            """
            CREATE TABLE IF NOT EXISTS cats (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL CHECK (length(trim(name)) > 0)
            );
            CREATE TABLE IF NOT EXISTS responsibilities (
                id TEXT PRIMARY KEY,
                cat_id TEXT NOT NULL REFERENCES cats(id) ON DELETE CASCADE,
                title TEXT NOT NULL CHECK (length(trim(title)) > 0),
                category TEXT NOT NULL CHECK (length(trim(category)) > 0),
                due_at TEXT,
                state TEXT NOT NULL CHECK (state IN ('planned', 'completed', 'cancelled')),
                created_at TEXT NOT NULL,
                completed_at TEXT
            );
            CREATE TABLE IF NOT EXISTS events (
                id TEXT PRIMARY KEY,
                cat_id TEXT NOT NULL REFERENCES cats(id) ON DELETE CASCADE,
                type TEXT NOT NULL,
                occurred_at TEXT NOT NULL,
                description TEXT NOT NULL,
                responsibility_id TEXT,
                details TEXT NOT NULL DEFAULT '{}'
            );
            """
        )
        self.connection.execute(
            "INSERT OR IGNORE INTO cats (id, name) VALUES ('primary', 'Mimi')"
        )
        self.connection.commit()

    def cat(self) -> dict[str, str]:
        row = self.connection.execute(
            "SELECT id, name FROM cats WHERE id = 'primary'"
        ).fetchone()
        return dict(row)

    def list_responsibilities(self, now: datetime, threshold: timedelta) -> list[dict[str, object]]:
        now = require_aware(now, "current time")
        rows = self.connection.execute(
            """
            SELECT id, title, category, due_at, state, created_at, completed_at
            FROM responsibilities
            WHERE cat_id = 'primary'
            ORDER BY due_at IS NULL, due_at, id
            """
        ).fetchall()
        return [self._responsibility_record(row, now, threshold) for row in rows]

    def add_responsibility(
        self,
        title: str,
        category: str,
        due_at: datetime | None,
        now: datetime,
    ) -> dict[str, object]:
        title = title.strip()
        category = category.strip()
        if not title or not category:
            raise ValueError("title and category are required")
        now = require_aware(now, "current time")
        due = require_aware(due_at, "due time") if due_at else None
        responsibility_id = str(uuid4())
        with self.connection:
            self.connection.execute(
                """
                INSERT INTO responsibilities
                    (id, cat_id, title, category, due_at, state, created_at)
                VALUES (?, 'primary', ?, ?, ?, 'planned', ?)
                """,
                (responsibility_id, title, category, due.isoformat() if due else None, now.isoformat()),
            )
            self._insert_event(
                "responsibility_created",
                now,
                title,
                responsibility_id,
                {"category": category, "due_at": due.isoformat() if due else None},
            )
        return self.get_responsibility(responsibility_id, now, timedelta(days=2))

    def get_responsibility(
        self, responsibility_id: str, now: datetime, threshold: timedelta
    ) -> dict[str, object]:
        row = self.connection.execute(
            """
            SELECT id, title, category, due_at, state, created_at, completed_at
            FROM responsibilities WHERE id = ? AND cat_id = 'primary'
            """,
            (responsibility_id,),
        ).fetchone()
        if row is None:
            raise KeyError(responsibility_id)
        return self._responsibility_record(row, require_aware(now, "current time"), threshold)

    def complete_responsibility(self, responsibility_id: str, now: datetime) -> dict[str, object]:
        now = require_aware(now, "completion time")
        row = self.connection.execute(
            "SELECT title, state FROM responsibilities WHERE id = ? AND cat_id = 'primary'",
            (responsibility_id,),
        ).fetchone()
        if row is None:
            raise KeyError(responsibility_id)
        if row["state"] != "planned":
            raise ValueError("only a planned responsibility can be completed")
        with self.connection:
            self.connection.execute(
                "UPDATE responsibilities SET state = 'completed', completed_at = ? WHERE id = ?",
                (now.isoformat(), responsibility_id),
            )
            self._insert_event(
                "responsibility_completed", now, row["title"], responsibility_id, {}
            )
        return self.get_responsibility(responsibility_id, now, timedelta(days=2))

    def status(self, now: datetime, threshold: timedelta) -> Status:
        if threshold < timedelta(0):
            raise ValueError("due-soon threshold cannot be negative")
        active = [
            item
            for item in self.list_responsibilities(now, threshold)
            if item["state"] == "planned"
        ]
        overdue = [item for item in active if item["derived_state"] == "overdue"]
        if overdue:
            return Status("overdue", "Something important is overdue.", overdue[0]["id"])
        undated = next((item for item in active if item["due_at"] is None), None)
        if undated:
            return Status(
                "unknown", "Some future care information is unknown.", str(undated["id"])
            )
        if active:
            nearest = active[0]
            if nearest["derived_state"] == "due_soon":
                return Status("due_soon", f"Next: {nearest['title']} soon.", str(nearest["id"]))
            return Status(
                "planned",
                f"Nothing important is due soon. Next: {nearest['title']}.",
                str(nearest["id"]),
            )
        return Status("clear", "Nothing important is pending.", None)

    def timeline(self) -> list[dict[str, object]]:
        rows = self.connection.execute(
            """
            SELECT id, type, occurred_at, description, responsibility_id, details
            FROM events WHERE cat_id = 'primary'
            ORDER BY occurred_at DESC, type DESC, id DESC
            """
        ).fetchall()
        return [
            {
                "id": row["id"],
                "type": row["type"],
                "occurred_at": row["occurred_at"],
                "description": row["description"],
                "responsibility_id": row["responsibility_id"],
                "details": json.loads(row["details"]),
            }
            for row in rows
        ]

    def _insert_event(
        self,
        event_type: str,
        occurred_at: datetime,
        description: str,
        responsibility_id: str | None,
        details: dict[str, object],
    ) -> None:
        self.connection.execute(
            """
            INSERT INTO events
                (id, cat_id, type, occurred_at, description, responsibility_id, details)
            VALUES (?, 'primary', ?, ?, ?, ?, ?)
            """,
            (
                str(uuid4()),
                event_type,
                occurred_at.isoformat(),
                description,
                responsibility_id,
                json.dumps(details, sort_keys=True),
            ),
        )

    @staticmethod
    def _responsibility_record(
        row: sqlite3.Row, now: datetime, threshold: timedelta
    ) -> dict[str, object]:
        due_at = datetime.fromisoformat(row["due_at"]) if row["due_at"] else None
        derived_state = row["state"]
        if row["state"] == "planned" and due_at is None:
            derived_state = "unknown"
        elif row["state"] == "planned" and due_at < now:
            derived_state = "overdue"
        elif row["state"] == "planned" and due_at <= now + threshold:
            derived_state = "due_soon"
        return {
            "id": row["id"],
            "title": row["title"],
            "category": row["category"],
            "due_at": row["due_at"],
            "state": row["state"],
            "derived_state": derived_state,
            "created_at": row["created_at"],
            "completed_at": row["completed_at"],
        }
