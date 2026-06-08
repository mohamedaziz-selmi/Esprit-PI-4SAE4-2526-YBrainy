from __future__ import annotations

import argparse
from pathlib import Path

from financial_recommendations import run_pipeline


def main() -> None:
    base_dir = Path(__file__).resolve().parent

    parser = argparse.ArgumentParser(
        description="Train the finance recommendation model from forecasting outputs."
    )
    parser.add_argument(
        "--forecasting-dir",
        type=Path,
        default=base_dir.parent / "forcasting",
        help="Path to the forecasting folder containing income.csv, expenses.csv and outputs/.",
    )
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=base_dir / "outputs",
        help="Directory where recommendation CSV/JSON/TXT outputs will be written.",
    )
    parser.add_argument(
        "--artifact-dir",
        type=Path,
        default=base_dir / "artifacts",
        help="Directory where the trained model artifact will be saved.",
    )
    parser.add_argument(
        "--top-n",
        type=int,
        default=10,
        help="How many ranked recommendations to include in the JSON summary.",
    )
    parser.add_argument(
        "--random-state",
        type=int,
        default=42,
        help="Random seed for reproducible scenario generation and training.",
    )
    args = parser.parse_args()

    summary = run_pipeline(
        forecasting_dir=args.forecasting_dir,
        output_dir=args.output_dir,
        artifact_dir=args.artifact_dir,
        top_n=args.top_n,
        random_state=args.random_state,
    )

    top = summary.get("top_recommendations", [])
    print("Finance recommendation model training complete.")
    print(f"Summary file: {summary['artifacts']['summary_path']}")
    print(f"Model file: {summary['artifacts']['model_path']}")
    print(f"Top recommendations exported: {len(top)}")
    for item in top[:5]:
        print(
            f"- #{item['recommendation_rank']} {item['title']} "
            f"[{item['confidence_level']}] score={item['hybrid_final_score']}"
        )


if __name__ == "__main__":
    main()
