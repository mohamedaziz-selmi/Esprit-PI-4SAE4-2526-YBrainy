from __future__ import annotations

import argparse
import json
from pathlib import Path

import numpy as np

from generate_cartoon_avatar import DEFAULT_MODEL_PATH, detect_face, ensure_models, identity_signature


def compare_vectors(vector_a: list[float], vector_b: list[float]) -> dict[str, float | str]:
    a = np.array(vector_a, dtype=np.float32)
    b = np.array(vector_b, dtype=np.float32)
    l2_distance = float(np.linalg.norm(a - b))
    cosine_similarity = float(np.dot(a, b) / (np.linalg.norm(a) * np.linalg.norm(b)))

    if l2_distance < 2.5:
        verdict = "likely_same_person"
    elif l2_distance < 4.5:
        verdict = "uncertain"
    else:
        verdict = "likely_different_person"

    return {
        "l2_distance": round(l2_distance, 5),
        "cosine_similarity": round(cosine_similarity, 5),
        "verdict": verdict,
    }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Compare two images using a normalized face-geometry signature."
    )
    parser.add_argument("--image-a", type=Path, required=True, help="First image path.")
    parser.add_argument("--image-b", type=Path, required=True, help="Second image path.")
    parser.add_argument(
        "--model-path",
        type=Path,
        default=DEFAULT_MODEL_PATH,
        help=f"Path to the MediaPipe face landmarker task file. Defaults to {DEFAULT_MODEL_PATH}.",
    )
    parser.add_argument(
        "--min-confidence",
        type=float,
        default=0.35,
        help="Minimum face detection and presence confidence.",
    )
    parser.add_argument(
        "--json",
        action="store_true",
        help="Print the result as JSON.",
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    for image_path in (args.image_a, args.image_b):
        if not image_path.exists():
            raise FileNotFoundError(f"Input image not found: {image_path}")

    ensure_models([args.model_path])

    _, landmarks_a, _ = detect_face(args.image_a, args.model_path, args.min_confidence)
    _, landmarks_b, _ = detect_face(args.image_b, args.model_path, args.min_confidence)
    signature_a = identity_signature(landmarks_a)
    signature_b = identity_signature(landmarks_b)
    comparison = compare_vectors(signature_a["vector"], signature_b["vector"])

    result = {
        "image_a": str(args.image_a),
        "image_b": str(args.image_b),
        "signature_a_hash": signature_a["hash"],
        "signature_b_hash": signature_b["hash"],
        "comparison": comparison,
        "note": (
            "This is a geometry-based face comparison from MediaPipe landmarks. "
            "It is useful for rough structural similarity, not biometric identity verification."
        ),
    }

    if args.json:
        print(json.dumps(result, indent=2))
        return

    print(f"Image A: {result['image_a']}")
    print(f"Image B: {result['image_b']}")
    print(f"Signature A: {result['signature_a_hash']}")
    print(f"Signature B: {result['signature_b_hash']}")
    print(f"L2 distance: {comparison['l2_distance']}")
    print(f"Cosine similarity: {comparison['cosine_similarity']}")
    print(f"Verdict: {comparison['verdict']}")
    print(result["note"])


if __name__ == "__main__":
    main()
