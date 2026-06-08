from __future__ import annotations

from typing import Dict, List, Optional

from pydantic import BaseModel, ConfigDict, Field, field_validator


class CamelModel(BaseModel):
    model_config = ConfigDict(populate_by_name=True)


class SentimentPredictionRequest(CamelModel):
    comment: str = Field(min_length=1, max_length=5000)

    @field_validator("comment")
    @classmethod
    def validate_comment(cls, value: str) -> str:
        cleaned = value.strip()
        if not cleaned:
            raise ValueError("comment must not be blank")
        return cleaned


class SentimentPredictionResponse(CamelModel):
    comment: str
    label: str
    scores: Dict[str, float] = Field(default_factory=dict)


class RatingPredictionRequest(CamelModel):
    student_id: int = Field(alias="studentId")
    event_id: int = Field(alias="eventId")
    event_name: Optional[str] = Field(default=None, alias="eventName")
    event_type: Optional[str] = Field(default=None, alias="eventType")
    event_description: str = Field(default="", alias="eventDescription")
    event_average_rating: Optional[float] = Field(
        default=None, ge=1.0, le=5.0, alias="eventAverageRating"
    )
    history: List[StudentHistoryItem] = Field(default_factory=list)


class RatingPredictionResponse(CamelModel):
    student_id: int = Field(alias="studentId")
    event_id: int = Field(alias="eventId")
    predicted_rating: float = Field(alias="predictedRating")
    components: Dict[str, float] = Field(default_factory=dict)
    reason: str


class RecommendationCandidate(CamelModel):
    event_id: int = Field(alias="eventId")
    name: str
    type: Optional[str] = None
    description: str = ""
    location: Optional[str] = None
    date_debut: Optional[str] = Field(default=None, alias="dateDebut")
    average_rating: Optional[float] = Field(
        default=None, ge=1.0, le=5.0, alias="averageRating"
    )


class StudentHistoryItem(CamelModel):
    event_id: int = Field(alias="eventId")
    rating: float = Field(ge=1.0, le=5.0)
    description: str = ""
    event_type: Optional[str] = Field(default=None, alias="eventType")


class RecommendationRequest(CamelModel):
    student_id: int = Field(alias="studentId")
    limit: int = Field(default=5, ge=1, le=50)
    svd_weight: float = Field(default=0.6, ge=0.0, le=1.0, alias="svdWeight")
    content_weight: float = Field(
        default=0.4, ge=0.0, le=1.0, alias="contentWeight"
    )
    candidate_events: List[RecommendationCandidate] = Field(alias="candidateEvents")
    history: List[StudentHistoryItem] = Field(default_factory=list)

    @field_validator("candidate_events")
    @classmethod
    def validate_candidates(cls, value: List[RecommendationCandidate]) -> List[RecommendationCandidate]:
        if not value:
            raise ValueError("candidateEvents must not be empty")
        return value


class RecommendedEvent(CamelModel):
    event_id: int = Field(alias="eventId")
    name: str
    type: Optional[str] = None
    description: str = ""
    location: Optional[str] = None
    date_debut: Optional[str] = Field(default=None, alias="dateDebut")
    predicted_rating: float = Field(alias="predictedRating")
    preference_score: float = Field(alias="preferenceScore")
    popularity_score: float = Field(alias="popularityScore")
    content_score: float = Field(alias="contentScore")
    final_score: float = Field(alias="finalScore")
    reason: str


class RecommendationResponse(CamelModel):
    student_id: int = Field(alias="studentId")
    limit: int
    used_history_count: int = Field(alias="usedHistoryCount")
    recommendations: List[RecommendedEvent]


class HealthResponse(CamelModel):
    status: str
    service: str
    models: Dict[str, object]
