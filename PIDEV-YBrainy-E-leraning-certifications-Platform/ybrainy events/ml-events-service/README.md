# YBrainy MEvents ML Service

Standalone FastAPI microservice for the YBrainy events module.

This service is intentionally separate from the existing Java recommendation logic in `event-service`.
It exposes ML inference endpoints only:

- Sentiment analysis with the exported `LinearSVC + TF-IDF`
- Rating prediction with a lightweight request-time hybrid scoring engine
- Top-N recommendations using:
  - the student's live rating history and type preferences
  - optional event average rating from Spring Boot
  - request-time TF-IDF cosine similarity for content matching

## Important runtime design

This service does not require `events_v3.csv` or `feedback_v3.csv` at runtime.

Instead:

- the trained `.pkl` models are loaded from `./models`
- Spring Boot sends live event candidates and student history in the HTTP request
- the content-based TF-IDF profile is built on the fly from the request payload

That keeps the service independent from the training notebook and from CSV files.

## Expected model files

Place these files in [models](./models):

- `sentiment_svm.pkl`
- `tfidf_vectorizer.pkl`
- `label_encoder.pkl`

## Run locally

This version no longer depends on `scikit-surprise`, so it installs much more easily on Windows.

```powershell
cd "ybrainy events/ml-events-service"
python -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install --upgrade pip
pip install -r requirements.txt
$env:SERVICE_PORT="9010"
python start.py
```

Or from the repo root:

```powershell
.\run-services\run-events-ml-service.ps1
```

## API

### Health

`GET /health`

### Sentiment

`POST /api/ml/sentiment/predict`

```json
{
  "comment": "The event was well organized and really useful."
}
```

### Single rating prediction

`POST /api/ml/recommendations/predict-rating`

```json
{
  "studentId": 12,
  "eventId": 44,
  "eventType": "WEBINAR",
  "eventDescription": "Practical AI career guidance and live demos.",
  "eventAverageRating": 4.3,
  "history": [
    {
      "eventId": 10,
      "rating": 5,
      "description": "Hands-on workshop about machine learning projects."
    }
  ]
}
```

### Top-N recommendation

`POST /api/ml/recommendations/top-n`

```json
{
  "studentId": 12,
  "limit": 3,
  "candidateEvents": [
    {
      "eventId": 44,
      "name": "AI Career Webinar",
      "type": "WEBINAR",
      "description": "Practical AI career guidance and live demos.",
      "location": "Online",
      "averageRating": 4.3
    },
    {
      "eventId": 45,
      "name": "Cloud Hackathon",
      "type": "HACKATHON",
      "description": "Team-based cloud engineering challenge.",
      "averageRating": 4.6
    }
  ],
  "history": [
    {
      "eventId": 10,
      "rating": 5,
      "description": "Hands-on workshop about machine learning projects.",
      "eventType": "WORKSHOP"
    },
    {
      "eventId": 22,
      "rating": 4,
      "description": "Conference on AI tools for students.",
      "eventType": "CONFERENCE"
    }
  ]
}
```

## Spring Boot integration idea

Typical split of responsibility:

- `event-service` keeps ownership of event retrieval, filtering, and business rules
- `feedback-service` keeps ownership of feedback persistence
- `ml-events-service` only scores payloads sent by Spring Boot

That avoids confusion with the already existing recommendation flow in `event-service`.
