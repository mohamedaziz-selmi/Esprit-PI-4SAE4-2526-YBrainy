from __future__ import annotations

import argparse
import threading
import time
import urllib.request
from collections import deque
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path

import cv2
import mediapipe as mp
import numpy as np

try:
    import winsound
except ImportError:
    winsound = None


RIGHT_EYE_OUTLINE = [33, 7, 163, 144, 145, 153, 154, 155, 133, 173, 157, 158, 159, 160, 161, 246]
LEFT_EYE_OUTLINE = [362, 382, 381, 380, 374, 373, 390, 249, 263, 466, 388, 387, 386, 385, 384, 398]
RIGHT_IRIS = [469, 470, 471, 472]
LEFT_IRIS = [474, 475, 476, 477]

RIGHT_EYE_CORNERS = (33, 133)
LEFT_EYE_CORNERS = (362, 263)
RIGHT_EYE_LIDS = (159, 145)
LEFT_EYE_LIDS = (386, 374)

NOSE_TIP = 1
LEFT_CHEEK = 234
RIGHT_CHEEK = 454
FACE_LANDMARKER_MODEL_URL = "https://storage.googleapis.com/mediapipe-assets/face_landmarker_v2.task"


@dataclass
class EyeMeasurement:
    iris_center: np.ndarray
    horizontal_ratio: float
    vertical_ratio: float
    openness: float
    eye_width: float


@dataclass
class GazeFeatures:
    left_horizontal: float
    right_horizontal: float
    left_vertical: float
    right_vertical: float
    average_horizontal: float
    average_vertical: float
    face_horizontal: float
    average_openness: float


@dataclass
class GazeDecision:
    looking_at_screen: bool
    focus_score: float
    horizontal_deviation: float
    vertical_deviation: float
    face_deviation: float
    dominant_ratio: float


class AwayAlert:
    def __init__(self, enabled: bool, frequency: int, duration_ms: int, cooldown_sec: float) -> None:
        self.enabled = enabled
        self.frequency = frequency
        self.duration_ms = duration_ms
        self.cooldown_sec = cooldown_sec
        self.last_trigger_time = 0.0
        self._lock = threading.Lock()
        self._active = False

    def trigger(self) -> None:
        if not self.enabled:
            return
        now = time.monotonic()
        if now - self.last_trigger_time < self.cooldown_sec:
            return
        with self._lock:
            if self._active:
                return
            self._active = True
            self.last_trigger_time = now
        threading.Thread(target=self._play_alert, daemon=True).start()

    def _play_alert(self) -> None:
        try:
            if winsound is not None:
                winsound.Beep(self.frequency, self.duration_ms)
            else:
                print("\a", end="", flush=True)
        finally:
            with self._lock:
                self._active = False


class EventLogger:
    def __init__(self, enabled: bool, log_path: Path, cooldown_sec: float) -> None:
        self.enabled = enabled
        self.log_path = log_path
        self.cooldown_sec = cooldown_sec
        self.last_state: str | None = None
        self.last_logged_at = 0.0
        self.not_focused_event_count = 0
        self.low_attention_event_count = 0
        self.last_message = "No events logged yet"

        if self.enabled:
            self.log_path.parent.mkdir(parents=True, exist_ok=True)
            with self.log_path.open("a", encoding="utf-8") as handle:
                handle.write(f"\n=== Session started {self._timestamp()} ===\n")

    def _timestamp(self) -> str:
        return datetime.now().strftime("%Y-%m-%d %H:%M:%S")

    def _write_message(self, message: str) -> None:
        self.last_message = message
        print(message, flush=True)
        with self.log_path.open("a", encoding="utf-8") as handle:
            handle.write(message + "\n")

    def log_state(
        self,
        state: str,
        focus_score: float | None = None,
        reason: str | None = None,
    ) -> None:
        if not self.enabled:
            return

        now = time.monotonic()
        should_log = state != self.last_state
        if state == "away" and not should_log and now - self.last_logged_at >= self.cooldown_sec:
            should_log = True
        if not should_log:
            return

        self.last_state = state
        self.last_logged_at = now

        score_text = ""
        if focus_score is not None:
            score_text = f" focus={focus_score:.2f}"
        reason_text = ""
        if reason:
            reason_text = f" reason={reason}"

        timestamp = self._timestamp()
        if state in {"away", "not_focused"}:
            self.not_focused_event_count += 1
            message = f"[{timestamp}] NOT FOCUSED ON WORK{score_text}{reason_text}"
        elif state in {"looking", "focused"}:
            message = f"[{timestamp}] FOCUSED ON WORK{score_text}{reason_text}"
        else:
            message = f"[{timestamp}] {state}{score_text}{reason_text}"

        self._write_message(message)

    def log_low_attention(self, focus_score: float, duration_sec: float, threshold: float) -> None:
        if not self.enabled:
            return

        self.low_attention_event_count += 1
        timestamp = self._timestamp()
        message = (
            f"[{timestamp}] LOW ATTENTION focus={focus_score:.2f} "
            f"threshold={threshold:.2f} duration={duration_sec:.1f}s"
        )
        self._write_message(message)


class LowAttentionMonitor:
    def __init__(self, threshold: float, min_duration_sec: float) -> None:
        self.threshold = threshold
        self.min_duration_sec = min_duration_sec
        self.reset()

    def reset(self) -> None:
        self.low_start_time: float | None = None
        self.current_duration_sec = 0.0
        self.event_logged = False

    def update(self, focus_score: float, now: float, event_logger: EventLogger) -> None:
        if focus_score < self.threshold:
            if self.low_start_time is None:
                self.low_start_time = now
                self.current_duration_sec = 0.0
                self.event_logged = False
            else:
                self.current_duration_sec = now - self.low_start_time

            if self.current_duration_sec >= self.min_duration_sec and not self.event_logged:
                event_logger.log_low_attention(
                    focus_score=focus_score,
                    duration_sec=self.current_duration_sec,
                    threshold=self.threshold,
                )
                self.event_logged = True
        else:
            self.reset()


class EyeClosureMonitor:
    def __init__(self, closed_ratio_threshold: float, blink_max_duration_sec: float) -> None:
        self.closed_ratio_threshold = closed_ratio_threshold
        self.blink_max_duration_sec = blink_max_duration_sec
        self.reset()

    def reset(self) -> None:
        self.closed_since: float | None = None
        self.closed_duration_sec = 0.0

    def update(self, openness_ratio: float, now: float) -> tuple[bool, float]:
        if openness_ratio < self.closed_ratio_threshold:
            if self.closed_since is None:
                self.closed_since = now
                self.closed_duration_sec = 0.0
            else:
                self.closed_duration_sec = now - self.closed_since
        else:
            self.reset()

        is_blinking = 0.0 < self.closed_duration_sec < self.blink_max_duration_sec
        return is_blinking, self.closed_duration_sec


class FocusStateMachine:
    def __init__(self, focus_hold_sec: float, unfocus_hold_sec: float) -> None:
        self.focus_hold_sec = focus_hold_sec
        self.unfocus_hold_sec = unfocus_hold_sec
        self.current_state = "unknown"
        self.pending_state: str | None = None
        self.pending_since: float | None = None

    def reset(self) -> None:
        self.current_state = "unknown"
        self.pending_state = None
        self.pending_since = None

    def update(self, candidate_state: str | None, now: float) -> tuple[str, bool]:
        if candidate_state is None:
            self.pending_state = None
            self.pending_since = None
            return self.current_state, False

        if self.current_state == "unknown":
            self.current_state = candidate_state
            self.pending_state = None
            self.pending_since = None
            return self.current_state, True

        if candidate_state == self.current_state:
            self.pending_state = None
            self.pending_since = None
            return self.current_state, False

        if self.pending_state != candidate_state:
            self.pending_state = candidate_state
            self.pending_since = now
            return self.current_state, False

        hold_sec = self.focus_hold_sec if candidate_state == "focused" else self.unfocus_hold_sec
        if self.pending_since is not None and now - self.pending_since >= hold_sec:
            self.current_state = candidate_state
            self.pending_state = None
            self.pending_since = None
            return self.current_state, True

        return self.current_state, False


class CalibrationModel:
    def __init__(
        self,
        target_frames: int,
        min_horizontal_threshold: float,
        min_vertical_threshold: float,
        min_face_threshold: float,
    ) -> None:
        self.target_frames = target_frames
        self.min_horizontal_threshold = min_horizontal_threshold
        self.min_vertical_threshold = min_vertical_threshold
        self.min_face_threshold = min_face_threshold
        self.reset()

    def reset(self) -> None:
        self.samples: list[np.ndarray] = []
        self.openness_samples: list[float] = []
        self.baseline: np.ndarray | None = None
        self.thresholds: np.ndarray | None = None
        self.baseline_openness: float | None = None

    @property
    def ready(self) -> bool:
        return (
            self.baseline is not None
            and self.thresholds is not None
            and self.baseline_openness is not None
        )

    @property
    def collected_frames(self) -> int:
        return len(self.samples)

    def observe(self, features: GazeFeatures) -> None:
        if self.ready:
            return
        sample = np.array(
            [features.average_horizontal, features.average_vertical, features.face_horizontal],
            dtype=np.float32,
        )
        self.samples.append(sample)
        self.openness_samples.append(float(features.average_openness))
        if len(self.samples) < self.target_frames:
            return

        samples = np.vstack(self.samples)
        std = samples.std(axis=0)
        self.baseline = samples.mean(axis=0)
        self.thresholds = np.array(
            [
                max(self.min_horizontal_threshold, float(std[0] * 3.0 + 0.02)),
                max(self.min_vertical_threshold, float(std[1] * 3.0 + 0.02)),
                max(self.min_face_threshold, float(std[2] * 3.0 + 0.015)),
            ],
            dtype=np.float32,
        )
        self.baseline_openness = max(float(np.mean(self.openness_samples)), 1e-3)

    def update_when_stable(self, features: GazeFeatures, alpha: float = 0.02) -> None:
        if not self.ready:
            return
        new_sample = np.array(
            [features.average_horizontal, features.average_vertical, features.face_horizontal],
            dtype=np.float32,
        )
        self.baseline = ((1.0 - alpha) * self.baseline) + (alpha * new_sample)
        self.baseline_openness = ((1.0 - alpha) * float(self.baseline_openness)) + (
            alpha * float(features.average_openness)
        )

    def decide(self, features: GazeFeatures) -> GazeDecision:
        if not self.ready:
            raise RuntimeError("Calibration is not ready.")

        current = np.array(
            [features.average_horizontal, features.average_vertical, features.face_horizontal],
            dtype=np.float32,
        )
        deviations = np.abs(current - self.baseline)
        ratios = deviations / np.maximum(self.thresholds, 1e-6)
        dominant_ratio = float(np.max(ratios))
        focus_score = float(np.clip(1.0 - min(dominant_ratio, 2.0) / 2.0, 0.0, 1.0))
        looking_at_screen = dominant_ratio <= 1.0

        return GazeDecision(
            looking_at_screen=looking_at_screen,
            focus_score=focus_score,
            horizontal_deviation=float(deviations[0]),
            vertical_deviation=float(deviations[1]),
            face_deviation=float(deviations[2]),
            dominant_ratio=dominant_ratio,
        )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Live screen-attention tracker using iris landmarks and a quick personal calibration."
    )
    parser.add_argument(
        "--model-path",
        type=Path,
        default=None,
        help="Legacy option from the older KNN version. It is ignored in this landmark-based tracker.",
    )
    parser.add_argument("--camera-index", type=int, default=0, help="Webcam index for OpenCV.")
    parser.add_argument("--frame-width", type=int, default=1280, help="Requested capture width.")
    parser.add_argument("--frame-height", type=int, default=720, help="Requested capture height.")
    parser.add_argument(
        "--landmarker-model",
        type=Path,
        default=Path("models/face_landmarker_v2.task"),
        help="Path to the MediaPipe Face Landmarker task model. It is auto-downloaded if missing.",
    )
    parser.add_argument(
        "--smoothing-window",
        type=int,
        default=6,
        help="Number of recent decision frames to smooth together.",
    )
    parser.add_argument(
        "--calibration-frames",
        type=int,
        default=35,
        help="Number of good frames to capture while you look at the screen center.",
    )
    parser.add_argument(
        "--min-horizontal-threshold",
        type=float,
        default=0.10,
        help="Smallest allowed horizontal iris-ratio drift before marking away.",
    )
    parser.add_argument(
        "--min-vertical-threshold",
        type=float,
        default=0.12,
        help="Smallest allowed vertical iris-ratio drift before marking away.",
    )
    parser.add_argument(
        "--min-face-threshold",
        type=float,
        default=0.07,
        help="Smallest allowed head-turn drift before marking away.",
    )
    parser.add_argument(
        "--min-eye-open-ratio",
        type=float,
        default=0.18,
        help="Legacy absolute eye-open ratio floor used before calibration is ready.",
    )
    parser.add_argument(
        "--closed-eye-ratio-threshold",
        type=float,
        default=0.60,
        help="Relative eye openness threshold versus your calibrated baseline for considering the eyes closed.",
    )
    parser.add_argument(
        "--blink-max-duration-sec",
        type=float,
        default=0.45,
        help="Eye closure shorter than this is treated as a blink rather than loss of focus.",
    )
    parser.add_argument(
        "--eyes-closed-not-focused-sec",
        type=float,
        default=0.90,
        help="Continuous eye closure longer than this marks the user as not focused.",
    )
    parser.add_argument(
        "--work-focus-threshold",
        type=float,
        default=0.58,
        help="Minimum smoothed work-focus score required to count as focused.",
    )
    parser.add_argument(
        "--min-screen-attention-score",
        type=float,
        default=0.45,
        help="Minimum smoothed screen-attention score required to count as focused.",
    )
    parser.add_argument(
        "--focus-hold-sec",
        type=float,
        default=0.45,
        help="How long focused evidence must persist before switching to focused.",
    )
    parser.add_argument(
        "--unfocus-hold-sec",
        type=float,
        default=0.80,
        help="How long unfocused evidence must persist before switching to not focused.",
    )
    parser.add_argument(
        "--mirror-preview",
        action="store_true",
        help="Flip the preview horizontally so it behaves like a mirror.",
    )
    parser.add_argument(
        "--beep-on-away",
        action="store_true",
        default=False,
        help="Play a short alert when the tracker decides you are not looking at the screen.",
    )
    parser.add_argument(
        "--beep-frequency",
        type=int,
        default=1200,
        help="Alert beep frequency in Hz.",
    )
    parser.add_argument(
        "--beep-duration-ms",
        type=int,
        default=180,
        help="Alert beep duration in milliseconds.",
    )
    parser.add_argument(
        "--beep-cooldown-sec",
        type=float,
        default=1.0,
        help="Minimum time between away-alert beeps.",
    )
    parser.add_argument(
        "--no-beep",
        dest="beep_on_away",
        action="store_false",
        help="Disable the away-alert beep.",
    )
    parser.add_argument(
        "--log-events",
        action="store_true",
        default=True,
        help="Print and save away/return events while tracking.",
    )
    parser.add_argument(
        "--no-log-events",
        dest="log_events",
        action="store_false",
        help="Disable event logging.",
    )
    parser.add_argument(
        "--log-file",
        type=Path,
        default=Path("tracker_events.log"),
        help="File used for tracker event logs.",
    )
    parser.add_argument(
        "--log-cooldown-sec",
        type=float,
        default=1.0,
        help="Minimum time between repeated away log entries while still off-screen.",
    )
    parser.add_argument(
        "--attention-span-threshold",
        type=float,
        default=0.2,
        help="Log a low-attention event when the smoothed focus score stays below this value.",
    )
    parser.add_argument(
        "--attention-span-duration-sec",
        type=float,
        default=3.0,
        help="Minimum continuous time below the attention threshold before logging an event.",
    )
    return parser.parse_args()


def landmark_to_point(landmarks, idx: int, width: int, height: int) -> np.ndarray:
    landmark = landmarks[idx]
    return np.array([landmark.x * width, landmark.y * height], dtype=np.float32)


def points_from_indices(landmarks, indices: list[int], width: int, height: int) -> np.ndarray:
    return np.array([landmark_to_point(landmarks, idx, width, height) for idx in indices], dtype=np.float32)


def measure_eye(
    landmarks,
    iris_indices: list[int],
    corner_indices: tuple[int, int],
    lid_indices: tuple[int, int],
    width: int,
    height: int,
) -> EyeMeasurement:
    iris_points = points_from_indices(landmarks, iris_indices, width, height)
    iris_center = iris_points.mean(axis=0)

    corner_a = landmark_to_point(landmarks, corner_indices[0], width, height)
    corner_b = landmark_to_point(landmarks, corner_indices[1], width, height)
    left_x = float(min(corner_a[0], corner_b[0]))
    right_x = float(max(corner_a[0], corner_b[0]))
    eye_width = max(right_x - left_x, 1.0)

    upper_lid = landmark_to_point(landmarks, lid_indices[0], width, height)
    lower_lid = landmark_to_point(landmarks, lid_indices[1], width, height)
    top_y = float(min(upper_lid[1], lower_lid[1]))
    bottom_y = float(max(upper_lid[1], lower_lid[1]))
    eye_height = max(bottom_y - top_y, 1.0)

    horizontal_ratio = float((iris_center[0] - left_x) / eye_width)
    vertical_ratio = float((iris_center[1] - top_y) / eye_height)
    openness = float(eye_height / eye_width)

    return EyeMeasurement(
        iris_center=iris_center,
        horizontal_ratio=horizontal_ratio,
        vertical_ratio=vertical_ratio,
        openness=openness,
        eye_width=eye_width,
    )


def extract_gaze_features(landmarks, frame_shape: tuple[int, int, int]) -> GazeFeatures:
    height, width = frame_shape[:2]

    right_eye = measure_eye(
        landmarks,
        iris_indices=RIGHT_IRIS,
        corner_indices=RIGHT_EYE_CORNERS,
        lid_indices=RIGHT_EYE_LIDS,
        width=width,
        height=height,
    )
    left_eye = measure_eye(
        landmarks,
        iris_indices=LEFT_IRIS,
        corner_indices=LEFT_EYE_CORNERS,
        lid_indices=LEFT_EYE_LIDS,
        width=width,
        height=height,
    )

    nose_tip = landmark_to_point(landmarks, NOSE_TIP, width, height)
    cheek_left = landmark_to_point(landmarks, LEFT_CHEEK, width, height)
    cheek_right = landmark_to_point(landmarks, RIGHT_CHEEK, width, height)
    face_left = float(min(cheek_left[0], cheek_right[0]))
    face_right = float(max(cheek_left[0], cheek_right[0]))
    face_width = max(face_right - face_left, 1.0)
    face_horizontal = float((nose_tip[0] - face_left) / face_width)

    average_horizontal = float((left_eye.horizontal_ratio + right_eye.horizontal_ratio) / 2.0)
    average_vertical = float((left_eye.vertical_ratio + right_eye.vertical_ratio) / 2.0)
    average_openness = float((left_eye.openness + right_eye.openness) / 2.0)

    return GazeFeatures(
        left_horizontal=left_eye.horizontal_ratio,
        right_horizontal=right_eye.horizontal_ratio,
        left_vertical=left_eye.vertical_ratio,
        right_vertical=right_eye.vertical_ratio,
        average_horizontal=average_horizontal,
        average_vertical=average_vertical,
        face_horizontal=face_horizontal,
        average_openness=average_openness,
    )


def compute_face_box(landmarks, frame_shape: tuple[int, int, int]) -> tuple[int, int, int, int]:
    height, width = frame_shape[:2]
    all_points = np.array(
        [[landmark.x * width, landmark.y * height] for landmark in landmarks],
        dtype=np.float32,
    )
    min_xy = np.maximum(np.floor(all_points.min(axis=0) - 10), 0).astype(int)
    max_xy = np.minimum(
        np.ceil(all_points.max(axis=0) + 10),
        np.array([width - 1, height - 1]),
    ).astype(int)
    x1, y1 = min_xy.tolist()
    x2, y2 = max_xy.tolist()
    return x1, y1, max(1, x2 - x1), max(1, y2 - y1)


def draw_landmark_groups(frame: np.ndarray, landmarks) -> None:
    height, width = frame.shape[:2]

    for indices, color in (
        (RIGHT_EYE_OUTLINE, (255, 255, 255)),
        (LEFT_EYE_OUTLINE, (255, 255, 255)),
    ):
        eye_points = points_from_indices(landmarks, indices, width, height).astype(np.int32)
        cv2.polylines(frame, [eye_points], True, color, 1, cv2.LINE_AA)

    for iris_indices, color in (
        (RIGHT_IRIS, (0, 255, 255)),
        (LEFT_IRIS, (0, 255, 255)),
    ):
        iris_points = points_from_indices(landmarks, iris_indices, width, height)
        center, radius = cv2.minEnclosingCircle(iris_points)
        cx, cy = int(center[0]), int(center[1])
        cv2.circle(frame, (cx, cy), max(2, int(radius)), color, 2, cv2.LINE_AA)
        cv2.circle(frame, (cx, cy), 2, color, -1, cv2.LINE_AA)


def draw_text_block(
    frame: np.ndarray, lines: list[str], top_left: tuple[int, int], color: tuple[int, int, int]
) -> None:
    x, y = top_left
    for idx, line in enumerate(lines):
        baseline_y = y + (idx * 26)
        cv2.putText(
            frame,
            line,
            (x, baseline_y),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.67,
            (0, 0, 0),
            4,
            cv2.LINE_AA,
        )
        cv2.putText(
            frame,
            line,
            (x, baseline_y),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.67,
            color,
            2,
            cv2.LINE_AA,
        )


def truncate_text(text: str, limit: int = 58) -> str:
    if len(text) <= limit:
        return text
    return text[-limit:]


def clamp_score(value: float) -> float:
    return float(np.clip(value, 0.0, 1.0))


def compute_eye_openness_score(openness_ratio: float, closed_ratio_threshold: float) -> float:
    span = max(1.0 - closed_ratio_threshold, 1e-6)
    return clamp_score((openness_ratio - closed_ratio_threshold) / span)


def ensure_landmarker_model(model_path: Path) -> Path:
    if model_path.exists():
        return model_path

    model_path.parent.mkdir(parents=True, exist_ok=True)
    print(f"Downloading face landmarker model to: {model_path}")
    urllib.request.urlretrieve(FACE_LANDMARKER_MODEL_URL, model_path)
    return model_path


def main() -> None:
    args = parse_args()
    model_path = ensure_landmarker_model(args.landmarker_model)

    away_alert = AwayAlert(
        enabled=args.beep_on_away,
        frequency=args.beep_frequency,
        duration_ms=args.beep_duration_ms,
        cooldown_sec=args.beep_cooldown_sec,
    )
    event_logger = EventLogger(
        enabled=args.log_events,
        log_path=args.log_file,
        cooldown_sec=args.log_cooldown_sec,
    )
    low_attention_monitor = LowAttentionMonitor(
        threshold=args.attention_span_threshold,
        min_duration_sec=args.attention_span_duration_sec,
    )
    eye_closure_monitor = EyeClosureMonitor(
        closed_ratio_threshold=args.closed_eye_ratio_threshold,
        blink_max_duration_sec=args.blink_max_duration_sec,
    )
    focus_state_machine = FocusStateMachine(
        focus_hold_sec=args.focus_hold_sec,
        unfocus_hold_sec=args.unfocus_hold_sec,
    )
    calibration = CalibrationModel(
        target_frames=args.calibration_frames,
        min_horizontal_threshold=args.min_horizontal_threshold,
        min_vertical_threshold=args.min_vertical_threshold,
        min_face_threshold=args.min_face_threshold,
    )

    base_options = mp.tasks.BaseOptions(model_asset_path=str(model_path))
    vision = mp.tasks.vision
    landmarker_options = vision.FaceLandmarkerOptions(
        base_options=base_options,
        running_mode=vision.RunningMode.VIDEO,
        num_faces=1,
        min_face_detection_confidence=0.5,
        min_face_presence_confidence=0.5,
        min_tracking_confidence=0.5,
    )
    face_landmarker = vision.FaceLandmarker.create_from_options(landmarker_options)

    camera = cv2.VideoCapture(args.camera_index)
    camera.set(cv2.CAP_PROP_FRAME_WIDTH, args.frame_width)
    camera.set(cv2.CAP_PROP_FRAME_HEIGHT, args.frame_height)

    if not camera.isOpened():
        raise RuntimeError(
            f"Could not open webcam index {args.camera_index}. Try a different --camera-index."
        )

    print("Landmark-based tracker ready.")
    print(f"Using landmarker model: {model_path.resolve()}")
    if args.log_events:
        print(f"Logging events to: {args.log_file.resolve()}")
        print(
            "Low-attention logging: "
            f"work_focus < {args.attention_span_threshold:.2f} for >= {args.attention_span_duration_sec:.1f}s"
        )
    print("Look at the screen for calibration, then press Q to quit or C to recalibrate.")

    screen_attention_history: deque[float] = deque(maxlen=max(1, args.smoothing_window))
    eye_openness_score_history: deque[float] = deque(maxlen=max(1, args.smoothing_window))
    work_focus_history: deque[float] = deque(maxlen=max(1, args.smoothing_window))
    horizontal_history: deque[float] = deque(maxlen=max(1, args.smoothing_window))
    vertical_history: deque[float] = deque(maxlen=max(1, args.smoothing_window))
    face_history: deque[float] = deque(maxlen=max(1, args.smoothing_window))

    while True:
        ok, frame = camera.read()
        if not ok:
            print("Failed to read a frame from the webcam.")
            break

        frame = cv2.flip(frame, 1) if args.mirror_preview else frame
        rgb_frame = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
        mp_image = mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb_frame)
        frame_time_sec = time.monotonic()
        frame_timestamp_ms = int(frame_time_sec * 1000)
        results = face_landmarker.detect_for_video(mp_image, frame_timestamp_ms)

        status_lines = [
            "Status: no face detected",
            f"Not focused logs: {event_logger.not_focused_event_count}",
            f"Low attention logs: {event_logger.low_attention_event_count}",
            "Press C to recalibrate, Q to quit",
        ]
        status_color = (0, 180, 255)

        if results.face_landmarks:
            landmarks = results.face_landmarks[0]
            face_box = compute_face_box(landmarks, frame.shape)
            x, y, w, h = face_box
            cv2.rectangle(frame, (x, y), (x + w, y + h), (255, 200, 0), 2)
            draw_landmark_groups(frame, landmarks)

            features = extract_gaze_features(landmarks, frame.shape)

            if not calibration.ready:
                if features.average_openness < args.min_eye_open_ratio:
                    status_lines = [
                        "Status: calibrating, open eyes a bit more",
                        f"Eye openness: {features.average_openness:.2f}",
                        f"Not focused logs: {event_logger.not_focused_event_count}",
                        f"Low attention logs: {event_logger.low_attention_event_count}",
                        "Press C to restart calibration",
                    ]
                else:
                    calibration.observe(features)
                    progress = calibration.collected_frames / calibration.target_frames
                    bar_width = 240
                    filled = int(bar_width * min(progress, 1.0))
                    cv2.rectangle(frame, (20, frame.shape[0] - 46), (20 + bar_width, frame.shape[0] - 20), (255, 255, 255), 2)
                    cv2.rectangle(frame, (22, frame.shape[0] - 44), (22 + filled, frame.shape[0] - 22), (0, 220, 120), -1)
                    status_color = (0, 220, 120)
                    status_lines = [
                        "Status: calibrating, look at the screen center",
                        f"Calibration: {calibration.collected_frames}/{calibration.target_frames}",
                        f"Eye openness: {features.average_openness:.2f}",
                        "Press C to restart calibration",
                    ]
                low_attention_monitor.reset()
                eye_closure_monitor.reset()
            else:
                decision = calibration.decide(features)
                baseline_openness = max(float(calibration.baseline_openness), 1e-3)
                openness_ratio = float(features.average_openness / baseline_openness)
                eye_openness_score = compute_eye_openness_score(
                    openness_ratio,
                    args.closed_eye_ratio_threshold,
                )
                is_blinking, closed_duration_sec = eye_closure_monitor.update(
                    openness_ratio,
                    frame_time_sec,
                )
                eyes_closed_too_long = closed_duration_sec >= args.eyes_closed_not_focused_sec
                screen_attention_score = decision.focus_score
                raw_work_focus_score = clamp_score((0.72 * screen_attention_score) + (0.28 * eye_openness_score))

                if not is_blinking:
                    screen_attention_history.append(screen_attention_score)
                    eye_openness_score_history.append(eye_openness_score)
                    work_focus_history.append(raw_work_focus_score)
                horizontal_history.append(decision.horizontal_deviation)
                vertical_history.append(decision.vertical_deviation)
                face_history.append(decision.face_deviation)

                smoothed_screen_attention = (
                    float(np.mean(screen_attention_history))
                    if screen_attention_history
                    else screen_attention_score
                )
                smoothed_eye_openness = (
                    float(np.mean(eye_openness_score_history))
                    if eye_openness_score_history
                    else eye_openness_score
                )
                smoothed_work_focus = (
                    float(np.mean(work_focus_history))
                    if work_focus_history
                    else raw_work_focus_score
                )
                smoothed_horizontal = float(np.mean(horizontal_history))
                smoothed_vertical = float(np.mean(vertical_history))
                smoothed_face = float(np.mean(face_history))

                if is_blinking and not eyes_closed_too_long:
                    low_attention_monitor.reset()
                    candidate_state = None
                    focus_reason = "blink"
                else:
                    low_attention_monitor.update(
                        focus_score=smoothed_work_focus,
                        now=frame_time_sec,
                        event_logger=event_logger,
                    )
                    if eyes_closed_too_long:
                        candidate_state = "not_focused"
                        focus_reason = "eyes_closed"
                    else:
                        focused_candidate = (
                            smoothed_work_focus >= args.work_focus_threshold
                            and smoothed_screen_attention >= args.min_screen_attention_score
                        )
                        candidate_state = "focused" if focused_candidate else "not_focused"
                        if focused_candidate:
                            focus_reason = "engaged"
                        elif smoothed_screen_attention < args.min_screen_attention_score:
                            focus_reason = "looking_away"
                        elif smoothed_eye_openness < 0.45:
                            focus_reason = "low_eye_openness"
                        else:
                            focus_reason = "low_work_focus"

                stable_state, state_changed = focus_state_machine.update(candidate_state, frame_time_sec)

                if stable_state == "focused" and candidate_state != "not_focused":
                    calibration.update_when_stable(features)

                if state_changed:
                    if stable_state == "focused":
                        event_logger.log_state("focused", smoothed_work_focus, focus_reason)
                    elif stable_state == "not_focused":
                        away_alert.trigger()
                        event_logger.log_state("not_focused", smoothed_work_focus, focus_reason)
                elif stable_state == "not_focused":
                    away_alert.trigger()

                if stable_state == "focused":
                    status_color = (40, 200, 60)
                    status_text = "FOCUSED ON WORK"
                elif stable_state == "not_focused":
                    status_color = (30, 30, 220)
                    status_text = "NOT FOCUSED ON WORK"
                else:
                    status_color = (0, 180, 255)
                    status_text = "ANALYZING"

                if is_blinking and stable_state == "focused":
                    status_text = "FOCUSED ON WORK (BLINK)"
                    status_color = (255, 190, 40)
                elif eyes_closed_too_long:
                    status_text = "NOT FOCUSED ON WORK (EYES CLOSED)"
                    status_color = (0, 90, 255)

                thresholds = calibration.thresholds if calibration.thresholds is not None else np.zeros(3, dtype=np.float32)
                status_lines = [
                    f"Status: {status_text}",
                    f"Work focus score: {smoothed_work_focus:.2f}",
                    f"Screen attention: {smoothed_screen_attention:.2f}",
                    f"Eye openness score: {smoothed_eye_openness:.2f}",
                    f"Eye openness ratio: {openness_ratio:.2f}x",
                    f"Closed-eye timer: {closed_duration_sec:.1f}s",
                    f"Dev H/V/F: {smoothed_horizontal:.3f} / {smoothed_vertical:.3f} / {smoothed_face:.3f}",
                    f"Thr H/V/F: {thresholds[0]:.3f} / {thresholds[1]:.3f} / {thresholds[2]:.3f}",
                    f"Focus reason: {focus_reason}",
                    f"Not focused logs: {event_logger.not_focused_event_count}",
                    f"Low attention logs: {event_logger.low_attention_event_count}",
                    f"Low focus timer: {low_attention_monitor.current_duration_sec:.1f}s",
                    "Press C to recalibrate, Q to quit",
                ]
        else:
            screen_attention_history.clear()
            eye_openness_score_history.clear()
            work_focus_history.clear()
            horizontal_history.clear()
            vertical_history.clear()
            face_history.clear()
            low_attention_monitor.reset()
            eye_closure_monitor.reset()
            if calibration.ready:
                stable_state, state_changed = focus_state_machine.update("not_focused", frame_time_sec)
                if state_changed and stable_state == "not_focused":
                    event_logger.log_state("not_focused", reason="no_face")

        status_lines.append(
            f"Last log: {truncate_text(event_logger.last_message) if event_logger.enabled else 'logging off'}"
        )
        draw_text_block(frame, status_lines, (20, 35), status_color)

        cv2.imshow("Live Eye Screen Tracker", frame)
        key = cv2.waitKey(1) & 0xFF
        if key in (ord("q"), ord("Q")):
            break
        if key in (ord("c"), ord("C"), ord("r"), ord("R")):
            calibration.reset()
            focus_state_machine.reset()
            screen_attention_history.clear()
            eye_openness_score_history.clear()
            work_focus_history.clear()
            horizontal_history.clear()
            vertical_history.clear()
            face_history.clear()
            low_attention_monitor.reset()
            eye_closure_monitor.reset()
            event_logger.log_state("recalibrated")

    face_landmarker.close()
    camera.release()
    cv2.destroyAllWindows()


if __name__ == "__main__":
    main()
