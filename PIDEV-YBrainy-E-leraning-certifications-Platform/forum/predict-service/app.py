import warnings
warnings.filterwarnings("ignore")

import pickle
import os
import numpy as np
from flask import Flask, request, jsonify
from flask_cors import CORS

app = Flask(__name__)
CORS(app)

_default_model = os.path.join(os.path.dirname(__file__), "ybrainy_model.pkl")
MODEL_PATH = os.environ.get("MODEL_PATH", _default_model)

print(f"Loading model from {MODEL_PATH}...")
with open(MODEL_PATH, "rb") as f:
    bundle = pickle.load(f)

tfidf   = bundle["tfidf"]
svd     = bundle["svd"]
model   = bundle["model"]
encoder = bundle["encoder"]
classes = bundle["classes"]
print(f"Model loaded. Classes: {classes}")

LABEL_META = {
    "HQ":       {"color": "#22c55e", "message": "Excellent thread — well-written and clear!"},
    "LQ_CLOSE": {"color": "#ef4444", "message": "This thread may be closed — improve focus and clarity."},
    "LQ_EDIT":  {"color": "#f59e0b", "message": "This thread needs editing — add more detail."},
}


def predict_quality(title: str, body: str, tags: str = "") -> dict:
    title = title.strip() if title else ""
    body  = body.strip()  if body  else ""
    tags  = tags.strip()  if tags  else ""

    # Replicate the notebook's text_combined formula: title*3 + tags*2 + body*1
    text_combined = (title + " ") * 3 + (tags + " ") * 2 + body

    X     = tfidf.transform([text_combined])
    X_svd = svd.transform(X)
    proba = model.predict_proba(X_svd)[0]
    pred  = model.predict(X_svd)[0]
    label = encoder.inverse_transform([pred])[0]
    confidence = float(np.max(proba))

    meta = LABEL_META.get(label, {"color": "#6b7280", "message": ""})
    return {
        "label":      label,
        "confidence": round(confidence, 3),
        "color":      meta["color"],
        "message":    meta["message"],
        "probabilities": {
            classes[i]: round(float(proba[i]), 3) for i in range(len(classes))
        }
    }


@app.route("/predict", methods=["POST"])
def predict():
    data  = request.get_json(force=True) or {}
    title = data.get("title", "")
    body  = data.get("body", "")
    tags  = data.get("tags", "")

    if not title and not body:
        return jsonify({"error": "title or body is required"}), 400

    result = predict_quality(title, body, tags)
    return jsonify(result)


@app.route("/health", methods=["GET"])
def health():
    return jsonify({"status": "up", "model_version": bundle.get("version", "unknown")})


if __name__ == "__main__":
    port = int(os.environ.get("PORT", os.environ.get("SERVER_PORT", "5001")))
    app.run(host="0.0.0.0", port=port, debug=False)
