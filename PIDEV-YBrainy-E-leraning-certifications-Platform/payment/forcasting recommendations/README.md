# Financial Recommendations

This folder contains a recommendation model that is trained from the finance forecasting outputs in `../forcasting`.

## What it does

- Reads historical income and expense data from `../forcasting/income.csv` and `../forcasting/expenses.csv`
- Reads forecast outputs from `../forcasting/outputs`
- Builds scenario-augmented training data around the forecast states
- Trains a recommendation classifier to choose the best action for keeping profit and margin positive
- Exports ready-to-use JSON, CSV, TXT, and model artifacts

## Run

```bash
python "forcasting recommendations/train_recommendation_model.py"
```

## Generated files

- `outputs/financial_recommendations_summary.json`
- `outputs/financial_recommendations_by_month.csv`
- `outputs/financial_recommendation_actions.csv`
- `outputs/financial_recommendation_executive_scorecard.csv`
- `outputs/financial_recommendation_model_metrics.csv`
- `outputs/financial_recommendation_feature_importance.csv`
- `outputs/financial_recommendation_playbook.csv`
- `outputs/financial_recommendation_margin_path.png`
- `outputs/financial_recommendation_profit_uplift.png`
- `outputs/financial_recommendation_urgency.png`
- `outputs/financial_recommendation_report.txt`
- `artifacts/financial_recommendation_model.joblib`
