import hashlib
import os
import sys

import cv2


FACE_DIFF_THRESHOLD = 12000.0
FACE_SIZE = (100, 100)
DETECTION_MAX_WIDTH = 640  # downscale before detection — dramatically faster on HD/4K captures


def load_cascade(cascade_path):
    cascade = cv2.CascadeClassifier(cascade_path)
    if cascade.empty():
        raise RuntimeError("Could not load cascade classifier")
    return cascade


def load_image(image_path):
    image = cv2.imread(image_path)
    if image is None:
        raise RuntimeError(f"Failed to load image: {image_path}")
    return image


def extract_face_signature(image_path, cascade_path):
    cascade = load_cascade(cascade_path)
    image = load_image(image_path)

    # Downscale to DETECTION_MAX_WIDTH for fast cascade detection
    h, w = image.shape[:2]
    if w > DETECTION_MAX_WIDTH:
        scale = DETECTION_MAX_WIDTH / w
        image = cv2.resize(image, (DETECTION_MAX_WIDTH, int(h * scale)), interpolation=cv2.INTER_AREA)

    gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
    gray = cv2.equalizeHist(gray)  # normalize lighting before detection
    faces = cascade.detectMultiScale(gray, scaleFactor=1.1, minNeighbors=4)

    if len(faces) == 0:
        raise RuntimeError("No face detected")

    x, y, w, h = max(faces, key=lambda item: item[2] * item[3])
    face_roi = gray[y:y + h, x:x + w]
    normalized = cv2.resize(face_roi, FACE_SIZE)
    normalized = cv2.equalizeHist(normalized)
    return normalized


def compute_hash(face_signature):
    return hashlib.sha256(face_signature.tobytes()).hexdigest()


def command_hash(image_path, cascade_path):
    face_signature = extract_face_signature(image_path, cascade_path)
    print(compute_hash(face_signature))


def command_match(faces_dir, live_image_path, cascade_path):
    if not os.path.isdir(faces_dir):
        raise RuntimeError("Faces directory not found")

    live_signature = extract_face_signature(live_image_path, cascade_path)
    best_hash = ""
    best_score = None

    for filename in os.listdir(faces_dir):
        if not filename.lower().endswith((".png", ".jpg", ".jpeg")):
            continue

        stored_path = os.path.join(faces_dir, filename)
        try:
            stored_signature = extract_face_signature(stored_path, cascade_path)
        except RuntimeError:
            continue

        diff = cv2.norm(stored_signature, live_signature)
        if best_score is None or diff < best_score:
            best_score = diff
            best_hash = os.path.splitext(filename)[0]

    if best_score is not None and best_score < FACE_DIFF_THRESHOLD:
        print(best_hash)
    else:
        print("", file=sys.stderr) if best_score is None else print(f"[face] best diff={best_score:.1f} threshold={FACE_DIFF_THRESHOLD} — no match", file=sys.stderr)
        print("")


def main():
    if len(sys.argv) < 2:
        print("Usage: python face_biometric.py <hash|match> ...", file=sys.stderr)
        sys.exit(1)

    command = sys.argv[1].strip().lower()

    try:
        if command == "hash":
            if len(sys.argv) != 4:
                raise RuntimeError("Usage: python face_biometric.py hash <image_path> <cascade_path>")
            command_hash(sys.argv[2], sys.argv[3])
            return

        if command == "match":
            if len(sys.argv) != 5:
                raise RuntimeError(
                    "Usage: python face_biometric.py match <faces_dir> <live_image_path> <cascade_path>"
                )
            command_match(sys.argv[2], sys.argv[3], sys.argv[4])
            return

        raise RuntimeError(f"Unknown command: {command}")
    except RuntimeError as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
