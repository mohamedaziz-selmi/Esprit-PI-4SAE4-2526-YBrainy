from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import joblib
import matplotlib
import numpy as np
import pandas as pd
from sklearn.ensemble import RandomForestClassifier
from sklearn.metrics import accuracy_score, classification_report
from sklearn.model_selection import train_test_split

matplotlib.use("Agg")
from matplotlib import pyplot as plt


EXPENSE_CATEGORIES = [
    "SALARY",
    "MARKETING",
    "INFRASTRUCTURE",
    "TOOLS",
    "CONTENT",
    "SUPPORT",
]
TARGET_MARGIN_PCT = 25.0
MODEL_FILE_NAME = "financial_recommendation_model.joblib"


ACTION_LIBRARY: dict[str, dict[str, Any]] = {
    "DEFEND_MARGIN": {
        "title": "Defend margin with light discretionary controls",
        "focus_area": "Margin Defense",
        "income_multiplier": 0.995,
        "expense_multipliers": {
            "MARKETING": 0.96,
            "CONTENT": 0.98,
            "TOOLS": 0.95,
            "INFRASTRUCTURE": 0.97,
        },
        "target_metric": "profit_margin",
    },
    "OPTIMIZE_SALARY": {
        "title": "Optimize salary and contractor load",
        "focus_area": "Cost Control",
        "income_multiplier": 0.985,
        "expense_multipliers": {
            "SALARY": 0.90,
            "SUPPORT": 0.98,
        },
        "target_metric": "salary_ratio",
    },
    "BOOST_MARKETING": {
        "title": "Scale marketing on the strongest channels",
        "focus_area": "Revenue Growth",
        "income_multiplier": 1.085,
        "expense_multipliers": {
            "MARKETING": 1.12,
            "CONTENT": 1.04,
            "SUPPORT": 1.02,
        },
        "target_metric": "income_growth",
    },
    "INVEST_CONTENT": {
        "title": "Invest in bundle and content refresh",
        "focus_area": "Revenue Growth",
        "income_multiplier": 1.070,
        "expense_multipliers": {
            "CONTENT": 1.10,
            "MARKETING": 1.04,
            "SUPPORT": 1.01,
        },
        "target_metric": "bundle_conversion",
    },
    "LOCK_INFRA_TOOLS": {
        "title": "Lock infrastructure and tool costs",
        "focus_area": "Efficiency",
        "income_multiplier": 0.997,
        "expense_multipliers": {
            "INFRASTRUCTURE": 0.90,
            "TOOLS": 0.88,
        },
        "target_metric": "operating_expense",
    },
    "STABILIZE_SUPPORT": {
        "title": "Stabilize support operations with automation",
        "focus_area": "Service Efficiency",
        "income_multiplier": 1.002,
        "expense_multipliers": {
            "SUPPORT": 0.86,
            "TOOLS": 0.97,
        },
        "target_metric": "support_ratio",
    },
}

ACTION_PLAYBOOK: dict[str, dict[str, str]] = {
    "DEFEND_MARGIN": {
        "how": "Cap low-ROI discretionary spend, review tool usage weekly, and protect the most profitable offers first.",
        "success_signal": "Margin stabilizes above target without a major revenue drop.",
    },
    "OPTIMIZE_SALARY": {
        "how": "Pause non-critical hiring, rebalance contractor load, and align payroll with the months showing the weakest margin.",
        "success_signal": "Salary ratio drops while delivery quality stays stable.",
    },
    "BOOST_MARKETING": {
        "how": "Shift budget into the channels already converting, push proven bundle offers, and track weekly CAC against profit lift.",
        "success_signal": "Revenue grows faster than added marketing spend.",
    },
    "INVEST_CONTENT": {
        "how": "Refresh the highest-selling courses and bundles, launch new content where demand is already proven, and support the release with a small campaign.",
        "success_signal": "Bundle and course sales rise within the next cycle.",
    },
    "LOCK_INFRA_TOOLS": {
        "how": "Renegotiate hosting commitments, clean up unused subscriptions, and right-size environments before lower-margin months.",
        "success_signal": "Infrastructure and tool spend flatten or decline month over month.",
    },
    "STABILIZE_SUPPORT": {
        "how": "Automate repetitive support flows, consolidate support tools, and move common issues into self-serve content.",
        "success_signal": "Support cost growth slows while response quality remains acceptable.",
    },
}


FEATURE_COLUMNS = [
    "income",
    "expenses",
    "profit",
    "margin",
    "income_growth",
    "expense_growth",
    "profit_growth",
    "margin_buffer",
    "income_uncertainty_pct",
    "expense_uncertainty_pct",
    "profit_downside_gap",
    "risk_gap",
    "fixed_cost_share",
    "discretionary_share",
    "salary_share",
    "marketing_share",
    "infrastructure_share",
    "tools_share",
    "content_share",
    "support_share",
    "salary_growth",
    "marketing_growth",
    "infrastructure_growth",
    "tools_growth",
    "content_growth",
    "support_growth",
]


@dataclass(frozen=True)
class PipelineArtifacts:
    summary_path: Path
    monthly_recommendations_path: Path
    improvement_actions_path: Path
    executive_metrics_path: Path
    playbook_path: Path
    model_metrics_path: Path
    feature_importance_path: Path
    report_path: Path
    model_path: Path


def run_pipeline(
    forecasting_dir: Path | str,
    output_dir: Path | str,
    artifact_dir: Path | str,
    top_n: int = 10,
    random_state: int = 42,
) -> dict[str, Any]:
    forecasting_dir = Path(forecasting_dir)
    output_dir = Path(output_dir)
    artifact_dir = Path(artifact_dir)

    output_dir.mkdir(parents=True, exist_ok=True)
    artifact_dir.mkdir(parents=True, exist_ok=True)

    income_df = pd.read_csv(forecasting_dir / "income.csv")
    expenses_df = pd.read_csv(forecasting_dir / "expenses.csv")
    dashboard = json.loads((forecasting_dir / "outputs" / "dashboard_data.json").read_text(encoding="utf-8"))
    forecast_scorecard = pd.read_csv(forecasting_dir / "outputs" / "forecast_executive_scorecard.csv")

    actual_monthly = build_actual_monthly_history(income_df, expenses_df)
    forecast_monthly = build_forecast_monthly_history(forecasting_dir / "outputs" / "forecast_monthly_6m.csv")
    base_states = build_base_states(actual_monthly, forecast_monthly)
    baselines = compute_baselines(actual_monthly, forecast_monthly)

    scenario_states = generate_scenario_training_rows(base_states, baselines, random_state=random_state)
    scenario_features = engineer_features(scenario_states)
    training_frame = label_scenarios(scenario_features, baselines)

    model, metrics_df, feature_importance_df = train_recommendation_model(
        training_frame,
        random_state=random_state,
    )

    forecast_state_frame = base_states.loc[base_states["origin"] == "forecast"].copy().reset_index(drop=True)
    forecast_recommendations = score_forecast_horizon(
        model,
        engineer_features(forecast_state_frame),
        baselines,
    )

    improvement_actions = build_improvement_actions(forecast_recommendations)
    executive_metrics = build_executive_metrics(forecast_recommendations, metrics_df)
    user_playbook = build_user_playbook(forecast_recommendations)
    chart_assets = render_chart_assets(forecast_recommendations, output_dir)
    summary = build_summary_payload(
        dashboard=dashboard,
        forecast_scorecard=forecast_scorecard,
        income_df=income_df,
        actual_monthly=actual_monthly,
        forecast_recommendations=forecast_recommendations,
        improvement_actions=improvement_actions,
        executive_metrics=executive_metrics,
        user_playbook=user_playbook,
        chart_assets=chart_assets,
        metrics_df=metrics_df,
        feature_importance_df=feature_importance_df,
        baselines=baselines,
        top_n=top_n,
    )

    artifacts = export_outputs(
        output_dir=output_dir,
        artifact_dir=artifact_dir,
        summary=summary,
        forecast_recommendations=forecast_recommendations,
        improvement_actions=improvement_actions,
        executive_metrics=executive_metrics,
        user_playbook=user_playbook,
        metrics_df=metrics_df,
        feature_importance_df=feature_importance_df,
        model=model,
    )

    summary["artifacts"] = {
        "summary_path": str(artifacts.summary_path),
        "monthly_recommendations_path": str(artifacts.monthly_recommendations_path),
        "improvement_actions_path": str(artifacts.improvement_actions_path),
        "executive_metrics_path": str(artifacts.executive_metrics_path),
        "playbook_path": str(artifacts.playbook_path),
        "model_metrics_path": str(artifacts.model_metrics_path),
        "feature_importance_path": str(artifacts.feature_importance_path),
        "report_path": str(artifacts.report_path),
        "model_path": str(artifacts.model_path),
    }
    return summary


def build_actual_monthly_history(income_df: pd.DataFrame, expenses_df: pd.DataFrame) -> pd.DataFrame:
    income = income_df.copy()
    expenses = expenses_df.copy()

    income["received_date"] = pd.to_datetime(income["received_date"], errors="coerce")
    expenses["expense_date"] = pd.to_datetime(expenses["expense_date"], errors="coerce")

    income["month"] = income["received_date"].dt.to_period("M").astype(str)
    expenses["month"] = expenses["expense_date"].dt.to_period("M").astype(str)

    income_monthly = income.groupby("month", as_index=False)["amount"].sum().rename(columns={"amount": "income"})
    expense_by_category = (
        expenses.groupby(["month", "category"], as_index=False)["amount"]
        .sum()
        .pivot(index="month", columns="category", values="amount")
        .reindex(columns=EXPENSE_CATEGORIES, fill_value=0.0)
        .fillna(0.0)
        .reset_index()
    )

    monthly = income_monthly.merge(expense_by_category, on="month", how="outer").fillna(0.0)
    monthly["origin"] = "actual"
    monthly["expenses"] = monthly[EXPENSE_CATEGORIES].sum(axis=1)
    monthly["profit"] = monthly["income"] - monthly["expenses"]
    monthly["margin"] = np.where(monthly["income"] > 0, monthly["profit"] / monthly["income"] * 100.0, 0.0)

    income_std = max(float(monthly["income"].std(ddof=0) or 0.0), 1500.0)
    expense_std = max(float(monthly["expenses"].std(ddof=0) or 0.0), 1200.0)

    monthly["income_low"] = np.maximum(monthly["income"] - income_std, 0.0)
    monthly["income_high"] = monthly["income"] + income_std
    monthly["expenses_low"] = np.maximum(monthly["expenses"] - expense_std, 0.0)
    monthly["expenses_high"] = monthly["expenses"] + expense_std
    monthly["profit_low"] = monthly["income_low"] - monthly["expenses_high"]
    monthly["profit_high"] = monthly["income_high"] - monthly["expenses_low"]
    monthly["margin_low"] = np.where(monthly["income_low"] > 0, monthly["profit_low"] / monthly["income_low"] * 100.0, 0.0)
    monthly["margin_high"] = np.where(monthly["income_high"] > 0, monthly["profit_high"] / monthly["income_high"] * 100.0, 0.0)

    return monthly.sort_values("month").reset_index(drop=True)


def build_forecast_monthly_history(path: Path) -> pd.DataFrame:
    forecast = pd.read_csv(path)
    rename_map = {
        "predicted_income": "income",
        "predicted_income_low": "income_low",
        "predicted_income_high": "income_high",
        "predicted_expenses": "expenses",
        "predicted_expenses_low": "expenses_low",
        "predicted_expenses_high": "expenses_high",
        "predicted_profit": "profit",
        "predicted_profit_low": "profit_low",
        "predicted_profit_high": "profit_high",
        "predicted_margin": "margin",
        "predicted_margin_low": "margin_low",
        "predicted_margin_high": "margin_high",
    }
    for category in EXPENSE_CATEGORIES:
        rename_map[f"predicted_{category.lower()}"] = category

    forecast = forecast.rename(columns=rename_map)
    forecast["origin"] = "forecast"
    for category in EXPENSE_CATEGORIES:
        if category not in forecast.columns:
            forecast[category] = 0.0
    return forecast.sort_values("month").reset_index(drop=True)


def build_base_states(actual_monthly: pd.DataFrame, forecast_monthly: pd.DataFrame) -> pd.DataFrame:
    columns = [
        "month",
        "origin",
        "income",
        "income_low",
        "income_high",
        "expenses",
        "expenses_low",
        "expenses_high",
        "profit",
        "profit_low",
        "profit_high",
        "margin",
        "margin_low",
        "margin_high",
        *EXPENSE_CATEGORIES,
    ]
    combined = pd.concat(
        [actual_monthly[columns], forecast_monthly[columns]],
        ignore_index=True,
        axis=0,
    )
    combined = combined.sort_values("month").reset_index(drop=True)

    previous = combined.shift(1)
    combined["prev_income"] = previous["income"].fillna(combined["income"])
    combined["prev_expenses"] = previous["expenses"].fillna(combined["expenses"])
    combined["prev_profit"] = previous["profit"].fillna(combined["profit"])
    for category in EXPENSE_CATEGORIES:
        combined[f"prev_{category}"] = previous[category].fillna(combined[category])
    return combined


def compute_baselines(actual_monthly: pd.DataFrame, forecast_monthly: pd.DataFrame) -> dict[str, Any]:
    actual_shares = {}
    for category in EXPENSE_CATEGORIES:
        actual_shares[category] = float(
            (actual_monthly[category] / actual_monthly["expenses"].replace(0, np.nan)).fillna(0.0).median()
        )

    category_growth_volatility = {}
    for category in EXPENSE_CATEGORIES:
        growth = actual_monthly[category].pct_change().replace([np.inf, -np.inf], np.nan).fillna(0.0)
        category_growth_volatility[category] = float(max(growth.std(ddof=0), 0.05))

    last_actual = actual_monthly.iloc[-1]
    forecast_first = forecast_monthly.iloc[0]
    pressure = {
        category: safe_pct_change(forecast_first[category], last_actual[category]) for category in EXPENSE_CATEGORIES
    }
    watch_category = max(pressure, key=pressure.get)

    return {
        "avg_income": float(actual_monthly["income"].mean()),
        "avg_expenses": float(actual_monthly["expenses"].mean()),
        "share_medians": actual_shares,
        "category_growth_volatility": category_growth_volatility,
        "watch_category": watch_category,
        "watch_growth_pct": round(float(pressure[watch_category]) * 100.0, 2),
        "target_margin_pct": TARGET_MARGIN_PCT,
    }


def generate_scenario_training_rows(
    base_states: pd.DataFrame,
    baselines: dict[str, Any],
    random_state: int,
    scenarios_per_state: int = 220,
) -> pd.DataFrame:
    rng = np.random.default_rng(random_state)
    rows: list[dict[str, Any]] = []

    for _, row in base_states.iterrows():
        income_uncertainty = uncertainty_ratio(row["income"], row["income_low"], row["income_high"], fallback=0.06)
        expense_uncertainty = uncertainty_ratio(row["expenses"], row["expenses_low"], row["expenses_high"], fallback=0.07)

        for _ in range(scenarios_per_state):
            sampled_income = float(
                max(
                    row["income"] * (1.0 + rng.normal(0.0, max(income_uncertainty / 2.0, 0.02))),
                    row["income"] * 0.55,
                )
            )
            sampled_categories: dict[str, float] = {}
            for category in EXPENSE_CATEGORIES:
                base_value = float(row[category])
                category_noise = baselines["category_growth_volatility"][category]
                sampled_categories[category] = float(
                    max(base_value * (1.0 + rng.normal(0.0, max(category_noise / 2.5, 0.03))), 0.0)
                )

            sampled_expenses = float(sum(sampled_categories.values()))
            sampled_profit = float(sampled_income - sampled_expenses)
            sampled_margin = float(sampled_profit / sampled_income * 100.0) if sampled_income > 0 else 0.0

            sampled_row = {
                "month": row["month"],
                "origin": row["origin"],
                "income": sampled_income,
                "income_low": max(sampled_income * (1.0 - income_uncertainty), 0.0),
                "income_high": sampled_income * (1.0 + income_uncertainty),
                "expenses": sampled_expenses,
                "expenses_low": max(sampled_expenses * (1.0 - expense_uncertainty), 0.0),
                "expenses_high": sampled_expenses * (1.0 + expense_uncertainty),
                "profit": sampled_profit,
                "profit_low": max(sampled_income * (1.0 - income_uncertainty), 0.0)
                - sampled_expenses * (1.0 + expense_uncertainty),
                "profit_high": sampled_income * (1.0 + income_uncertainty)
                - max(sampled_expenses * (1.0 - expense_uncertainty), 0.0),
                "margin": sampled_margin,
                "margin_low": 0.0,
                "margin_high": 0.0,
                "prev_income": row["prev_income"],
                "prev_expenses": row["prev_expenses"],
                "prev_profit": row["prev_profit"],
            }
            for category in EXPENSE_CATEGORIES:
                sampled_row[category] = sampled_categories[category]
                sampled_row[f"prev_{category}"] = row[f"prev_{category}"]

            sampled_row["margin_low"] = (
                sampled_row["profit_low"] / sampled_row["income_low"] * 100.0 if sampled_row["income_low"] > 0 else 0.0
            )
            sampled_row["margin_high"] = (
                sampled_row["profit_high"] / sampled_row["income_high"] * 100.0 if sampled_row["income_high"] > 0 else 0.0
            )
            rows.append(sampled_row)

    return pd.DataFrame(rows)


def engineer_features(df: pd.DataFrame) -> pd.DataFrame:
    features = df.copy()

    features["income_growth"] = pct_change_series(features["income"], features["prev_income"])
    features["expense_growth"] = pct_change_series(features["expenses"], features["prev_expenses"])
    features["profit_growth"] = pct_change_series(features["profit"], features["prev_profit"])
    features["margin_buffer"] = features["margin"] - TARGET_MARGIN_PCT
    features["income_uncertainty_pct"] = [
        uncertainty_ratio(base, low, high, fallback=0.05)
        for base, low, high in zip(features["income"], features["income_low"], features["income_high"])
    ]
    features["expense_uncertainty_pct"] = [
        uncertainty_ratio(base, low, high, fallback=0.06)
        for base, low, high in zip(features["expenses"], features["expenses_low"], features["expenses_high"])
    ]
    features["profit_downside_gap"] = features["profit"] - features["profit_low"]
    features["risk_gap"] = np.maximum(TARGET_MARGIN_PCT - features["margin"], 0.0) + np.maximum(-features["profit_low"], 0.0) / 1000.0

    for category in EXPENSE_CATEGORIES:
        lower = category.lower()
        features[f"{lower}_share"] = np.where(
            features["expenses"] > 0,
            features[category] / features["expenses"],
            0.0,
        )
        features[f"{lower}_growth"] = pct_change_series(features[category], features[f"prev_{category}"])

    features["fixed_cost_share"] = features["salary_share"] + features["infrastructure_share"] + features["support_share"]
    features["discretionary_share"] = features["marketing_share"] + features["content_share"] + features["tools_share"]
    return features.fillna(0.0)


def label_scenarios(feature_frame: pd.DataFrame, baselines: dict[str, Any]) -> pd.DataFrame:
    labeled = feature_frame.copy()
    best_labels: list[str] = []
    best_scores: list[float] = []

    for _, row in labeled.iterrows():
        best_action = None
        best_score = -1e18
        for action_name, action_spec in ACTION_LIBRARY.items():
            action_result = simulate_action(row, action_name, action_spec, baselines)
            if action_result["score"] > best_score:
                best_action = action_name
                best_score = action_result["score"]
        best_labels.append(str(best_action))
        best_scores.append(float(best_score))

    labeled["recommended_action"] = best_labels
    labeled["training_score"] = best_scores
    return labeled


def train_recommendation_model(
    training_frame: pd.DataFrame,
    random_state: int,
) -> tuple[RandomForestClassifier, pd.DataFrame, pd.DataFrame]:
    X = training_frame[FEATURE_COLUMNS]
    y = training_frame["recommended_action"]

    label_counts = y.value_counts()
    if label_counts.min() >= 2 and len(label_counts) > 1:
        X_train, X_test, y_train, y_test = train_test_split(
            X,
            y,
            test_size=0.20,
            random_state=random_state,
            stratify=y,
        )
    else:
        X_train, X_test, y_train, y_test = train_test_split(
            X,
            y,
            test_size=0.20,
            random_state=random_state,
        )

    model = RandomForestClassifier(
        n_estimators=320,
        max_depth=10,
        min_samples_leaf=8,
        class_weight="balanced_subsample",
        random_state=random_state,
    )
    model.fit(X_train, y_train)

    predictions = model.predict(X_test)
    probabilities = model.predict_proba(X_test)
    confidence = probabilities.max(axis=1)
    report = classification_report(y_test, predictions, output_dict=True, zero_division=0)

    metric_rows = [
        {"metric": "training_rows", "value": float(len(training_frame))},
        {"metric": "train_rows", "value": float(len(X_train))},
        {"metric": "test_rows", "value": float(len(X_test))},
        {"metric": "holdout_accuracy", "value": float(accuracy_score(y_test, predictions))},
        {"metric": "avg_prediction_confidence", "value": float(np.mean(confidence))},
    ]
    for label, values in report.items():
        if not isinstance(values, dict):
            continue
        metric_rows.append({"metric": f"{label}_precision", "value": float(values.get("precision", 0.0))})
        metric_rows.append({"metric": f"{label}_recall", "value": float(values.get("recall", 0.0))})
        metric_rows.append({"metric": f"{label}_f1", "value": float(values.get("f1-score", 0.0))})

    metrics_df = pd.DataFrame(metric_rows)
    feature_importance_df = (
        pd.DataFrame({
            "feature": FEATURE_COLUMNS,
            "importance": model.feature_importances_,
        })
        .sort_values("importance", ascending=False)
        .reset_index(drop=True)
    )
    return model, metrics_df, feature_importance_df


def score_forecast_horizon(
    model: RandomForestClassifier,
    forecast_features: pd.DataFrame,
    baselines: dict[str, Any],
) -> pd.DataFrame:
    scored = forecast_features.copy().reset_index(drop=True)
    probabilities = model.predict_proba(scored[FEATURE_COLUMNS])
    class_names = list(model.classes_)
    predicted_actions = model.predict(scored[FEATURE_COLUMNS])

    recommendation_rows: list[dict[str, Any]] = []
    for idx, row in scored.iterrows():
        probability_map = {class_name: float(probabilities[idx, class_idx]) for class_idx, class_name in enumerate(class_names)}
        predicted_action = str(predicted_actions[idx])
        predicted_spec = ACTION_LIBRARY[predicted_action]
        simulation = simulate_action(row, predicted_action, predicted_spec, baselines)

        sorted_probs = sorted(probability_map.items(), key=lambda item: item[1], reverse=True)
        confidence = probability_to_confidence(probability_map[predicted_action])
        urgency = float(
            max(TARGET_MARGIN_PCT - row["margin"], 0.0)
            + max(-row["profit_low"], 0.0) / 1500.0
            + max(row["support_share"] - baselines["share_medians"]["SUPPORT"], 0.0) * 20.0
        )
        recommendation_rows.append(
            {
                "month": row["month"],
                "forecast_origin": row["origin"],
                "recommended_action": predicted_action,
                "recommendation_title": predicted_spec["title"],
                "focus_area": predicted_spec["focus_area"],
                "target_metric": predicted_spec["target_metric"],
                "model_probability": round(probability_map[predicted_action], 4),
                "runner_up_action": sorted_probs[1][0] if len(sorted_probs) > 1 else None,
                "confidence_level": confidence,
                "impact_band": impact_band(simulation["margin_uplift"], urgency),
                "margin_before": round(float(row["margin"]), 2),
                "margin_after": round(float(simulation["margin_after"]), 2),
                "margin_uplift": round(float(simulation["margin_uplift"]), 2),
                "profit_before": round(float(row["profit"]), 2),
                "profit_after": round(float(simulation["profit_after"]), 2),
                "profit_uplift": round(float(simulation["profit_uplift"]), 2),
                "profit_low_before": round(float(row["profit_low"]), 2),
                "profit_low_after": round(float(simulation["profit_low_after"]), 2),
                "urgency_score": round(urgency, 2),
                "recommendation_reason": build_recommendation_reason(row, predicted_action, simulation, baselines),
            }
        )

    recommendations = pd.DataFrame(recommendation_rows).sort_values(
        ["urgency_score", "model_probability"],
        ascending=[False, False],
    ).reset_index(drop=True)
    recommendations["recommendation_rank"] = recommendations.index + 1
    return recommendations


def simulate_action(
    row: pd.Series,
    action_name: str,
    action_spec: dict[str, Any],
    baselines: dict[str, Any],
) -> dict[str, float]:
    adjusted_income = float(row["income"]) * float(action_spec["income_multiplier"])
    adjusted_categories: dict[str, float] = {}

    for category in EXPENSE_CATEGORIES:
        multiplier = float(action_spec["expense_multipliers"].get(category, 1.0))
        adjusted_categories[category] = float(row[category]) * multiplier

    adjusted_expenses = float(sum(adjusted_categories.values()))
    adjusted_profit = adjusted_income - adjusted_expenses
    adjusted_margin = adjusted_profit / adjusted_income * 100.0 if adjusted_income > 0 else 0.0

    income_downside = max(float(row["income"]) - float(row["income_low"]), 0.0)
    expense_upside = max(float(row["expenses_high"]) - float(row["expenses"]), 0.0)
    adjusted_profit_low = max(adjusted_income - income_downside, 0.0) - (adjusted_expenses + expense_upside)

    fit_bonus = score_action_fit(row, action_name, baselines)
    score = (
        (adjusted_profit / max(baselines["avg_income"], 1.0)) * 4.5
        + (adjusted_margin / TARGET_MARGIN_PCT) * 3.0
        + (adjusted_profit_low / max(baselines["avg_income"], 1.0)) * 5.0
        + fit_bonus
        - max(TARGET_MARGIN_PCT - adjusted_margin, 0.0) * 0.18
        - max(-adjusted_profit_low, 0.0) / max(baselines["avg_income"], 1.0) * 6.0
    )

    return {
        "score": float(score),
        "income_after": float(adjusted_income),
        "expenses_after": float(adjusted_expenses),
        "profit_after": float(adjusted_profit),
        "margin_after": float(adjusted_margin),
        "profit_low_after": float(adjusted_profit_low),
        "profit_uplift": float(adjusted_profit - row["profit"]),
        "margin_uplift": float(adjusted_margin - row["margin"]),
    }


def score_action_fit(row: pd.Series, action_name: str, baselines: dict[str, Any]) -> float:
    share_medians = baselines["share_medians"]

    if action_name == "OPTIMIZE_SALARY":
        return (
            max(row["salary_share"] - share_medians["SALARY"], 0.0) * 10.0
            + max(TARGET_MARGIN_PCT - row["margin"], 0.0) * 0.15
        )
    if action_name == "STABILIZE_SUPPORT":
        return (
            max(row["support_share"] - share_medians["SUPPORT"], 0.0) * 14.0
            + max(row["support_growth"], 0.0) * 3.0
        )
    if action_name == "LOCK_INFRA_TOOLS":
        return (
            max(row["infrastructure_share"] - share_medians["INFRASTRUCTURE"], 0.0) * 10.0
            + max(row["tools_share"] - share_medians["TOOLS"], 0.0) * 10.0
            + max(TARGET_MARGIN_PCT - row["margin"], 0.0) * 0.08
        )
    if action_name == "BOOST_MARKETING":
        return (
            max(row["margin"] - TARGET_MARGIN_PCT, 0.0) * 0.10
            + max(share_medians["MARKETING"] - row["marketing_share"], 0.0) * 12.0
            + max(row["income_growth"], 0.0) * 1.8
        )
    if action_name == "INVEST_CONTENT":
        return (
            max(row["margin"] - TARGET_MARGIN_PCT, 0.0) * 0.08
            + max(share_medians["CONTENT"] - row["content_share"], 0.0) * 12.0
            + max(row["income_growth"], 0.0) * 1.4
        )
    if action_name == "DEFEND_MARGIN":
        return (
            max(TARGET_MARGIN_PCT - row["margin"], 0.0) * 0.10
            + max(row["tools_share"] - share_medians["TOOLS"], 0.0) * 8.0
            + max(row["infrastructure_share"] - share_medians["INFRASTRUCTURE"], 0.0) * 8.0
        )
    return 0.0


def build_recommendation_reason(
    row: pd.Series,
    action_name: str,
    simulation: dict[str, float],
    baselines: dict[str, Any],
) -> str:
    reasons: list[str] = []
    if row["margin"] < TARGET_MARGIN_PCT:
        reasons.append(f"margin is below the {TARGET_MARGIN_PCT:.0f}% safety target")
    if row["profit_low"] < 0:
        reasons.append("downside profit range turns negative")

    if action_name == "OPTIMIZE_SALARY" and row["salary_share"] > baselines["share_medians"]["SALARY"]:
        reasons.append("salary share is above the historical balance")
    if action_name == "STABILIZE_SUPPORT" and row["support_share"] > baselines["share_medians"]["SUPPORT"]:
        reasons.append("support cost pressure is rising faster than baseline")
    if action_name == "LOCK_INFRA_TOOLS" and (
        row["infrastructure_share"] > baselines["share_medians"]["INFRASTRUCTURE"]
        or row["tools_share"] > baselines["share_medians"]["TOOLS"]
    ):
        reasons.append("infrastructure and tool spend are crowding margin")
    if action_name == "BOOST_MARKETING":
        reasons.append("the model sees room to buy growth while staying profitable")
    if action_name == "INVEST_CONTENT":
        reasons.append("content refresh should lift bundle and course conversion")
    if action_name == "DEFEND_MARGIN":
        reasons.append("light controls improve resilience without cutting core capacity")
    if action_name == "OPTIMIZE_SALARY":
        reasons.append("rebalancing payroll protects margin in the weakest months")
    if action_name == "STABILIZE_SUPPORT":
        reasons.append("support automation helps stop cost creep before it erodes profit")
    if action_name == "LOCK_INFRA_TOOLS":
        reasons.append("locking platform costs keeps operating leverage intact")

    reasons.append(
        f"projected profit changes by {simulation['profit_uplift']:+.0f} and margin by {simulation['margin_uplift']:+.2f} pts"
    )
    return "; ".join(reasons[:4])


def build_improvement_actions(forecast_recommendations: pd.DataFrame) -> list[dict[str, Any]]:
    grouped = (
        forecast_recommendations.groupby(["recommended_action", "recommendation_title", "target_metric"], as_index=False)
        .agg(
            months=("month", lambda values: ", ".join(sorted(values))),
            average_margin_uplift=("margin_uplift", "mean"),
            average_profit_uplift=("profit_uplift", "mean"),
            max_urgency=("urgency_score", "max"),
        )
        .sort_values(["max_urgency", "average_margin_uplift"], ascending=[False, False])
        .reset_index(drop=True)
    )

    actions: list[dict[str, Any]] = []
    for _, row in grouped.head(5).iterrows():
        priority = "HIGH" if row["max_urgency"] >= 7 else "MEDIUM" if row["max_urgency"] >= 4 else "LOW"
        actions.append(
            {
                "priority": priority,
                "action": row["recommendation_title"],
                "rationale": (
                    f"Recommended across {row['months']}. "
                    f"Average projected margin uplift: {row['average_margin_uplift']:+.2f} pts "
                    f"and profit uplift: {row['average_profit_uplift']:+.0f}."
                ),
                "target_metric": row["target_metric"],
            }
        )
    return actions


def build_executive_metrics(
    forecast_recommendations: pd.DataFrame,
    metrics_df: pd.DataFrame,
) -> list[dict[str, str]]:
    months_at_risk_before = int(
        ((forecast_recommendations["margin_before"] < TARGET_MARGIN_PCT) | (forecast_recommendations["profit_low_before"] < 0)).sum()
    )
    months_at_risk_after = int(
        ((forecast_recommendations["margin_after"] < TARGET_MARGIN_PCT) | (forecast_recommendations["profit_low_after"] < 0)).sum()
    )
    accuracy_value = metrics_df.loc[metrics_df["metric"] == "holdout_accuracy", "value"]
    accuracy_text = f"{float(accuracy_value.iloc[0]) * 100:.1f}%" if not accuracy_value.empty else "N/A"
    best_month = forecast_recommendations.sort_values("margin_uplift", ascending=False).iloc[0]

    return [
        {
            "metric": "months_at_risk_before",
            "value": str(months_at_risk_before),
            "notes": "Forecast months below margin target or with negative downside profit before recommendations.",
            "status": "HIGH" if months_at_risk_before >= 4 else "MEDIUM",
        },
        {
            "metric": "months_at_risk_after",
            "value": str(months_at_risk_after),
            "notes": "Remaining months still at risk after applying the suggested actions.",
            "status": "HIGH" if months_at_risk_after >= 4 else "MEDIUM" if months_at_risk_after >= 2 else "LOW",
        },
        {
            "metric": "avg_margin_uplift",
            "value": f"{forecast_recommendations['margin_uplift'].mean():+.2f} pts",
            "notes": "Average margin improvement across the 6-month horizon.",
            "status": "LOW",
        },
        {
            "metric": "avg_profit_uplift",
            "value": f"${forecast_recommendations['profit_uplift'].mean():,.0f}",
            "notes": "Average monthly profit improvement expected from the recommendations.",
            "status": "LOW",
        },
        {
            "metric": "best_gain_window",
            "value": str(best_month["month"]),
            "notes": f"Highest projected margin lift comes from '{best_month['recommendation_title']}'.",
            "status": "LOW",
        },
        {
            "metric": "model_accuracy",
            "value": accuracy_text,
            "notes": "Holdout accuracy of the recommendation classifier on scenario-based validation data.",
            "status": "LOW",
        },
    ]


def build_user_playbook(forecast_recommendations: pd.DataFrame) -> list[dict[str, Any]]:
    steps: list[dict[str, Any]] = []
    chronological = forecast_recommendations.sort_values("month").reset_index(drop=True)
    for idx, (_, row) in enumerate(chronological.iterrows(), start=1):
        guidance = ACTION_PLAYBOOK.get(str(row["recommended_action"]), {})
        monitoring_note = ""
        if row["profit_low_after"] < 0 or row["margin_after"] < TARGET_MARGIN_PCT:
            monitoring_note = " This improves the plan, but downside risk is still present, so review the target metric weekly."
        steps.append(
            {
                "step": idx,
                "window": row["month"],
                "priority": "HIGH" if row["impact_band"] == "IMMEDIATE" else "MEDIUM" if row["impact_band"] == "STRONG" else "LOW",
                "action": row["recommendation_title"],
                "why": row["recommendation_reason"],
                "how": guidance.get("how", "Follow the recommendation, then review the target metric weekly."),
                "expected_impact": (
                    f"Profit {row['profit_before']:.0f} -> {row['profit_after']:.0f}; "
                    f"margin {row['margin_before']:.2f}% -> {row['margin_after']:.2f}%."
                    f"{monitoring_note}"
                ),
                "success_signal": guidance.get("success_signal", "The target metric improves while profit stays positive."),
                "target_metric": row["target_metric"],
            }
        )
    return steps


def render_chart_assets(forecast_recommendations: pd.DataFrame, output_dir: Path) -> list[dict[str, str]]:
    chart_assets: list[dict[str, str]] = []
    month_labels = forecast_recommendations.sort_values("month")["month"].tolist()

    margin_chart = output_dir / "financial_recommendation_margin_path.png"
    plt.figure(figsize=(10, 5))
    ordered = forecast_recommendations.sort_values("month")
    plt.plot(month_labels, ordered["margin_before"], marker="o", linewidth=2, label="Before")
    plt.plot(month_labels, ordered["margin_after"], marker="o", linewidth=2, label="After")
    plt.axhline(TARGET_MARGIN_PCT, color="#b45309", linestyle="--", linewidth=1.5, label="Target Margin")
    plt.title("Forecast Margin Before vs After Recommendations")
    plt.ylabel("Margin %")
    plt.grid(alpha=0.2)
    plt.legend()
    plt.tight_layout()
    plt.savefig(margin_chart, dpi=160)
    plt.close()
    chart_assets.append({"title": "Margin Before vs After", "file_name": margin_chart.name})

    profit_chart = output_dir / "financial_recommendation_profit_uplift.png"
    plt.figure(figsize=(10, 5))
    plt.bar(month_labels, ordered["profit_uplift"], color="#2563eb")
    plt.axhline(0, color="#111827", linewidth=1)
    plt.title("Projected Monthly Profit Uplift")
    plt.ylabel("Profit Uplift")
    plt.tight_layout()
    plt.savefig(profit_chart, dpi=160)
    plt.close()
    chart_assets.append({"title": "Projected Profit Uplift", "file_name": profit_chart.name})

    urgency_chart = output_dir / "financial_recommendation_urgency.png"
    plt.figure(figsize=(10, 5))
    urgency_order = forecast_recommendations.sort_values("urgency_score", ascending=True)
    plt.barh(urgency_order["month"], urgency_order["urgency_score"], color="#dc2626")
    plt.title("Urgency Score by Forecast Month")
    plt.xlabel("Urgency Score")
    plt.tight_layout()
    plt.savefig(urgency_chart, dpi=160)
    plt.close()
    chart_assets.append({"title": "Urgency by Month", "file_name": urgency_chart.name})

    return chart_assets


def build_summary_payload(
    dashboard: dict[str, Any],
    forecast_scorecard: pd.DataFrame,
    income_df: pd.DataFrame,
    actual_monthly: pd.DataFrame,
    forecast_recommendations: pd.DataFrame,
    improvement_actions: list[dict[str, Any]],
    executive_metrics: list[dict[str, str]],
    user_playbook: list[dict[str, Any]],
    chart_assets: list[dict[str, str]],
    metrics_df: pd.DataFrame,
    feature_importance_df: pd.DataFrame,
    baselines: dict[str, Any],
    top_n: int,
) -> dict[str, Any]:
    income_mix = income_df.groupby("source_type", dropna=False)["amount"].sum().sort_values(ascending=False)
    top_revenue_source = None if income_mix.empty else str(income_mix.index[0])

    first_month = str(forecast_recommendations.sort_values("month").iloc[0]["month"])
    last_month = str(forecast_recommendations.sort_values("month").iloc[-1]["month"])
    scorecard_values = {str(row["metric"]): row["value"] for _, row in forecast_scorecard.iterrows()}

    forecast_context = {
        "margin_pct": rounded_or_none(scorecard_values.get("margin_pct")),
        "margin_trend_pct": rounded_or_none(
            float(forecast_recommendations.sort_values("month").iloc[-1]["margin_before"])
            - float(forecast_recommendations.sort_values("month").iloc[0]["margin_before"])
        ),
        "top_revenue_source": top_revenue_source,
        "watch_category": baselines["watch_category"],
        "watch_growth_pct": rounded_or_none(baselines["watch_growth_pct"]),
        "forecast_horizon": {
            "from": first_month,
            "to": last_month,
            "months_ahead": int(len(forecast_recommendations)),
            "income_total": rounded_or_none(sum(item.get("income", 0.0) for item in dashboard.get("forecast_monthly", {}).values())),
            "expenses_total": rounded_or_none(sum(item.get("expenses", 0.0) for item in dashboard.get("forecast_monthly", {}).values())),
            "profit_total": rounded_or_none(sum(item.get("profit", 0.0) for item in dashboard.get("forecast_monthly", {}).values())),
        },
    }

    top_rows = forecast_recommendations.head(top_n).copy()
    top_recommendations = []
    for _, row in top_rows.iterrows():
        playbook_match = next(
            (
                step
                for step in user_playbook
                if step["window"] == row["month"] and step["action"] == row["recommendation_title"]
            ),
            None,
        )
        top_recommendations.append(
            {
                "recommendation_rank": int(row["recommendation_rank"]),
                "title": row["recommendation_title"],
                "platform": "Finance AI",
                "skill_category": row["focus_area"],
                "hybrid_final_score": round(float(row["model_probability"]), 4),
                "confidence_level": row["confidence_level"],
                "impact_band": row["impact_band"],
                "recommendation_reason": row["recommendation_reason"],
                "url": None,
                "month": row["month"],
                "margin_before": rounded_or_none(row["margin_before"]),
                "margin_after": rounded_or_none(row["margin_after"]),
                "profit_before": rounded_or_none(row["profit_before"]),
                "profit_after": rounded_or_none(row["profit_after"]),
                "profit_low_before": rounded_or_none(row["profit_low_before"]),
                "profit_low_after": rounded_or_none(row["profit_low_after"]),
                "action_key": row["recommended_action"],
                "target_metric": row["target_metric"],
                "priority": None if playbook_match is None else playbook_match.get("priority"),
                "how": None if playbook_match is None else playbook_match.get("how"),
                "expected_outcome": None if playbook_match is None else playbook_match.get("expected_impact"),
                "success_signal": None if playbook_match is None else playbook_match.get("success_signal"),
            }
        )

    summary_text = build_summary_text(forecast_context, top_rows, improvement_actions, metrics_df, actual_monthly)
    return {
        "forecast_context": forecast_context,
        "recommendation_scorecard": metrics_df.to_dict(orient="records"),
        "executive_metrics": executive_metrics,
        "model_feature_importance": feature_importance_df.head(10).to_dict(orient="records"),
        "user_playbook": user_playbook,
        "charts": chart_assets,
        "top_recommendations": top_recommendations,
        "improvement_actions": improvement_actions,
        "summary_text": summary_text,
    }


def export_outputs(
    output_dir: Path,
    artifact_dir: Path,
    summary: dict[str, Any],
    forecast_recommendations: pd.DataFrame,
    improvement_actions: list[dict[str, Any]],
    executive_metrics: list[dict[str, str]],
    user_playbook: list[dict[str, Any]],
    metrics_df: pd.DataFrame,
    feature_importance_df: pd.DataFrame,
    model: RandomForestClassifier,
) -> PipelineArtifacts:
    summary_path = output_dir / "financial_recommendations_summary.json"
    monthly_recommendations_path = output_dir / "financial_recommendations_by_month.csv"
    improvement_actions_path = output_dir / "financial_recommendation_actions.csv"
    executive_metrics_path = output_dir / "financial_recommendation_executive_scorecard.csv"
    playbook_path = output_dir / "financial_recommendation_playbook.csv"
    model_metrics_path = output_dir / "financial_recommendation_model_metrics.csv"
    feature_importance_path = output_dir / "financial_recommendation_feature_importance.csv"
    report_path = output_dir / "financial_recommendation_report.txt"
    model_path = artifact_dir / MODEL_FILE_NAME

    summary_path.write_text(json.dumps(summary, indent=2), encoding="utf-8")
    forecast_recommendations.to_csv(monthly_recommendations_path, index=False)
    pd.DataFrame(improvement_actions).to_csv(improvement_actions_path, index=False)
    pd.DataFrame(executive_metrics).to_csv(executive_metrics_path, index=False)
    pd.DataFrame(user_playbook).to_csv(playbook_path, index=False)
    metrics_df.to_csv(model_metrics_path, index=False)
    feature_importance_df.to_csv(feature_importance_path, index=False)
    report_path.write_text(summary["summary_text"], encoding="utf-8")
    joblib.dump(
        {
            "model": model,
            "feature_columns": FEATURE_COLUMNS,
            "expense_categories": EXPENSE_CATEGORIES,
            "action_library": ACTION_LIBRARY,
            "target_margin_pct": TARGET_MARGIN_PCT,
        },
        model_path,
    )

    return PipelineArtifacts(
        summary_path=summary_path,
        monthly_recommendations_path=monthly_recommendations_path,
        improvement_actions_path=improvement_actions_path,
        executive_metrics_path=executive_metrics_path,
        playbook_path=playbook_path,
        model_metrics_path=model_metrics_path,
        feature_importance_path=feature_importance_path,
        report_path=report_path,
        model_path=model_path,
    )


def build_summary_text(
    forecast_context: dict[str, Any],
    top_rows: pd.DataFrame,
    improvement_actions: list[dict[str, Any]],
    metrics_df: pd.DataFrame,
    actual_monthly: pd.DataFrame,
) -> str:
    accuracy = metrics_df.loc[metrics_df["metric"] == "holdout_accuracy", "value"]
    accuracy_text = f"{float(accuracy.iloc[0]) * 100:.1f}%" if not accuracy.empty else "N/A"
    lines = [
        "FINANCIAL RECOMMENDATIONS TO KEEP OUTCOMES POSITIVE",
        "--------------------------------",
        f"Forecast margin context: {forecast_context['margin_pct']:.2f}%",
        f"Forecast horizon: {forecast_context['forecast_horizon']['from']} to {forecast_context['forecast_horizon']['to']}",
        f"Top revenue source: {forecast_context['top_revenue_source']}",
        f"Holdout accuracy: {accuracy_text}",
        "",
        "PRIORITY RECOMMENDATIONS",
    ]
    for _, row in top_rows.iterrows():
        lines.append(
            f"- {row['month']}: {row['recommendation_title']} | "
            f"margin {row['margin_before']:.2f}% -> {row['margin_after']:.2f}% | "
            f"profit {row['profit_before']:.0f} -> {row['profit_after']:.0f}"
        )

    lines.append("")
    lines.append("IMPROVEMENT ACTIONS")
    for action in improvement_actions:
        lines.append(f"- [{action['priority']}] {action['action']} -> {action['rationale']}")

    lines.append("")
    lines.append("HISTORICAL BASELINE")
    for _, row in actual_monthly.iterrows():
        lines.append(
            f"- {row['month']}: income {row['income']:.0f}, expenses {row['expenses']:.0f}, "
            f"profit {row['profit']:.0f}, margin {row['margin']:.2f}%"
        )
    return "\n".join(lines)


def pct_change_series(current: pd.Series, previous: pd.Series) -> pd.Series:
    denominator = previous.replace(0, np.nan)
    return ((current - previous) / denominator).replace([np.inf, -np.inf], np.nan).fillna(0.0)


def safe_pct_change(current: float, previous: float) -> float:
    if previous == 0:
        return 0.0
    return float((current - previous) / previous)


def uncertainty_ratio(base: float, low: float, high: float, fallback: float) -> float:
    if base <= 0:
        return fallback
    spread = max(abs(base - low), abs(high - base))
    return float(max(spread / base, fallback))


def probability_to_confidence(value: float) -> str:
    if value >= 0.72:
        return "HIGH"
    if value >= 0.48:
        return "MEDIUM"
    return "LOW"


def impact_band(margin_uplift: float, urgency: float) -> str:
    if urgency >= 7.0 or margin_uplift >= 5.0:
        return "IMMEDIATE"
    if urgency >= 3.5 or margin_uplift >= 2.0:
        return "STRONG"
    return "WATCH"


def rounded_or_none(value: Any) -> float | None:
    if value is None or (isinstance(value, float) and np.isnan(value)):
        return None
    return round(float(value), 4)
