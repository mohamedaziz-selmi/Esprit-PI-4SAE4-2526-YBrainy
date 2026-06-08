"""
YBrainy Dataset Exporter
Generates the same synthetic datasets used in generate_and_train.py
and saves them as CSV files for inspection / external use.
"""
import os
import pandas as pd
import numpy as np
from scipy.stats import norm as sp_norm, lognorm, beta as sp_beta
from datetime import datetime, timedelta

np.random.seed(42)

DATASETS_DIR = os.path.join(os.path.dirname(__file__), 'datasets')
os.makedirs(DATASETS_DIR, exist_ok=True)

CATEGORIES  = ['PROGRAMMING','DESIGN','BUSINESS','MARKETING',
                'PHOTOGRAPHY','MUSIC','LANGUAGE','SCIENCE','MATH','OTHER']
CAT_PROBS   = [0.25, 0.12, 0.15, 0.10, 0.05, 0.05, 0.08, 0.07, 0.05, 0.08]
LEVELS      = ['BEGINNER','INTERMEDIATE','ADVANCED']
LEVEL_PROBS = [0.42, 0.34, 0.24]

print("=" * 60)
print("YBrainy Dataset Exporter")
print("=" * 60)

# ─────────────────────────────────────────────────────────────
# 1. GENERATE 200K COURSES
# ─────────────────────────────────────────────────────────────
print("\n[1/3] Generating 200,000 courses...")
N = 200_000

category_arr = np.random.choice(CATEGORIES, N, p=CAT_PROBS)
level_arr    = np.random.choice(LEVELS, N, p=LEVEL_PROBS)

is_paid_arr  = np.random.choice([True, False], N, p=[0.92, 0.08])
price_arr    = np.where(
    is_paid_arr,
    np.clip(np.random.lognormal(2.70, 0.80, N), 9.99, 199.99).round(2),
    0.0)

_small_mask = np.random.random(N) < 0.30
num_lectures_arr = np.where(
    _small_mask,
    np.random.randint(4, 21, N),
    np.clip(np.random.lognormal(3.50, 0.80, N).astype(int), 5, 300)
)
approx_duration_arr  = np.clip(
    (num_lectures_arr * np.random.lognormal(2.5, 0.45, N)).astype(int), 10, 12000)
offers_cert_arr      = np.random.choice([True, False], N, p=[0.45, 0.55])

struct_quality = (
    np.clip(num_lectures_arr / 60.0, 0.3, 2.2) *
    np.where(offers_cert_arr, 1.35, 0.80) *
    np.where(np.isin(category_arr, ['PROGRAMMING','SCIENCE']), 1.20, 1.0))

rating_base = np.clip(3.80 + (struct_quality - struct_quality.mean()) * 0.82, 0, 5)
rating_arr  = np.clip(np.random.normal(rating_base, 0.15), 0.0, 5.0).round(1)

rating_count_arr = np.clip(
    np.random.lognormal(4.5, 1.2, N).astype(int), 0, 50000)

num_lessons_arr = np.clip(
    num_lectures_arr + np.random.randint(-1, 2, size=N),
    1, None
).astype(int)

lesson_type_variety_arr = np.random.randint(1, 5, N)
pct_video_arr           = np.clip(np.random.beta(2.5, 1.5, N), 0.1, 1.0).round(3)
content_duration_arr    = (num_lessons_arr * np.random.lognormal(2.8, 0.5, N) / 60).round(2)

quality_score = (
    np.clip(num_lectures_arr / 30.0, 0, 1) * 0.30 +
    (rating_arr - 2.0) / 3.0 * 0.30 +
    np.where(offers_cert_arr, 1.0, 0.0) * 0.20 +
    lesson_type_variety_arr / 4.0 * 0.20)
quality_label_arr = (quality_score > 0.50).astype(int)

level_enc_arr = np.array([{'BEGINNER':0,'INTERMEDIATE':1,'ADVANCED':2}[l] for l in level_arr])

courses_df = pd.DataFrame({
    'course_id':            np.arange(1, N+1),
    'category':             category_arr,
    'level':                level_arr,
    'level_encoded':        level_enc_arr,
    'num_lectures':         num_lectures_arr,
    'num_lessons':          num_lessons_arr,
    'content_duration':     content_duration_arr,
    'lesson_type_variety':  lesson_type_variety_arr,
    'pct_video_lessons':    pct_video_arr,
    'offers_certificate':   offers_cert_arr.astype(int),
    'price':                price_arr,
    'is_paid':              is_paid_arr.astype(int),
    'rating':               rating_arr,
    'rating_count':         rating_count_arr,
    'quality_label':        quality_label_arr,
})

out_courses = os.path.join(DATASETS_DIR, 'courses_200k.csv')
courses_df.to_csv(out_courses, index=False)
print(f"  Saved -> {out_courses}")
print(f"  Shape : {courses_df.shape}")
print(f"  First 5 rows:\n{courses_df.head().to_string()}")

# ─────────────────────────────────────────────────────────────
# 2. GENERATE 50K STUDENTS
# ─────────────────────────────────────────────────────────────
print("\n[2/3] Generating 50,000 students...")
N_STU = 50_000

corr_matrix = np.array([
    [1.00, 0.65, 0.42, 0.70, 0.58, 0.35],
    [0.65, 1.00, 0.55, 0.75, 0.50, 0.40],
    [0.42, 0.55, 1.00, 0.48, 0.35, 0.30],
    [0.70, 0.75, 0.48, 1.00, 0.52, 0.42],
    [0.58, 0.50, 0.35, 0.52, 1.00, 0.60],
    [0.35, 0.40, 0.30, 0.42, 0.60, 1.00],
])
rng = np.random.default_rng(99)
L   = np.linalg.cholesky(corr_matrix)
Z   = rng.standard_normal((N_STU, 6))
U   = sp_norm.cdf((Z @ L.T))

time_spent     = np.clip(lognorm.ppf(np.clip(U[:,0],0.001,0.999), s=0.9, scale=np.exp(2.5)), 0.5, 50.0).round(1)
completion     = np.clip(sp_beta.ppf(np.clip(U[:,1],0.001,0.999), a=1.5, b=5.0)*100, 0.0, 100.0).round(1)
quiz_scores    = np.clip(sp_norm.ppf(np.clip(U[:,2],0.001,0.999), loc=60, scale=18), 0.0, 100.0).round(1)
videos_watched = np.clip((completion/10 * np.random.uniform(0.8,1.2,N_STU)).astype(int), 0, 20)
num_logins     = np.clip(np.round(sp_norm.ppf(np.clip(U[:,4],0.001,0.999), loc=8, scale=3.5)).astype(int), 1, 50)
forum_reads    = np.zeros(N_STU)

eng_score = (0.30*(time_spent/50).clip(0,1) +
             0.25*(completion/100) +
             0.20*(quiz_scores/100) +
             0.12*(videos_watched/20).clip(0,1) +
             0.08*(num_logins/50).clip(0,1) +
             0.05*(forum_reads/30).clip(0,1))
converted = (eng_score > np.percentile(eng_score, 65)).astype(int)

students_df = pd.DataFrame({
    'student_id':             np.arange(1, N_STU+1),
    'time_spent_minutes':     time_spent,
    'completion_rate':        completion / 100.0,
    'quiz_scores':            quiz_scores,
    'videos_watched':         videos_watched,
    'num_logins':             num_logins,
    'forum_reads':            forum_reads,
    'converted':              converted,
})

out_students = os.path.join(DATASETS_DIR, 'students_50k.csv')
students_df.to_csv(out_students, index=False)
print(f"  Saved -> {out_students}")
print(f"  Shape : {students_df.shape}")
print(f"  First 5 rows:\n{students_df.head().to_string()}")

# ─────────────────────────────────────────────────────────────
# 3. GENERATE ENROLLMENT TIME SERIES (60 months)
# ─────────────────────────────────────────────────────────────
print("\n[3/3] Generating 60-month enrollment time series...")
np.random.seed(42)
t = np.arange(60)
trend        = 500 + t * 15
seasonality  = 80 * np.sin(2 * np.pi * t / 12)
noise        = np.random.normal(0, 30, 60)
monthly_enrollments = (trend + seasonality + noise).clip(min=100).astype(int)

start_date = datetime(2020, 1, 1)
dates = [start_date + timedelta(days=30*i) for i in range(60)]
ts_df = pd.DataFrame({'date': dates, 'enrollments': monthly_enrollments})

out_ts = os.path.join(DATASETS_DIR, 'enrollment_timeseries.csv')
ts_df.to_csv(out_ts, index=False)
print(f"  Saved -> {out_ts}")
print(f"  Shape : {ts_df.shape}")
print(f"  First 5 rows:\n{ts_df.head().to_string()}")

# ─────────────────────────────────────────────────────────────
# SUMMARY
# ─────────────────────────────────────────────────────────────
print("\n" + "=" * 60)
print("ALL DATASETS EXPORTED")
print("=" * 60)
for fname in ['courses_200k.csv', 'students_50k.csv', 'enrollment_timeseries.csv']:
    fpath = os.path.join(DATASETS_DIR, fname)
    size  = os.path.getsize(fpath) / 1024 / 1024
    print(f"  {fname:<35s}  {size:.1f} MB")
