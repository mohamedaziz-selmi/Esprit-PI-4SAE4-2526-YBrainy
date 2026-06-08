# Pack Conversion Score

This folder contains a starter ML pipeline for `purchase probability per pack`.

It uses:
- the payment module pack catalog you provided
- scraper outputs from `scraper/result/elearning_outputs`
- synthetic user behavior generated from pack features plus market-demand signals

Because the scraper data does not contain real pack sales, the pipeline uses scraper `roi_score`, `demand_level`, `views`, `engagement_score`, and `skill_category` as a demand proxy.

## What it generates

- `outputs/top_pack_market_signals.csv`
- `outputs/pack_conversion_training_data.csv`
- `outputs/pack_conversion_scores.csv`
- `outputs/pack_conversion_summary.json`
- `outputs/schema_pack_conversion.sql`
- `outputs/seed_pack_conversion_scores.sql`
- `outputs/seed_pack_user_behavior_samples.sql`
- `artifacts/pack_conversion_model.joblib`

## Model idea

Each training row represents one `user-pack` interaction with:
- pack price
- discount percentage
- category
- level
- duration
- market demand score from scraper data
- user views, clicks, wishlist adds, cart adds
- prior purchases and category affinity

Target:
- `purchased` = `1` or `0`

The exported `pack_conversion_scores.csv` is the main file to use on `/dashboard/packs`.

## Run

```bash
python "Conversion packs/pack_conversion_pipeline.py"
```

## SQL tables

The generated SQL creates two analytics tables:
- `pack_conversion_scores`
- `pack_user_behavior_samples`

These are separate from your live `carts`, `cart_items`, and `cart_history` tables, so you can import them safely for dashboard analytics and ML demos.
