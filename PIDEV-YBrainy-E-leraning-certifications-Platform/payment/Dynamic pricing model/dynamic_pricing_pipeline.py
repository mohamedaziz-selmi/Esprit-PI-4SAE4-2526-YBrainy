from __future__ import annotations

import json
import math
from datetime import datetime
from pathlib import Path
from typing import Any

import joblib
import numpy as np
import pandas as pd
from sklearn.ensemble import RandomForestClassifier
from sklearn.metrics import accuracy_score, roc_auc_score
from sklearn.model_selection import train_test_split


BASE_DIR = Path(__file__).resolve().parent
PROJECT_ROOT = BASE_DIR.parent
SCRAPER_DIR = PROJECT_ROOT / "scraper" / "result" / "elearning_outputs"
OUTPUTS_DIR = BASE_DIR / "outputs"
ARTIFACTS_DIR = BASE_DIR / "artifacts"

RANDOM_SEED = 42
USER_COUNT = 340

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
BASE_DISCOUNT_GRID = [10.0, 12.5, 15.0, 17.5, 20.0, 22.5, 25.0, 27.5, 30.0, 32.5, 35.0, 37.5, 40.0, 45.0]

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
    numeric = pd.to_numeric(series, errors="coerce").fillna(0.0).astype(float)
    min_value = float(numeric.min())
    max_value = float(numeric.max())
    if math.isclose(min_value, max_value):
        return pd.Series(np.full(len(numeric), 0.5), index=numeric.index)
    return (numeric - min_value) / (max_value - min_value)


def sigmoid(value: float) -> float:
    return 1.0 / (1.0 + math.exp(-value))


def clamp(value: float, low: float, high: float) -> float:
    return max(low, min(value, high))


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
    trends["estimated_salary_boost"] = pd.to_numeric(trends["estimated_salary_boost"], errors="coerce").fillna(0.0)
    trends["demand_level_score"] = trends["demand_level"].fillna("medium").astype(str).str.lower().map(DEMAND_LEVEL_SCORES).fillna(0.55)

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
        trend_avg_salary_boost=("estimated_salary_boost", "mean"),
        trend_demand_level_score=("demand_level_score", "mean"),
    ).reset_index()

    market = course_agg.merge(trend_agg, on="skill_category", how="outer").fillna(0.0)
    market["skill_category"] = market["skill_category"].fillna("general")
    market["course_count_norm"] = normalize_series(market["market_course_count"])
    market["roi_norm"] = normalize_series((market["market_avg_roi"] * 0.70) + (market["trend_avg_roi"] * 0.30))
    market["views_norm"] = normalize_series(np.log1p(market["trend_total_views"]))
    market["engagement_norm"] = normalize_series(np.log1p(market["trend_total_engagement"]))
    market["salary_norm"] = normalize_series((market["market_avg_salary_boost"] * 0.65) + (market["trend_avg_salary_boost"] * 0.35))
    market["demand_norm"] = normalize_series((market["market_demand_level_score"] * 0.60) + (market["trend_demand_level_score"] * 0.40))
    market["market_demand_score"] = (
        market["course_count_norm"] * 0.18
        + market["roi_norm"] * 0.24
        + market["views_norm"] * 0.20
        + market["engagement_norm"] * 0.16
        + market["salary_norm"] * 0.12
        + market["demand_norm"] * 0.10
    ).round(6)

    return market, {"courses_file": str(courses_path), "skill_trends_file": str(trends_path)}


def build_pack_frame(market: pd.DataFrame) -> tuple[pd.DataFrame, dict[str, int]]:
    packs = pd.DataFrame(
        PACK_ROWS,
        columns=[
            "pack_id",
            "title",
            "description",
            "duration_hours",
            "level",
            "original_price",
            "current_sale_price",
            "category_id",
        ],
    )
    packs["category_name"] = packs["category_id"].map(CATEGORY_NAMES)
    packs["current_discount_pct"] = (
        (packs["original_price"] - packs["current_sale_price"]) / packs["original_price"] * 100.0
    ).round(4)
    packs["level_rank"] = packs["level"].map(LEVEL_ORDER).astype(int)
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
                "roi_norm",
                "salary_norm",
            ]
        ],
        left_on="primary_skill",
        right_on="skill_category",
        how="left",
    )

    defaults = {
        "market_demand_score": float(market["market_demand_score"].median()) if not market.empty else 0.5,
        "market_avg_roi": float(market["market_avg_roi"].median()) if not market.empty else 75.0,
        "market_avg_salary_boost": float(market["market_avg_salary_boost"].median()) if not market.empty else 22000.0,
        "trend_total_views": float(market["trend_total_views"].median()) if not market.empty else 1_000_000.0,
        "trend_total_engagement": float(market["trend_total_engagement"].median()) if not market.empty else 10_000.0,
        "market_course_count": float(market["market_course_count"].median()) if not market.empty else 12.0,
        "roi_norm": float(market["roi_norm"].median()) if not market.empty else 0.5,
        "salary_norm": float(market["salary_norm"].median()) if not market.empty else 0.5,
    }
    for column, fallback in defaults.items():
        merged[column] = merged[column].fillna(fallback)

    skill_codes = {skill: idx for idx, skill in enumerate(sorted(merged["primary_skill"].dropna().unique()))}
    merged["primary_skill_code"] = merged["primary_skill"].map(skill_codes).fillna(-1).astype(int)
    merged["trend_total_views_log"] = np.log1p(merged["trend_total_views"]).round(6)
    merged["trend_total_engagement_log"] = np.log1p(merged["trend_total_engagement"]).round(6)
    merged["value_strength"] = (
        merged["market_demand_score"] * 0.44
        + merged["roi_norm"] * 0.36
        + merged["salary_norm"] * 0.20
    ).round(6)
    return merged, skill_codes


def candidate_discounts_for_pack(current_discount_pct: float) -> list[float]:
    candidates = set(BASE_DISCOUNT_GRID)
    candidates.add(round(float(current_discount_pct), 4))
    return sorted(discount for discount in candidates if 8.0 <= discount <= 48.0)


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
                "preferred_level": str(rng.choice(levels, p=[0.38, 0.40, 0.22])),
                "preferred_duration_hours": int(np.clip(rng.normal(82, 26), 25, 165)),
                "user_budget": round(float(np.clip(rng.normal(230, 75), 80, 520)), 2),
                "activity_score": round(float(np.clip(rng.beta(2.4, 1.9), 0.04, 0.99)), 6),
                "loyalty_score": round(float(np.clip(rng.beta(1.8, 3.0), 0.02, 0.98)), 6),
                "price_sensitivity": round(float(np.clip(rng.beta(2.8, 2.1), 0.05, 0.98)), 6),
                "budget_flex_pct": round(float(np.clip(rng.beta(2.1, 4.6), 0.01, 0.28)), 6),
            }
        )
    users = pd.DataFrame(rows)
    users["preferred_level_rank"] = users["preferred_level"].map(LEVEL_ORDER).astype(int)
    return users


def generate_training_data(packs: pd.DataFrame, users: pd.DataFrame) -> pd.DataFrame:
    rng = np.random.default_rng(RANDOM_SEED + 11)
    rows: list[dict[str, Any]] = []

    for pack in packs.itertuples(index=False):
        hidden_target_discount = clamp(
            12.0 + (1.0 - float(pack.value_strength)) * 20.0 + (2.0 if pack.level == "ADVANCED" else 0.0),
            10.0,
            42.0,
        )
        for discount_pct in candidate_discounts_for_pack(float(pack.current_discount_pct)):
            sale_price = round(float(pack.original_price) * (1.0 - (discount_pct / 100.0)), 2)
            savings_amount = round(float(pack.original_price) - sale_price, 2)
            price_delta_vs_current_pct = round(
                ((sale_price - float(pack.current_sale_price)) / max(float(pack.current_sale_price), 1.0)) * 100.0, 4
            )
            discount_change_vs_current_pct = round(discount_pct - float(pack.current_discount_pct), 4)
            for user in users.itertuples(index=False):
                level_gap = abs(int(pack.level_rank) - int(user.preferred_level_rank))
                level_match = 1 if level_gap == 0 else 0
                category_match = 1 if int(pack.category_id) == int(user.preferred_category_id) else 0
                duration_gap = abs(int(pack.duration_hours) - int(user.preferred_duration_hours))

                affordability_ceiling = float(user.user_budget) * (
                    1.02 + ((1.0 - float(user.price_sensitivity)) * 0.28) + (float(user.budget_flex_pct) * 0.55)
                )
                budget_pressure = sale_price / max(float(user.user_budget), 1.0)
                price_gap = max(sale_price - affordability_ceiling, 0.0)
                affordability_score = max(0.0, 1.0 - (price_gap / max(affordability_ceiling, 1.0)))
                target_wallet_price = float(user.user_budget) * (0.84 + ((1.0 - float(user.price_sensitivity)) * 0.22))
                price_fit_score = max(0.0, 1.0 - abs(sale_price - target_wallet_price) / max(float(user.user_budget), 1.0))
                value_per_hour = round(float(pack.market_avg_roi) / max(sale_price, 1.0), 6)
                demand_to_price_ratio = round((float(pack.market_demand_score) * 100.0) / max(sale_price, 1.0), 6)
                discount_alignment = max(0.0, 1.0 - abs(discount_pct - hidden_target_discount) / 18.0)

                purchase_logit = -4.85
                purchase_logit += float(pack.market_demand_score) * 1.42
                purchase_logit += float(pack.roi_norm) * 0.92
                purchase_logit += affordability_score * (1.10 + (float(user.price_sensitivity) * 0.20))
                purchase_logit += price_fit_score * 0.86
                purchase_logit += discount_alignment * (1.52 + (float(user.price_sensitivity) * 0.46))
                purchase_logit += category_match * 0.78
                purchase_logit += level_match * 0.52
                purchase_logit += float(user.activity_score) * 0.42
                purchase_logit += float(user.loyalty_score) * 0.34
                purchase_logit += min(float(pack.market_course_count), 60.0) / 60.0 * 0.20
                purchase_logit += min(float(pack.trend_total_views_log), 20.0) / 20.0 * 0.22
                purchase_logit += min(float(pack.trend_total_engagement_log), 16.0) / 16.0 * 0.18
                purchase_logit -= max(budget_pressure - 1.18, 0.0) * (1.62 + float(user.price_sensitivity))
                purchase_logit -= abs(discount_pct - hidden_target_discount) * 0.031
                purchase_logit -= duration_gap * 0.0055
                purchase_logit -= level_gap * 0.22
                purchase_logit += rng.normal(0.0, 0.18)
                heuristic_probability = sigmoid(purchase_logit)
                purchased = int(rng.binomial(1, heuristic_probability))

                rows.append(
                    {
                        "user_id": int(user.user_id),
                        "pack_id": int(pack.pack_id),
                        "title": pack.title,
                        "category_id": int(pack.category_id),
                        "category_name": pack.category_name,
                        "level": pack.level,
                        "level_rank": int(pack.level_rank),
                        "primary_skill": pack.primary_skill,
                        "primary_skill_code": int(pack.primary_skill_code),
                        "original_price": round(float(pack.original_price), 2),
                        "current_sale_price": round(float(pack.current_sale_price), 2),
                        "current_discount_pct": round(float(pack.current_discount_pct), 4),
                        "sale_price": round(float(sale_price), 2),
                        "discount_pct": round(float(discount_pct), 4),
                        "savings_amount": round(float(savings_amount), 2),
                        "price_delta_vs_current_pct": round(float(price_delta_vs_current_pct), 4),
                        "discount_change_vs_current_pct": round(float(discount_change_vs_current_pct), 4),
                        "duration_hours": int(pack.duration_hours),
                        "market_demand_score": round(float(pack.market_demand_score), 6),
                        "market_avg_roi": round(float(pack.market_avg_roi), 4),
                        "market_avg_salary_boost": round(float(pack.market_avg_salary_boost), 2),
                        "market_course_count": int(round(float(pack.market_course_count))),
                        "trend_total_views_log": round(float(pack.trend_total_views_log), 6),
                        "trend_total_engagement_log": round(float(pack.trend_total_engagement_log), 6),
                        "value_strength": round(float(pack.value_strength), 6),
                        "preferred_category_id": int(user.preferred_category_id),
                        "preferred_level": user.preferred_level,
                        "preferred_level_rank": int(user.preferred_level_rank),
                        "preferred_duration_hours": int(user.preferred_duration_hours),
                        "user_budget": round(float(user.user_budget), 2),
                        "activity_score": round(float(user.activity_score), 6),
                        "loyalty_score": round(float(user.loyalty_score), 6),
                        "price_sensitivity": round(float(user.price_sensitivity), 6),
                        "budget_flex_pct": round(float(user.budget_flex_pct), 6),
                        "category_match": int(category_match),
                        "level_match": int(level_match),
                        "level_gap": int(level_gap),
                        "duration_gap": int(duration_gap),
                        "affordability_score": round(float(affordability_score), 6),
                        "price_fit_score": round(float(price_fit_score), 6),
                        "budget_pressure": round(float(budget_pressure), 6),
                        "value_per_hour": round(float(value_per_hour), 6),
                        "demand_to_price_ratio": round(float(demand_to_price_ratio), 6),
                        "latent_target_discount_pct": round(float(hidden_target_discount), 4),
                        "heuristic_purchase_probability": round(float(heuristic_probability), 6),
                        "purchased": int(purchased),
                        "is_current_price": int(math.isclose(discount_pct, float(pack.current_discount_pct), rel_tol=0.0, abs_tol=0.0001)),
                    }
                )

    return pd.DataFrame(rows)


def train_model(data: pd.DataFrame) -> tuple[RandomForestClassifier, pd.DataFrame, dict[str, Any]]:
    features = [
        "sale_price",
        "discount_pct",
        "savings_amount",
        "price_delta_vs_current_pct",
        "discount_change_vs_current_pct",
        "duration_hours",
        "category_id",
        "level_rank",
        "primary_skill_code",
        "market_demand_score",
        "market_avg_roi",
        "market_avg_salary_boost",
        "market_course_count",
        "trend_total_views_log",
        "trend_total_engagement_log",
        "value_strength",
        "user_budget",
        "activity_score",
        "loyalty_score",
        "price_sensitivity",
        "budget_flex_pct",
        "preferred_category_id",
        "preferred_level_rank",
        "preferred_duration_hours",
        "category_match",
        "level_match",
        "level_gap",
        "duration_gap",
        "affordability_score",
        "price_fit_score",
        "budget_pressure",
        "value_per_hour",
        "demand_to_price_ratio",
    ]

    X = data[features]
    y = data["purchased"]
    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=0.25, random_state=RANDOM_SEED, stratify=y
    )

    model = RandomForestClassifier(
        n_estimators=260,
        max_depth=12,
        min_samples_leaf=8,
        class_weight="balanced_subsample",
        random_state=RANDOM_SEED,
        n_jobs=1,
    )
    model.fit(X_train, y_train)

    test_probabilities = model.predict_proba(X_test)[:, 1]
    test_predictions = (test_probabilities >= 0.5).astype(int)
    metrics = {
        "training_rows": int(len(data)),
        "test_rows": int(len(X_test)),
        "positive_rate": round(float(y.mean()), 6),
        "accuracy": round(float(accuracy_score(y_test, test_predictions)), 6),
        "roc_auc": round(float(roc_auc_score(y_test, test_probabilities)), 6),
    }

    scored = data.copy()
    scored["predicted_purchase_probability"] = model.predict_proba(X)[:, 1].round(6)
    return model, scored, metrics


def build_price_scenarios(scored: pd.DataFrame) -> pd.DataFrame:
    scenarios = (
        scored.groupby(
            [
                "pack_id",
                "title",
                "category_id",
                "category_name",
                "level",
                "primary_skill",
                "original_price",
                "current_sale_price",
                "current_discount_pct",
                "sale_price",
                "discount_pct",
                "is_current_price",
            ],
            as_index=False,
        )
        .agg(
            market_demand_score=("market_demand_score", "mean"),
            market_avg_roi=("market_avg_roi", "mean"),
            market_course_count=("market_course_count", "mean"),
            expected_conversion_rate=("predicted_purchase_probability", "mean"),
            simulated_conversion_rate=("purchased", "mean"),
            expected_buyers=("predicted_purchase_probability", "sum"),
            scenario_support=("user_id", "count"),
        )
        .reset_index(drop=True)
    )
    scenarios["expected_revenue"] = (scenarios["expected_buyers"] * scenarios["sale_price"]).round(2)
    scenarios["market_course_count"] = scenarios["market_course_count"].round(0).astype(int)
    scenarios["scenario_support"] = scenarios["scenario_support"].astype(int)
    scenarios["discount_gap_vs_current_pct"] = (scenarios["discount_pct"] - scenarios["current_discount_pct"]).round(4)
    return scenarios


def discount_band_label(low_pct: float, high_pct: float) -> str:
    midpoint = (low_pct + high_pct) / 2.0
    if midpoint < 18.0:
        return "margin protect"
    if midpoint < 27.0:
        return "balanced growth"
    if midpoint < 35.0:
        return "conversion push"
    return "aggressive demand capture"


def price_action_label(current_price: float, recommended_price: float) -> str:
    if recommended_price > current_price * 1.03:
        return "increase"
    if recommended_price < current_price * 0.97:
        return "decrease"
    return "hold"


def build_recommendations(scenarios: pd.DataFrame) -> tuple[pd.DataFrame, pd.DataFrame]:
    scenario_frames = []
    recommendations = []

    for _, pack_group in scenarios.groupby("pack_id", sort=False):
        group = pack_group.copy()
        group["expected_revenue"] = (group["expected_buyers"] * group["sale_price"]).round(2)
        group = group.sort_values(
            ["expected_revenue", "expected_conversion_rate", "discount_pct"],
            ascending=[False, False, True],
        ).reset_index(drop=True)
        group["scenario_rank"] = np.arange(1, len(group) + 1)

        current_matches = group[group["is_current_price"] == 1]
        baseline = current_matches.iloc[0] if not current_matches.empty else group.iloc[group["discount_gap_vs_current_pct"].abs().argmin()]
        best = group.iloc[0]
        second_best_revenue = float(group.iloc[1]["expected_revenue"]) if len(group) > 1 else float(best["expected_revenue"])
        revenue_floor = float(best["expected_revenue"]) * 0.975
        near_optimal = group[group["expected_revenue"] >= revenue_floor]

        discount_min = round(float(near_optimal["discount_pct"].min()), 4)
        discount_max = round(float(near_optimal["discount_pct"].max()), 4)
        recommended_band = discount_band_label(discount_min, discount_max)
        price_action = price_action_label(float(baseline["sale_price"]), float(best["sale_price"]))

        baseline_revenue = float(baseline["expected_revenue"])
        recommended_revenue = float(best["expected_revenue"])
        baseline_conversion = float(baseline["expected_conversion_rate"])
        recommended_conversion = float(best["expected_conversion_rate"])
        revenue_lift_pct = 0.0 if math.isclose(baseline_revenue, 0.0) else ((recommended_revenue - baseline_revenue) / baseline_revenue) * 100.0
        conversion_lift_pct = 0.0 if math.isclose(baseline_conversion, 0.0) else ((recommended_conversion - baseline_conversion) / baseline_conversion) * 100.0
        confidence = clamp(
            0.58 + (((recommended_revenue - second_best_revenue) / max(recommended_revenue, 1.0)) * 1.35) + (float(best["market_demand_score"]) * 0.12),
            0.55,
            0.97,
        )

        recommendations.append(
            {
                "pack_id": int(best["pack_id"]),
                "title": best["title"],
                "category_id": int(best["category_id"]),
                "category_name": best["category_name"],
                "level": best["level"],
                "primary_skill": best["primary_skill"],
                "original_price": round(float(best["original_price"]), 2),
                "current_sale_price": round(float(baseline["sale_price"]), 2),
                "current_discount_pct": round(float(baseline["discount_pct"]), 4),
                "recommended_sale_price": round(float(best["sale_price"]), 2),
                "recommended_discount_pct": round(float(best["discount_pct"]), 4),
                "discount_range_min_pct": discount_min,
                "discount_range_max_pct": discount_max,
                "recommended_band": recommended_band,
                "price_action": price_action,
                "baseline_conversion_rate": round(baseline_conversion, 6),
                "recommended_conversion_rate": round(recommended_conversion, 6),
                "baseline_expected_revenue": round(baseline_revenue, 2),
                "recommended_expected_revenue": round(recommended_revenue, 2),
                "revenue_lift_pct": round(revenue_lift_pct, 4),
                "conversion_lift_pct": round(conversion_lift_pct, 4),
                "pricing_confidence": round(float(confidence), 6),
                "market_demand_score": round(float(best["market_demand_score"]), 6),
                "market_avg_roi": round(float(best["market_avg_roi"]), 4),
                "market_course_count": int(best["market_course_count"]),
                "scenario_count": int(len(group)),
            }
        )

        group["revenue_index"] = (
            group["expected_revenue"] / max(baseline_revenue, 1.0)
        ).round(6)
        group["price_action_hint"] = group["sale_price"].apply(lambda value: price_action_label(float(baseline["sale_price"]), float(value)))
        scenario_frames.append(group)

    scenario_output = pd.concat(scenario_frames, ignore_index=True)
    scenario_output = scenario_output.sort_values(["pack_id", "scenario_rank"]).reset_index(drop=True)
    scenario_output["scenario_id"] = np.arange(1, len(scenario_output) + 1)

    recommendations_df = pd.DataFrame(recommendations).sort_values(
        ["revenue_lift_pct", "recommended_expected_revenue", "pricing_confidence"],
        ascending=[False, False, False],
    ).reset_index(drop=True)
    recommendations_df["pricing_rank"] = np.arange(1, len(recommendations_df) + 1)
    return recommendations_df, scenario_output


def portfolio_summary(recommendations: pd.DataFrame) -> dict[str, Any]:
    current_total = round(float(recommendations["baseline_expected_revenue"].sum()), 2)
    recommended_total = round(float(recommendations["recommended_expected_revenue"].sum()), 2)
    uplift_pct = round(((recommended_total - current_total) / max(current_total, 1.0)) * 100.0, 4)
    return {
        "current_expected_revenue_total": current_total,
        "recommended_expected_revenue_total": recommended_total,
        "revenue_uplift_pct": uplift_pct,
        "packs_to_increase_price": int((recommendations["price_action"] == "increase").sum()),
        "packs_to_decrease_price": int((recommendations["price_action"] == "decrease").sum()),
        "packs_to_hold_price": int((recommendations["price_action"] == "hold").sum()),
    }


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


def write_sql_outputs(recommendations: pd.DataFrame, scenarios: pd.DataFrame) -> None:
    generated_at = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    schema_sql = """
CREATE TABLE IF NOT EXISTS pack_dynamic_pricing_recommendations (
    pack_id BIGINT PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    category_id INT NOT NULL,
    category_name VARCHAR(100) NOT NULL,
    level VARCHAR(30) NOT NULL,
    primary_skill VARCHAR(80) NOT NULL,
    original_price DECIMAL(10,2) NOT NULL,
    current_sale_price DECIMAL(10,2) NOT NULL,
    current_discount_pct DECIMAL(10,4) NOT NULL,
    recommended_sale_price DECIMAL(10,2) NOT NULL,
    recommended_discount_pct DECIMAL(10,4) NOT NULL,
    discount_range_min_pct DECIMAL(10,4) NOT NULL,
    discount_range_max_pct DECIMAL(10,4) NOT NULL,
    recommended_band VARCHAR(80) NOT NULL,
    price_action VARCHAR(20) NOT NULL,
    baseline_conversion_rate DECIMAL(10,6) NOT NULL,
    recommended_conversion_rate DECIMAL(10,6) NOT NULL,
    baseline_expected_revenue DECIMAL(12,2) NOT NULL,
    recommended_expected_revenue DECIMAL(12,2) NOT NULL,
    revenue_lift_pct DECIMAL(10,4) NOT NULL,
    conversion_lift_pct DECIMAL(10,4) NOT NULL,
    pricing_confidence DECIMAL(10,6) NOT NULL,
    market_demand_score DECIMAL(10,6) NOT NULL,
    market_avg_roi DECIMAL(10,4) NOT NULL,
    market_course_count INT NOT NULL,
    scenario_count INT NOT NULL,
    pricing_rank INT NOT NULL,
    generated_at DATETIME NOT NULL
);

CREATE TABLE IF NOT EXISTS pack_pricing_scenarios (
    scenario_id BIGINT PRIMARY KEY,
    pack_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    category_id INT NOT NULL,
    category_name VARCHAR(100) NOT NULL,
    level VARCHAR(30) NOT NULL,
    primary_skill VARCHAR(80) NOT NULL,
    original_price DECIMAL(10,2) NOT NULL,
    current_sale_price DECIMAL(10,2) NOT NULL,
    current_discount_pct DECIMAL(10,4) NOT NULL,
    sale_price DECIMAL(10,2) NOT NULL,
    discount_pct DECIMAL(10,4) NOT NULL,
    discount_gap_vs_current_pct DECIMAL(10,4) NOT NULL,
    is_current_price TINYINT(1) NOT NULL,
    expected_conversion_rate DECIMAL(10,6) NOT NULL,
    simulated_conversion_rate DECIMAL(10,6) NOT NULL,
    expected_buyers DECIMAL(10,2) NOT NULL,
    expected_revenue DECIMAL(12,2) NOT NULL,
    revenue_index DECIMAL(10,6) NOT NULL,
    market_demand_score DECIMAL(10,6) NOT NULL,
    market_avg_roi DECIMAL(10,4) NOT NULL,
    market_course_count INT NOT NULL,
    price_action_hint VARCHAR(20) NOT NULL,
    scenario_rank INT NOT NULL,
    scenario_support INT NOT NULL,
    generated_at DATETIME NOT NULL
);
""".strip()
    (OUTPUTS_DIR / "schema_dynamic_pricing.sql").write_text(schema_sql + "\n", encoding="utf-8")

    recommendation_columns = [
        "pack_id", "title", "category_id", "category_name", "level", "primary_skill",
        "original_price", "current_sale_price", "current_discount_pct",
        "recommended_sale_price", "recommended_discount_pct",
        "discount_range_min_pct", "discount_range_max_pct",
        "recommended_band", "price_action",
        "baseline_conversion_rate", "recommended_conversion_rate",
        "baseline_expected_revenue", "recommended_expected_revenue",
        "revenue_lift_pct", "conversion_lift_pct",
        "pricing_confidence", "market_demand_score", "market_avg_roi",
        "market_course_count", "scenario_count", "pricing_rank",
    ]
    recommendation_values = []
    for row in recommendations.itertuples(index=False):
        values = [sql_value(getattr(row, column)) for column in recommendation_columns] + [sql_value(generated_at)]
        recommendation_values.append("    (" + ", ".join(values) + ")")
    (OUTPUTS_DIR / "seed_dynamic_pricing_recommendations.sql").write_text(
        "INSERT INTO pack_dynamic_pricing_recommendations (" + ", ".join(recommendation_columns) + ", generated_at) VALUES\n"
        + ",\n".join(recommendation_values)
        + ";\n",
        encoding="utf-8",
    )

    scenario_columns = [
        "scenario_id", "pack_id", "title", "category_id", "category_name", "level", "primary_skill",
        "original_price", "current_sale_price", "current_discount_pct", "sale_price", "discount_pct",
        "discount_gap_vs_current_pct", "is_current_price", "expected_conversion_rate", "simulated_conversion_rate",
        "expected_buyers", "expected_revenue", "revenue_index", "market_demand_score", "market_avg_roi",
        "market_course_count", "price_action_hint", "scenario_rank", "scenario_support",
    ]
    scenario_values = []
    for row in scenarios.itertuples(index=False):
        values = [sql_value(getattr(row, column)) for column in scenario_columns] + [sql_value(generated_at)]
        scenario_values.append("    (" + ", ".join(values) + ")")
    (OUTPUTS_DIR / "seed_pack_pricing_scenarios.sql").write_text(
        "INSERT INTO pack_pricing_scenarios (" + ", ".join(scenario_columns) + ", generated_at) VALUES\n"
        + ",\n".join(scenario_values)
        + ";\n",
        encoding="utf-8",
    )


def write_summary(metadata: dict[str, Any], metrics: dict[str, Any], recommendations: pd.DataFrame) -> None:
    summary = {
        "generated_at": datetime.now().isoformat(),
        "scraper_inputs": metadata,
        "model": {
            "algorithm": "RandomForestClassifier",
            "random_seed": RANDOM_SEED,
            "user_count": USER_COUNT,
            "discount_grid_pct": BASE_DISCOUNT_GRID,
            **metrics,
        },
        "assumption": "Scraper files provide demand, ROI, and engagement signals but not real competitor prices or real pack checkout history, so the pricing model learns on simulated offer scenarios anchored to your pack catalog.",
        "portfolio_summary": portfolio_summary(recommendations),
        "top_pricing_recommendations": recommendations.head(10).to_dict(orient="records"),
    }
    (OUTPUTS_DIR / "dynamic_pricing_summary.json").write_text(json.dumps(summary, indent=2), encoding="utf-8")


def main() -> None:
    ensure_dirs()
    market, metadata = load_market_signals()
    packs, _ = build_pack_frame(market)
    users = generate_user_profiles(USER_COUNT)
    training_data = generate_training_data(packs, users)
    model, scored_data, metrics = train_model(training_data)
    price_scenarios = build_price_scenarios(scored_data)
    recommendations, scenario_output = build_recommendations(price_scenarios)

    recommendations.to_csv(OUTPUTS_DIR / "dynamic_pricing_recommendations.csv", index=False)
    scenario_output.to_csv(OUTPUTS_DIR / "pack_pricing_scenarios.csv", index=False)
    scored_data.to_csv(OUTPUTS_DIR / "dynamic_pricing_training_data.csv", index=False)
    recommendations.head(10).to_csv(OUTPUTS_DIR / "top_dynamic_pricing_opportunities.csv", index=False)
    joblib.dump(model, ARTIFACTS_DIR / "dynamic_pricing_model.joblib")
    write_sql_outputs(recommendations, scenario_output)
    write_summary(metadata, metrics, recommendations)

    portfolio = portfolio_summary(recommendations)
    print("Dynamic pricing pipeline completed.")
    print(f"Training rows: {metrics['training_rows']}")
    print(f"Accuracy: {metrics['accuracy']}")
    print(f"ROC AUC: {metrics['roc_auc']}")
    print(f"Portfolio revenue uplift: {portfolio['revenue_uplift_pct']}%")
    print(
        recommendations[
            [
                "pricing_rank",
                "title",
                "price_action",
                "current_sale_price",
                "recommended_sale_price",
                "recommended_discount_pct",
                "revenue_lift_pct",
            ]
        ].head(8).to_string(index=False)
    )


if __name__ == "__main__":
    main()
