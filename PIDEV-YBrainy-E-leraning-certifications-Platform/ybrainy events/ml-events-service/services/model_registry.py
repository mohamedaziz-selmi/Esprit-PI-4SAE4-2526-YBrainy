import os
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, List

import joblib
import numpy as np
from fastapi import HTTPException
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.metrics.pairwise import cosine_similarity

from schemas import (
    RecommendationRequest,
    RecommendationResponse,
    RecommendedEvent,
    RatingPredictionRequest,
    RatingPredictionResponse,
    SentimentPredictionRequest,
    SentimentPredictionResponse,
)


@dataclass(frozen=True)
class ModelPaths:
    base_dir: Path
    sentiment_model: Path
    sentiment_vectorizer: Path
    label_encoder: Path


class ModelRegistry:
    def __init__(self) -> None:
        base_dir = Path(
            os.getenv(
                "MODEL_DIR",
                Path(__file__).resolve().parent.parent / "models",
            )
        )
        self.paths = ModelPaths(
            base_dir=base_dir,
            sentiment_model=Path(
                os.getenv("SENTIMENT_MODEL_PATH", base_dir / "sentiment_svm.pkl")
            ),
            sentiment_vectorizer=Path(
                os.getenv("SENTIMENT_VECTORIZER_PATH", base_dir / "tfidf_vectorizer.pkl")
            ),
            label_encoder=Path(
                os.getenv("LABEL_ENCODER_PATH", base_dir / "label_encoder.pkl")
            ),
        )
        self._sentiment_model = None
        self._sentiment_vectorizer = None
        self._label_encoder = None

    def warm_up(self) -> None:
        missing = self._missing_files()
        if missing:
            return

        try:
            self._load_sentiment_assets()
        except Exception:
            # Delay surfacing until a real request asks for the affected model.
            return

    def status(self) -> Dict[str, object]:
        missing = self._missing_files()
        return {
            "modelDir": str(self.paths.base_dir),
            "missingFiles": missing,
            "sentimentReady": not any(
                path in missing
                for path in (
                    str(self.paths.sentiment_model),
                    str(self.paths.sentiment_vectorizer),
                    str(self.paths.label_encoder),
                )
            ),
            "recommendationReady": True,
            "recommendationEngine": "request-time hybrid heuristic",
        }

    def predict_sentiment(
        self, payload: SentimentPredictionRequest
    ) -> SentimentPredictionResponse:
        self._load_sentiment_assets()

        vector = self._sentiment_vectorizer.transform([payload.comment])
        encoded_prediction = self._sentiment_model.predict(vector)[0]
        label = self._label_encoder.inverse_transform([encoded_prediction])[0]

        scores = {}
        if hasattr(self._sentiment_model, "decision_function"):
            decision_values = self._sentiment_model.decision_function(vector)
            flattened = np.asarray(decision_values).reshape(-1)
            classes = [str(item) for item in self._label_encoder.classes_]
            scores = {
                class_name: float(np.round(score, 6))
                for class_name, score in zip(classes, flattened, strict=False)
            }

        return SentimentPredictionResponse(
            comment=payload.comment,
            label=str(label),
            scores=scores,
        )

    def predict_rating(
        self, payload: RatingPredictionRequest
    ) -> RatingPredictionResponse:
        prediction = self._score_recommendation_candidate(
            event_id=payload.event_id,
            event_type=payload.event_type,
            event_description=payload.event_description,
            event_average_rating=payload.event_average_rating,
            history=payload.history,
        )
        return RatingPredictionResponse(
            studentId=payload.student_id,
            eventId=payload.event_id,
            predictedRating=prediction["predicted_rating"],
            components={
                "preferenceScore": prediction["preference_score"],
                "popularityScore": prediction["popularity_score"],
                "contentScore": prediction["content_score"],
                "finalScore": prediction["final_score"],
            },
            reason=prediction["reason"],
        )

    def recommend_top_n(
        self, payload: RecommendationRequest
    ) -> RecommendationResponse:
        total_weight = payload.svd_weight + payload.content_weight
        if total_weight == 0:
            raise HTTPException(
                status_code=400,
                detail="svdWeight and contentWeight cannot both be zero",
            )

        normalized_preference_weight = payload.svd_weight / total_weight
        normalized_content_weight = payload.content_weight / total_weight

        seen_event_ids = {item.event_id for item in payload.history}
        candidate_events = [
            candidate
            for candidate in payload.candidate_events
            if candidate.event_id not in seen_event_ids
        ]

        if not candidate_events:
            return RecommendationResponse(
                studentId=payload.student_id,
                limit=payload.limit,
                usedHistoryCount=0,
                recommendations=[],
            )

        liked_history = [
            item.description.strip()
            for item in payload.history
            if item.rating >= 4 and item.description and item.description.strip()
        ]

        recommendations: List[RecommendedEvent] = []
        for candidate in candidate_events:
            prediction = self._score_recommendation_candidate(
                event_id=candidate.event_id,
                event_type=candidate.type,
                event_description=candidate.description,
                event_average_rating=candidate.average_rating,
                history=payload.history,
                preference_weight=normalized_preference_weight,
                content_weight=normalized_content_weight,
            )

            recommendations.append(
                RecommendedEvent(
                    eventId=candidate.event_id,
                    name=candidate.name,
                    type=candidate.type,
                    description=candidate.description,
                    location=candidate.location,
                    dateDebut=candidate.date_debut,
                    predictedRating=prediction["predicted_rating"],
                    preferenceScore=prediction["preference_score"],
                    popularityScore=prediction["popularity_score"],
                    contentScore=prediction["content_score"],
                    finalScore=prediction["final_score"],
                    reason=prediction["reason"],
                )
            )

        recommendations.sort(key=lambda item: item.final_score, reverse=True)
        recommendations = recommendations[: payload.limit]

        return RecommendationResponse(
            studentId=payload.student_id,
            limit=payload.limit,
            usedHistoryCount=len(liked_history),
            recommendations=recommendations,
        )

    def _score_recommendation_candidate(
        self,
        event_id: int,
        event_type: str | None,
        event_description: str,
        event_average_rating: float | None,
        history,
        preference_weight: float = 0.6,
        content_weight: float = 0.4,
    ) -> Dict[str, float | str]:
        content_scores = self._build_content_scores(
            [
                item.description.strip()
                for item in history
                if item.rating >= 4 and item.description and item.description.strip()
            ],
            [
                type(
                    "Candidate",
                    (),
                    {"event_id": event_id, "description": event_description or ""},
                )()
            ],
        )
        content_score = float(content_scores.get(event_id, 0.0))
        preference_score = self._compute_preference_score(history, event_type)
        popularity_score = self._compute_popularity_score(event_average_rating)
        base_rating = self._compute_base_rating(history, event_average_rating)

        preference_component = (preference_score * 0.7) + (popularity_score * 0.3)
        final_score = float(
            np.round(
                (preference_component * preference_weight)
                + (content_score * content_weight),
                6,
            )
        )
        predicted_rating = float(
            np.round(np.clip((base_rating * 0.65) + (final_score * 5.0 * 0.35), 1.0, 5.0), 4)
        )

        return {
            "predicted_rating": predicted_rating,
            "preference_score": float(np.round(preference_score, 6)),
            "popularity_score": float(np.round(popularity_score, 6)),
            "content_score": float(np.round(content_score, 6)),
            "final_score": final_score,
            "reason": self._generate_reason(
                preference_score=preference_score,
                popularity_score=popularity_score,
                content_score=content_score,
                event_type=event_type,
            ),
        }

    def _compute_base_rating(self, history, event_average_rating: float | None) -> float:
        if history:
            history_mean = float(np.mean([item.rating for item in history]))
        else:
            history_mean = 3.5

        if event_average_rating is None:
            return history_mean

        return float(np.clip((history_mean * 0.6) + (event_average_rating * 0.4), 1.0, 5.0))

    def _compute_preference_score(self, history, event_type: str | None) -> float:
        if not history:
            return 0.5

        overall_mean = float(np.mean([item.rating for item in history])) / 5.0
        if not event_type:
            return float(np.clip(overall_mean, 0.0, 1.0))

        typed_ratings = [
            item.rating
            for item in history
            if getattr(item, "event_type", None) and str(item.event_type).upper() == str(event_type).upper()
        ]
        if not typed_ratings:
            return float(np.clip(overall_mean, 0.0, 1.0))

        type_mean = float(np.mean(typed_ratings)) / 5.0
        return float(np.clip((type_mean * 0.75) + (overall_mean * 0.25), 0.0, 1.0))

    def _compute_popularity_score(self, event_average_rating: float | None) -> float:
        if event_average_rating is None:
            return 0.5
        return float(np.clip(event_average_rating / 5.0, 0.0, 1.0))

    def _build_content_scores(self, liked_history: List[str], candidate_events) -> Dict[int, float]:
        if not liked_history:
            return {}

        candidate_descriptions = [candidate.description.strip() for candidate in candidate_events]
        if not any(candidate_descriptions):
            return {}

        corpus = liked_history + candidate_descriptions
        vectorizer = TfidfVectorizer(stop_words="english", max_features=200)
        tfidf_matrix = vectorizer.fit_transform(corpus)
        history_matrix = tfidf_matrix[: len(liked_history)]
        candidate_matrix = tfidf_matrix[len(liked_history) :]

        profile_vector = np.asarray(history_matrix.mean(axis=0))
        similarity_scores = cosine_similarity(profile_vector, candidate_matrix).flatten()

        return {
            candidate.event_id: float(np.clip(score, 0.0, 1.0))
            for candidate, score in zip(candidate_events, similarity_scores, strict=False)
        }

    def _generate_reason(
        self,
        preference_score: float,
        popularity_score: float,
        content_score: float,
        event_type: str | None,
    ) -> str:
        if content_score >= 0.7:
            return "Strong content match with events the student rated highly."
        if preference_score >= 0.75:
            return "Strong match with the student's rating history and preferences."
        if popularity_score >= 0.8:
            return "Highly rated by other students and aligned with the current profile."
        if event_type:
            return f"Recommended for this student's profile among available {event_type} events."
        return "Recommended from the student's profile and current candidate set."

    def _load_sentiment_assets(self) -> None:
        self._ensure_exists(
            self.paths.sentiment_model,
            "Sentiment model file not found. Add sentiment_svm.pkl to the models directory.",
        )
        self._ensure_exists(
            self.paths.sentiment_vectorizer,
            "TF-IDF vectorizer file not found. Add tfidf_vectorizer.pkl to the models directory.",
        )
        self._ensure_exists(
            self.paths.label_encoder,
            "Label encoder file not found. Add label_encoder.pkl to the models directory.",
        )

        if self._sentiment_model is None:
            self._sentiment_model = joblib.load(self.paths.sentiment_model)
        if self._sentiment_vectorizer is None:
            self._sentiment_vectorizer = joblib.load(self.paths.sentiment_vectorizer)
        if self._label_encoder is None:
            self._label_encoder = joblib.load(self.paths.label_encoder)

    def _missing_files(self) -> List[str]:
        return [
            str(path)
            for path in (
                self.paths.sentiment_model,
                self.paths.sentiment_vectorizer,
                self.paths.label_encoder,
            )
            if not path.exists()
        ]

    def _ensure_exists(self, path: Path, message: str) -> None:
        if not path.exists():
            raise HTTPException(status_code=503, detail=message)
