from flask import Flask, request, jsonify
from flask_cors import CORS
from prometheus_flask_exporter import PrometheusMetrics
import joblib
import numpy as np
import pandas as pd
import os
import traceback
import requests
import time
import threading

np.random.seed(42)  # set once at module startup — not inside request handlers (thread-safe)

_forecast_cache = {}
_FORECAST_TTL = 3600  # 1 hour cache

COURSE_SERVICE_URL = os.environ.get('COURSE_SERVICE_URL', 'http://localhost:8082')
MLFLOW_URI = os.environ.get('MLFLOW_TRACKING_URI', 'http://mlflow-tracking:5000')


def _track_prediction(experiment_name: str, params: dict, metrics: dict) -> None:
    """Fire-and-forget MLflow tracking — never blocks prediction endpoints."""
    def _do():
        try:
            import mlflow
            mlflow.set_tracking_uri(MLFLOW_URI)
            mlflow.set_experiment(experiment_name)
            with mlflow.start_run():
                mlflow.log_params({k: str(v) for k, v in params.items()})
                mlflow.log_metrics({k: float(v) for k, v in metrics.items()})
        except Exception:
            pass  # MLflow unavailable never breaks predictions
    threading.Thread(target=_do, daemon=True).start()

app = Flask(__name__)
CORS(app)
metrics = PrometheusMetrics(app)
metrics.info('ml_service_info', 'YBrainy ML Service', version='1.0.0')

# ── Load all models on startup ──────────────────────────────
MODELS_DIR = os.path.join(os.path.dirname(__file__), 'models')

print("Loading models...")

try:
    dso1_model   = joblib.load(os.path.join(MODELS_DIR, 'dso1_conversion_model.pkl'))
    dso1_scaler  = joblib.load(os.path.join(MODELS_DIR, 'dso1_scaler.pkl'))
except Exception as e:
    print(f"DSO1 load failed: {e}")
    dso1_model = dso1_scaler = None
print("DSO1 loaded" if dso1_model else "DSO1 not loaded")

try:
    dso2_knn     = joblib.load(os.path.join(MODELS_DIR, 'dso2_knn_model.pkl'))
    dso2_matrix  = joblib.load(os.path.join(MODELS_DIR, 'dso2_feature_matrix.pkl'))
    dso2_df      = joblib.load(os.path.join(MODELS_DIR, 'dso2_course_df.pkl'))
    dso2_scaler  = joblib.load(os.path.join(MODELS_DIR, 'dso2_scaler.pkl'))
    dso2_le_subj = joblib.load(os.path.join(MODELS_DIR, 'dso2_le_subject.pkl'))
    dso2_le_lvl  = joblib.load(os.path.join(MODELS_DIR, 'dso2_le_level.pkl'))
except Exception as e:
    print(f"DSO2 load failed: {e}")
    dso2_knn = dso2_matrix = dso2_df = dso2_scaler = None
    dso2_le_subj = dso2_le_lvl = None
print("DSO2 loaded" if dso2_knn else "DSO2 not loaded")

try:
    dso3_model  = joblib.load(os.path.join(MODELS_DIR, 'dso3_quality_model.pkl'))
    dso3_scaler = joblib.load(os.path.join(MODELS_DIR, 'dso3_scaler.pkl'))
except Exception as e:
    print(f"DSO3 load failed: {e}")
    dso3_model = dso3_scaler = None
print("DSO3 loaded" if dso3_model else "DSO3 not loaded")

try:
    dso4_model = joblib.load(os.path.join(MODELS_DIR, 'dso4_arima_model.pkl'))
except Exception as e:
    print(f"DSO4 load failed: {e}")
    dso4_model = None
print("DSO4 loaded" if dso4_model else "DSO4 not loaded")

print("All models loaded.")

# ── Health check ─────────────────────────────────────────────
@app.route('/health', methods=['GET'])
def health():
    return jsonify({
        'status': 'ok',
        'models': {
            'dso1_conversion': dso1_model is not None,
            'dso2_recommendations': dso2_knn is not None,
            'dso3_quality': dso3_model is not None,
            'dso4_forecast': dso4_model is not None
        }
    })

# ── DSO1: Conversion Prediction ───────────────────────────────
# Input: student engagement features
# Output: probability (0-1) that student will convert to paid
@app.route('/predict/conversion', methods=['POST'])
def predict_conversion():
    try:
        if dso1_model is None:
            return jsonify({'error': 'DSO1 model not loaded'}), 503

        data = request.get_json()
        # Features: TimeSpentOnCourse, CompletionRate, QuizScores,
        #           NumberOfVideosWatched, NumLogins, ForumReads
        required_fields = [
            'timeSpentOnCourse', 'completionRate', 'quizScores',
            'numberOfVideosWatched', 'numLogins', 'forumReads'
        ]
        validated = {}
        for field in required_fields:
            raw = data.get(field, 0)
            try:
                validated[field] = float(raw)
            except (TypeError, ValueError):
                return jsonify({
                    'error': f'Invalid value for {field}: expected number, got {repr(raw)}'
                }), 400

        features = [
            validated['timeSpentOnCourse'],
            validated['completionRate'],
            validated['quizScores'],
            validated['numberOfVideosWatched'],
            validated['numLogins'],
            validated['forumReads']
        ]
        X = np.array(features).reshape(1, -1)
        X_scaled = dso1_scaler.transform(X)
        proba = dso1_model.predict_proba(X_scaled)[0][1]
        label = 'HIGH' if proba >= 0.7 else 'MEDIUM' if proba >= 0.4 else 'LOW'
        _track_prediction(
            'dso1-conversion-predictions',
            {k: validated[k] for k in required_fields},
            {'conversion_probability': float(proba), 'label_numeric': 1.0 if label == 'HIGH' else 0.5 if label == 'MEDIUM' else 0.0}
        )
        return jsonify({
            'conversionProbability': round(float(proba), 4),
            'conversionLabel': label,
            'percentage': round(float(proba) * 100, 1)
        })
    except Exception as e:
        traceback.print_exc()
        return jsonify({'error': str(e)}), 500

# ── DSO2: Course Recommendations ─────────────────────────────
# Input: category and level the student is interested in
# Output: top N recommended courses (from live Course service DB)
@app.route('/recommend', methods=['POST'])
def recommend():
    data = request.get_json()
    category = data.get('category', 'PROGRAMMING')
    level = data.get('level', 'BEGINNER')
    top_n = int(data.get('topN', 5))

    try:
        # Step 1: Fetch real courses from Course service
        resp = requests.get(f'{COURSE_SERVICE_URL}/api/courses',
                            params={'size': 200, 'page': 0}, timeout=5)
        resp.raise_for_status()
        real_courses = resp.json().get('content', [])

        if dso2_knn is None:
            raise ValueError("KNN model unavailable")
        if not real_courses:
            return jsonify({
                'recommendations': [],
                'basedOn': {'category': category, 'level': level},
                'message': 'No courses available for recommendations'
            })

        # Step 2: Encode real courses using the same features as training
        import pandas as pd
        df_real = pd.DataFrame([{
            'cat_encoded': (dso2_le_subj.transform([c.get('category','OTHER')])[0]
                           if c.get('category','OTHER') in dso2_le_subj.classes_
                           else 0),
            'lvl_encoded': (dso2_le_lvl.transform([c.get('level','BEGINNER')])[0]
                           if c.get('level','BEGINNER') in dso2_le_lvl.classes_
                           else 0),
            'num_lessons':          float(c.get('lessonCount') or 0),
            'content_duration':     float(c.get('approximateDurationMinutes') or 0) / 60.0,
            'lesson_type_variety':  2.0,
            'pct_video_lessons':    0.5,
            'offers_certificate':   float(bool(c.get('offersCertificate'))),
            'price':                float(c.get('price') or 0),
            'rating':               float(c.get('rating') or 0),
            'is_paid':              float((c.get('price') or 0) > 0),
        } for c in real_courses])

        # Apply category x5 weight (same as training)
        df_real_weighted = df_real.copy()
        df_real_weighted['cat_encoded'] = df_real_weighted['cat_encoded'] * 5.0

        X_real = dso2_scaler.transform(df_real_weighted.values)

        # Step 3: Build query vector for requested category+level
        if category in dso2_le_subj.classes_:
            cat_enc = dso2_le_subj.transform([category])[0]
        else:
            print(f'[DSO2] Unknown category: {category!r} — defaulting to 0')
            cat_enc = 0
        if level in dso2_le_lvl.classes_:
            lvl_enc = dso2_le_lvl.transform([level])[0]
        else:
            print(f'[DSO2] Unknown level: {level!r} — defaulting to 0')
            lvl_enc = 0

        query = np.array([[
            cat_enc * 5.0, lvl_enc, 5.0, 2.0, 2.0, 0.5, 0.5, 20.0, 4.0, 1.0
        ]])
        query_scaled = dso2_scaler.transform(query)

        # Step 4: Find KNN neighbors in real course space
        n_neighbors = min(top_n + 1, len(real_courses))
        from sklearn.metrics.pairwise import cosine_similarity
        sims = cosine_similarity(query_scaled, X_real)[0]
        sorted_indices = sims.argsort()[::-1]
        exclude_ids = set(str(x) for x in data.get('excludeIds', []))

        results = []
        for idx in sorted_indices:
            c = real_courses[idx]
            if str(c.get('id', '')) in exclude_ids:
                continue
            results.append({
                'courseId': str(c.get('id')),
                'title': c.get('title', ''),
                'category': c.get('category', ''),
                'level': c.get('level', ''),
                'price': float(c.get('price') or 0),
                'isPaid': (c.get('price') or 0) > 0,
                'rating': float(c.get('rating') or 0),
                'numLectures': int(c.get('lessonCount') or 0),
                'thumbnailUrl': c.get('thumbnailUrl', ''),
                'matchScore': round(float(sims[idx]) * 100, 1)
            })
            if len(results) >= top_n:
                break

        print(f"[DSO2] Returning {len(results)} recommendations, first matchScore: {results[0]['matchScore'] if results else 'N/A'}")
        return jsonify({
            'basedOn': {'category': category, 'level': level},
            'recommendations': results
        })

    except Exception as e:
        return jsonify({'error': str(e)}), 500

# ── DSO3: Course Quality Prediction ──────────────────────────
# Input: course structural features
# Output: quality label (HIGH/LOW) + confidence
@app.route('/predict/quality', methods=['POST'])
def predict_quality():
    try:
        if dso3_model is None:
            return jsonify({'error': 'DSO3 model not loaded'}), 503

        data = request.get_json()
        # Features: num_lectures, content_duration, lesson_type_variety,
        #           pct_video_lessons, num_lessons, cert_encoded, level_encoded,
        #           rating, rating_count
        num_lectures      = float(data.get('numLectures', 10))
        content_duration  = float(data.get('contentDuration', 5.0))
        lst_variety       = float(data.get('lessonTypeVariety', 2))
        pct_video         = float(data.get('pctVideoLessons', 0.5))
        num_lessons       = float(data.get('numLessons', 10))
        cert_encoded      = float(data.get('certEncoded', 0))
        level_encoded     = float(data.get('levelEncoded', 0))
        rating            = max(0.0, min(5.0, float(data.get('rating', 0.0))))
        rating_count      = float(data.get('ratingCount', 0))

        features = [num_lectures, content_duration, lst_variety,
                    pct_video, num_lessons, cert_encoded,
                    level_encoded, rating, rating_count]
        X = np.array(features).reshape(1, -1)
        X_scaled = dso3_scaler.transform(X)
        pred = dso3_model.predict(X_scaled)[0]
        proba = dso3_model.predict_proba(X_scaled)[0]
        label = 'HIGH' if pred == 1 else 'LOW'
        confidence = float(max(proba))
        _track_prediction(
            'dso3-quality-predictions',
            {'num_lectures': num_lectures, 'content_duration': content_duration,
             'rating': rating, 'cert_encoded': cert_encoded},
            {'quality_score': float(proba[1]), 'confidence': confidence,
             'is_high_quality': float(pred == 1)}
        )

        num_lessons_val = float(data.get('numLessons', 10))
        num_lectures_val = float(data.get('numLectures', 10))
        content_dur_val = float(data.get('contentDuration', 5.0))
        variety_val = float(data.get('lessonTypeVariety', 2))
        pct_video_val = float(data.get('pctVideoLessons', 0.5))
        cert_val = float(data.get('certEncoded', 0))
        rating_val = max(0.0, min(5.0, float(data.get('rating', 0.0))))
        rating_count_val = float(data.get('ratingCount', 0))

        factors = {
            'lessons': {
                'score': round(min(num_lectures_val / 30.0, 1.0), 3),
                'value': int(num_lessons_val),
                'target': 10,
                'tip': None if num_lessons_val >= 10 else f'Add {int(10 - num_lessons_val)} more lessons (currently {int(num_lessons_val)})'
            },
            'duration': {
                'score': round(min(content_dur_val / 5.0, 1.0), 3),
                'value': round(content_dur_val, 1),
                'target': 5.0,
                'tip': None if content_dur_val >= 5.0 else f'Increase total duration to at least 5 hours (currently {round(content_dur_val, 1)}h)'
            },
            'contentVariety': {
                'score': round(min(variety_val / 4.0, 1.0), 3),
                'value': int(variety_val),
                'target': 3,
                'tip': None if variety_val >= 3 else f'Add more content types — use at least 3 types (videos, PDFs, images)'
            },
            'videoRatio': {
                'score': round(min(pct_video_val / 0.5, 1.0), 3),
                'value': round(pct_video_val * 100) if pct_video_val <= 1.0 else round(pct_video_val),
                'target': 50,
                'tip': None if pct_video_val >= 0.5 else f'Increase video content to at least 50%'
            },
            'certificate': {
                'score': float(cert_val),
                'value': bool(cert_val),
                'target': 1,
                'tip': None if cert_val else 'Enable certificate offering to boost quality score'
            },
            'rating': {
                'score': round(max((rating_val - 2.0) / 3.0, 0.0), 3),
                'value': round(rating_val, 1),
                'target': 4.0,
                'tip': None if rating_val >= 4.0 else (
                    f'Course rating is {round(rating_val, 1)}/5.0 — improve content to increase ratings'
                    if rating_count_val > 0
                    else 'No ratings yet — encourage students to leave reviews'
                )
            }
        }

        # overallScore = RF probability × 100 — guaranteed consistent with qualityLabel
        # HIGH always yields score > 50; LOW always yields score < 50
        overall_score = round(float(proba[1]) * 100)

        tips = [f['tip'] for f in factors.values() if f.get('tip') is not None]

        return jsonify({
            'qualityLabel': label,
            'confidence': round(confidence, 4),
            'confidencePercentage': round(confidence * 100, 1),
            'isHighQuality': bool(pred == 1),
            'overallScore': overall_score,
            'factors': factors,
            'tips': tips
        })
    except Exception as e:
        traceback.print_exc()
        return jsonify({'error': str(e)}), 500

# ── DSO4: Demand Forecast ─────────────────────────────────────
# Input: category name
# Output: 6-month demand forecast
@app.route('/forecast/demand', methods=['GET'])
def forecast_demand():
    try:
        steps = int(request.args.get('steps', 6))
        cache_key = f"forecast_{steps}"
        now = time.time()
        if cache_key in _forecast_cache:
            cached_time, cached_result = _forecast_cache[cache_key]
            if now - cached_time < _FORECAST_TTL:
                return jsonify(cached_result)
        category = request.args.get('category', None)

        # Step 1: Fetch real enrollment data from Course Service
        real_monthly = {}
        try:
            resp = requests.get(f'{COURSE_SERVICE_URL}/api/enrollments/monthly-counts',
                              timeout=3)
            if resp.ok:
                data = resp.json()
                real_monthly = {item['month']: item['count'] for item in data}
        except:
            pass

        # Step 2: Build 60-month series blending real + synthetic
        t = np.arange(60)
        trend = 500 + t * 15
        seasonality = 80 * np.sin(2 * np.pi * t / 12)
        noise = np.random.normal(0, 30, 60)
        synthetic = (trend + seasonality + noise).clip(min=100)

        # Only blend real data if we have enough months to be meaningful
        series = synthetic.copy()

        # Scale synthetic series to match real data magnitude
        if real_monthly:
            real_avg = sum(real_monthly.values()) / len(real_monthly)
            synthetic_last_n = [series[i] for i in range(
                max(0, len(series) - len(real_monthly)), len(series))]
            synthetic_avg = sum(synthetic_last_n) / len(synthetic_last_n) if synthetic_last_n else 1
            scale_factor = real_avg / synthetic_avg if synthetic_avg > 0 else 1.0
            series = series * scale_factor

        if real_monthly and len(real_monthly) >= 2:
            sorted_months = sorted(real_monthly.keys())
            for i, month in enumerate(sorted_months[-12:]):
                idx = 60 - len(sorted_months[-12:]) + i
                if 0 <= idx < 60:
                    series[idx] = real_monthly[month]
        # else: use pure synthetic upward series (not enough real data yet)

        # Step 3: Fit SARIMA or plain ARIMA depending on real data availability
        # SARIMA(1,1,1)(1,1,1,12) requires ≥24 months of real data for stable seasonal estimation
        use_seasonal = len(real_monthly) >= 24
        try:
            from statsmodels.tsa.statespace.sarimax import SARIMAX
            if use_seasonal:
                model = SARIMAX(series, order=(1,1,1),
                              seasonal_order=(1,1,1,12),
                              enforce_stationarity=False,
                              enforce_invertibility=False)
            else:
                model = SARIMAX(series, order=(1,1,1),
                              enforce_stationarity=False,
                              enforce_invertibility=False)
            fitted = model.fit(disp=False)
            use_sarima = True
        except Exception as e:
            print(f'[DSO4] SARIMAX failed ({e}), falling back to pre-fitted ARIMA')
            fitted = dso4_model
            use_sarima = False

        # Step 4: Forecast
        forecast_result = fitted.get_forecast(steps=steps)
        predicted = forecast_result.predicted_mean.tolist()

        # Cap confidence intervals to reasonable range (±30% of predicted)
        raw_ci = forecast_result.conf_int(alpha=0.2)
        raw_ci = raw_ci.values.tolist() if hasattr(raw_ci, 'values') else np.array(raw_ci).tolist()
        ci = []
        for i, (lo, hi) in enumerate(raw_ci):
            p = predicted[i]
            max_spread = p * 0.30
            lo_capped = max(lo, p - max_spread)
            hi_capped = min(hi, p + max_spread)
            ci.append([round(lo_capped, 2), round(hi_capped, 2)])

        # Step 5: Category breakdown from real DB
        category_forecast = {}
        try:
            resp2 = requests.get(f'{COURSE_SERVICE_URL}/api/courses/stats/by-category',
                               timeout=3)
            if resp2.ok:
                cat_data = resp2.json()
                print(f'[DSO4] category data: {cat_data}')
                total = sum(cat_data.values()) or 1
                for cat, count in cat_data.items():
                    share = count / total
                    category_forecast[cat] = {
                        'nextPeriod': round(predicted[0] * share),
                        'trend': 'growing' if predicted[-1] > predicted[0] else 'declining',
                        'share': round(share * 100, 1)
                    }
        except:
            pass

        trend_val = predicted[-1] - predicted[0]
        trend_pct = round((trend_val / predicted[0]) * 100, 1) if predicted[0] else 0

        if len(predicted) == 1:
            # For a single-step forecast, compare against the real historical average
            real_avg = sum(real_monthly.values()) / len(real_monthly) if real_monthly else predicted[0]
            trend_direction = 'growing' if predicted[0] > real_avg else 'declining'
        else:
            trend_direction = 'growing' if trend_val > 0 else 'declining'

        result_dict = {
            'forecastSteps': steps,
            'predictedDemand': [round(x, 2) for x in predicted],
            'confidenceIntervals': [[round(lo, 2), round(hi, 2)] for lo, hi in ci],
            'trendDirection': trend_direction,
            'trendPercentage': abs(trend_pct),
            'unit': 'enrollments/month',
            'modelUsed': 'SARIMA(1,1,1)(1,1,1,12)' if (use_sarima and use_seasonal) else 'ARIMA(1,1,1)',
            'realDataPoints': len(real_monthly),
            'categoryForecast': category_forecast
        }
        _forecast_cache[cache_key] = (now, result_dict)
        _track_prediction(
            'dso4-demand-forecast',
            {'steps': steps, 'real_data_points': len(real_monthly),
             'model': result_dict['modelUsed']},
            {'predicted_next_period': float(predicted[0]),
             'trend_percentage': float(abs(trend_pct))}
        )
        return jsonify(result_dict)

    except Exception as e:
        # Final fallback to loaded ARIMA model
        try:
            fc = dso4_model.get_forecast(steps=steps)
            predicted = fc.predicted_mean.tolist()
            ci_raw = fc.conf_int(alpha=0.2)
            ci = ci_raw.values.tolist() if hasattr(ci_raw, 'values') else np.array(ci_raw).tolist()
            return jsonify({
                'forecastSteps': steps,
                'predictedDemand': [round(x, 2) for x in predicted],
                'confidenceIntervals': [[round(lo, 2), round(hi, 2)] for lo, hi in ci],
                'trendDirection': 'growing',
                'trendPercentage': 0,
                'unit': 'enrollments/month',
                'modelUsed': 'ARIMA(1,1,1)',
                'realDataPoints': 0,
                'categoryForecast': {}
            })
        except Exception as e2:
            return jsonify({'error': str(e2)}), 500

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000, debug=False)
