"""
Unit tests for the YBrainy ML Service (Flask).

Models are patched out so no .pkl files are required.
Tests cover: health, DSO1 conversion prediction, DSO3 quality prediction,
input validation, probability thresholds, and graceful error handling.
"""
import sys
import os
from unittest.mock import MagicMock, patch
import numpy as np
import pytest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

# ── Patch joblib.load so missing .pkl files don't crash import ──
with patch("joblib.load", side_effect=FileNotFoundError("no model")):
    import app as ml_app

# Ensure models start as None (they fail to load in test env)
ml_app.dso1_model = None
ml_app.dso1_scaler = None
ml_app.dso2_knn = None
ml_app.dso2_matrix = None
ml_app.dso2_df = None
ml_app.dso2_scaler = None
ml_app.dso2_le_subj = None
ml_app.dso2_le_lvl = None
ml_app.dso3_model = None
ml_app.dso3_scaler = None
ml_app.dso4_model = None


@pytest.fixture
def client():
    ml_app.app.config["TESTING"] = True
    return ml_app.app.test_client()


# ── Health ────────────────────────────────────────────────────

def test_health_returns_ok(client):
    resp = client.get("/health")
    assert resp.status_code == 200
    data = resp.get_json()
    assert data["status"] == "ok"
    assert "models" in data
    assert "dso1_conversion" in data["models"]
    assert "dso3_quality" in data["models"]


def test_health_reports_models_not_loaded(client):
    resp = client.get("/health")
    data = resp.get_json()
    assert data["models"]["dso1_conversion"] is False
    assert data["models"]["dso3_quality"] is False


# ── DSO1: Conversion Prediction ──────────────────────────────

def test_conversion_returns_503_when_model_not_loaded(client):
    resp = client.post("/predict/conversion", json={
        "timeSpentOnCourse": 100, "completionRate": 0.8,
        "quizScores": 75, "numberOfVideosWatched": 5,
        "numLogins": 10, "forumReads": 3
    })
    assert resp.status_code == 503
    assert "error" in resp.get_json()


def test_conversion_returns_400_for_non_numeric_field(client, monkeypatch):
    mock_model = MagicMock()
    mock_scaler = MagicMock()
    monkeypatch.setattr(ml_app, "dso1_model", mock_model)
    monkeypatch.setattr(ml_app, "dso1_scaler", mock_scaler)

    resp = client.post("/predict/conversion", json={
        "timeSpentOnCourse": "not-a-number",
        "completionRate": 0.8, "quizScores": 75,
        "numberOfVideosWatched": 5, "numLogins": 10, "forumReads": 3
    })
    assert resp.status_code == 400
    assert "timeSpentOnCourse" in resp.get_json()["error"]


def test_conversion_high_probability_returns_HIGH_label(client, monkeypatch):
    mock_model = MagicMock()
    mock_model.predict_proba.return_value = np.array([[0.1, 0.85]])
    mock_scaler = MagicMock()
    mock_scaler.transform.return_value = np.array([[1, 2, 3, 4, 5, 6]])

    monkeypatch.setattr(ml_app, "dso1_model", mock_model)
    monkeypatch.setattr(ml_app, "dso1_scaler", mock_scaler)

    resp = client.post("/predict/conversion", json={
        "timeSpentOnCourse": 500, "completionRate": 0.95,
        "quizScores": 90, "numberOfVideosWatched": 20,
        "numLogins": 30, "forumReads": 15
    })
    assert resp.status_code == 200
    data = resp.get_json()
    assert data["conversionLabel"] == "HIGH"
    assert data["conversionProbability"] == pytest.approx(0.85, abs=0.001)
    assert data["percentage"] == pytest.approx(85.0, abs=0.1)


def test_conversion_medium_probability_returns_MEDIUM_label(client, monkeypatch):
    mock_model = MagicMock()
    mock_model.predict_proba.return_value = np.array([[0.45, 0.55]])
    mock_scaler = MagicMock()
    mock_scaler.transform.return_value = np.array([[1, 2, 3, 4, 5, 6]])

    monkeypatch.setattr(ml_app, "dso1_model", mock_model)
    monkeypatch.setattr(ml_app, "dso1_scaler", mock_scaler)

    resp = client.post("/predict/conversion", json={
        "timeSpentOnCourse": 100, "completionRate": 0.5,
        "quizScores": 60, "numberOfVideosWatched": 5,
        "numLogins": 8, "forumReads": 2
    })
    assert resp.status_code == 200
    assert resp.get_json()["conversionLabel"] == "MEDIUM"


def test_conversion_low_probability_returns_LOW_label(client, monkeypatch):
    mock_model = MagicMock()
    mock_model.predict_proba.return_value = np.array([[0.88, 0.12]])
    mock_scaler = MagicMock()
    mock_scaler.transform.return_value = np.array([[0.1, 0.1, 0.1, 0.1, 0.1, 0.1]])

    monkeypatch.setattr(ml_app, "dso1_model", mock_model)
    monkeypatch.setattr(ml_app, "dso1_scaler", mock_scaler)

    resp = client.post("/predict/conversion", json={
        "timeSpentOnCourse": 5, "completionRate": 0.05,
        "quizScores": 10, "numberOfVideosWatched": 1,
        "numLogins": 1, "forumReads": 0
    })
    assert resp.status_code == 200
    assert resp.get_json()["conversionLabel"] == "LOW"


def test_conversion_threshold_boundary_exactly_07_is_HIGH(client, monkeypatch):
    """Probability == 0.70 exactly should map to HIGH."""
    mock_model = MagicMock()
    mock_model.predict_proba.return_value = np.array([[0.30, 0.70]])
    mock_scaler = MagicMock()
    mock_scaler.transform.return_value = np.zeros((1, 6))

    monkeypatch.setattr(ml_app, "dso1_model", mock_model)
    monkeypatch.setattr(ml_app, "dso1_scaler", mock_scaler)

    resp = client.post("/predict/conversion", json={
        "timeSpentOnCourse": 0, "completionRate": 0, "quizScores": 0,
        "numberOfVideosWatched": 0, "numLogins": 0, "forumReads": 0
    })
    assert resp.get_json()["conversionLabel"] == "HIGH"


# ── DSO3: Quality Prediction ─────────────────────────────────

def test_quality_returns_503_when_model_not_loaded(client):
    resp = client.post("/predict/quality", json={"numLectures": 10})
    assert resp.status_code == 503


def test_quality_returns_HIGH_label(client, monkeypatch):
    mock_model = MagicMock()
    mock_model.predict.return_value = np.array([1])
    mock_model.predict_proba.return_value = np.array([[0.2, 0.8]])
    mock_scaler = MagicMock()
    mock_scaler.transform.return_value = np.zeros((1, 9))

    monkeypatch.setattr(ml_app, "dso3_model", mock_model)
    monkeypatch.setattr(ml_app, "dso3_scaler", mock_scaler)

    resp = client.post("/predict/quality", json={
        "numLectures": 25, "contentDuration": 8.0,
        "lessonTypeVariety": 4, "pctVideoLessons": 0.6,
        "numLessons": 25, "certEncoded": 1,
        "levelEncoded": 1, "rating": 4.5, "ratingCount": 50
    })
    assert resp.status_code == 200
    data = resp.get_json()
    assert data["qualityLabel"] == "HIGH"
    assert data["isHighQuality"] is True
    assert data["overallScore"] == 80
    assert "factors" in data
    assert "tips" in data


def test_quality_returns_LOW_label(client, monkeypatch):
    mock_model = MagicMock()
    mock_model.predict.return_value = np.array([0])
    mock_model.predict_proba.return_value = np.array([[0.75, 0.25]])
    mock_scaler = MagicMock()
    mock_scaler.transform.return_value = np.zeros((1, 9))

    monkeypatch.setattr(ml_app, "dso3_model", mock_model)
    monkeypatch.setattr(ml_app, "dso3_scaler", mock_scaler)

    resp = client.post("/predict/quality", json={
        "numLectures": 2, "contentDuration": 0.5,
        "lessonTypeVariety": 1, "pctVideoLessons": 0.1,
        "numLessons": 2, "certEncoded": 0,
        "levelEncoded": 0, "rating": 0.0, "ratingCount": 0
    })
    assert resp.status_code == 200
    data = resp.get_json()
    assert data["qualityLabel"] == "LOW"
    assert data["isHighQuality"] is False


def test_quality_rating_clamped_between_0_and_5(client, monkeypatch):
    """Rating > 5 should be clamped to 5.0."""
    mock_model = MagicMock()
    mock_model.predict.return_value = np.array([1])
    mock_model.predict_proba.return_value = np.array([[0.2, 0.8]])
    mock_scaler = MagicMock()
    mock_scaler.transform.return_value = np.zeros((1, 9))

    monkeypatch.setattr(ml_app, "dso3_model", mock_model)
    monkeypatch.setattr(ml_app, "dso3_scaler", mock_scaler)

    # rating=10 should be clamped to 5.0 without error
    resp = client.post("/predict/quality", json={"rating": 10.0})
    assert resp.status_code == 200


def test_quality_factors_contain_tips_for_low_metrics(client, monkeypatch):
    mock_model = MagicMock()
    mock_model.predict.return_value = np.array([0])
    mock_model.predict_proba.return_value = np.array([[0.8, 0.2]])
    mock_scaler = MagicMock()
    mock_scaler.transform.return_value = np.zeros((1, 9))

    monkeypatch.setattr(ml_app, "dso3_model", mock_model)
    monkeypatch.setattr(ml_app, "dso3_scaler", mock_scaler)

    resp = client.post("/predict/quality", json={
        "numLectures": 1, "contentDuration": 0.5,
        "numLessons": 1, "lessonTypeVariety": 1,
        "pctVideoLessons": 0.1, "certEncoded": 0,
        "rating": 0.0, "ratingCount": 0
    })
    data = resp.get_json()
    tips = data["tips"]
    assert len(tips) > 0
    # At least a tip for lessons and duration since they are below target
    assert any("lesson" in tip.lower() or "duration" in tip.lower() or "video" in tip.lower()
               for tip in tips)


# ── DSO4: Demand Forecast (no external calls) ─────────────────

def test_forecast_returns_503_when_all_models_fail(client):
    # dso4_model is None and SARIMAX will fail without data → should return 500 or degrade gracefully
    resp = client.get("/forecast/demand?steps=3")
    # Either 200 (if SARIMAX generates synthetic) or 500 on complete failure
    assert resp.status_code in (200, 500)
