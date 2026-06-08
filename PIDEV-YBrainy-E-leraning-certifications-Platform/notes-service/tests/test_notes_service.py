"""
Unit tests for the YBrainy Notes Service (FastAPI).

Uses an in-memory SQLite database so no PostgreSQL is required.
The Eureka registration startup event is patched out.
"""
import sys
import os
from unittest.mock import patch, MagicMock

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker

# ── Path setup ────────────────────────────────────────────────
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

# Patch Eureka before importing main so the startup event doesn't hang
with patch("py_eureka_client.eureka_client.init", return_value=None):
    import models
    from database import get_db
    import main as notes_main

# ── In-memory SQLite database for tests ──────────────────────
SQLALCHEMY_DATABASE_URL = "sqlite:///:memory:"
engine = create_engine(
    SQLALCHEMY_DATABASE_URL,
    connect_args={"check_same_thread": False}
)
TestingSessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)
models.Base.metadata.create_all(bind=engine)


def override_get_db():
    db = TestingSessionLocal()
    try:
        yield db
    finally:
        db.close()


notes_main.app.dependency_overrides[get_db] = override_get_db

client = TestClient(notes_main.app, raise_server_exceptions=True)

STUDENT_HEADERS = {"X-User-Id": "42"}
OTHER_HEADERS = {"X-User-Id": "99"}


# ── Health ────────────────────────────────────────────────────

def test_health_returns_ok():
    resp = client.get("/health")
    assert resp.status_code == 200
    data = resp.json()
    assert data["status"] == "ok"
    assert data["service"] == "notes-service"


# ── Auth guard ───────────────────────────────────────────────

def test_get_notes_without_header_returns_401():
    resp = client.get("/api/notes")
    assert resp.status_code == 401


def test_get_notes_with_invalid_header_returns_400():
    resp = client.get("/api/notes", headers={"X-User-Id": "not-a-number"})
    assert resp.status_code == 400


# ── Create note ──────────────────────────────────────────────

def test_create_note_returns_201():
    resp = client.post("/api/notes", json={"title": "My Note", "content": "Hello world"},
                       headers=STUDENT_HEADERS)
    assert resp.status_code == 201
    data = resp.json()
    assert data["title"] == "My Note"
    assert data["student_id"] == 42
    assert data["directory"] == "General"
    assert data["is_pinned"] is False
    assert data["is_archived"] is False


def test_create_note_defaults_applied():
    resp = client.post("/api/notes", json={"title": "Minimal"}, headers=STUDENT_HEADERS)
    assert resp.status_code == 201
    data = resp.json()
    assert data["color"] == "#ffffff"
    assert data["content"] == ""


# ── Read notes ───────────────────────────────────────────────

def test_get_notes_returns_only_own_notes():
    client.post("/api/notes", json={"title": "Own"}, headers=STUDENT_HEADERS)
    client.post("/api/notes", json={"title": "Other"}, headers=OTHER_HEADERS)

    resp = client.get("/api/notes", headers=STUDENT_HEADERS)
    assert resp.status_code == 200
    titles = [n["title"] for n in resp.json()]
    assert all(n["student_id"] == 42 for n in resp.json())
    assert "Own" in titles
    assert "Other" not in titles


def test_get_notes_filters_by_directory():
    client.post("/api/notes", json={"title": "Work Note", "directory": "Work"},
                headers=STUDENT_HEADERS)
    client.post("/api/notes", json={"title": "General Note", "directory": "General"},
                headers=STUDENT_HEADERS)

    resp = client.get("/api/notes?directory=Work", headers=STUDENT_HEADERS)
    assert resp.status_code == 200
    notes = resp.json()
    assert all(n["directory"] == "Work" for n in notes)


def test_get_notes_excludes_archived_by_default():
    create_resp = client.post("/api/notes", json={"title": "To Archive"},
                              headers=STUDENT_HEADERS)
    note_id = create_resp.json()["id"]
    client.patch(f"/api/notes/{note_id}/archive", headers=STUDENT_HEADERS)

    resp = client.get("/api/notes", headers=STUDENT_HEADERS)
    ids = [n["id"] for n in resp.json()]
    assert note_id not in ids


def test_get_notes_include_archived_flag():
    create_resp = client.post("/api/notes", json={"title": "Archived Note"},
                              headers=STUDENT_HEADERS)
    note_id = create_resp.json()["id"]
    client.patch(f"/api/notes/{note_id}/archive", headers=STUDENT_HEADERS)

    resp = client.get("/api/notes?include_archived=true", headers=STUDENT_HEADERS)
    ids = [n["id"] for n in resp.json()]
    assert note_id in ids


# ── Update note ──────────────────────────────────────────────

def test_update_note_changes_fields():
    create_resp = client.post("/api/notes", json={"title": "Old Title"},
                              headers=STUDENT_HEADERS)
    note_id = create_resp.json()["id"]

    resp = client.put(f"/api/notes/{note_id}",
                      json={"title": "New Title", "content": "Updated"},
                      headers=STUDENT_HEADERS)
    assert resp.status_code == 200
    assert resp.json()["title"] == "New Title"
    assert resp.json()["content"] == "Updated"


def test_update_note_returns_404_for_other_user():
    create_resp = client.post("/api/notes", json={"title": "Private"},
                              headers=STUDENT_HEADERS)
    note_id = create_resp.json()["id"]

    resp = client.put(f"/api/notes/{note_id}", json={"title": "Hijacked"},
                      headers=OTHER_HEADERS)
    assert resp.status_code == 404


def test_update_nonexistent_note_returns_404():
    resp = client.put("/api/notes/99999", json={"title": "Ghost"},
                      headers=STUDENT_HEADERS)
    assert resp.status_code == 404


# ── Delete note ──────────────────────────────────────────────

def test_delete_note_returns_204():
    create_resp = client.post("/api/notes", json={"title": "To Delete"},
                              headers=STUDENT_HEADERS)
    note_id = create_resp.json()["id"]

    resp = client.delete(f"/api/notes/{note_id}", headers=STUDENT_HEADERS)
    assert resp.status_code == 204

    get_resp = client.get("/api/notes", headers=STUDENT_HEADERS)
    ids = [n["id"] for n in get_resp.json()]
    assert note_id not in ids


def test_delete_nonexistent_note_returns_404():
    resp = client.delete("/api/notes/99999", headers=STUDENT_HEADERS)
    assert resp.status_code == 404


# ── Pin / Archive toggles ────────────────────────────────────

def test_toggle_pin_changes_pin_state():
    create_resp = client.post("/api/notes", json={"title": "Pinnable"},
                              headers=STUDENT_HEADERS)
    note_id = create_resp.json()["id"]
    assert create_resp.json()["is_pinned"] is False

    pin_resp = client.patch(f"/api/notes/{note_id}/pin", headers=STUDENT_HEADERS)
    assert pin_resp.status_code == 200
    assert pin_resp.json()["is_pinned"] is True

    unpin_resp = client.patch(f"/api/notes/{note_id}/pin", headers=STUDENT_HEADERS)
    assert unpin_resp.json()["is_pinned"] is False


def test_toggle_archive_changes_archive_state():
    create_resp = client.post("/api/notes", json={"title": "Archivable"},
                              headers=STUDENT_HEADERS)
    note_id = create_resp.json()["id"]

    archive_resp = client.patch(f"/api/notes/{note_id}/archive", headers=STUDENT_HEADERS)
    assert archive_resp.status_code == 200
    assert archive_resp.json()["is_archived"] is True


# ── Search ───────────────────────────────────────────────────

def test_search_notes_by_title():
    client.post("/api/notes", json={"title": "Django REST Framework", "content": "Python"},
                headers=STUDENT_HEADERS)
    client.post("/api/notes", json={"title": "React Hooks", "content": "JavaScript"},
                headers=STUDENT_HEADERS)

    resp = client.get("/api/notes/search?q=django", headers=STUDENT_HEADERS)
    assert resp.status_code == 200
    notes = resp.json()
    assert any("Django" in n["title"] for n in notes)
    assert all("React" not in n["title"] for n in notes)


def test_search_notes_by_content():
    client.post("/api/notes", json={"title": "Note", "content": "unique-search-term-xyz"},
                headers=STUDENT_HEADERS)

    resp = client.get("/api/notes/search?q=unique-search-term-xyz", headers=STUDENT_HEADERS)
    assert resp.status_code == 200
    assert len(resp.json()) >= 1


# ── Directories ──────────────────────────────────────────────

def test_get_directories_always_includes_general():
    resp = client.get("/api/notes/directories", headers=STUDENT_HEADERS)
    assert resp.status_code == 200
    assert "General" in resp.json()


def test_get_directories_lists_custom_directories():
    client.post("/api/notes", json={"title": "Work Note", "directory": "Work"},
                headers=STUDENT_HEADERS)
    client.post("/api/notes", json={"title": "Study Note", "directory": "Study"},
                headers=STUDENT_HEADERS)

    resp = client.get("/api/notes/directories", headers=STUDENT_HEADERS)
    dirs = resp.json()
    assert "Work" in dirs
    assert "Study" in dirs
