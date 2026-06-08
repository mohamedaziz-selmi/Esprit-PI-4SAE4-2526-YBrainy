# Scenario Simulator

This folder contains a finance scenario simulator for `/dashboard/finance`.

It combines four inputs that already exist in the project:

- `forcasting/outputs/forecast_monthly_6m.csv` as the baseline finance forecast
- `forcasting recommendations/outputs/financial_recommendations_summary.json` as the finance action context
- `Dynamic pricing model/outputs/dynamic_pricing_summary.json` as the pricing uplift input
- scraper outputs from `scraper/result/elearning_outputs` as the live market-demand signal

## What the simulator does

The simulator answers questions like:

- What happens if marketing budget goes up by 10%?
- What happens if dynamic pricing is rolled out across the catalog?
- What if we launch 2 or 3 new packs in the strongest scraper-driven markets?
- What if demand drops and we need a defensive plan?

It uses a hybrid approach:

- a rule engine to generate realistic scenario outcomes
- `RandomForestRegressor` models to estimate income and expenses
- a `RandomForestClassifier` to estimate scenario risk level

## Generated outputs

Running `scenario_simulator_pipeline.py` creates:

- `outputs/scenario_simulator_summary.json`
- `outputs/scenario_catalog.csv`
- `outputs/scenario_monthly_projection.csv`
- `outputs/scenario_training_data.csv`
- `outputs/schema_finance_scenario_simulator.sql`
- `outputs/seed_finance_scenarios.sql`
- `outputs/seed_income_history.sql`
- `outputs/seed_expenses_history.sql`
- `outputs/run_summary.json`

It also stores the trained models in `artifacts/`.

## SQL seed rules

The SQL export matches the finance tables you shared, with safe mappings for enum compatibility:

- income `BANK_TRANSFER` -> `LOCAL`
- expenses `TOOLS` -> `SOFTWARE`
- expenses `CONTENT` -> `OTHER`
- expenses `SUPPORT` -> `OTHER`
- expenses `SALARY` -> `SALARIES`

For `income.reference_id`, non-numeric values such as `REF179046` are converted to their numeric part (`179046`). Blank values remain `NULL`.

## Run

```powershell
python "Scenario Simulator\scenario_simulator_pipeline.py"
```

## Best use in the dashboard

The most useful frontend blocks for this model are:

- scenario cards: `Base`, `Pricing Rollout`, `Growth Push`, `AI Expansion`, `Efficiency Guardrail`, `Slowdown Defense`
- a baseline vs scenario profit comparison chart
- a monthly revenue / expense / profit table
- a risk badge per scenario
- sliders for the scenario controls:
  - marketing budget change
  - dynamic pricing rollout
  - new pack launches
  - cost control
  - salary optimization
  - market demand shock
  - support automation
  - focus on top scraper market
