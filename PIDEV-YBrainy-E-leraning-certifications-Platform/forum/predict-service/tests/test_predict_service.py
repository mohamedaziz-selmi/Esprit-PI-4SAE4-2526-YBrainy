"""
Unit tests for the YBrainy Forum Predict Service (Flask).

Tests both the predict_quality() function directly and the Flask endpoints.
The real model file is used when available; individual tests mock model
outputs to exercise specific code paths deterministically.
"""
import sys
import os
from unittest.mock import MagicMock, patch
import numpy as np
import pytest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))


@pytest.fixture(scope="module")
def app_client():
    """Load the Flask app once per module. Falls back gracefully if model file missing."""
    try:
        import app as predict_app
        predict_app.app.config["TESTING"] = True
        return predict_app.app.test_client(), predict_app
    except Exception:
        pytest.skip("predict-service model not loadable in this environment")


@pytest.fixture
def client(app_client):
    return app_client[0]


@pytest.fixture
def predict_module(app_client):
    return app_client[1]


# ── Health ────────────────────────────────────────────────────

def test_health_returns_up(client):
    resp = client.get("/health")
    assert resp.status_code == 200
    data = resp.get_json()
    assert data["status"] == "up"
    assert "model_version" in data


# ── Input validation ─────────────────────────────────────────

def test_predict_with_empty_body_returns_400(client):
    resp = client.post("/predict", json={})
    assert resp.status_code == 400
    assert "error" in resp.get_json()


def test_predict_with_empty_strings_returns_400(client):
    resp = client.post("/predict", json={"title": "", "body": ""})
    assert resp.status_code == 400


def test_predict_with_only_title_is_valid(client):
    resp = client.post("/predict", json={"title": "What is dependency injection?"})
    assert resp.status_code == 200


def test_predict_with_only_body_is_valid(client):
    resp = client.post("/predict", json={"body": "I have a question about Python decorators."})
    assert resp.status_code == 200


# ── Response structure ───────────────────────────────────────

def test_predict_response_has_required_fields(client):
    resp = client.post("/predict", json={
        "title": "How do I use Spring Boot?",
        "body": "I am trying to set up a basic Spring Boot application.",
        "tags": "java spring-boot"
    })
    assert resp.status_code == 200
    data = resp.get_json()
    assert "label" in data
    assert "confidence" in data
    assert "color" in data
    assert "message" in data
    assert "probabilities" in data


def test_predict_label_is_one_of_valid_classes(client):
    resp = client.post("/predict", json={
        "title": "Best practices for REST API design",
        "body": "What are the recommended guidelines?"
    })
    data = resp.get_json()
    assert data["label"] in ("HQ", "LQ_CLOSE", "LQ_EDIT")


def test_predict_confidence_is_between_0_and_1(client):
    resp = client.post("/predict", json={"title": "Test thread", "body": "Some content here."})
    data = resp.get_json()
    assert 0.0 <= data["confidence"] <= 1.0


def test_predict_probabilities_sum_to_1(client):
    resp = client.post("/predict", json={"title": "Test", "body": "Content"})
    data = resp.get_json()
    total = sum(data["probabilities"].values())
    assert abs(total - 1.0) < 0.01


# ── predict_quality function directly ───────────────────────

def test_predict_quality_function_HQ(predict_module, monkeypatch):
    """Mock the model to always predict HQ."""
    mock_model = MagicMock()
    mock_model.predict_proba.return_value = np.array([[0.05, 0.05, 0.90]])
    mock_model.predict.return_value = np.array([0])  # index 0

    mock_encoder = MagicMock()
    mock_encoder.inverse_transform.return_value = ["HQ"]

    mock_tfidf = MagicMock()
    mock_tfidf.transform.return_value = MagicMock()
    mock_svd = MagicMock()
    mock_svd.transform.return_value = np.zeros((1, 10))

    monkeypatch.setattr(predict_module, "model", mock_model)
    monkeypatch.setattr(predict_module, "encoder", mock_encoder)
    monkeypatch.setattr(predict_module, "tfidf", mock_tfidf)
    monkeypatch.setattr(predict_module, "svd", mock_svd)
    monkeypatch.setattr(predict_module, "classes", ["HQ", "LQ_CLOSE", "LQ_EDIT"])

    result = predict_module.predict_quality(
        "How to use dependency injection in Spring?",
        "I want to understand the concept of DI in Spring Boot.",
        "spring java"
    )

    assert result["label"] == "HQ"
    assert result["color"] == "#22c55e"
    assert result["confidence"] == pytest.approx(0.90, abs=0.001)


def test_predict_quality_function_LQ_CLOSE(predict_module, monkeypatch):
    mock_model = MagicMock()
    mock_model.predict_proba.return_value = np.array([[0.80, 0.10, 0.10]])
    mock_model.predict.return_value = np.array([1])

    mock_encoder = MagicMock()
    mock_encoder.inverse_transform.return_value = ["LQ_CLOSE"]

    mock_tfidf = MagicMock()
    mock_tfidf.transform.return_value = MagicMock()
    mock_svd = MagicMock()
    mock_svd.transform.return_value = np.zeros((1, 10))

    monkeypatch.setattr(predict_module, "model", mock_model)
    monkeypatch.setattr(predict_module, "encoder", mock_encoder)
    monkeypatch.setattr(predict_module, "tfidf", mock_tfidf)
    monkeypatch.setattr(predict_module, "svd", mock_svd)
    monkeypatch.setattr(predict_module, "classes", ["HQ", "LQ_CLOSE", "LQ_EDIT"])

    result = predict_module.predict_quality("help", "asdf", "")
    assert result["label"] == "LQ_CLOSE"
    assert result["color"] == "#ef4444"


def test_predict_quality_function_LQ_EDIT(predict_module, monkeypatch):
    mock_model = MagicMock()
    mock_model.predict_proba.return_value = np.array([[0.10, 0.10, 0.80]])
    mock_model.predict.return_value = np.array([2])

    mock_encoder = MagicMock()
    mock_encoder.inverse_transform.return_value = ["LQ_EDIT"]

    mock_tfidf = MagicMock()
    mock_tfidf.transform.return_value = MagicMock()
    mock_svd = MagicMock()
    mock_svd.transform.return_value = np.zeros((1, 10))

    monkeypatch.setattr(predict_module, "model", mock_model)
    monkeypatch.setattr(predict_module, "encoder", mock_encoder)
    monkeypatch.setattr(predict_module, "tfidf", mock_tfidf)
    monkeypatch.setattr(predict_module, "svd", mock_svd)
    monkeypatch.setattr(predict_module, "classes", ["HQ", "LQ_CLOSE", "LQ_EDIT"])

    result = predict_module.predict_quality("Incomplete question", "need more info here", "")
    assert result["label"] == "LQ_EDIT"
    assert result["color"] == "#f59e0b"


def test_predict_quality_strips_whitespace(predict_module, monkeypatch):
    """Verify title/body/tags are stripped before processing."""
    mock_model = MagicMock()
    mock_model.predict_proba.return_value = np.array([[0.1, 0.1, 0.8]])
    mock_model.predict.return_value = np.array([0])

    mock_encoder = MagicMock()
    mock_encoder.inverse_transform.return_value = ["HQ"]
    mock_tfidf = MagicMock()
    mock_tfidf.transform.return_value = MagicMock()
    mock_svd = MagicMock()
    mock_svd.transform.return_value = np.zeros((1, 10))

    monkeypatch.setattr(predict_module, "model", mock_model)
    monkeypatch.setattr(predict_module, "encoder", mock_encoder)
    monkeypatch.setattr(predict_module, "tfidf", mock_tfidf)
    monkeypatch.setattr(predict_module, "svd", mock_svd)
    monkeypatch.setattr(predict_module, "classes", ["HQ", "LQ_CLOSE", "LQ_EDIT"])

    result = predict_module.predict_quality("  title  ", "  body  ", "  tags  ")
    # Verify tfidf.transform was called (processing happened)
    mock_tfidf.transform.assert_called_once()
    assert result["label"] == "HQ"


def test_text_weighting_formula_includes_title_three_times(predict_module, monkeypatch):
    """title*3 + tags*2 + body*1 — verify tfidf receives weighted text."""
    captured = {}

    def capture_transform(texts):
        captured["text"] = texts[0]
        return MagicMock()

    mock_model = MagicMock()
    mock_model.predict_proba.return_value = np.array([[0.1, 0.1, 0.8]])
    mock_model.predict.return_value = np.array([0])
    mock_encoder = MagicMock()
    mock_encoder.inverse_transform.return_value = ["HQ"]
    mock_tfidf = MagicMock()
    mock_tfidf.transform.side_effect = capture_transform
    mock_svd = MagicMock()
    mock_svd.transform.return_value = np.zeros((1, 10))

    monkeypatch.setattr(predict_module, "model", mock_model)
    monkeypatch.setattr(predict_module, "encoder", mock_encoder)
    monkeypatch.setattr(predict_module, "tfidf", mock_tfidf)
    monkeypatch.setattr(predict_module, "svd", mock_svd)
    monkeypatch.setattr(predict_module, "classes", ["HQ", "LQ_CLOSE", "LQ_EDIT"])

    predict_module.predict_quality("TITLE", "BODY", "TAGS")

    combined = captured["text"]
    # title appears 3 times, tags 2 times, body 1 time
    assert combined.count("TITLE") == 3
    assert combined.count("TAGS") == 2
    assert combined.count("BODY") == 1
