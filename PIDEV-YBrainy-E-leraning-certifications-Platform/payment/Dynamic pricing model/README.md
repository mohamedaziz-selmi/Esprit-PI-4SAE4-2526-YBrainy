# Dynamic Pricing Model

This folder contains a starter ML pipeline for `recommended sale price` and `best discount range per pack`.

It uses:
- your payment module pack catalog
- scraper outputs from `scraper/result/elearning_outputs`
- simulated offer scenarios across multiple discount levels
- synthetic learner budgets and price-sensitivity profiles

Because the scraper data does not include real competitor prices or real checkout history, the model uses scraper `roi_score`, `demand_level`, `views`, `engagement_score`, and `skill_category` as market-demand proxies.

## What it generates

- `outputs/dynamic_pricing_recommendations.csv`
- `outputs/pack_pricing_scenarios.csv`
- `outputs/dynamic_pricing_training_data.csv`
- `outputs/top_dynamic_pricing_opportunities.csv`
- `outputs/dynamic_pricing_summary.json`
- `outputs/schema_dynamic_pricing.sql`
- `outputs/seed_dynamic_pricing_recommendations.sql`
- `outputs/seed_pack_pricing_scenarios.sql`
- `artifacts/dynamic_pricing_model.joblib`

## Model idea

Each training row represents one `user-pack-price-offer` scenario with:
- original price
- offered sale price
- discount percentage
- category
- level
- duration
- market demand score from scraper data
- ROI and salary-boost proxies from scraper data
- user budget, loyalty, activity, and price sensitivity

Target:
- `purchased` = `1` or `0`

The pipeline scores multiple price points for each pack, compares them with the current sale price, and recommends:
- the best sale price
- the best discount range
- whether to `increase`, `decrease`, or `hold` the current price

## Run

```bash
python "Dynamic pricing model/dynamic_pricing_pipeline.py"
```

## SQL tables

The generated SQL creates two analytics tables:
- `pack_dynamic_pricing_recommendations`
- `pack_pricing_scenarios`

These are separate analytics tables, so you can import them safely for dashboards and pricing demos without changing your live `packs` table.
