from fastapi.testclient import TestClient

from cat_care_api.main import create_app


def test_api_create_list_complete_and_read_timeline(tmp_path):
    app = create_app(str(tmp_path / "cat-care.db"))
    with TestClient(app) as client:
        assert client.get("/healthz").json() == {"status": "ok"}
        created = client.post(
            "/api/v1/responsibilities",
            json={"title": "Annual exam", "category": "veterinary", "due_at": None},
        )
        assert created.status_code == 201
        responsibility = created.json()

        status = client.get("/api/v1/status").json()
        assert status["kind"] == "unknown"
        assert status["nearest_responsibility_id"] == responsibility["id"]

        completed = client.post(
            f"/api/v1/responsibilities/{responsibility['id']}/complete"
        )
        assert completed.status_code == 200
        assert completed.json()["state"] == "completed"
        assert client.get("/api/v1/status").json()["kind"] == "clear"
        assert len(client.get("/api/v1/timeline").json()) == 2


def test_api_rejects_naive_due_time(tmp_path):
    app = create_app(str(tmp_path / "cat-care.db"))
    with TestClient(app) as client:
        response = client.post(
            "/api/v1/responsibilities",
            json={
                "title": "Annual exam",
                "category": "veterinary",
                "due_at": "2026-09-02T10:00:00",
            },
        )
        assert response.status_code == 422
