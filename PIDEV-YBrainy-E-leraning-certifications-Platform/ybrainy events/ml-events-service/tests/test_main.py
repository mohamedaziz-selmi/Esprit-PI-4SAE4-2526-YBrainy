from fastapi.testclient import TestClient

import main


class FakeVectorizer:
    def transform(self, texts):
        return texts


class FakeSentimentModel:
    def predict(self, vectors):
        return [2]

    def decision_function(self, vectors):
        return [[-0.2, 0.1, 0.8]]


class FakeLabelEncoder:
    classes_ = ["negative", "neutral", "positive"]

    def inverse_transform(self, values):
        mapping = {0: "negative", 1: "neutral", 2: "positive"}
        return [mapping[value] for value in values]


def setup_module():
    registry = main.model_registry
    registry._sentiment_model = FakeSentimentModel()
    registry._sentiment_vectorizer = FakeVectorizer()
    registry._label_encoder = FakeLabelEncoder()
    registry._ensure_exists = lambda path, message: None


client = TestClient(main.app)


def test_health():
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json()["service"] == "events-ml-service"


def test_predict_sentiment():
    response = client.post(
        "/api/ml/sentiment/predict",
        json={"comment": "Very helpful event"},
    )

    assert response.status_code == 200
    data = response.json()
    assert data["label"] == "positive"
    assert "positive" in data["scores"]


def test_predict_rating():
    response = client.post(
        "/api/ml/recommendations/predict-rating",
        json={
            "studentId": 7,
            "eventId": 101,
            "eventType": "WORKSHOP",
            "eventDescription": "Hands-on AI workshop",
            "eventAverageRating": 4.6,
            "history": [
                {
                    "eventId": 88,
                    "rating": 5,
                    "eventType": "WORKSHOP",
                    "description": "Practical AI lab with projects",
                }
            ],
        },
    )

    assert response.status_code == 200
    assert response.json()["predictedRating"] >= 4.0


def test_recommend_top_n_filters_seen_events_and_sorts():
    response = client.post(
        "/api/ml/recommendations/top-n",
        json={
            "studentId": 7,
            "limit": 2,
            "candidateEvents": [
                {
                    "eventId": 101,
                    "name": "AI Workshop",
                    "type": "WORKSHOP",
                    "description": "Hands-on AI workshop",
                },
                {
                    "eventId": 202,
                    "name": "Networking Night",
                    "type": "NETWORKING",
                    "description": "Meet professionals and alumni",
                },
            ],
            "history": [
                {
                    "eventId": 202,
                    "rating": 5,
                    "eventType": "NETWORKING",
                    "description": "Great networking and hands-on practice",
                }
            ],
        },
    )

    assert response.status_code == 200
    data = response.json()
    assert len(data["recommendations"]) == 1
    assert data["recommendations"][0]["eventId"] == 101
