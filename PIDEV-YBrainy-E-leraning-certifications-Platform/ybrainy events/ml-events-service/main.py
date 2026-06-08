import os
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from schemas import (
    HealthResponse,
    RecommendationRequest,
    RecommendationResponse,
    RatingPredictionRequest,
    RatingPredictionResponse,
    SentimentPredictionRequest,
    SentimentPredictionResponse,
)
from services.model_registry import ModelRegistry

try:
    import py_eureka_client.eureka_client as eureka_client
except Exception:  # pragma: no cover - optional at runtime
    eureka_client = None


model_registry = ModelRegistry()


@asynccontextmanager
async def lifespan(_: FastAPI):
    if os.getenv("PRELOAD_MODELS", "true").lower() == "true":
        model_registry.warm_up()

    if (
        os.getenv("REGISTER_WITH_EUREKA", "false").lower() == "true"
        and eureka_client is not None
    ):
        await eureka_client.init_async(
            eureka_server=os.getenv("EUREKA_SERVER", "http://localhost:8761/eureka/"),
            app_name=os.getenv("EUREKA_APP_NAME", "events-ml-service"),
            instance_port=int(os.getenv("SERVICE_PORT", "9010")),
            instance_host=os.getenv("SERVICE_HOST", "localhost"),
            health_check_url=os.getenv("SERVICE_HEALTH_URL", "http://localhost:9010/health"),
        )

    yield


app = FastAPI(
    title="YBrainy MEvents ML Service",
    version="1.0.0",
    description="Standalone FastAPI microservice for event recommendation and sentiment analysis.",
    lifespan=lifespan,
)

allowed_origins = os.getenv(
    "ALLOWED_ORIGINS", "http://localhost:4200,http://127.0.0.1:4200"
).split(",")
app.add_middleware(
    CORSMiddleware,
    allow_origins=allowed_origins,
    allow_methods=["*"],
    allow_headers=["*"],
    allow_credentials=True,
)


@app.get("/health", response_model=HealthResponse)
def health() -> HealthResponse:
    return HealthResponse(
        status="ok",
        service="events-ml-service",
        models=model_registry.status(),
    )


@app.post("/api/ml/sentiment/predict", response_model=SentimentPredictionResponse)
def predict_sentiment(payload: SentimentPredictionRequest) -> SentimentPredictionResponse:
    return model_registry.predict_sentiment(payload)


@app.post(
    "/api/ml/recommendations/predict-rating",
    response_model=RatingPredictionResponse,
)
def predict_rating(payload: RatingPredictionRequest) -> RatingPredictionResponse:
    return model_registry.predict_rating(payload)


@app.post("/api/ml/recommendations/top-n", response_model=RecommendationResponse)
def recommend_top_n(payload: RecommendationRequest) -> RecommendationResponse:
    return model_registry.recommend_top_n(payload)
