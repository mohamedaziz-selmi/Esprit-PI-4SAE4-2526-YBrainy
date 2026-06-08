from __future__ import annotations

import json
import math
from datetime import datetime
from pathlib import Path
from typing import Any

import joblib
import numpy as np
import pandas as pd
from sklearn.compose import ColumnTransformer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import accuracy_score, roc_auc_score
from sklearn.model_selection import train_test_split
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import OneHotEncoder, StandardScaler


BASE_DIR = Path(__file__).resolve().parent
PROJECT_ROOT = BASE_DIR.parent
SCRAPER_DIR = PROJECT_ROOT / "scraper" / "result" / "elearning_outputs"
OUTPUTS_DIR = BASE_DIR / "outputs"
ARTIFACTS_DIR = BASE_DIR / "artifacts"

RANDOM_SEED = 42
USER_COUNT = 420
SQL_BEHAVIOR_SAMPLE_SIZE = 900

CATEGORY_NAMES = {
    1: "Web Development",
    2: "Data and AI",
    3: "Cloud",
    4: "Cybersecurity",
    5: "Mobile Development",
    6: "Business",
    7: "Marketing",
    8: "Design",
    9: "DevOps",
}

LEVEL_ORDER = {"BEGINNER": 0, "INTERMEDIATE": 1, "ADVANCED": 2}
DEMAND_LEVEL_SCORES = {"very_high": 1.0, "high": 0.82, "medium": 0.60, "low": 0.35}

PACK_ROWS = [
    (1, "Frontend Web Development Bootcamp", "HTML CSS JavaScript React responsive design", 65, "BEGINNER", 149.99, 99.99, 1),
    (2, "Full-Stack JavaScript Mastery", "React Node Express MongoDB full-stack apps", 120, "INTERMEDIATE", 299.99, 199.99, 1),
    (3, "Python & Django Backend Development", "Python Django REST APIs PostgreSQL deployment", 90, "INTERMEDIATE", 249.99, 179.99, 1),
    (4, "Next.js Advanced Full-Stack Development", "Next.js TypeScript Prisma server-side rendering", 75, "ADVANCED", 279.99, 219.99, 1),
    (5, "Data Analytics Professional Certificate", "Python Pandas NumPy SQL Tableau dashboards", 80, "BEGINNER", 199.99, 129.99, 2),
    (6, "Machine Learning Engineering Masterclass", "machine learning feature engineering scikit-learn xgboost fastapi", 130, "INTERMEDIATE", 349.99, 249.99, 2),
    (7, "Deep Learning & NLP with Python", "deep learning tensorflow pytorch transformers bert gpt nlp", 150, "ADVANCED", 399.99, 299.99, 2),
    (9, "AWS Solutions Architect Certification Prep", "aws cloud ec2 s3 rds vpc iam lambda", 70, "INTERMEDIATE", 299.99, 199.99, 3),
    (10, "Microsoft Azure Fundamentals AZ-900", "azure cloud services pricing sla compliance", 35, "BEGINNER", 149.99, 99.99, 3),
    (11, "Google Cloud Professional Architect Prep", "google cloud scalable solutions architecture", 80, "ADVANCED", 349.99, 259.99, 3),
    (12, "CompTIA Security+ Certification Prep", "network security cryptography identity management", 60, "BEGINNER", 199.99, 139.99, 4),
    (13, "Ethical Hacking & Penetration Testing", "ethical hacking kali metasploit burp suite nmap", 110, "INTERMEDIATE", 349.99, 259.99, 4),
    (14, "SOC Analyst & Incident Response Mastery", "soc analyst siem incident response digital forensics", 90, "ADVANCED", 399.99, 299.99, 4),
    (15, "Flutter & Dart Mobile Development", "flutter dart mobile apps firebase", 85, "INTERMEDIATE", 279.99, 199.99, 5),
    (16, "iOS App Development with Swift", "swift ios uikit swiftui app store testing", 100, "INTERMEDIATE", 299.99, 219.99, 5),
    (17, "React Native Cross-Platform Development", "react native expo firebase redux mobile", 70, "INTERMEDIATE", 249.99, 179.99, 5),
    (18, "Business Strategy & Planning Fundamentals", "strategic planning swot okr business model canvas", 40, "BEGINNER", 199.99, 129.99, 6),
    (19, "Project Management Professional (PMP) Prep", "pmp agile scrum kanban waterfall risk stakeholder", 65, "INTERMEDIATE", 249.99, 179.99, 6),
    (20, "MBA Essentials - Business Leadership Track", "mba finance operations marketing strategy leadership", 120, "ADVANCED", 499.99, 349.99, 6),
    (21, "Digital Marketing Complete Guide", "seo google ads meta ads email marketing analytics", 50, "BEGINNER", 249.99, 169.99, 7),
    (22, "Advanced SEO & Growth Marketing", "advanced seo cro growth marketing data-driven", 60, "INTERMEDIATE", 279.99, 199.99, 7),
    (23, "Social Media Marketing Mastery", "instagram tiktok linkedin youtube influencer marketing", 40, "BEGINNER", 179.99, 119.99, 7),
    (24, "UI/UX Design with Figma", "figma user research wireframing prototyping design systems", 75, "BEGINNER", 249.99, 169.99, 8),
    (25, "Advanced Product Design & Design Systems", "interaction design accessibility design tokens figma dev mode", 90, "ADVANCED", 329.99, 239.99, 8),
    (26, "DevOps Engineering Bootcamp", "github actions docker kubernetes terraform prometheus grafana devops", 95, "INTERMEDIATE", 319.99, 229.99, 9),
    (27, "Certified Kubernetes Administrator (CKA) Prep", "kubernetes cluster networking storage maintenance", 70, "ADVANCED", 299.99, 219.99, 9),
    (28, "Site Reliability Engineering (SRE) Mastery", "sre slo sla error budgets chaos engineering on-call", 80, "ADVANCED", 349.99, 259.99, 9),
]


def ensure_dirs() -> None:
    OUTPUTS_DIR.mkdir(parents=True, exist_ok=True)
    ARTIFACTS_DIR.mkdir(parents=True, exist_ok=True)


def latest_file(pattern: str) -> Path:
    files = sorted(SCRAPER_DIR.glob(pattern))
    if not files:
        raise FileNotFoundError(f"No scraper files found for pattern: {pattern}")
    return files[-1]


def normalize_series(series: pd.Series) -> pd.Series:
    series = series.fillna(0.0).astype(float)
    min_value = float(series.min())
    max_value = float(series.max())
    if math.isclose(min_value, max_value):
        return pd.Series(np.full(len(series), 0.5), index=series.index)
    return (series - min_value) / (max_value - min_value)


def sigmoid(value: float) -> float:
    return 1.0 / (1.0 + math.exp(-value))


def infer_primary_skill(title: str, description: str, category_id: int) -> str:
    text = f"{title} {description}".lower()
    rules = [
        ("deep-learning", ["deep learning", "transformers", "bert", "gpt", "tensorflow", "pytorch"]),
        ("nlp", ["nlp", "natural language"]),
        ("machine-learning", ["machine learning", "xgboost", "scikit-learn"]),
        ("data-science", ["data analytics", "data science", "pandas", "numpy", "tableau"]),
        ("react", ["react native", "react", "next.js", "nextjs"]),
        ("javascript", ["javascript", "frontend", "node", "express"]),
        ("python", ["python", "django", "fastapi"]),
        ("aws", ["aws", "azure", "google cloud", "cloud", "kubernetes", "terraform", "devops", "sre"]),
    ]
    for skill, keywords in rules:
        if any(keyword in text for keyword in keywords):
            return skill
    return {
        1: "javascript",
        2: "data-science",
        3: "aws",
        4: "general",
        5: "react",
        6: "general",
        7: "general",
        8: "general",
        9: "aws",
    }.get(category_id, "general")


def load_market_signals() -> tuple[pd.DataFrame, dict[str, Any]]:
    courses_path = latest_file("courses_*.csv")
    trends_path = latest_file("skill_trends_*.csv")

    courses = pd.read_csv(courses_path)
    trends = pd.read_csv(trends_path)

    courses["roi_score"] = pd.to_numeric(courses["roi_score"], errors="coerce").fillna(0.0)
    courses["estimated_salary_boost"] = pd.to_numeric(courses["estimated_salary_boost"], errors="coerce").fillna(0.0)
    courses["demand_level_score"] = courses["demand_level"].fillna("medium").astype(str).str.lower().map(DEMAND_LEVEL_SCORES).fillna(0.55)

    trends["views"] = pd.to_numeric(trends["views"], errors="coerce").fillna(0.0)
    trends["engagement_score"] = pd.to_numeric(trends["engagement_score"], errors="coerce").fillna(0.0)
    trends["roi_score"] = pd.to_numeric(trends["roi_score"], errors="coerce").fillna(0.0)

    course_agg = courses.groupby("skill_category", dropna=False).agg(
        market_course_count=("title", "count"),
        market_avg_roi=("roi_score", "mean"),
        market_avg_salary_boost=("estimated_salary_boost", "mean"),
        market_demand_level_score=("demand_level_score", "mean"),
    ).reset_index()
    trend_agg = trends.groupby("skill_category", dropna=False).agg(
        trend_rows=("title", "count"),
        trend_total_views=("views", "sum"),
        trend_total_engagement=("engagement_score", "sum"),
        trend_avg_roi=("roi_score", "mean"),
    ).reset_index()

    market = course_agg.merge(trend_agg, on="skill_category", how="outer").fillna(0.0)
    market["skill_category"] = market["skill_category"].fillna("general")
    market["course_count_norm"] = normalize_series(market["market_course_count"])
    market["roi_norm"] = normalize_series((market["market_avg_roi"] * 0.65) + (market["trend_avg_roi"] * 0.35))
    market["views_norm"] = normalize_series(np.log1p(market["trend_total_views"]))
    market["engagement_norm"] = normalize_series(np.log1p(market["trend_total_engagement"]))
    market["salary_norm"] = normalize_series(market["market_avg_salary_boost"])
    market["demand_norm"] = normalize_series(market["market_demand_level_score"])
    market["market_demand_score"] = (
        market["course_count_norm"] * 0.18
        + market["roi_norm"] * 0.24
        + market["views_norm"] * 0.22
        + market["engagement_norm"] * 0.16
        + market["salary_norm"] * 0.10
        + market["demand_norm"] * 0.10
    ).round(6)

    return market, {"courses_file": str(courses_path), "skill_trends_file": str(trends_path)}


def build_pack_frame(market: pd.DataFrame) -> pd.DataFrame:
    packs = pd.DataFrame(
        PACK_ROWS,
        columns=[
            "id",
            "title",
            "description",
            "duration_hours",
            "level",
            "original_price",
            "sale_price",
            "category_id",
        ],
    )
    packs["category_name"] = packs["category_id"].map(CATEGORY_NAMES)
    packs["discount_pct"] = ((packs["original_price"] - packs["sale_price"]) / packs["original_price"] * 100.0).round(4)
    packs["primary_skill"] = packs.apply(
        lambda row: infer_primary_skill(str(row["title"]), str(row["description"]), int(row["category_id"])),
        axis=1,
    )

    merged = packs.merge(
        market[
            [
                "skill_category",
                "market_course_count",
                "market_avg_roi",
                "market_avg_salary_boost",
                "trend_total_views",
                "trend_total_engagement",
                "market_demand_score",
            ]
        ],
        left_on="primary_skill",
        right_on="skill_category",
        how="left",
    )

    for column, fallback in {
        "market_demand_score": float(market["market_demand_score"].median()) if not market.empty else 0.5,
        "market_avg_roi": float(market["market_avg_roi"].median()) if not market.empty else 75.0,
        "trend_total_views": float(market["trend_total_views"].median()) if not market.empty else 1_000_000.0,
        "trend_total_engagement": float(market["trend_total_engagement"].median()) if not market.empty else 10_000.0,
        "market_course_count": float(market["market_course_count"].median()) if not market.empty else 10.0,
        "market_avg_salary_boost": float(market["market_avg_salary_boost"].median()) if not market.empty else 25_000.0,
    }.items():
        merged[column] = merged[column].fillna(fallback)

    merged["market_view_log"] = np.log1p(merged["trend_total_views"]).round(6)
    merged["market_engagement_log"] = np.log1p(merged["trend_total_engagement"]).round(6)
    return merged


def generate_user_profiles(user_count: int) -> pd.DataFrame:
    rng = np.random.default_rng(RANDOM_SEED)
    category_ids = list(CATEGORY_NAMES.keys())
    levels = list(LEVEL_ORDER.keys())
    rows = []
    for user_id in range(1, user_count + 1):
        rows.append(
            {
                "user_id": user_id,
                "preferred_category_id": int(rng.choice(category_ids)),
                "preferred_level": str(rng.choice(levels, p=[0.36, 0.42, 0.22])),
                "preferred_duration_hours": int(np.clip(rng.normal(82, 28), 25, 160)),
                "user_budget": round(float(np.clip(rng.normal(225, 72), 80, 480)), 2),
                "activity_score": round(float(np.clip(rng.beta(2.5, 1.9), 0.05, 0.99)), 6),
                "loyalty_score": round(float(np.clip(rng.beta(1.8, 3.2), 0.02, 0.98)), 6),
                "prior_pack_purchases": int(rng.poisson(1.4 + (rng.random() * 1.8))),
            }
        )
    return pd.DataFrame(rows)


def generate_training_data(packs: pd.DataFrame, users: pd.DataFrame) -> pd.DataFrame:
    rng = np.random.default_rng(RANDOM_SEED + 7)
    rows: list[dict[str, Any]] = []

    for user in users.itertuples(index=False):
        for pack in packs.itertuples(index=False):
            level_gap = abs(LEVEL_ORDER[pack.level] - LEVEL_ORDER[user.preferred_level])
            level_match = 1 if level_gap == 0 else 0
            category_match = 1 if pack.category_id == user.preferred_category_id else 0
            price_gap = max(float(pack.sale_price) - float(user.user_budget), 0.0)
            price_fit_score = max(
                0.0,
                1.0 - abs(float(pack.sale_price) - float(user.user_budget)) / max(float(user.user_budget), 1.0),
            )
            duration_gap = abs(int(pack.duration_hours) - int(user.preferred_duration_hours))

            view_lambda = max(
                0.4,
                0.8
                + (float(user.activity_score) * 3.6)
                + (float(pack.market_demand_score) * 2.4)
                + (category_match * 0.95)
                + (price_fit_score * 0.9)
                - (level_gap * 0.25),
            )
            viewed_count = int(rng.poisson(view_lambda))
            clicked_count = int(
                rng.binomial(
                    max(viewed_count, 1),
                    min(0.88, 0.10 + (category_match * 0.18) + (price_fit_score * 0.22) + (float(pack.market_demand_score) * 0.20)),
                )
            )
            wishlist_count = int(
                rng.binomial(max(clicked_count, 1), min(0.45, 0.05 + (price_fit_score * 0.12) + (level_match * 0.10)))
            )
            add_to_cart_count = int(
                rng.binomial(
                    max(clicked_count, 1),
                    min(0.55, 0.04 + (wishlist_count * 0.08) + (price_fit_score * 0.18) + (float(user.loyalty_score) * 0.10)),
                )
            )
            prior_category_purchases = int(
                rng.poisson(max(0.1, user.prior_pack_purchases * (0.55 if category_match else 0.18)))
            )

            purchase_logit = -4.25
            purchase_logit += float(pack.market_demand_score) * 1.35
            purchase_logit += float(price_fit_score) * 1.10
            purchase_logit += category_match * 1.00
            purchase_logit += level_match * 0.72
            purchase_logit += min(clicked_count, 5) * 0.16
            purchase_logit += min(add_to_cart_count, 3) * 0.72
            purchase_logit += min(wishlist_count, 2) * 0.20
            purchase_logit += min(prior_category_purchases, 5) * 0.13
            purchase_logit += min(user.prior_pack_purchases, 6) * 0.08
            purchase_logit += (float(pack.discount_pct) / 100.0) * 2.30
            purchase_logit -= price_gap * 0.0065
            purchase_logit -= duration_gap * 0.006
            purchase_logit -= level_gap * 0.28
            heuristic_probability = sigmoid(purchase_logit)
            purchased = int(rng.binomial(1, heuristic_probability))

            rows.append(
                {
                    "user_id": int(user.user_id),
                    "pack_id": int(pack.id),
                    "title": pack.title,
                    "category_id": int(pack.category_id),
                    "category_name": pack.category_name,
                    "level": pack.level,
                    "primary_skill": pack.primary_skill,
                    "sale_price": round(float(pack.sale_price), 2),
                    "original_price": round(float(pack.original_price), 2),
                    "discount_pct": round(float(pack.discount_pct), 4),
                    "duration_hours": int(pack.duration_hours),
                    "market_demand_score": round(float(pack.market_demand_score), 6),
                    "market_avg_roi": round(float(pack.market_avg_roi), 4),
                    "market_course_count": int(round(float(pack.market_course_count))),
                    "trend_total_views": int(round(float(pack.trend_total_views))),
                    "trend_total_engagement": int(round(float(pack.trend_total_engagement))),
                    "preferred_category_id": int(user.preferred_category_id),
                    "preferred_level": user.preferred_level,
                    "preferred_duration_hours": int(user.preferred_duration_hours),
                    "user_budget": round(float(user.user_budget), 2),
                    "activity_score": round(float(user.activity_score), 6),
                    "loyalty_score": round(float(user.loyalty_score), 6),
                    "prior_pack_purchases": int(user.prior_pack_purchases),
                    "prior_category_purchases": int(prior_category_purchases),
                    "level_gap": int(level_gap),
                    "duration_gap": int(duration_gap),
                    "price_gap": round(price_gap, 4),
                    "price_fit_score": round(float(price_fit_score), 6),
                    "category_match": int(category_match),
                    "level_match": int(level_match),
                    "viewed_count": int(viewed_count),
                    "clicked_count": int(clicked_count),
                    "wishlist_count": int(wishlist_count),
                    "add_to_cart_count": int(add_to_cart_count),
                    "heuristic_purchase_probability": round(float(heuristic_probability), 6),
                    "purchased": int(purchased),
                }
            )

    return pd.DataFrame(rows)


def train_model(data: pd.DataFrame) -> tuple[Pipeline, pd.DataFrame, dict[str, Any]]:
    features = [
        "sale_price",
        "discount_pct",
        "duration_hours",
        "market_demand_score",
        "market_avg_roi",
        "market_course_count",
        "trend_total_views",
        "trend_total_engagement",
        "user_budget",
        "activity_score",
        "loyalty_score",
        "prior_pack_purchases",
        "prior_category_purchases",
        "level_gap",
        "duration_gap",
        "price_gap",
        "price_fit_score",
        "category_match",
        "level_match",
        "viewed_count",
        "clicked_count",
        "wishlist_count",
        "add_to_cart_count",
        "category_name",
        "level",
        "primary_skill",
        "preferred_level",
    ]

    X = data[features]
    y = data["purchased"]
    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=0.25, random_state=RANDOM_SEED, stratify=y
    )

    numeric_features = [column for column in features if column not in {"category_name", "level", "primary_skill", "preferred_level"}]
    categorical_features = ["category_name", "level", "primary_skill", "preferred_level"]

    pipeline = Pipeline(
        steps=[
            (
                "preprocessor",
                ColumnTransformer(
                    transformers=[
                        ("num", StandardScaler(), numeric_features),
                        ("cat", OneHotEncoder(handle_unknown="ignore"), categorical_features),
                    ]
                ),
            ),
            (
                "classifier",
                LogisticRegression(max_iter=1200, class_weight="balanced", random_state=RANDOM_SEED),
            ),
        ]
    )
    pipeline.fit(X_train, y_train)

    test_probabilities = pipeline.predict_proba(X_test)[:, 1]
    test_predictions = (test_probabilities >= 0.5).astype(int)
    metrics = {
        "training_rows": int(len(data)),
        "test_rows": int(len(X_test)),
        "positive_rate": round(float(y.mean()), 6),
        "accuracy": round(float(accuracy_score(y_test, test_predictions)), 6),
        "roc_auc": round(float(roc_auc_score(y_test, test_probabilities)), 6),
    }

    scored = data.copy()
    scored["predicted_purchase_probability"] = pipeline.predict_proba(X)[:, 1].round(6)
    return pipeline, scored, metrics


def build_pack_scores(scored: pd.DataFrame) -> pd.DataFrame:
    pack_scores = (
        scored.groupby(
            ["pack_id", "title", "category_id", "category_name", "level", "primary_skill", "sale_price", "original_price", "discount_pct", "duration_hours"],
            as_index=False,
        )
        .agg(
            market_demand_score=("market_demand_score", "mean"),
            market_avg_roi=("market_avg_roi", "mean"),
            market_course_count=("market_course_count", "mean"),
            avg_views=("viewed_count", "mean"),
            avg_cart_adds=("add_to_cart_count", "mean"),
            observed_purchase_rate=("purchased", "mean"),
            predicted_conversion_rate=("predicted_purchase_probability", "mean"),
            expected_buyers=("predicted_purchase_probability", "sum"),
            training_support=("user_id", "count"),
        )
        .sort_values(["predicted_conversion_rate", "market_demand_score", "expected_buyers"], ascending=False)
        .reset_index(drop=True)
    )
    pack_scores["expected_revenue"] = (pack_scores["expected_buyers"] * pack_scores["sale_price"]).round(2)
    for column in ["market_demand_score", "observed_purchase_rate", "predicted_conversion_rate", "avg_views", "avg_cart_adds", "expected_buyers"]:
        pack_scores[column] = pack_scores[column].round(6 if "rate" in column or "score" in column else 2)
    pack_scores["market_course_count"] = pack_scores["market_course_count"].round(0).astype(int)
    pack_scores["training_support"] = pack_scores["training_support"].astype(int)
    pack_scores["conversion_rank"] = pack_scores.index + 1
    return pack_scores


def sql_value(value: Any) -> str:
    if value is None or (isinstance(value, float) and math.isnan(value)):
        return "NULL"
    if isinstance(value, (np.integer, int)):
        return str(int(value))
    if isinstance(value, (np.floating, float)):
        return f"{float(value):.6f}"
    if isinstance(value, (pd.Timestamp, datetime)):
        return f"'{value.strftime('%Y-%m-%d %H:%M:%S')}'"
    return "'" + str(value).replace("\\", "\\\\").replace("'", "''") + "'"


def write_sql_outputs(pack_scores: pd.DataFrame, scored: pd.DataFrame) -> None:
    generated_at = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    schema_sql = """
CREATE TABLE IF NOT EXISTS pack_conversion_scores (
    pack_id BIGINT PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    category_id INT NOT NULL,
    category_name VARCHAR(100) NOT NULL,
    level VARCHAR(30) NOT NULL,
    primary_skill VARCHAR(80) NOT NULL,
    sale_price DECIMAL(10,2) NOT NULL,
    original_price DECIMAL(10,2) NOT NULL,
    discount_pct DECIMAL(10,4) NOT NULL,
    duration_hours INT NOT NULL,
    market_demand_score DECIMAL(10,6) NOT NULL,
    market_avg_roi DECIMAL(10,4) NOT NULL,
    market_course_count INT NOT NULL,
    predicted_conversion_rate DECIMAL(10,6) NOT NULL,
    observed_purchase_rate DECIMAL(10,6) NOT NULL,
    expected_buyers DECIMAL(10,2) NOT NULL,
    expected_revenue DECIMAL(12,2) NOT NULL,
    training_support INT NOT NULL,
    conversion_rank INT NOT NULL,
    generated_at DATETIME NOT NULL
);

CREATE TABLE IF NOT EXISTS pack_user_behavior_samples (
    sample_id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    pack_id BIGINT NOT NULL,
    category_id INT NOT NULL,
    category_name VARCHAR(100) NOT NULL,
    level VARCHAR(30) NOT NULL,
    primary_skill VARCHAR(80) NOT NULL,
    sale_price DECIMAL(10,2) NOT NULL,
    discount_pct DECIMAL(10,4) NOT NULL,
    duration_hours INT NOT NULL,
    market_demand_score DECIMAL(10,6) NOT NULL,
    viewed_count INT NOT NULL,
    clicked_count INT NOT NULL,
    wishlist_count INT NOT NULL,
    add_to_cart_count INT NOT NULL,
    prior_pack_purchases INT NOT NULL,
    prior_category_purchases INT NOT NULL,
    user_budget DECIMAL(10,2) NOT NULL,
    price_fit_score DECIMAL(10,6) NOT NULL,
    category_match TINYINT(1) NOT NULL,
    level_match TINYINT(1) NOT NULL,
    predicted_purchase_probability DECIMAL(10,6) NOT NULL,
    purchased TINYINT(1) NOT NULL,
    generated_at DATETIME NOT NULL
);
""".strip()
    (OUTPUTS_DIR / "schema_pack_conversion.sql").write_text(schema_sql + "\n", encoding="utf-8")

    score_columns = [
        "pack_id", "title", "category_id", "category_name", "level", "primary_skill",
        "sale_price", "original_price", "discount_pct", "duration_hours",
        "market_demand_score", "market_avg_roi", "market_course_count",
        "predicted_conversion_rate", "observed_purchase_rate",
        "expected_buyers", "expected_revenue", "training_support", "conversion_rank",
    ]
    score_values = []
    for row in pack_scores.itertuples(index=False):
        values = [sql_value(getattr(row, column)) for column in score_columns] + [sql_value(generated_at)]
        score_values.append("    (" + ", ".join(values) + ")")
    (OUTPUTS_DIR / "seed_pack_conversion_scores.sql").write_text(
        "INSERT INTO pack_conversion_scores (" + ", ".join(score_columns) + ", generated_at) VALUES\n"
        + ",\n".join(score_values)
        + ";\n",
        encoding="utf-8",
    )

    purchased_rows = scored[scored["purchased"] == 1]
    not_purchased_rows = scored[scored["purchased"] == 0]
    half = SQL_BEHAVIOR_SAMPLE_SIZE // 2
    sampled_behavior = pd.concat(
        [
            purchased_rows.sample(min(len(purchased_rows), half), random_state=RANDOM_SEED),
            not_purchased_rows.sample(min(len(not_purchased_rows), SQL_BEHAVIOR_SAMPLE_SIZE - min(len(purchased_rows), half)), random_state=RANDOM_SEED),
        ],
        ignore_index=True,
    ).sample(frac=1.0, random_state=RANDOM_SEED).reset_index(drop=True)
    sampled_behavior["sample_id"] = np.arange(1, len(sampled_behavior) + 1)

    behavior_columns = [
        "sample_id", "user_id", "pack_id", "category_id", "category_name", "level",
        "primary_skill", "sale_price", "discount_pct", "duration_hours",
        "market_demand_score", "viewed_count", "clicked_count", "wishlist_count",
        "add_to_cart_count", "prior_pack_purchases", "prior_category_purchases",
        "user_budget", "price_fit_score", "category_match", "level_match",
        "predicted_purchase_probability", "purchased",
    ]
    behavior_values = []
    for row in sampled_behavior.itertuples(index=False):
        values = [sql_value(getattr(row, column)) for column in behavior_columns] + [sql_value(generated_at)]
        behavior_values.append("    (" + ", ".join(values) + ")")
    (OUTPUTS_DIR / "seed_pack_user_behavior_samples.sql").write_text(
        "INSERT INTO pack_user_behavior_samples (" + ", ".join(behavior_columns) + ", generated_at) VALUES\n"
        + ",\n".join(behavior_values)
        + ";\n",
        encoding="utf-8",
    )


def write_summary(metadata: dict[str, Any], metrics: dict[str, Any], pack_scores: pd.DataFrame) -> None:
    summary = {
        "generated_at": datetime.now().isoformat(),
        "scraper_inputs": metadata,
        "model": {"algorithm": "LogisticRegression", "random_seed": RANDOM_SEED, "user_count": USER_COUNT, **metrics},
        "assumption": "Scraper demand and engagement are used as a market-sales proxy because the scraped files do not contain real purchase counts.",
        "top_conversion_packs": pack_scores.head(10).to_dict(orient="records"),
    }
    (OUTPUTS_DIR / "pack_conversion_summary.json").write_text(json.dumps(summary, indent=2), encoding="utf-8")


def main() -> None:
    ensure_dirs()
    market, metadata = load_market_signals()
    packs = build_pack_frame(market)
    users = generate_user_profiles(USER_COUNT)
    training_data = generate_training_data(packs, users)
    model, scored_data, metrics = train_model(training_data)
    pack_scores = build_pack_scores(scored_data)

    pack_scores[
        [
            "pack_id", "title", "primary_skill", "category_name", "level", "sale_price",
            "discount_pct", "market_course_count", "market_avg_roi", "market_demand_score",
            "expected_revenue", "predicted_conversion_rate", "conversion_rank",
        ]
    ].to_csv(OUTPUTS_DIR / "top_pack_market_signals.csv", index=False)
    scored_data.to_csv(OUTPUTS_DIR / "pack_conversion_training_data.csv", index=False)
    pack_scores.to_csv(OUTPUTS_DIR / "pack_conversion_scores.csv", index=False)
    joblib.dump(model, ARTIFACTS_DIR / "pack_conversion_model.joblib")
    write_sql_outputs(pack_scores, scored_data)
    write_summary(metadata, metrics, pack_scores)

    print("Pack conversion pipeline completed.")
    print(f"Training rows: {metrics['training_rows']}")
    print(f"Accuracy: {metrics['accuracy']}")
    print(f"ROC AUC: {metrics['roc_auc']}")
    print(pack_scores[["conversion_rank", "title", "predicted_conversion_rate", "expected_revenue"]].head(5).to_string(index=False))


if __name__ == "__main__":
    main()
