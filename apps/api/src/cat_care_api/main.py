import os
from contextlib import asynccontextmanager
from datetime import datetime, timedelta
from typing import Annotated

from fastapi import Depends, FastAPI, HTTPException, Query, status
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field, field_validator

from cat_care_api.store import CatCareStore, require_aware, utc_now


class ResponsibilityCreate(BaseModel):
    title: str = Field(min_length=1, max_length=160)
    category: str = Field(min_length=1, max_length=80)
    due_at: datetime | None = None

    @field_validator("title", "category")
    @classmethod
    def non_blank(cls, value: str) -> str:
        if not value.strip():
            raise ValueError("must not be blank")
        return value.strip()

    @field_validator("due_at")
    @classmethod
    def aware_due_at(cls, value: datetime | None) -> datetime | None:
        return require_aware(value, "due time") if value else None


def create_app(database_path: str | None = None) -> FastAPI:
    @asynccontextmanager
    async def lifespan(app: FastAPI):
        app.state.store = CatCareStore(
            database_path or os.environ.get("CAT_CARE_DB_PATH", ".local/cat-care.db")
        )
        yield
        app.state.store.close()

    application = FastAPI(
        title="Cat Care API",
        version="0.1.0",
        lifespan=lifespan,
    )
    origins = os.environ.get(
        "CAT_CARE_WEB_ORIGINS", "http://127.0.0.1:5173,http://localhost:5173"
    ).split(",")
    application.add_middleware(
        CORSMiddleware,
        allow_origins=[origin.strip() for origin in origins if origin.strip()],
        allow_methods=["GET", "POST"],
        allow_headers=["Content-Type"],
    )

    def store() -> CatCareStore:
        return application.state.store

    Store = Annotated[CatCareStore, Depends(store)]

    @application.get("/healthz")
    def health() -> dict[str, str]:
        return {"status": "ok"}

    @application.get("/api/v1/cat")
    def get_cat(cat_store: Store) -> dict[str, str]:
        return cat_store.cat()

    @application.get("/api/v1/status")
    def get_status(
        cat_store: Store,
        due_soon_days: int = Query(default=2, ge=0, le=365),
    ) -> dict[str, object]:
        snapshot = cat_store.status(utc_now(), timedelta(days=due_soon_days))
        return {
            "kind": snapshot.kind,
            "sentence": snapshot.sentence,
            "nearest_responsibility_id": snapshot.nearest_responsibility_id,
            "due_soon_days": due_soon_days,
        }

    @application.get("/api/v1/responsibilities")
    def list_responsibilities(cat_store: Store) -> list[dict[str, object]]:
        return cat_store.list_responsibilities(utc_now(), timedelta(days=2))

    @application.post(
        "/api/v1/responsibilities",
        status_code=status.HTTP_201_CREATED,
    )
    def create_responsibility(
        command: ResponsibilityCreate, cat_store: Store
    ) -> dict[str, object]:
        return cat_store.add_responsibility(
            command.title, command.category, command.due_at, utc_now()
        )

    @application.post("/api/v1/responsibilities/{responsibility_id}/complete")
    def complete_responsibility(
        responsibility_id: str, cat_store: Store
    ) -> dict[str, object]:
        try:
            return cat_store.complete_responsibility(responsibility_id, utc_now())
        except KeyError as error:
            raise HTTPException(status_code=404, detail="responsibility not found") from error
        except ValueError as error:
            raise HTTPException(status_code=409, detail=str(error)) from error

    @application.get("/api/v1/timeline")
    def get_timeline(cat_store: Store) -> list[dict[str, object]]:
        return cat_store.timeline()

    return application


app = create_app()
