from __future__ import annotations

import json
import math
import re
from datetime import datetime
from pathlib import Path
from typing import Any

import joblib
import numpy as np
import pandas as pd
from sklearn.ensemble import RandomForestClassifier, RandomForestRegressor
from sklearn.metrics import accuracy_score, mean_absolute_error, r2_score
from sklearn.model_selection import train_test_split


BASE_DIR = Path(__file__).resolve().parent
PROJECT_ROOT = BASE_DIR.parent
FORECAST_DIR = PROJECT_ROOT / "forcasting"
RECOMMENDATIONS_DIR = PROJECT_ROOT / "forcasting recommendations" / "outputs"
DYNAMIC_PRICING_DIR = PROJECT_ROOT / "Dynamic pricing model" / "outputs"
SCRAPER_DIR = PROJECT_ROOT / "scraper" / "result" / "elearning_outputs"
OUTPUTS_DIR = BASE_DIR / "outputs"
ARTIFACTS_DIR = BASE_DIR / "artifacts"

RANDOM_SEED = 42
SCENARIOS_PER_MONTH = 1_600
TARGET_MARGIN_PCT = 25.0

DEMAND_LEVEL_SCORES = {"very_high": 1.0, "high": 0.82, "medium": 0.60, "low": 0.35}
PAYMENT_METHOD_SQL_MAP = {"BANK_TRANSFER": "LOCAL"}
EXPENSE_CATEGORY_SQL_MAP = {
    "TOOLS": "SOFTWARE",
    "CONTENT": "OTHER",
    "SUPPORT": "OTHER",
    "SALARY": "SALARIES",
}

SCENARIO_LIBRARY = [
    {
        "scenario_name": "Base Plan",
        "scenario_slug": "base-plan",
        "description": "Keep the current finance forecast unchanged and use it as the benchmark.",
        "controls": {
            "marketing_budget_change_pct": 0.0,
            "dynamic_pricing_rollout_pct": 0.0,
            "new_pack_launches": 0,
            "cost_control_pct": 0.0,
            "salary_optimization_pct": 0.0,
            "market_demand_shock_pct": 0.0,
            "support_automation_pct": 0.0,
            "focus_top_market_pct": 0.0,
        },
    },
    {
        "scenario_name": "Dynamic Pricing Rollout",
        "scenario_slug": "dynamic-pricing-rollout",
        "description": "Apply the pack pricing model to most of the catalog while keeping spend nearly flat.",
        "controls": {
            "marketing_budget_change_pct": 2.0,
            "dynamic_pricing_rollout_pct": 85.0,
            "new_pack_launches": 0,
            "cost_control_pct": 2.0,
            "salary_optimization_pct": 0.0,
            "market_demand_shock_pct": 2.0,
            "support_automation_pct": 4.0,
            "focus_top_market_pct": 4.0,
        },
    },
    {
        "scenario_name": "Growth Push",
        "scenario_slug": "growth-push",
        "description": "Increase marketing and launch new packs while also rolling out dynamic pricing.",
        "controls": {
            "marketing_budget_change_pct": 12.0,
            "dynamic_pricing_rollout_pct": 80.0,
            "new_pack_launches": 2,
            "cost_control_pct": 1.5,
            "salary_optimization_pct": 0.0,
            "market_demand_shock_pct": 5.0,
            "support_automation_pct": 3.0,
            "focus_top_market_pct": 8.0,
        },
    },
    {
        "scenario_name": "AI Expansion",
        "scenario_slug": "ai-expansion",
        "description": "Concentrate spend on the strongest scraper-driven AI and data opportunities.",
        "controls": {
            "marketing_budget_change_pct": 10.0,
            "dynamic_pricing_rollout_pct": 70.0,
            "new_pack_launches": 3,
            "cost_control_pct": 2.0,
            "salary_optimization_pct": 1.0,
            "market_demand_shock_pct": 8.0,
            "support_automation_pct": 4.0,
            "focus_top_market_pct": 12.0,
        },
    },
    {
        "scenario_name": "Efficiency Guardrail",
        "scenario_slug": "efficiency-guardrail",
        "description": "Protect margin by trimming overhead, automating support, and keeping growth moderate.",
        "controls": {
            "marketing_budget_change_pct": -4.0,
            "dynamic_pricing_rollout_pct": 45.0,
            "new_pack_launches": 0,
            "cost_control_pct": 8.0,
            "salary_optimization_pct": 5.0,
            "market_demand_shock_pct": -1.0,
            "support_automation_pct": 9.0,
            "focus_top_market_pct": 3.0,
        },
    },
    {
        "scenario_name": "Market Slowdown Defense",
        "scenario_slug": "market-slowdown-defense",
        "description": "Prepare for weaker demand by tightening costs and leaning on pricing discipline.",
        "controls": {
            "marketing_budget_change_pct": -8.0,
            "dynamic_pricing_rollout_pct": 65.0,
            "new_pack_launches": 0,
            "cost_control_pct": 10.0,
            "salary_optimization_pct": 6.0,
            "market_demand_shock_pct": -10.0,
            "support_automation_pct": 10.0,
            "focus_top_market_pct": 2.0,
        },
    },
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


def clamp(value: float, low: float, high: float) -> float:
    return max(low, min(value, high))


def parse_metric_number(raw_value: Any) -> float:
    text = str(raw_value).strip()
    if not text:
        return 0.0
    cleaned = text.replace(",", "")
    match = re.search(r"-?\d+(?:\.\d+)?", cleaned)
    return float(match.group()) if match else 0.0


def sql_quote(value: Any) -> str:
    if value is None or (isinstance(value, float) and math.isnan(value)):
        return "NULL"
    if isinstance(value, (int, np.integer)):
        return str(int(value))
    if isinstance(value, (float, np.floating)):
        return f"{float(value):.6f}".rstrip("0").rstrip(".")
    text = str(value).replace("\\", "\\\\").replace("'", "''")
    return f"'{text}'"


def sql_datetime(value: Any) -> str:
    if value is None or pd.isna(value):
        return "NULL"
    timestamp = pd.to_datetime(value)
    return sql_quote(timestamp.strftime("%Y-%m-%d %H:%M:%S.%f"))


def extract_reference_id(value: Any) -> int | None:
    if value is None or pd.isna(value):
        return None
    digits = re.sub(r"\D", "", str(value))
    return int(digits) if digits else None


def load_forecast_baseline() -> tuple[pd.DataFrame, dict[str, Any], dict[str, Any]]:
    forecast_path = FORECAST_DIR / "outputs" / "forecast_monthly_6m.csv"
    dashboard_path = FORECAST_DIR / "outputs" / "dashboard_data.json"
    forecast = pd.read_csv(forecast_path)
    dashboard = json.loads(dashboard_path.read_text(encoding="utf-8"))

    forecast = forecast.rename(
        columns={
            "predicted_income": "baseline_income",
            "predicted_income_low": "baseline_income_low",
            "predicted_income_high": "baseline_income_high",
            "predicted_expenses": "baseline_expenses",
            "predicted_expenses_low": "baseline_expenses_low",
            "predicted_expenses_high": "baseline_expenses_high",
            "predicted_profit": "baseline_profit",
            "predicted_profit_low": "baseline_profit_low",
            "predicted_profit_high": "baseline_profit_high",
            "predicted_margin": "baseline_margin",
            "predicted_margin_low": "baseline_margin_low",
            "predicted_margin_high": "baseline_margin_high",
            "predicted_salary": "baseline_salary",
            "predicted_marketing": "baseline_marketing",
            "predicted_infrastructure": "baseline_infrastructure",
            "predicted_tools": "baseline_tools",
            "predicted_content": "baseline_content",
            "predicted_support": "baseline_support",
        }
    )
    forecast["month_index"] = np.arange(1, len(forecast) + 1)
    forecast["baseline_risk_gap"] = (TARGET_MARGIN_PCT - forecast["baseline_margin"]).clip(lower=0.0)
    forecast["baseline_operational_pressure"] = (
        forecast["baseline_salary"] + forecast["baseline_support"] + forecast["baseline_content"]
    ) / forecast["baseline_expenses"].replace(0, np.nan)
    forecast["baseline_operational_pressure"] = forecast["baseline_operational_pressure"].fillna(0.0)
    forecast["baseline_marketing_share"] = (
        forecast["baseline_marketing"] / forecast["baseline_expenses"].replace(0, np.nan)
    ).fillna(0.0)

    inputs = {"forecast_file": str(forecast_path), "dashboard_file": str(dashboard_path)}
    return forecast, dashboard, inputs


def load_recommendations_summary() -> tuple[dict[str, Any], dict[str, Any]]:
    summary_path = RECOMMENDATIONS_DIR / "financial_recommendations_summary.json"
    summary = json.loads(summary_path.read_text(encoding="utf-8"))
    executive_metrics = {item["metric"]: item["value"] for item in summary.get("executive_metrics", [])}
    forecast_context = summary.get("forecast_context", {})

    parsed = {
        "forecast_margin_pct": float(forecast_context.get("margin_pct", 0.0)),
        "margin_trend_pct": float(forecast_context.get("margin_trend_pct", 0.0)),
        "watch_category": str(forecast_context.get("watch_category", "OTHER")),
        "watch_growth_pct": float(forecast_context.get("watch_growth_pct", 0.0)),
        "avg_margin_uplift_pts": parse_metric_number(executive_metrics.get("avg_margin_uplift", 0.0)),
        "avg_profit_uplift": parse_metric_number(executive_metrics.get("avg_profit_uplift", 0.0)),
        "months_at_risk_before": parse_metric_number(executive_metrics.get("months_at_risk_before", 0.0)),
        "months_at_risk_after": parse_metric_number(executive_metrics.get("months_at_risk_after", 0.0)),
        "best_gain_window": str(executive_metrics.get("best_gain_window", "")),
        "top_revenue_source": str(forecast_context.get("top_revenue_source", "COURSE_SALE")),
        "user_playbook": summary.get("user_playbook", []),
    }
    return parsed, {"recommendations_summary_file": str(summary_path)}


def load_dynamic_pricing_summary() -> tuple[dict[str, Any], dict[str, Any]]:
    summary_path = DYNAMIC_PRICING_DIR / "dynamic_pricing_summary.json"
    summary = json.loads(summary_path.read_text(encoding="utf-8"))
    portfolio = summary.get("portfolio_summary", {})
    top_recommendations = summary.get("top_pricing_recommendations", [])
    parsed = {
        "revenue_uplift_pct": float(portfolio.get("revenue_uplift_pct", 0.0)),
        "recommended_expected_revenue_total": float(portfolio.get("recommended_expected_revenue_total", 0.0)),
        "current_expected_revenue_total": float(portfolio.get("current_expected_revenue_total", 0.0)),
        "packs_to_increase_price": int(portfolio.get("packs_to_increase_price", 0)),
        "packs_to_decrease_price": int(portfolio.get("packs_to_decrease_price", 0)),
        "packs_to_hold_price": int(portfolio.get("packs_to_hold_price", 0)),
        "top_pricing_recommendations": top_recommendations[:5],
    }
    return parsed, {"dynamic_pricing_summary_file": str(summary_path)}


def load_market_signals() -> tuple[pd.DataFrame, dict[str, Any], dict[str, Any]]:
    courses_path = latest_file("courses_*.csv")
    trends_path = latest_file("skill_trends_*.csv")

    courses = pd.read_csv(courses_path)
    trends = pd.read_csv(trends_path)

    courses["roi_score"] = pd.to_numeric(courses["roi_score"], errors="coerce").fillna(0.0)
    courses["estimated_salary_boost"] = pd.to_numeric(courses["estimated_salary_boost"], errors="coerce").fillna(0.0)
    courses["demand_level_score"] = (
        courses["demand_level"].fillna("medium").astype(str).str.lower().map(DEMAND_LEVEL_SCORES).fillna(0.55)
    )

    trends["views"] = pd.to_numeric(trends["views"], errors="coerce").fillna(0.0)
    trends["engagement_score"] = pd.to_numeric(trends["engagement_score"], errors="coerce").fillna(0.0)
    trends["roi_score"] = pd.to_numeric(trends["roi_score"], errors="coerce").fillna(0.0)
    trends["estimated_salary_boost"] = pd.to_numeric(trends["estimated_salary_boost"], errors="coerce").fillna(0.0)
    trends["demand_level_score"] = (
        trends["demand_level"].fillna("medium").astype(str).str.lower().map(DEMAND_LEVEL_SCORES).fillna(0.55)
    )

    course_agg = courses.groupby("skill_category", dropna=False).agg(
        market_course_count=("title", "count"),
        market_avg_roi=("roi_score", "mean"),
        market_avg_salary_boost=("estimated_salary_boost", "mean"),
        market_demand_level_score=("demand_level_score", "mean"),
    )
    trend_agg = trends.groupby("skill_category", dropna=False).agg(
        trend_rows=("title", "count"),
        trend_total_views=("views", "sum"),
        trend_total_engagement=("engagement_score", "sum"),
        trend_avg_roi=("roi_score", "mean"),
        trend_avg_salary_boost=("estimated_salary_boost", "mean"),
        trend_demand_level_score=("demand_level_score", "mean"),
    )

    market = course_agg.join(trend_agg, how="outer").reset_index().fillna(0.0)
    market["skill_category"] = market["skill_category"].fillna("general")
    market["course_count_norm"] = normalize_series(market["market_course_count"])
    market["roi_norm"] = normalize_series((market["market_avg_roi"] * 0.65) + (market["trend_avg_roi"] * 0.35))
    market["views_norm"] = normalize_series(np.log1p(market["trend_total_views"]))
    market["engagement_norm"] = normalize_series(np.log1p(market["trend_total_engagement"]))
    market["salary_norm"] = normalize_series(
        (market["market_avg_salary_boost"] * 0.60) + (market["trend_avg_salary_boost"] * 0.40)
    )
    market["demand_norm"] = normalize_series(
        (market["market_demand_level_score"] * 0.55) + (market["trend_demand_level_score"] * 0.45)
    )
    market["market_opportunity_score"] = (
        market["course_count_norm"] * 0.14
        + market["roi_norm"] * 0.24
        + market["views_norm"] * 0.24
        + market["engagement_norm"] * 0.18
        + market["salary_norm"] * 0.10
        + market["demand_norm"] * 0.10
    ).round(6)

    total_views = float(market["trend_total_views"].sum()) or 1.0
    weighted_global_signal = float(
        np.average(market["market_opportunity_score"], weights=market["trend_total_views"].replace(0, 1.0))
    )
    weighted_roi = float(np.average(market["market_avg_roi"], weights=market["market_course_count"].replace(0, 1.0)))
    weighted_salary_boost = float(
        np.average(market["market_avg_salary_boost"], weights=market["market_course_count"].replace(0, 1.0))
    )

    top_opportunities_df = market.sort_values(
        ["market_opportunity_score", "trend_total_views"], ascending=[False, False]
    ).head(5)
    top_opportunities = top_opportunities_df[
        [
            "skill_category",
            "market_opportunity_score",
            "market_avg_roi",
            "trend_total_views",
            "trend_total_engagement",
            "market_course_count",
        ]
    ].to_dict(orient="records")

    summary = {
        "global_market_signal": weighted_global_signal,
        "top_opportunity_score": float(top_opportunities_df["market_opportunity_score"].head(3).mean()),
        "weighted_market_roi": weighted_roi,
        "weighted_salary_boost": weighted_salary_boost,
        "top_skill_category": str(top_opportunities_df.iloc[0]["skill_category"]) if not top_opportunities_df.empty else "general",
        "top_opportunities": top_opportunities,
        "tracked_skills": int(market["skill_category"].nunique()),
        "total_scraper_views": total_views,
    }
    inputs = {"courses_file": str(courses_path), "skill_trends_file": str(trends_path)}
    return market, summary, inputs


def simulate_scenario(
    baseline_row: pd.Series,
    controls: dict[str, float | int],
    market_summary: dict[str, Any],
    pricing_summary: dict[str, Any],
    recommendations_summary: dict[str, Any],
    rng: np.random.Generator | None = None,
    include_noise: bool = False,
) -> dict[str, Any]:
    marketing_change = float(controls["marketing_budget_change_pct"])
    dynamic_rollout = float(controls["dynamic_pricing_rollout_pct"])
    new_packs = int(controls["new_pack_launches"])
    cost_control = float(controls["cost_control_pct"])
    salary_optimization = float(controls["salary_optimization_pct"])
    demand_shock = float(controls["market_demand_shock_pct"])
    support_automation = float(controls["support_automation_pct"])
    focus_top_market = float(controls["focus_top_market_pct"])

    global_market_signal = float(market_summary["global_market_signal"])
    top_opportunity_score = float(market_summary["top_opportunity_score"])
    pricing_uplift_pct = float(pricing_summary["revenue_uplift_pct"])
    baseline_risk_gap = float(baseline_row["baseline_risk_gap"])
    margin_trend_pct = float(recommendations_summary["margin_trend_pct"])

    pricing_gain = (dynamic_rollout / 100.0) * (pricing_uplift_pct / 100.0) * (0.45 + 0.20 * global_market_signal)
    marketing_gain = (marketing_change / 100.0) * (0.72 + 0.34 * global_market_signal - 0.012 * baseline_risk_gap)
    launch_gain = new_packs * (0.011 + 0.007 * top_opportunity_score + 0.002 * global_market_signal)
    focus_gain = (focus_top_market / 100.0) * (0.28 * top_opportunity_score + 0.06 * global_market_signal)
    demand_gain = (demand_shock / 100.0) * (0.82 + 0.25 * global_market_signal)
    automation_gain = (support_automation / 100.0) * 0.06
    recommendation_gain = max(recommendations_summary["avg_margin_uplift_pts"], 0.0) / 100.0 * 0.35
    synergy_gain = (dynamic_rollout / 100.0) * max(marketing_change, 0.0) / 100.0 * 0.09
    inefficiency_penalty = (
        max(marketing_change - 12.0, 0.0) / 100.0
        * max(0.0, 0.10 - 0.08 * global_market_signal + baseline_risk_gap / 300.0)
    )
    demand_penalty = max(-demand_shock, 0.0) / 100.0 * max(marketing_change, 0.0) / 100.0 * 0.12
    margin_trend_penalty = abs(min(margin_trend_pct, 0.0)) / 100.0 * 0.08

    income_multiplier = 1.0
    income_multiplier += pricing_gain + marketing_gain + launch_gain + focus_gain + demand_gain
    income_multiplier += automation_gain + recommendation_gain + synergy_gain
    income_multiplier -= inefficiency_penalty + demand_penalty + margin_trend_penalty
    income_multiplier = clamp(income_multiplier, 0.72, 1.85)

    income = float(baseline_row["baseline_income"]) * income_multiplier

    marketing_expense = float(baseline_row["baseline_marketing"]) * clamp(
        1.0 + marketing_change / 100.0 + new_packs * 0.025, 0.60, 1.70
    )
    salary_expense = float(baseline_row["baseline_salary"]) * clamp(
        1.0 - salary_optimization / 100.0 + new_packs * 0.010 - support_automation / 400.0, 0.74, 1.22
    )
    infrastructure_expense = float(baseline_row["baseline_infrastructure"]) * clamp(
        1.0 + new_packs * 0.015 + max(demand_shock, 0.0) / 250.0 - cost_control / 250.0, 0.78, 1.30
    )
    tools_expense = float(baseline_row["baseline_tools"]) * clamp(
        1.0 + new_packs * 0.012 - cost_control / 180.0 - support_automation / 250.0, 0.70, 1.18
    )
    content_expense = float(baseline_row["baseline_content"]) * clamp(
        1.0 + new_packs * 0.050 + focus_top_market / 160.0 - cost_control / 220.0, 0.76, 1.52
    )
    support_expense = float(baseline_row["baseline_support"]) * clamp(
        1.0 + new_packs * 0.018 + max(demand_shock, 0.0) / 180.0 - support_automation / 100.0 - cost_control / 300.0,
        0.55,
        1.25,
    )

    category_expenses = {
        "SALARIES": salary_expense,
        "MARKETING": marketing_expense,
        "INFRASTRUCTURE": infrastructure_expense,
        "SOFTWARE": tools_expense,
        "OTHER_CONTENT": content_expense,
        "OTHER_SUPPORT": support_expense,
    }

    expense_multiplier_bonus = 1.0 + (dynamic_rollout / 100.0) * 0.005 + max(demand_shock, 0.0) / 100.0 * 0.02
    expenses = sum(category_expenses.values()) * expense_multiplier_bonus

    if include_noise and rng is not None:
        income_noise = rng.normal(0.0, 0.018 + baseline_risk_gap / 800.0)
        expense_noise = rng.normal(0.0, 0.012 + float(baseline_row["baseline_operational_pressure"]) / 25.0)
        income *= clamp(1.0 + income_noise, 0.88, 1.16)
        expenses *= clamp(1.0 + expense_noise, 0.90, 1.18)

    category_total = sum(category_expenses.values()) or 1.0
    scale_factor = expenses / category_total
    category_expenses = {key: value * scale_factor for key, value in category_expenses.items()}

    profit = income - expenses
    margin = (profit / income * 100.0) if income else 0.0

    risk_level = "LOW"
    if profit < 0 or margin < 18.0 or (demand_shock < -8.0 and marketing_change > 8.0):
        risk_level = "HIGH"
    elif margin < 28.0 or profit < float(baseline_row["baseline_profit"]) * 0.90 or baseline_risk_gap > 0.0:
        risk_level = "MEDIUM"

    return {
        "income": round(float(income), 2),
        "expenses": round(float(expenses), 2),
        "profit": round(float(profit), 2),
        "margin": round(float(margin), 2),
        "risk_level": risk_level,
        "category_expenses": {key: round(float(value), 2) for key, value in category_expenses.items()},
    }


def build_training_frame(
    forecast: pd.DataFrame,
    market_summary: dict[str, Any],
    pricing_summary: dict[str, Any],
    recommendations_summary: dict[str, Any],
) -> pd.DataFrame:
    rng = np.random.default_rng(RANDOM_SEED)
    records: list[dict[str, Any]] = []

    for row in forecast.to_dict(orient="records"):
        baseline_row = pd.Series(row)
        for _ in range(SCENARIOS_PER_MONTH):
            controls = {
                "marketing_budget_change_pct": float(rng.integers(-10, 26)),
                "dynamic_pricing_rollout_pct": float(rng.integers(0, 101)),
                "new_pack_launches": int(rng.integers(0, 5)),
                "cost_control_pct": float(rng.integers(0, 13)),
                "salary_optimization_pct": float(rng.integers(0, 9)),
                "market_demand_shock_pct": float(rng.integers(-12, 19)),
                "support_automation_pct": float(rng.integers(0, 11)),
                "focus_top_market_pct": float(rng.integers(0, 13)),
            }
            outcome = simulate_scenario(
                baseline_row,
                controls,
                market_summary=market_summary,
                pricing_summary=pricing_summary,
                recommendations_summary=recommendations_summary,
                rng=rng,
                include_noise=True,
            )
            records.append(
                {
                    "month": row["month"],
                    "month_index": row["month_index"],
                    "baseline_income": row["baseline_income"],
                    "baseline_expenses": row["baseline_expenses"],
                    "baseline_profit": row["baseline_profit"],
                    "baseline_margin": row["baseline_margin"],
                    "baseline_salary": row["baseline_salary"],
                    "baseline_marketing": row["baseline_marketing"],
                    "baseline_infrastructure": row["baseline_infrastructure"],
                    "baseline_tools": row["baseline_tools"],
                    "baseline_content": row["baseline_content"],
                    "baseline_support": row["baseline_support"],
                    "baseline_risk_gap": row["baseline_risk_gap"],
                    "baseline_operational_pressure": row["baseline_operational_pressure"],
                    "baseline_marketing_share": row["baseline_marketing_share"],
                    "global_market_signal": market_summary["global_market_signal"],
                    "top_opportunity_score": market_summary["top_opportunity_score"],
                    "weighted_market_roi": market_summary["weighted_market_roi"],
                    "weighted_salary_boost": market_summary["weighted_salary_boost"],
                    "pricing_revenue_uplift_pct": pricing_summary["revenue_uplift_pct"],
                    "recommendation_margin_uplift_pts": recommendations_summary["avg_margin_uplift_pts"],
                    "watch_growth_pct": recommendations_summary["watch_growth_pct"],
                    **controls,
                    "target_income": outcome["income"],
                    "target_expenses": outcome["expenses"],
                    "target_profit": outcome["profit"],
                    "target_margin": outcome["margin"],
                    "target_risk_level": outcome["risk_level"],
                }
            )

    return pd.DataFrame.from_records(records)


def train_models(training_frame: pd.DataFrame) -> tuple[dict[str, Any], dict[str, float], list[str]]:
    feature_columns = [
        "month_index",
        "baseline_income",
        "baseline_expenses",
        "baseline_profit",
        "baseline_margin",
        "baseline_salary",
        "baseline_marketing",
        "baseline_infrastructure",
        "baseline_tools",
        "baseline_content",
        "baseline_support",
        "baseline_risk_gap",
        "baseline_operational_pressure",
        "baseline_marketing_share",
        "global_market_signal",
        "top_opportunity_score",
        "weighted_market_roi",
        "weighted_salary_boost",
        "pricing_revenue_uplift_pct",
        "recommendation_margin_uplift_pts",
        "watch_growth_pct",
        "marketing_budget_change_pct",
        "dynamic_pricing_rollout_pct",
        "new_pack_launches",
        "cost_control_pct",
        "salary_optimization_pct",
        "market_demand_shock_pct",
        "support_automation_pct",
        "focus_top_market_pct",
    ]

    X = training_frame[feature_columns]
    y_income = training_frame["target_income"]
    y_expenses = training_frame["target_expenses"]
    y_risk = training_frame["target_risk_level"]

    X_train, X_test, y_income_train, y_income_test, y_exp_train, y_exp_test, y_risk_train, y_risk_test = train_test_split(
        X,
        y_income,
        y_expenses,
        y_risk,
        test_size=0.25,
        random_state=RANDOM_SEED,
        stratify=y_risk,
    )

    income_model = RandomForestRegressor(
        n_estimators=260,
        random_state=RANDOM_SEED,
        min_samples_leaf=2,
        n_jobs=1,
    )
    expense_model = RandomForestRegressor(
        n_estimators=260,
        random_state=RANDOM_SEED + 1,
        min_samples_leaf=2,
        n_jobs=1,
    )
    risk_model = RandomForestClassifier(
        n_estimators=280,
        random_state=RANDOM_SEED + 2,
        min_samples_leaf=2,
        n_jobs=1,
    )

    income_model.fit(X_train, y_income_train)
    expense_model.fit(X_train, y_exp_train)
    risk_model.fit(X_train, y_risk_train)

    income_predictions = income_model.predict(X_test)
    expense_predictions = expense_model.predict(X_test)
    risk_predictions = risk_model.predict(X_test)

    metrics = {
        "training_rows": float(len(X_train)),
        "test_rows": float(len(X_test)),
        "income_mae": float(mean_absolute_error(y_income_test, income_predictions)),
        "expense_mae": float(mean_absolute_error(y_exp_test, expense_predictions)),
        "income_r2": float(r2_score(y_income_test, income_predictions)),
        "expense_r2": float(r2_score(y_exp_test, expense_predictions)),
        "risk_accuracy": float(accuracy_score(y_risk_test, risk_predictions)),
    }

    joblib.dump(income_model, ARTIFACTS_DIR / "scenario_income_model.joblib")
    joblib.dump(expense_model, ARTIFACTS_DIR / "scenario_expense_model.joblib")
    joblib.dump(risk_model, ARTIFACTS_DIR / "scenario_risk_model.joblib")

    return {
        "income_model": income_model,
        "expense_model": expense_model,
        "risk_model": risk_model,
    }, metrics, feature_columns


def build_feature_row(
    baseline_row: pd.Series,
    controls: dict[str, float | int],
    market_summary: dict[str, Any],
    pricing_summary: dict[str, Any],
    recommendations_summary: dict[str, Any],
) -> dict[str, Any]:
    return {
        "month_index": baseline_row["month_index"],
        "baseline_income": baseline_row["baseline_income"],
        "baseline_expenses": baseline_row["baseline_expenses"],
        "baseline_profit": baseline_row["baseline_profit"],
        "baseline_margin": baseline_row["baseline_margin"],
        "baseline_salary": baseline_row["baseline_salary"],
        "baseline_marketing": baseline_row["baseline_marketing"],
        "baseline_infrastructure": baseline_row["baseline_infrastructure"],
        "baseline_tools": baseline_row["baseline_tools"],
        "baseline_content": baseline_row["baseline_content"],
        "baseline_support": baseline_row["baseline_support"],
        "baseline_risk_gap": baseline_row["baseline_risk_gap"],
        "baseline_operational_pressure": baseline_row["baseline_operational_pressure"],
        "baseline_marketing_share": baseline_row["baseline_marketing_share"],
        "global_market_signal": market_summary["global_market_signal"],
        "top_opportunity_score": market_summary["top_opportunity_score"],
        "weighted_market_roi": market_summary["weighted_market_roi"],
        "weighted_salary_boost": market_summary["weighted_salary_boost"],
        "pricing_revenue_uplift_pct": pricing_summary["revenue_uplift_pct"],
        "recommendation_margin_uplift_pts": recommendations_summary["avg_margin_uplift_pts"],
        "watch_growth_pct": recommendations_summary["watch_growth_pct"],
        **controls,
    }


def evaluate_scenarios(
    forecast: pd.DataFrame,
    models: dict[str, Any],
    feature_columns: list[str],
    market_summary: dict[str, Any],
    pricing_summary: dict[str, Any],
    recommendations_summary: dict[str, Any],
) -> tuple[pd.DataFrame, pd.DataFrame]:
    income_model = models["income_model"]
    expense_model = models["expense_model"]
    risk_model = models["risk_model"]

    scenario_rows: list[dict[str, Any]] = []
    monthly_rows: list[dict[str, Any]] = []

    for scenario in SCENARIO_LIBRARY:
        controls = scenario["controls"]
        for row in forecast.to_dict(orient="records"):
            baseline_row = pd.Series(row)
            if scenario["scenario_slug"] == "base-plan":
                predicted_income = float(row["baseline_income"])
                predicted_expenses = float(row["baseline_expenses"])
                predicted_profit = float(row["baseline_profit"])
                predicted_margin = float(row["baseline_margin"])
                predicted_risk = (
                    "HIGH"
                    if predicted_profit < 0 or predicted_margin < 18.0
                    else "MEDIUM"
                    if predicted_margin < 28.0 or float(row["baseline_risk_gap"]) > 0.0
                    else "LOW"
                )
                deterministic = {
                    "category_expenses": {
                        "SALARIES": float(row["baseline_salary"]),
                        "MARKETING": float(row["baseline_marketing"]),
                        "INFRASTRUCTURE": float(row["baseline_infrastructure"]),
                        "SOFTWARE": float(row["baseline_tools"]),
                        "OTHER_CONTENT": float(row["baseline_content"]),
                        "OTHER_SUPPORT": float(row["baseline_support"]),
                    }
                }
            else:
                feature_row = build_feature_row(
                    baseline_row,
                    controls=controls,
                    market_summary=market_summary,
                    pricing_summary=pricing_summary,
                    recommendations_summary=recommendations_summary,
                )
                X_eval = pd.DataFrame([feature_row], columns=feature_columns)
                predicted_income = float(income_model.predict(X_eval)[0])
                predicted_expenses = float(expense_model.predict(X_eval)[0])
                predicted_profit = predicted_income - predicted_expenses
                predicted_margin = (predicted_profit / predicted_income * 100.0) if predicted_income else 0.0
                predicted_risk = str(risk_model.predict(X_eval)[0])

                deterministic = simulate_scenario(
                    baseline_row,
                    controls=controls,
                    market_summary=market_summary,
                    pricing_summary=pricing_summary,
                    recommendations_summary=recommendations_summary,
                    include_noise=False,
                )

            monthly_rows.append(
                {
                    "scenario_name": scenario["scenario_name"],
                    "scenario_slug": scenario["scenario_slug"],
                    "description": scenario["description"],
                    "month": row["month"],
                    "baseline_income": row["baseline_income"],
                    "baseline_expenses": row["baseline_expenses"],
                    "baseline_profit": row["baseline_profit"],
                    "baseline_margin": row["baseline_margin"],
                    "projected_income": round(predicted_income, 2),
                    "projected_expenses": round(predicted_expenses, 2),
                    "projected_profit": round(predicted_profit, 2),
                    "projected_margin": round(predicted_margin, 2),
                    "risk_level": predicted_risk,
                    "salary_expense": deterministic["category_expenses"]["SALARIES"],
                    "marketing_expense": deterministic["category_expenses"]["MARKETING"],
                    "infrastructure_expense": deterministic["category_expenses"]["INFRASTRUCTURE"],
                    "software_expense": deterministic["category_expenses"]["SOFTWARE"],
                    "other_content_expense": deterministic["category_expenses"]["OTHER_CONTENT"],
                    "other_support_expense": deterministic["category_expenses"]["OTHER_SUPPORT"],
                    **controls,
                }
            )

    monthly_df = pd.DataFrame.from_records(monthly_rows)
    baseline_income_total = float(forecast["baseline_income"].sum())
    baseline_expenses_total = float(forecast["baseline_expenses"].sum())
    baseline_profit_total = float(forecast["baseline_profit"].sum())
    baseline_margin_avg = float(forecast["baseline_margin"].mean())

    for scenario in SCENARIO_LIBRARY:
        controls = scenario["controls"]
        scope = monthly_df[monthly_df["scenario_slug"] == scenario["scenario_slug"]].copy()
        projected_income_total = float(scope["projected_income"].sum())
        projected_expenses_total = float(scope["projected_expenses"].sum())
        projected_profit_total = float(scope["projected_profit"].sum())
        projected_margin_avg = float(scope["projected_margin"].mean())
        risk_counts = scope["risk_level"].value_counts().to_dict()
        dominant_risk = scope["risk_level"].mode().iat[0]
        scenario_rows.append(
            {
                "scenario_name": scenario["scenario_name"],
                "scenario_slug": scenario["scenario_slug"],
                "description": scenario["description"],
                "months_horizon": int(scope["month"].nunique()),
                "projected_income_total": round(projected_income_total, 2),
                "projected_expenses_total": round(projected_expenses_total, 2),
                "projected_profit_total": round(projected_profit_total, 2),
                "projected_margin_pct": round(projected_margin_avg, 2),
                "profit_uplift_pct": round(
                    ((projected_profit_total - baseline_profit_total) / baseline_profit_total * 100.0)
                    if baseline_profit_total
                    else 0.0,
                    2,
                ),
                "margin_uplift_pts": round(projected_margin_avg - baseline_margin_avg, 2),
                "income_uplift_pct": round(
                    ((projected_income_total - baseline_income_total) / baseline_income_total * 100.0)
                    if baseline_income_total
                    else 0.0,
                    2,
                ),
                "expense_delta_pct": round(
                    ((projected_expenses_total - baseline_expenses_total) / baseline_expenses_total * 100.0)
                    if baseline_expenses_total
                    else 0.0,
                    2,
                ),
                "risk_level": dominant_risk,
                "high_risk_months": int(risk_counts.get("HIGH", 0)),
                "medium_risk_months": int(risk_counts.get("MEDIUM", 0)),
                "low_risk_months": int(risk_counts.get("LOW", 0)),
                **controls,
            }
        )

    scenario_df = pd.DataFrame.from_records(scenario_rows)
    scenario_df = scenario_df.sort_values(
        ["projected_profit_total", "projected_margin_pct"], ascending=[False, False]
    ).reset_index(drop=True)
    scenario_df["scenario_rank"] = np.arange(1, len(scenario_df) + 1)

    return scenario_df, monthly_df


def build_summary_payload(
    forecast: pd.DataFrame,
    dashboard_data: dict[str, Any],
    scenario_df: pd.DataFrame,
    monthly_df: pd.DataFrame,
    market_summary: dict[str, Any],
    pricing_summary: dict[str, Any],
    recommendations_summary: dict[str, Any],
    inputs: dict[str, Any],
    model_metrics: dict[str, float],
) -> dict[str, Any]:
    baseline_summary = {
        "forecast_from": str(forecast["month"].min()),
        "forecast_to": str(forecast["month"].max()),
        "months_horizon": int(forecast["month"].nunique()),
        "baseline_income_total": round(float(forecast["baseline_income"].sum()), 2),
        "baseline_expenses_total": round(float(forecast["baseline_expenses"].sum()), 2),
        "baseline_profit_total": round(float(forecast["baseline_profit"].sum()), 2),
        "baseline_margin_avg": round(float(forecast["baseline_margin"].mean()), 2),
        "baseline_high_risk_months": int((forecast["baseline_margin"] < TARGET_MARGIN_PCT).sum()),
    }

    recommended_scope = scenario_df[
        (scenario_df["risk_level"].isin(["LOW", "MEDIUM"]))
        & (scenario_df["scenario_slug"] != "base-plan")
    ]
    recommended_scenario = (
        recommended_scope.sort_values(["projected_profit_total", "projected_margin_pct"], ascending=[False, False]).head(1)
        if not recommended_scope.empty
        else scenario_df.head(1)
    )
    recommended_row = recommended_scenario.iloc[0].to_dict()
    recommended_months = monthly_df[monthly_df["scenario_slug"] == recommended_row["scenario_slug"]].to_dict(orient="records")

    return {
        "generated_at": datetime.now().isoformat(),
        "inputs": inputs,
        "assumption": (
            "This is a hybrid finance simulator: scraper demand, the existing forecast outputs, "
            "the financial recommendation model, and the dynamic pricing uplift are blended into "
            "synthetic scenarios. It estimates directional impact, not audited accounting truth."
        ),
        "sql_seed_assumptions": {
            "income_payment_method_mapping": PAYMENT_METHOD_SQL_MAP,
            "expense_category_mapping": EXPENSE_CATEGORY_SQL_MAP,
            "reference_id_rule": "Non-numeric reference IDs are converted to their digit-only form; blank values stay NULL.",
        },
        "baseline_summary": baseline_summary,
        "market_summary": market_summary,
        "pricing_summary": {
            "portfolio_revenue_uplift_pct": pricing_summary["revenue_uplift_pct"],
            "packs_to_increase_price": pricing_summary["packs_to_increase_price"],
            "packs_to_decrease_price": pricing_summary["packs_to_decrease_price"],
            "packs_to_hold_price": pricing_summary["packs_to_hold_price"],
            "top_pricing_recommendations": pricing_summary["top_pricing_recommendations"],
        },
        "recommendation_context": {
            "avg_margin_uplift_pts": recommendations_summary["avg_margin_uplift_pts"],
            "avg_profit_uplift": recommendations_summary["avg_profit_uplift"],
            "watch_category": recommendations_summary["watch_category"],
            "watch_growth_pct": recommendations_summary["watch_growth_pct"],
            "months_at_risk_before": recommendations_summary["months_at_risk_before"],
            "months_at_risk_after": recommendations_summary["months_at_risk_after"],
            "best_gain_window": recommendations_summary["best_gain_window"],
            "user_playbook": recommendations_summary["user_playbook"],
        },
        "model": {
            "algorithm": "Hybrid rule engine + RandomForest regressors/classifier",
            "random_seed": RANDOM_SEED,
            "scenarios_per_month": SCENARIOS_PER_MONTH,
            "training_rows": int(model_metrics["training_rows"]),
            "test_rows": int(model_metrics["test_rows"]),
            "income_mae": round(model_metrics["income_mae"], 2),
            "expense_mae": round(model_metrics["expense_mae"], 2),
            "income_r2": round(model_metrics["income_r2"], 4),
            "expense_r2": round(model_metrics["expense_r2"], 4),
            "risk_accuracy": round(model_metrics["risk_accuracy"], 4),
        },
        "scenario_controls": [
            {"name": "marketing_budget_change_pct", "label": "Marketing budget change", "min": -10, "max": 25, "unit": "%"},
            {"name": "dynamic_pricing_rollout_pct", "label": "Dynamic pricing rollout", "min": 0, "max": 100, "unit": "%"},
            {"name": "new_pack_launches", "label": "New pack launches", "min": 0, "max": 4, "unit": "packs"},
            {"name": "cost_control_pct", "label": "Cost control", "min": 0, "max": 12, "unit": "%"},
            {"name": "salary_optimization_pct", "label": "Salary optimization", "min": 0, "max": 8, "unit": "%"},
            {"name": "market_demand_shock_pct", "label": "Market demand shock", "min": -12, "max": 18, "unit": "%"},
            {"name": "support_automation_pct", "label": "Support automation", "min": 0, "max": 10, "unit": "%"},
            {"name": "focus_top_market_pct", "label": "Focus on top scraper market", "min": 0, "max": 12, "unit": "%"},
        ],
        "scenario_rankings": scenario_df.to_dict(orient="records"),
        "recommended_scenario": recommended_row,
        "recommended_monthly_path": recommended_months,
        "dashboard_snapshot": dashboard_data.get("next_month_forecast", {}),
    }


def write_scenario_schema_sql(path: Path) -> None:
    sql = """CREATE TABLE `finance_scenario_simulations` (
  `id` bigint(20) NOT NULL,
  `scenario_name` varchar(255) NOT NULL,
  `scenario_slug` varchar(255) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `months_horizon` int(11) NOT NULL,
  `projected_income_total` double NOT NULL,
  `projected_expenses_total` double NOT NULL,
  `projected_profit_total` double NOT NULL,
  `projected_margin_pct` double NOT NULL,
  `profit_uplift_pct` double NOT NULL,
  `margin_uplift_pts` double NOT NULL,
  `income_uplift_pct` double NOT NULL,
  `expense_delta_pct` double NOT NULL,
  `risk_level` varchar(32) NOT NULL,
  `high_risk_months` int(11) NOT NULL,
  `medium_risk_months` int(11) NOT NULL,
  `low_risk_months` int(11) NOT NULL,
  `marketing_budget_change_pct` double NOT NULL,
  `dynamic_pricing_rollout_pct` double NOT NULL,
  `new_pack_launches` int(11) NOT NULL,
  `cost_control_pct` double NOT NULL,
  `salary_optimization_pct` double NOT NULL,
  `market_demand_shock_pct` double NOT NULL,
  `support_automation_pct` double NOT NULL,
  `focus_top_market_pct` double NOT NULL,
  `description` text DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
"""
    path.write_text(sql, encoding="utf-8")


def write_income_seed_sql(path: Path) -> int:
    income_path = FORECAST_DIR / "income.csv"
    income = pd.read_csv(income_path)
    rows: list[str] = []

    for row in income.to_dict(orient="records"):
        payment_method = PAYMENT_METHOD_SQL_MAP.get(str(row.get("payment_method", "")).upper(), str(row.get("payment_method", "")).upper())
        reference_id = extract_reference_id(row.get("reference_id"))
        rows.append(
            "("
            + ", ".join(
                [
                    sql_quote(int(row["id"])),
                    sql_quote(float(row["amount"])),
                    sql_datetime(row.get("created_at")),
                    sql_quote(row.get("currency")),
                    sql_quote(payment_method),
                    sql_datetime(row.get("received_date")),
                    sql_quote(reference_id) if reference_id is not None else "NULL",
                    sql_quote(row.get("source_type")),
                    sql_quote(row.get("description")),
                ]
            )
            + ")"
        )

    statement = (
        "INSERT INTO `income` (`id`, `amount`, `created_at`, `currency`, `payment_method`, `received_date`, `reference_id`, `source_type`, `description`) VALUES\n"
        + ",\n".join(rows)
        + ";\n"
    )
    path.write_text(statement, encoding="utf-8")
    return len(rows)


def write_expense_seed_sql(path: Path) -> int:
    expenses_path = FORECAST_DIR / "expenses.csv"
    expenses = pd.read_csv(expenses_path)
    rows: list[str] = []

    for row in expenses.to_dict(orient="records"):
        raw_category = str(row.get("category", "")).upper()
        category = EXPENSE_CATEGORY_SQL_MAP.get(raw_category, raw_category)
        rows.append(
            "("
            + ", ".join(
                [
                    sql_quote(int(row["id"])),
                    sql_quote(float(row["amount"])),
                    sql_quote(category),
                    sql_datetime(row.get("created_at")),
                    sql_quote(row.get("currency")),
                    sql_quote(row.get("description")),
                    sql_datetime(row.get("expense_date")),
                    sql_quote(str(row.get("status", "")).upper()),
                    sql_quote(row.get("title")),
                ]
            )
            + ")"
        )

    statement = (
        "INSERT INTO `expenses` (`id`, `amount`, `category`, `created_at`, `currency`, `description`, `expense_date`, `status`, `title`) VALUES\n"
        + ",\n".join(rows)
        + ";\n"
    )
    path.write_text(statement, encoding="utf-8")
    return len(rows)


def write_scenario_seed_sql(path: Path, scenario_df: pd.DataFrame) -> int:
    created_at = datetime.now().strftime("%Y-%m-%d %H:%M:%S.%f")
    rows: list[str] = []

    for index, row in enumerate(scenario_df.to_dict(orient="records"), start=1):
        rows.append(
            "("
            + ", ".join(
                [
                    sql_quote(index),
                    sql_quote(row["scenario_name"]),
                    sql_quote(row["scenario_slug"]),
                    sql_quote(created_at),
                    sql_quote(int(row["months_horizon"])),
                    sql_quote(float(row["projected_income_total"])),
                    sql_quote(float(row["projected_expenses_total"])),
                    sql_quote(float(row["projected_profit_total"])),
                    sql_quote(float(row["projected_margin_pct"])),
                    sql_quote(float(row["profit_uplift_pct"])),
                    sql_quote(float(row["margin_uplift_pts"])),
                    sql_quote(float(row["income_uplift_pct"])),
                    sql_quote(float(row["expense_delta_pct"])),
                    sql_quote(row["risk_level"]),
                    sql_quote(int(row["high_risk_months"])),
                    sql_quote(int(row["medium_risk_months"])),
                    sql_quote(int(row["low_risk_months"])),
                    sql_quote(float(row["marketing_budget_change_pct"])),
                    sql_quote(float(row["dynamic_pricing_rollout_pct"])),
                    sql_quote(int(row["new_pack_launches"])),
                    sql_quote(float(row["cost_control_pct"])),
                    sql_quote(float(row["salary_optimization_pct"])),
                    sql_quote(float(row["market_demand_shock_pct"])),
                    sql_quote(float(row["support_automation_pct"])),
                    sql_quote(float(row["focus_top_market_pct"])),
                    sql_quote(row["description"]),
                ]
            )
            + ")"
        )

    statement = (
        "INSERT INTO `finance_scenario_simulations` (`id`, `scenario_name`, `scenario_slug`, `created_at`, `months_horizon`, `projected_income_total`, `projected_expenses_total`, `projected_profit_total`, `projected_margin_pct`, `profit_uplift_pct`, `margin_uplift_pts`, `income_uplift_pct`, `expense_delta_pct`, `risk_level`, `high_risk_months`, `medium_risk_months`, `low_risk_months`, `marketing_budget_change_pct`, `dynamic_pricing_rollout_pct`, `new_pack_launches`, `cost_control_pct`, `salary_optimization_pct`, `market_demand_shock_pct`, `support_automation_pct`, `focus_top_market_pct`, `description`) VALUES\n"
        + ",\n".join(rows)
        + ";\n"
    )
    path.write_text(statement, encoding="utf-8")
    return len(rows)


def main() -> None:
    ensure_dirs()

    forecast, dashboard_data, forecast_inputs = load_forecast_baseline()
    recommendations_summary, recommendation_inputs = load_recommendations_summary()
    pricing_summary, pricing_inputs = load_dynamic_pricing_summary()
    _, market_summary, market_inputs = load_market_signals()

    training_frame = build_training_frame(
        forecast,
        market_summary=market_summary,
        pricing_summary=pricing_summary,
        recommendations_summary=recommendations_summary,
    )
    models, model_metrics, feature_columns = train_models(training_frame)
    scenario_df, monthly_df = evaluate_scenarios(
        forecast,
        models=models,
        feature_columns=feature_columns,
        market_summary=market_summary,
        pricing_summary=pricing_summary,
        recommendations_summary=recommendations_summary,
    )

    summary_payload = build_summary_payload(
        forecast=forecast,
        dashboard_data=dashboard_data,
        scenario_df=scenario_df,
        monthly_df=monthly_df,
        market_summary=market_summary,
        pricing_summary=pricing_summary,
        recommendations_summary=recommendations_summary,
        inputs={**forecast_inputs, **recommendation_inputs, **pricing_inputs, **market_inputs},
        model_metrics=model_metrics,
    )

    training_frame.to_csv(OUTPUTS_DIR / "scenario_training_data.csv", index=False)
    scenario_df.to_csv(OUTPUTS_DIR / "scenario_catalog.csv", index=False)
    monthly_df.to_csv(OUTPUTS_DIR / "scenario_monthly_projection.csv", index=False)
    (OUTPUTS_DIR / "scenario_simulator_summary.json").write_text(
        json.dumps(summary_payload, indent=2),
        encoding="utf-8",
    )

    write_scenario_schema_sql(OUTPUTS_DIR / "schema_finance_scenario_simulator.sql")
    income_row_count = write_income_seed_sql(OUTPUTS_DIR / "seed_income_history.sql")
    expense_row_count = write_expense_seed_sql(OUTPUTS_DIR / "seed_expenses_history.sql")
    scenario_row_count = write_scenario_seed_sql(OUTPUTS_DIR / "seed_finance_scenarios.sql", scenario_df)

    run_summary = {
        "generated_at": datetime.now().isoformat(),
        "training_rows": int(len(training_frame)),
        "income_seed_rows": income_row_count,
        "expense_seed_rows": expense_row_count,
        "scenario_seed_rows": scenario_row_count,
        "recommended_scenario": summary_payload["recommended_scenario"]["scenario_name"],
        "recommended_profit_total": summary_payload["recommended_scenario"]["projected_profit_total"],
        "recommended_margin_pct": summary_payload["recommended_scenario"]["projected_margin_pct"],
        "model_metrics": summary_payload["model"],
    }
    (OUTPUTS_DIR / "run_summary.json").write_text(json.dumps(run_summary, indent=2), encoding="utf-8")

    print(json.dumps(run_summary, indent=2))


if __name__ == "__main__":
    main()
