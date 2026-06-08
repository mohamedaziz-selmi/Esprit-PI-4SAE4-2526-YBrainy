from __future__ import annotations

import argparse
import base64
import hashlib
import json
import math
import urllib.request
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Callable

import cv2
import mediapipe as mp
import numpy as np
from mediapipe.tasks.python import vision
from mediapipe.tasks.python.core.base_options import BaseOptions


ROOT = Path(__file__).resolve().parent
DEFAULT_MODEL_PATH = ROOT / "face_landmarker.task"
DEFAULT_SELFIE_MULTICLASS_MODEL_PATH = ROOT / "selfie_multiclass_256x256.tflite"
DEFAULT_HAIR_SEGMENTER_MODEL_PATH = ROOT / "hair_segmenter.tflite"
DEFAULT_OUTPUT_DIR = ROOT / "avatar_outputs"
EPS = 1e-6

MODEL_URLS = {
    DEFAULT_MODEL_PATH: (
        "https://storage.googleapis.com/mediapipe-models/"
        "face_landmarker/face_landmarker/float16/1/face_landmarker.task"
    ),
    DEFAULT_SELFIE_MULTICLASS_MODEL_PATH: (
        "https://storage.googleapis.com/mediapipe-models/"
        "image_segmenter/selfie_multiclass_256x256/float32/latest/"
        "selfie_multiclass_256x256.tflite"
    ),
    DEFAULT_HAIR_SEGMENTER_MODEL_PATH: (
        "https://storage.googleapis.com/mediapipe-models/"
        "image_segmenter/hair_segmenter/float32/latest/hair_segmenter.tflite"
    ),
}

FACE_CONTOUR_IDX = [
    10, 338, 297, 332, 284, 251, 389, 356, 454, 323, 361, 288, 397, 365, 379,
    378, 400, 377, 152, 148, 176, 149, 150, 136, 172, 58, 132, 93, 234, 127,
    162, 21, 54, 103, 67, 109,
]
LEFT_BROW_IDX = [70, 63, 105, 66, 107]
RIGHT_BROW_IDX = [336, 296, 334, 293, 300]
LEFT_EYE_IDX = [33, 246, 161, 160, 159, 158, 157, 173, 133, 155, 154, 153, 145, 144, 163, 7]
RIGHT_EYE_IDX = [362, 398, 384, 385, 386, 387, 388, 466, 263, 249, 390, 373, 374, 380, 381, 382]
LEFT_UPPER_LID_IDX = [33, 246, 161, 160, 159, 158, 157, 173, 133]
RIGHT_UPPER_LID_IDX = [362, 398, 384, 385, 386, 387, 388, 466, 263]
LEFT_IRIS_IDX = [468, 469, 470, 471, 472]
RIGHT_IRIS_IDX = [473, 474, 475, 476, 477]
NOSE_BRIDGE_IDX = [168, 6, 197, 195, 5, 4]
NOSE_BASE_IDX = [98, 97, 2, 326, 327]
OUTER_LIP_IDX = [61, 146, 91, 181, 84, 17, 314, 405, 321, 375, 291, 409, 270, 269, 267, 0, 37, 39, 40, 185]
INNER_LIP_IDX = [78, 95, 88, 178, 87, 14, 317, 402, 318, 324, 308, 415, 310, 311, 312, 13, 82, 81, 80, 191]
@dataclass
class AvatarMeasurements:
    face_shape_index: float
    jaw_width_ratio: float
    chin_projection_ratio: float
    eye_open_ratio: float
    eye_spacing_ratio: float
    eye_tilt_ratio: float
    eyebrow_arch_ratio: float
    nose_width_ratio: float
    nose_bridge_ratio: float
    mouth_width_ratio: float
    lip_fullness_ratio: float
    mouth_open_ratio: float
    smile_ratio: float


@dataclass
class AvatarPresets:
    face_shape: str
    jaw: str
    eyes: str
    eye_spacing: str
    brows: str
    nose: str
    mouth: str
    expression: str


@dataclass
class AvatarPalette:
    background: str
    background_accent: str
    skin: str
    skin_light: str
    skin_shadow: str
    skin_deep_shadow: str
    hair: str
    hair_shadow: str
    hair_highlight: str
    stroke: str
    lip: str
    lip_dark: str
    lip_highlight: str
    shirt: str
    shirt_shadow: str
    shirt_highlight: str
    iris: str
    iris_dark: str
    eye_white: str
    highlight: str
    shadow: str


@dataclass
class SegmentationData:
    face_mask: np.ndarray
    body_skin_mask: np.ndarray
    clothes_mask: np.ndarray
    hair_mask: np.ndarray
    head_mask: np.ndarray
    neck_mask: np.ndarray
    front_hair_mask: np.ndarray
    crop_bbox: tuple[int, int, int, int]
    head_bbox: tuple[int, int, int, int]
    head_contour: list[tuple[float, float]]
    hair_contour: list[tuple[float, float]]
    face_contour: list[tuple[float, float]]
    clothes_contour: list[tuple[float, float]]
    neck_contour: list[tuple[float, float]]
    front_hair_contour: list[tuple[float, float]]
    multiclass_mask: np.ndarray


def clamp(value: float, low: float, high: float) -> float:
    return max(low, min(high, value))


def ensure_model(model_path: Path) -> None:
    if model_path.exists():
        return
    url = MODEL_URLS.get(model_path)
    if url is None:
        raise FileNotFoundError(f"No download URL configured for missing model: {model_path}")
    urllib.request.urlretrieve(url, model_path)


def ensure_models(model_paths: list[Path]) -> None:
    for model_path in model_paths:
        ensure_model(model_path)


def distance(landmarks: list[tuple[float, float]], a: int, b: int) -> float:
    ax, ay = landmarks[a]
    bx, by = landmarks[b]
    return math.hypot(ax - bx, ay - by)


def rgb_to_hex(rgb: tuple[int, int, int]) -> str:
    return "#{:02x}{:02x}{:02x}".format(*rgb)


def hex_to_rgb(color: str) -> tuple[int, int, int]:
    cleaned = color.lstrip("#")
    if len(cleaned) != 6:
        raise ValueError(f"Expected a 6-digit hex color, got: {color}")
    return tuple(int(cleaned[offset:offset + 2], 16) for offset in (0, 2, 4))


def blend_rgb(a: tuple[int, int, int], b: tuple[int, int, int], amount: float) -> tuple[int, int, int]:
    t = clamp(amount, 0.0, 1.0)
    return tuple(int(round((1.0 - t) * av + t * bv)) for av, bv in zip(a, b))


def scale_rgb(rgb: tuple[int, int, int], factor: float) -> tuple[int, int, int]:
    return tuple(int(clamp(round(channel * factor), 0, 255)) for channel in rgb)


def bounded_region(
    image: np.ndarray,
    x0: float,
    y0: float,
    x1: float,
    y1: float,
) -> np.ndarray | None:
    h, w = image.shape[:2]
    left = int(clamp(round(x0), 0, w - 1))
    top = int(clamp(round(y0), 0, h - 1))
    right = int(clamp(round(x1), left + 1, w))
    bottom = int(clamp(round(y1), top + 1, h))
    if right <= left or bottom <= top:
        return None
    return image[top:bottom, left:right]


def masked_region_pixels(
    image: np.ndarray,
    mask: np.ndarray,
    x0: float,
    y0: float,
    x1: float,
    y1: float,
) -> np.ndarray | None:
    h, w = image.shape[:2]
    left = int(clamp(round(x0), 0, w - 1))
    top = int(clamp(round(y0), 0, h - 1))
    right = int(clamp(round(x1), left + 1, w))
    bottom = int(clamp(round(y1), top + 1, h))
    if right <= left or bottom <= top:
        return None
    region = image[top:bottom, left:right]
    region_mask = mask[top:bottom, left:right] > 0
    pixels = region[region_mask]
    return pixels if pixels.size else None


def trim_luminance_outliers(
    pixels: np.ndarray | None,
    low_q: float = 0.08,
    high_q: float = 0.92,
) -> np.ndarray | None:
    if pixels is None or pixels.size == 0 or len(pixels) < 12:
        return pixels
    luminance = np.mean(pixels, axis=1)
    lo = float(np.quantile(luminance, low_q))
    hi = float(np.quantile(luminance, high_q))
    trimmed = pixels[(luminance >= lo) & (luminance <= hi)]
    return trimmed if trimmed.size else pixels


def median_rgb(region: np.ndarray | None, fallback: tuple[int, int, int]) -> tuple[int, int, int]:
    if region is None or region.size == 0:
        return fallback
    median = np.median(region.reshape(-1, 3), axis=0)
    return tuple(int(clamp(round(value), 0, 255)) for value in median)


def sample_circular_region(
    image: np.ndarray,
    center: tuple[float, float],
    radius: float,
) -> np.ndarray | None:
    h, w = image.shape[:2]
    if radius <= 0:
        return None
    x, y = center
    x0 = int(clamp(math.floor(x - radius), 0, w - 1))
    y0 = int(clamp(math.floor(y - radius), 0, h - 1))
    x1 = int(clamp(math.ceil(x + radius) + 1, x0 + 1, w))
    y1 = int(clamp(math.ceil(y + radius) + 1, y0 + 1, h))
    if x1 <= x0 or y1 <= y0:
        return None

    region = image[y0:y1, x0:x1]
    yy, xx = np.ogrid[y0:y1, x0:x1]
    mask = ((xx - x) ** 2 + (yy - y) ** 2) <= radius ** 2
    pixels = region[mask]
    return pixels if pixels.size else None


def average_xy(points: list[tuple[float, float]]) -> tuple[float, float]:
    return (
        sum(point[0] for point in points) / len(points),
        sum(point[1] for point in points) / len(points),
    )


def midpoint(a: tuple[float, float], b: tuple[float, float]) -> tuple[float, float]:
    return ((a[0] + b[0]) / 2.0, (a[1] + b[1]) / 2.0)


def lerp_point(a: tuple[float, float], b: tuple[float, float], t: float) -> tuple[float, float]:
    amount = clamp(t, 0.0, 1.0)
    return ((1.0 - amount) * a[0] + amount * b[0], (1.0 - amount) * a[1] + amount * b[1])


def select_points(landmarks: list[tuple[float, float]], indices: list[int]) -> list[tuple[float, float]]:
    return [landmarks[index] for index in indices]


def landmark_to_pixel(landmark: tuple[float, float], width: int, height: int) -> tuple[float, float]:
    return landmark[0] * width, landmark[1] * height


def points_to_dicts(points: list[tuple[float, float]], precision: int = 2) -> list[dict[str, float]]:
    return [{"x": round(x, precision), "y": round(y, precision)} for x, y in points]


def point_bounds(points: list[tuple[float, float]]) -> tuple[float, float, float, float]:
    xs = [point[0] for point in points]
    ys = [point[1] for point in points]
    return min(xs), min(ys), max(xs), max(ys)


def image_data_uri(image_rgb: np.ndarray, quality: int = 90, max_dim: int = 720) -> str:
    work = image_rgb
    h, w = work.shape[:2]
    longest = max(h, w)
    if longest > max_dim:
        scale = max_dim / float(longest)
        resized_w = max(1, int(round(w * scale)))
        resized_h = max(1, int(round(h * scale)))
        work = cv2.resize(work, (resized_w, resized_h), interpolation=cv2.INTER_AREA)

    if work.ndim != 3 or work.shape[2] not in (3, 4):
        raise ValueError("Embedded image data must be RGB or RGBA.")

    if work.shape[2] == 4:
        bgra = cv2.cvtColor(work, cv2.COLOR_RGBA2BGRA)
        success, encoded = cv2.imencode(".png", bgra, [int(cv2.IMWRITE_PNG_COMPRESSION), 3])
        mime_type = "image/png"
    else:
        bgr = cv2.cvtColor(work, cv2.COLOR_RGB2BGR)
        success, encoded = cv2.imencode(".jpg", bgr, [int(cv2.IMWRITE_JPEG_QUALITY), quality])
        mime_type = "image/jpeg"

    if not success:
        raise RuntimeError("Could not encode embedded image data.")
    payload = base64.b64encode(encoded.tobytes()).decode("ascii")
    return f"data:{mime_type};base64,{payload}"


def stylize_portrait_crop(image_rgb: np.ndarray) -> np.ndarray:
    work_rgb = image_rgb
    h, w = work_rgb.shape[:2]
    longest = max(h, w)
    if longest < 720:
        upscale = 720.0 / float(longest)
        resized_w = max(1, int(round(w * upscale)))
        resized_h = max(1, int(round(h * upscale)))
        work_rgb = cv2.resize(work_rgb, (resized_w, resized_h), interpolation=cv2.INTER_CUBIC)

    bgr = cv2.cvtColor(work_rgb, cv2.COLOR_RGB2BGR)
    smooth = cv2.bilateralFilter(bgr, d=9, sigmaColor=70, sigmaSpace=70)
    smooth = cv2.bilateralFilter(smooth, d=7, sigmaColor=48, sigmaSpace=48)
    toon = cv2.stylization(smooth, sigma_s=70, sigma_r=0.32)
    base = cv2.edgePreservingFilter(smooth, flags=1, sigma_s=44, sigma_r=0.32)

    lab = cv2.cvtColor(base, cv2.COLOR_BGR2LAB)
    l_channel, a_channel, b_channel = cv2.split(lab)
    l_channel = cv2.createCLAHE(clipLimit=1.5, tileGridSize=(8, 8)).apply(l_channel)
    l_channel = np.uint8(np.clip(np.round(l_channel / 12.0) * 12.0, 0, 255))
    a_channel = np.uint8(np.clip(np.round((a_channel.astype(np.float32) - 128.0) / 10.0) * 10.0 + 128.0, 0, 255))
    b_channel = np.uint8(np.clip(np.round((b_channel.astype(np.float32) - 128.0) / 10.0) * 10.0 + 128.0, 0, 255))
    poster_bgr = cv2.cvtColor(cv2.merge([l_channel, a_channel, b_channel]), cv2.COLOR_LAB2BGR)

    blended = cv2.addWeighted(poster_bgr, 0.84, toon, 0.10, 0.0)
    blended = cv2.addWeighted(blended, 0.88, base, 0.12, 0.0)
    softened = cv2.GaussianBlur(blended, (0, 0), sigmaX=0.9, sigmaY=0.9)
    stylized_bgr = cv2.addWeighted(blended, 0.86, softened, 0.14, 0.0)
    stylized_rgb = cv2.cvtColor(stylized_bgr, cv2.COLOR_BGR2RGB)
    return stylized_rgb


def adaptive_kernel(image_shape: tuple[int, int], fraction: float, minimum: int = 3, maximum: int = 61) -> int:
    h, w = image_shape[:2]
    size = int(round(min(h, w) * fraction))
    size = max(minimum, min(maximum, size))
    if size % 2 == 0:
        size += 1
    return size


def make_binary(mask: np.ndarray) -> np.ndarray:
    return (mask > 0).astype(np.uint8)


def largest_component(mask: np.ndarray) -> np.ndarray:
    binary = make_binary(mask)
    if binary.sum() == 0:
        return binary
    num_labels, labels, stats, _ = cv2.connectedComponentsWithStats(binary, connectivity=8)
    if num_labels <= 1:
        return binary
    largest_label = 1 + int(np.argmax(stats[1:, cv2.CC_STAT_AREA]))
    return (labels == largest_label).astype(np.uint8)


def close_mask(mask: np.ndarray, kernel_size: int) -> np.ndarray:
    kernel = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (kernel_size, kernel_size))
    return cv2.morphologyEx(mask.astype(np.uint8), cv2.MORPH_CLOSE, kernel)


def open_mask(mask: np.ndarray, kernel_size: int) -> np.ndarray:
    kernel = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (kernel_size, kernel_size))
    return cv2.morphologyEx(mask.astype(np.uint8), cv2.MORPH_OPEN, kernel)


def dilate_mask(mask: np.ndarray, kernel_size: int) -> np.ndarray:
    kernel = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (kernel_size, kernel_size))
    return cv2.dilate(mask.astype(np.uint8), kernel)


def fill_holes(mask: np.ndarray) -> np.ndarray:
    filled = mask.astype(np.uint8).copy()
    h, w = filled.shape[:2]
    flood = np.zeros((h + 2, w + 2), dtype=np.uint8)
    cv2.floodFill(filled, flood, (0, 0), 255)
    inverted = cv2.bitwise_not(filled)
    return make_binary(mask | inverted)


def mask_bbox(mask: np.ndarray) -> tuple[int, int, int, int] | None:
    ys, xs = np.where(mask > 0)
    if len(xs) == 0 or len(ys) == 0:
        return None
    return int(xs.min()), int(ys.min()), int(xs.max()) + 1, int(ys.max()) + 1


def union_bbox(boxes: list[tuple[int, int, int, int] | None]) -> tuple[int, int, int, int] | None:
    valid = [box for box in boxes if box is not None]
    if not valid:
        return None
    return (
        min(box[0] for box in valid),
        min(box[1] for box in valid),
        max(box[2] for box in valid),
        max(box[3] for box in valid),
    )


def clip_bbox(box: tuple[float, float, float, float], width: int, height: int) -> tuple[int, int, int, int]:
    x0, y0, x1, y1 = box
    return (
        int(clamp(round(x0), 0, width - 1)),
        int(clamp(round(y0), 0, height - 1)),
        int(clamp(round(x1), 1, width)),
        int(clamp(round(y1), 1, height)),
    )


def resample_polyline(
    points: list[tuple[float, float]],
    num_points: int,
    closed: bool,
) -> list[tuple[float, float]]:
    if len(points) <= 2 or num_points <= 2:
        return points

    work = points + [points[0]] if closed else list(points)
    segments: list[float] = []
    cumulative = [0.0]
    for idx in range(1, len(work)):
        length = math.hypot(work[idx][0] - work[idx - 1][0], work[idx][1] - work[idx - 1][1])
        segments.append(length)
        cumulative.append(cumulative[-1] + length)
    total = cumulative[-1]
    if total < EPS:
        return points

    targets = np.linspace(0.0, total, num_points + 1 if closed else num_points)
    if closed:
        targets = targets[:-1]

    sampled: list[tuple[float, float]] = []
    seg_idx = 0
    for target in targets:
        while seg_idx < len(segments) - 1 and cumulative[seg_idx + 1] < target:
            seg_idx += 1
        start = work[seg_idx]
        end = work[seg_idx + 1]
        seg_start = cumulative[seg_idx]
        seg_length = max(segments[seg_idx], EPS)
        t = (target - seg_start) / seg_length
        sampled.append((
            (1.0 - t) * start[0] + t * end[0],
            (1.0 - t) * start[1] + t * end[1],
        ))
    return sampled


def smooth_points(
    points: list[tuple[float, float]],
    passes: int = 1,
    closed: bool = True,
) -> list[tuple[float, float]]:
    result = list(points)
    if len(result) < 3:
        return result
    for _ in range(passes):
        smoothed: list[tuple[float, float]] = []
        for idx, point in enumerate(result):
            prev_idx = idx - 1 if idx > 0 else (len(result) - 1 if closed else 0)
            next_idx = idx + 1 if idx < len(result) - 1 else (0 if closed else len(result) - 1)
            px, py = result[prev_idx]
            cx, cy = point
            nx, ny = result[next_idx]
            smoothed.append(((px + 2.0 * cx + nx) / 4.0, (py + 2.0 * cy + ny) / 4.0))
        result = smoothed
    return result


def contour_from_mask(mask: np.ndarray, num_points: int, closed: bool = True) -> list[tuple[float, float]]:
    binary = (mask.astype(np.uint8) * 255)
    contours, _ = cv2.findContours(binary, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_NONE)
    if not contours:
        return []
    contour = max(contours, key=cv2.contourArea)
    perimeter = cv2.arcLength(contour, closed)
    epsilon = max(1.0, perimeter * 0.002)
    approx = cv2.approxPolyDP(contour, epsilon, closed)
    points = [(float(point[0][0]), float(point[0][1])) for point in approx]
    if len(points) < 3:
        points = [(float(point[0][0]), float(point[0][1])) for point in contour.squeeze(1)]
    points = resample_polyline(points, num_points=num_points, closed=closed)
    points = smooth_points(points, passes=2, closed=closed)
    return points


def mask_from_rect(shape: tuple[int, int], box: tuple[int, int, int, int]) -> np.ndarray:
    mask = np.zeros(shape, dtype=np.uint8)
    x0, y0, x1, y1 = box
    mask[y0:y1, x0:x1] = 1
    return mask


def build_canvas_mapper(
    crop_bbox: tuple[int, int, int, int],
    size: int,
) -> tuple[Callable[[tuple[float, float]], tuple[float, float]], dict[str, float]]:
    x0, y0, x1, y1 = crop_bbox
    crop_w = x1 - x0
    crop_h = y1 - y0
    pad_x = size * 0.020
    pad_top = size * 0.008
    pad_bottom = size * 0.006
    avail_w = size - 2.0 * pad_x
    avail_h = size - pad_top - pad_bottom
    scale = min(avail_w / max(crop_w, 1.0), avail_h / max(crop_h, 1.0))
    draw_w = crop_w * scale
    draw_h = crop_h * scale
    offset_x = (size - draw_w) / 2.0
    offset_y = pad_top + (avail_h - draw_h) / 2.0

    def mapper(point: tuple[float, float]) -> tuple[float, float]:
        return (
            offset_x + (point[0] - x0) * scale,
            offset_y + (point[1] - y0) * scale,
        )

    info = {
        "crop_x0": float(x0),
        "crop_y0": float(y0),
        "crop_x1": float(x1),
        "crop_y1": float(y1),
        "scale": float(scale),
        "offset_x": float(offset_x),
        "offset_y": float(offset_y),
    }
    return mapper, info


def closed_smooth_path(points: list[tuple[float, float]]) -> str:
    if len(points) < 3:
        raise ValueError("A closed path requires at least three points.")
    start = midpoint(points[-1], points[0])
    commands = [f"M {start[0]:.1f} {start[1]:.1f}"]
    for idx, point in enumerate(points):
        end = midpoint(point, points[(idx + 1) % len(points)])
        commands.append(f"Q {point[0]:.1f} {point[1]:.1f} {end[0]:.1f} {end[1]:.1f}")
    commands.append("Z")
    return " ".join(commands)


def open_smooth_path(points: list[tuple[float, float]]) -> str:
    if len(points) < 2:
        raise ValueError("An open path requires at least two points.")
    if len(points) == 2:
        return f"M {points[0][0]:.1f} {points[0][1]:.1f} L {points[1][0]:.1f} {points[1][1]:.1f}"
    commands = [f"M {points[0][0]:.1f} {points[0][1]:.1f}"]
    for idx in range(1, len(points) - 1):
        end = midpoint(points[idx], points[idx + 1])
        commands.append(f"Q {points[idx][0]:.1f} {points[idx][1]:.1f} {end[0]:.1f} {end[1]:.1f}")
    commands.append(f"Q {points[-2][0]:.1f} {points[-2][1]:.1f} {points[-1][0]:.1f} {points[-1][1]:.1f}")
    return " ".join(commands)


def polygon_path(points: list[tuple[float, float]]) -> str:
    if len(points) < 3:
        raise ValueError("A polygon path requires at least three points.")
    commands = [f"M {points[0][0]:.1f} {points[0][1]:.1f}"]
    for point in points[1:]:
        commands.append(f"L {point[0]:.1f} {point[1]:.1f}")
    commands.append("Z")
    return " ".join(commands)


def oval_points(
    center: tuple[float, float],
    rx: float,
    ry: float,
    count: int = 14,
    rotation: float = 0.0,
) -> list[tuple[float, float]]:
    if count < 4:
        raise ValueError("An oval needs at least four sample points.")
    cx, cy = center
    cos_r = math.cos(rotation)
    sin_r = math.sin(rotation)
    points: list[tuple[float, float]] = []
    for idx in range(count):
        theta = (idx / count) * math.tau
        x = math.cos(theta) * rx
        y = math.sin(theta) * ry
        px = cx + x * cos_r - y * sin_r
        py = cy + x * sin_r + y * cos_r
        points.append((px, py))
    return points


def extract_measurements(landmarks: list[tuple[float, float]]) -> AvatarMeasurements:
    face_w = distance(landmarks, 234, 454)
    face_h = distance(landmarks, 10, 152)
    eye_w_l = distance(landmarks, 33, 133)
    eye_w_r = distance(landmarks, 362, 263)
    mouth_w = distance(landmarks, 61, 291)
    upper_lip = distance(landmarks, 0, 13)
    lower_lip = distance(landmarks, 14, 17)
    mouth_open = distance(landmarks, 13, 14)
    nose_bridge = distance(landmarks, 6, 168) + distance(landmarks, 168, 2)
    eye_open_l = distance(landmarks, 159, 145)
    eye_open_r = distance(landmarks, 386, 374)
    mouth_center_y = (landmarks[13][1] + landmarks[14][1]) / 2.0
    corner_average_y = (landmarks[61][1] + landmarks[291][1]) / 2.0

    return AvatarMeasurements(
        face_shape_index=face_w / (face_h + EPS),
        jaw_width_ratio=distance(landmarks, 172, 397) / (face_w + EPS),
        chin_projection_ratio=distance(landmarks, 152, 18) / (face_h + EPS),
        eye_open_ratio=((eye_open_l / (eye_w_l + EPS)) + (eye_open_r / (eye_w_r + EPS))) / 2.0,
        eye_spacing_ratio=distance(landmarks, 468, 473) / (face_w + EPS),
        eye_tilt_ratio=(
            abs(landmarks[33][1] - landmarks[133][1]) / (eye_w_l + EPS)
            + abs(landmarks[362][1] - landmarks[263][1]) / (eye_w_r + EPS)
        )
        / 2.0,
        eyebrow_arch_ratio=distance(landmarks, 70, 159) / (eye_w_l + EPS),
        nose_width_ratio=distance(landmarks, 98, 327) / (face_w + EPS),
        nose_bridge_ratio=nose_bridge / (face_h + EPS),
        mouth_width_ratio=mouth_w / (face_w + EPS),
        lip_fullness_ratio=(upper_lip + lower_lip) / (mouth_w + EPS),
        mouth_open_ratio=mouth_open / (face_h + EPS),
        smile_ratio=(mouth_center_y - corner_average_y) / (mouth_w + EPS),
    )


def blendshape_scores(result: vision.FaceLandmarkerResult) -> dict[str, float]:
    scores: dict[str, float] = {}
    if not result.face_blendshapes:
        return scores
    for category in result.face_blendshapes[0]:
        scores[category.category_name] = float(category.score)
    return scores


def classify_presets(
    measurements: AvatarMeasurements,
    blendshapes: dict[str, float],
) -> AvatarPresets:
    def nearest_label(value: float, centers: dict[str, float]) -> str:
        return min(centers, key=lambda label: abs(value - centers[label]))

    def nearest_label_2d(value_a: float, value_b: float, centers: dict[str, tuple[float, float]]) -> str:
        return min(
            centers,
            key=lambda label: (
                ((value_a - centers[label][0]) / max(abs(centers[label][0]), EPS)) ** 2
                + ((value_b - centers[label][1]) / max(abs(centers[label][1]), EPS)) ** 2
            ),
        )

    face_shape = min(
        {
            "long": (0.78, 0.81, 0.16),
            "round": (0.95, 0.87, 0.15),
            "square": (0.92, 0.87, 0.13),
            "heart": (0.84, 0.77, 0.17),
            "oval": (0.87, 0.82, 0.15),
        },
        key=lambda label: (
            ((measurements.face_shape_index - {"long": 0.78, "round": 0.95, "square": 0.92, "heart": 0.84, "oval": 0.87}[label]) / 0.10) ** 2
            + ((measurements.jaw_width_ratio - {"long": 0.81, "round": 0.87, "square": 0.87, "heart": 0.77, "oval": 0.82}[label]) / 0.08) ** 2
            + ((measurements.chin_projection_ratio - {"long": 0.16, "round": 0.15, "square": 0.13, "heart": 0.17, "oval": 0.15}[label]) / 0.05) ** 2
        ),
    )

    jaw = nearest_label(measurements.jaw_width_ratio, {
        "narrow": 0.77,
        "balanced": 0.82,
        "broad": 0.87,
    })

    eyes = nearest_label(measurements.eye_open_ratio, {
        "relaxed": 0.26,
        "almond": 0.31,
        "open": 0.37,
    })

    eye_spacing = nearest_label(measurements.eye_spacing_ratio, {
        "close": 0.42,
        "balanced": 0.45,
        "wide": 0.48,
    })

    brows = nearest_label(measurements.eyebrow_arch_ratio, {
        "flat": 0.84,
        "soft": 0.93,
        "arched": 1.03,
    })

    nose = nearest_label_2d(
        measurements.nose_width_ratio,
        measurements.nose_bridge_ratio,
        {
            "small": (0.212, 0.315),
            "medium": (0.230, 0.332),
            "wide": (0.252, 0.332),
            "long": (0.228, 0.362),
            "strong": (0.252, 0.362),
        },
    )

    mouth = nearest_label_2d(
        measurements.mouth_width_ratio,
        measurements.lip_fullness_ratio,
        {
            "thin": (0.355, 0.205),
            "medium": (0.372, 0.275),
            "wide": (0.405, 0.265),
            "full": (0.372, 0.355),
        },
    )

    smile_score = max(
        blendshapes.get("mouthSmileLeft", 0.0),
        blendshapes.get("mouthSmileRight", 0.0),
    )
    open_score = max(
        blendshapes.get("jawOpen", 0.0),
        blendshapes.get("mouthOpen", 0.0),
    )
    frown_score = max(
        blendshapes.get("mouthFrownLeft", 0.0),
        blendshapes.get("mouthFrownRight", 0.0),
    )

    if open_score > 0.25 or measurements.mouth_open_ratio > 0.040:
        expression = "open"
    elif smile_score > 0.18 or measurements.smile_ratio > 0.020:
        expression = "smile"
    elif frown_score > 0.18 or measurements.smile_ratio < -0.015:
        expression = "serious"
    else:
        expression = "neutral"

    return AvatarPresets(
        face_shape=face_shape,
        jaw=jaw,
        eyes=eyes,
        eye_spacing=eye_spacing,
        brows=brows,
        nose=nose,
        mouth=mouth,
        expression=expression,
    )


def estimate_palette(
    image_rgb: np.ndarray,
    landmarks: list[tuple[float, float]],
    segmentation: SegmentationData,
) -> AvatarPalette:
    h, w = image_rgb.shape[:2]
    face_bbox = mask_bbox(segmentation.face_mask)
    head_bbox = segmentation.head_bbox
    if face_bbox is None:
        xs = np.array([point[0] for point in landmarks], dtype=np.float32) * w
        ys = np.array([point[1] for point in landmarks], dtype=np.float32) * h
        face_bbox = (int(xs.min()), int(ys.min()), int(xs.max()) + 1, int(ys.max()) + 1)

    fx0, fy0, fx1, fy1 = face_bbox
    hx0, hy0, hx1, hy1 = head_bbox
    face_w = fx1 - fx0
    face_h = fy1 - fy0
    head_w = hx1 - hx0
    head_h = hy1 - hy0

    fallback_skin = (216, 183, 160)
    skin_region = trim_luminance_outliers(
        masked_region_pixels(
            image_rgb,
            segmentation.face_mask,
            fx0 + face_w * 0.24,
            fy0 + face_h * 0.28,
            fx1 - face_w * 0.24,
            fy1 - face_h * 0.12,
        ),
        low_q=0.12,
        high_q=0.88,
    )
    if skin_region is None or skin_region.size == 0:
        skin_region = trim_luminance_outliers(
            masked_region_pixels(image_rgb, segmentation.face_mask, fx0, fy0, fx1, fy1),
            low_q=0.10,
            high_q=0.90,
        )
    skin = median_rgb(skin_region, fallback_skin)

    hair_region = trim_luminance_outliers(
        masked_region_pixels(
            image_rgb,
            segmentation.hair_mask,
            hx0 + head_w * 0.06,
            hy0,
            hx1 - head_w * 0.06,
            hy0 + head_h * 0.42,
        ),
        low_q=0.04,
        high_q=0.82,
    )
    if hair_region is None or hair_region.size == 0:
        hair_region = trim_luminance_outliers(
            masked_region_pixels(image_rgb, segmentation.hair_mask, hx0, hy0, hx1, hy1),
            low_q=0.04,
            high_q=0.86,
        )
    hair = median_rgb(hair_region, scale_rgb(skin, 0.55))
    if math.sqrt(sum((a - b) ** 2 for a, b in zip(hair, skin))) < 35:
        hair = scale_rgb(skin, 0.48)

    left_iris_center = average_xy([landmark_to_pixel(landmarks[index], w, h) for index in LEFT_IRIS_IDX])
    right_iris_center = average_xy([landmark_to_pixel(landmarks[index], w, h) for index in RIGHT_IRIS_IDX])
    iris_radius = max(2.0, face_w * 0.025)
    iris_samples: list[np.ndarray] = []
    for center in (left_iris_center, right_iris_center):
        sample = sample_circular_region(image_rgb, center, iris_radius)
        if sample is None or sample.size == 0:
            continue
        filtered = sample[np.mean(sample, axis=1) < 210.0]
        iris_samples.append(filtered if filtered.size else sample)

    iris_fallback = blend_rgb(hair, (96, 120, 148), 0.28)
    iris_region = np.concatenate(iris_samples, axis=0) if iris_samples else None
    iris = median_rgb(iris_region, iris_fallback)
    if np.mean(iris) > 190.0:
        iris = iris_fallback

    stroke_base = blend_rgb(scale_rgb(hair, 0.42), (58, 42, 38), 0.55)
    stroke = tuple(max(channel, 28) for channel in stroke_base)
    skin_light = blend_rgb(skin, (255, 248, 242), 0.28)
    skin_shadow = blend_rgb(skin, stroke, 0.20)
    skin_deep_shadow = blend_rgb(skin_shadow, stroke, 0.38)
    hair_shadow = blend_rgb(hair, stroke, 0.42)
    hair_highlight = blend_rgb(hair, skin_light, 0.22)
    lip = blend_rgb(skin, (188, 78, 86), 0.56)
    lip_dark = blend_rgb(lip, stroke, 0.30)
    lip_highlight = blend_rgb(lip, skin_light, 0.36)
    shirt_region = None
    clothes_bbox = mask_bbox(segmentation.clothes_mask)
    if clothes_bbox is not None:
        cx0, cy0, cx1, cy1 = clothes_bbox
        shirt_region = trim_luminance_outliers(
            masked_region_pixels(image_rgb, segmentation.clothes_mask, cx0, cy0, cx1, cy1),
            low_q=0.05,
            high_q=0.95,
        )
    shirt = median_rgb(shirt_region, blend_rgb(hair, (64, 92, 128), 0.55))
    shirt_shadow = blend_rgb(shirt, stroke, 0.34)
    shirt_highlight = blend_rgb(shirt, skin_light, 0.18)
    background = blend_rgb((246, 241, 233), skin, 0.10)
    background_accent = blend_rgb(background, hair_highlight, 0.42)
    eye_white = blend_rgb((255, 255, 255), skin_light, 0.12)
    iris_dark = blend_rgb(iris, stroke, 0.34)
    highlight = blend_rgb(skin_light, (255, 255, 255), 0.24)
    shadow = blend_rgb(stroke, skin, 0.35)

    return AvatarPalette(
        background=rgb_to_hex(background),
        background_accent=rgb_to_hex(background_accent),
        skin=rgb_to_hex(skin),
        skin_light=rgb_to_hex(skin_light),
        skin_shadow=rgb_to_hex(skin_shadow),
        skin_deep_shadow=rgb_to_hex(skin_deep_shadow),
        hair=rgb_to_hex(hair),
        hair_shadow=rgb_to_hex(hair_shadow),
        hair_highlight=rgb_to_hex(hair_highlight),
        stroke=rgb_to_hex(stroke),
        lip=rgb_to_hex(lip),
        lip_dark=rgb_to_hex(lip_dark),
        lip_highlight=rgb_to_hex(lip_highlight),
        shirt=rgb_to_hex(shirt),
        shirt_shadow=rgb_to_hex(shirt_shadow),
        shirt_highlight=rgb_to_hex(shirt_highlight),
        iris=rgb_to_hex(iris),
        iris_dark=rgb_to_hex(iris_dark),
        eye_white=rgb_to_hex(eye_white),
        highlight=rgb_to_hex(highlight),
        shadow=rgb_to_hex(shadow),
    )


def relative_luminance(rgb: tuple[int, int, int]) -> float:
    return 0.2126 * rgb[0] + 0.7152 * rgb[1] + 0.0722 * rgb[2]


def estimate_facial_hair(
    image_rgb: np.ndarray,
    landmarks: list[tuple[float, float]],
    segmentation: SegmentationData,
) -> dict[str, float]:
    h, w = image_rgb.shape[:2]
    face_bbox = mask_bbox(segmentation.face_mask)
    if face_bbox is None:
        return {"mustache": 0.0, "beard": 0.0, "presence": 0.0}

    fx0, fy0, fx1, fy1 = face_bbox
    face_w = fx1 - fx0
    face_h = fy1 - fy0

    mouth_left = landmark_to_pixel(landmarks[61], w, h)
    mouth_right = landmark_to_pixel(landmarks[291], w, h)
    mouth_center = average_xy([
        landmark_to_pixel(landmarks[13], w, h),
        landmark_to_pixel(landmarks[14], w, h),
    ])
    upper_lip = landmark_to_pixel(landmarks[0], w, h)
    nose_tip = landmark_to_pixel(landmarks[2], w, h)
    chin = landmark_to_pixel(landmarks[152], w, h)

    skin_region = trim_luminance_outliers(
        masked_region_pixels(
            image_rgb,
            segmentation.face_mask,
            fx0 + face_w * 0.28,
            fy0 + face_h * 0.24,
            fx1 - face_w * 0.28,
            fy0 + face_h * 0.54,
        ),
        low_q=0.12,
        high_q=0.88,
    )
    skin = median_rgb(skin_region, (200, 170, 150))

    mustache_region = bounded_region(
        image_rgb,
        mouth_left[0] - face_w * 0.04,
        nose_tip[1] - face_h * 0.02,
        mouth_right[0] + face_w * 0.04,
        upper_lip[1] + face_h * 0.02,
    )
    beard_region = bounded_region(
        image_rgb,
        fx0 + face_w * 0.16,
        mouth_center[1] + face_h * 0.02,
        fx1 - face_w * 0.16,
        chin[1] - face_h * 0.02,
    )

    mustache_rgb = median_rgb(mustache_region, skin)
    beard_rgb = median_rgb(beard_region, skin)
    skin_luma = relative_luminance(skin)
    mustache_luma = relative_luminance(mustache_rgb)
    beard_luma = relative_luminance(beard_rgb)

    mustache_strength = clamp((skin_luma - mustache_luma - 10.0) / 45.0, 0.0, 1.0)
    beard_strength = clamp((skin_luma - beard_luma - 14.0) / 48.0, 0.0, 1.0)
    if beard_strength < 0.12:
        beard_strength = 0.0
    if mustache_strength < 0.10:
        mustache_strength = 0.0

    return {
        "mustache": round(mustache_strength, 3),
        "beard": round(beard_strength, 3),
        "presence": round(max(mustache_strength, beard_strength), 3),
    }


def draw_polyline(
    image: np.ndarray,
    landmarks: list[tuple[float, float]],
    indices: list[int],
    color: tuple[int, int, int],
    closed: bool,
    thickness: int,
) -> None:
    h, w = image.shape[:2]
    pts = np.array(
        [[int(round(landmarks[index][0] * w)), int(round(landmarks[index][1] * h))] for index in indices],
        dtype=np.int32,
    )
    if pts.size == 0:
        return
    cv2.polylines(image, [pts], isClosed=closed, color=color, thickness=thickness, lineType=cv2.LINE_AA)


def draw_points(
    image: np.ndarray,
    landmarks: list[tuple[float, float]],
    indices: list[int],
    color: tuple[int, int, int],
    radius: int,
) -> None:
    h, w = image.shape[:2]
    for index in indices:
        x = int(round(landmarks[index][0] * w))
        y = int(round(landmarks[index][1] * h))
        cv2.circle(image, (x, y), radius, color, thickness=-1, lineType=cv2.LINE_AA)


def write_landmark_debug_overlay(
    image_rgb: np.ndarray,
    landmarks: list[tuple[float, float]],
    output_path: Path,
) -> None:
    debug = cv2.cvtColor(image_rgb, cv2.COLOR_RGB2BGR)
    thickness = max(2, debug.shape[1] // 700)
    point_r = max(2, debug.shape[1] // 900)

    draw_polyline(debug, landmarks, FACE_CONTOUR_IDX, (41, 128, 255), True, thickness)
    draw_polyline(debug, landmarks, LEFT_BROW_IDX, (80, 220, 120), False, thickness)
    draw_polyline(debug, landmarks, RIGHT_BROW_IDX, (80, 220, 120), False, thickness)
    draw_polyline(debug, landmarks, LEFT_EYE_IDX, (255, 220, 80), True, thickness)
    draw_polyline(debug, landmarks, RIGHT_EYE_IDX, (255, 220, 80), True, thickness)
    draw_polyline(debug, landmarks, NOSE_BRIDGE_IDX, (170, 110, 255), False, thickness)
    draw_polyline(debug, landmarks, NOSE_BASE_IDX, (170, 110, 255), False, thickness)
    draw_polyline(debug, landmarks, OUTER_LIP_IDX, (80, 80, 255), True, thickness)
    draw_polyline(debug, landmarks, INNER_LIP_IDX, (80, 80, 200), True, thickness)
    draw_points(debug, landmarks, LEFT_IRIS_IDX, (255, 255, 255), point_r)
    draw_points(debug, landmarks, RIGHT_IRIS_IDX, (255, 255, 255), point_r)
    cv2.imwrite(str(output_path), debug)


def write_segmentation_debug_overlay(
    image_rgb: np.ndarray,
    segmentation: SegmentationData,
    output_path: Path,
) -> None:
    debug = image_rgb.copy()
    overlay = debug.copy()
    overlay[segmentation.clothes_mask > 0] = (241, 222, 78)
    overlay[segmentation.hair_mask > 0] = (212, 122, 68)
    overlay[segmentation.face_mask > 0] = (247, 208, 126)
    overlay[segmentation.neck_mask > 0] = (226, 176, 116)
    overlay[segmentation.front_hair_mask > 0] = (180, 94, 52)
    mixed = cv2.addWeighted(debug, 0.55, overlay, 0.45, 0.0)
    cv2.imwrite(str(output_path), cv2.cvtColor(mixed, cv2.COLOR_RGB2BGR))


def identity_signature(landmarks: list[tuple[float, float]]) -> dict[str, object]:
    points = np.array(landmarks, dtype=np.float32)
    left_eye = np.mean(np.array(select_points(landmarks, LEFT_IRIS_IDX)), axis=0)
    right_eye = np.mean(np.array(select_points(landmarks, RIGHT_IRIS_IDX)), axis=0)
    eye_center = (left_eye + right_eye) / 2.0
    eye_delta = right_eye - left_eye
    eye_dist = float(np.linalg.norm(eye_delta))
    if eye_dist < EPS:
        eye_dist = 1.0
    angle = -math.atan2(float(eye_delta[1]), float(eye_delta[0]))
    cos_a = math.cos(angle)
    sin_a = math.sin(angle)
    rot = np.array([[cos_a, -sin_a], [sin_a, cos_a]], dtype=np.float32)
    normalized = (points - eye_center) @ rot.T / eye_dist
    vector = normalized.reshape(-1)
    rounded = [round(float(value), 5) for value in vector.tolist()]
    payload = json.dumps(rounded, separators=(",", ":")).encode("utf-8")
    return {
        "type": "geometry_signature_v1",
        "landmark_count": len(landmarks),
        "vector": rounded,
        "hash": hashlib.sha1(payload).hexdigest()[:20],
    }


def mouth_variants(
    outer_points: list[tuple[float, float]],
    inner_points: list[tuple[float, float]],
    center: tuple[float, float],
) -> dict[str, dict[str, list[dict[str, float]]]]:
    cx, cy = center

    def transform(
        points: list[tuple[float, float]],
        scale_x: float = 1.0,
        scale_y: float = 1.0,
        upper_shift: float = 0.0,
        lower_shift: float = 0.0,
        corner_lift: float = 0.0,
    ) -> list[tuple[float, float]]:
        result: list[tuple[float, float]] = []
        xs = [point[0] for point in points]
        left_x = min(xs)
        right_x = max(xs)
        span = max(right_x - left_x, EPS)
        for x, y in points:
            nx = cx + (x - cx) * scale_x
            ny = cy + (y - cy) * scale_y
            if y < cy:
                ny += upper_shift
            else:
                ny += lower_shift
            corner_weight = abs((x - cx) / span) * 2.0
            ny -= corner_lift * corner_weight
            result.append((nx, ny))
        return result

    return {
        "rest": {
            "outer": points_to_dicts(outer_points),
            "inner": points_to_dicts(inner_points),
        },
        "neutral": {
            "outer": points_to_dicts(outer_points),
            "inner": points_to_dicts(inner_points),
        },
        "smile": {
            "outer": points_to_dicts(transform(outer_points, scale_x=1.06, lower_shift=-1.5, corner_lift=4.2)),
            "inner": points_to_dicts(transform(inner_points, scale_x=1.02, lower_shift=-1.0, corner_lift=2.2)),
        },
        "open": {
            "outer": points_to_dicts(transform(outer_points, scale_x=1.02, scale_y=1.18, upper_shift=-2.0, lower_shift=2.0)),
            "inner": points_to_dicts(transform(inner_points, scale_x=0.96, scale_y=1.60, upper_shift=-3.2, lower_shift=3.8)),
        },
        "wide": {
            "outer": points_to_dicts(transform(outer_points, scale_x=1.12, scale_y=0.95)),
            "inner": points_to_dicts(transform(inner_points, scale_x=1.08, scale_y=0.90)),
        },
        "narrow": {
            "outer": points_to_dicts(transform(outer_points, scale_x=0.88, scale_y=0.92)),
            "inner": points_to_dicts(transform(inner_points, scale_x=0.82, scale_y=0.86)),
        },
        "aa": {
            "outer": points_to_dicts(transform(outer_points, scale_x=1.00, scale_y=1.20, upper_shift=-2.6, lower_shift=2.6)),
            "inner": points_to_dicts(transform(inner_points, scale_x=0.92, scale_y=1.92, upper_shift=-3.2, lower_shift=4.4)),
        },
        "ee": {
            "outer": points_to_dicts(transform(outer_points, scale_x=1.12, scale_y=0.86, corner_lift=1.8)),
            "inner": points_to_dicts(transform(inner_points, scale_x=1.18, scale_y=0.36, corner_lift=1.0)),
        },
        "oo": {
            "outer": points_to_dicts(transform(outer_points, scale_x=0.76, scale_y=1.18, upper_shift=-1.2, lower_shift=1.2)),
            "inner": points_to_dicts(transform(inner_points, scale_x=0.50, scale_y=1.52, upper_shift=-2.0, lower_shift=2.4)),
        },
        "fv": {
            "outer": points_to_dicts(transform(outer_points, scale_x=0.94, scale_y=0.82, upper_shift=0.4, lower_shift=-1.2)),
            "inner": points_to_dicts(transform(inner_points, scale_x=0.70, scale_y=0.26, upper_shift=-0.4, lower_shift=-1.0)),
        },
        "mbp": {
            "outer": points_to_dicts(transform(outer_points, scale_x=0.84, scale_y=0.70)),
            "inner": points_to_dicts(transform(inner_points, scale_x=0.48, scale_y=0.12)),
        },
    }


def eye_variants(points: list[tuple[float, float]], center: tuple[float, float]) -> dict[str, list[dict[str, float]]]:
    cx, cy = center

    def blink(points_in: list[tuple[float, float]], squeeze: float) -> list[tuple[float, float]]:
        return [(x, cy + (y - cy) * squeeze) for x, y in points_in]

    return {
        "neutral": points_to_dicts(points),
        "wide": points_to_dicts(blink(points, 1.18)),
        "blink": points_to_dicts(blink(points, 0.20)),
        "squint": points_to_dicts(blink(points, 0.45)),
        "closed": points_to_dicts(blink(points, 0.08)),
    }


def run_segmenter(image_rgb: np.ndarray, model_path: Path) -> np.ndarray:
    mp_image = mp.Image(image_format=mp.ImageFormat.SRGB, data=image_rgb)
    options = vision.ImageSegmenterOptions(
        base_options=BaseOptions(model_asset_path=str(model_path)),
        output_category_mask=True,
    )
    with vision.ImageSegmenter.create_from_options(options) as segmenter:
        result = segmenter.segment(mp_image)
    return result.category_mask.numpy_view().squeeze().astype(np.uint8)


def segment_portrait(
    image_rgb: np.ndarray,
    landmarks: list[tuple[float, float]],
    selfie_model_path: Path,
    hair_model_path: Path,
) -> SegmentationData:
    h, w = image_rgb.shape[:2]
    multiclass_mask = run_segmenter(image_rgb, selfie_model_path)
    hair_mask_model = run_segmenter(image_rgb, hair_model_path)

    face_mask = make_binary(multiclass_mask == 3)
    body_skin_mask = make_binary(multiclass_mask == 2)
    clothes_mask = make_binary(multiclass_mask == 4)
    hair_mask = make_binary((multiclass_mask == 1) | (hair_mask_model == 1))

    large_k = adaptive_kernel(image_rgb.shape, 0.014, minimum=7, maximum=51)
    small_k = max(3, large_k // 3)
    if small_k % 2 == 0:
        small_k += 1

    face_mask = largest_component(fill_holes(close_mask(face_mask, large_k)))
    hair_mask = largest_component(fill_holes(close_mask(hair_mask, large_k)))
    clothes_mask = largest_component(close_mask(clothes_mask, large_k))
    body_skin_mask = largest_component(close_mask(body_skin_mask, small_k))

    head_mask = largest_component(fill_holes(close_mask(make_binary(face_mask | hair_mask), large_k)))

    jaw_left = landmark_to_pixel(landmarks[234], w, h)
    jaw_right = landmark_to_pixel(landmarks[454], w, h)
    chin = landmark_to_pixel(landmarks[152], w, h)
    face_box = mask_bbox(face_mask)
    head_box = mask_bbox(head_mask)
    if face_box is None or head_box is None:
        raise RuntimeError("Segmentation failed to produce a usable face/head mask.")

    fx0, fy0, fx1, fy1 = face_box
    hx0, hy0, hx1, hy1 = head_box
    face_w = fx1 - fx0
    face_h = fy1 - fy0

    neck_box = clip_bbox(
        (
            jaw_left[0] - face_w * 0.05,
            chin[1] - face_h * 0.04,
            jaw_right[0] + face_w * 0.05,
            chin[1] + face_h * 0.42,
        ),
        width=w,
        height=h,
    )
    neck_region = mask_from_rect((h, w), neck_box)
    neck_mask = largest_component(close_mask(make_binary(body_skin_mask & neck_region), small_k))
    if neck_mask.sum() == 0:
        neck_mask = neck_region

    expanded_face = dilate_mask(face_mask, adaptive_kernel(image_rgb.shape, 0.022, minimum=11, maximum=71))
    front_region_box = clip_bbox(
        (
            fx0 - face_w * 0.20,
            fy0 - face_h * 0.08,
            fx1 + face_w * 0.20,
            fy0 + face_h * 0.82,
        ),
        width=w,
        height=h,
    )
    front_region = mask_from_rect((h, w), front_region_box)
    front_hair_mask = largest_component(open_mask(make_binary(hair_mask & expanded_face & front_region), small_k))
    if front_hair_mask.sum() < hair_mask.sum() * 0.03:
        front_hair_mask = np.zeros_like(hair_mask)

    clothes_box = mask_bbox(clothes_mask)
    crop_union = union_bbox([head_box, clothes_box, mask_bbox(neck_mask)])
    if crop_union is None:
        crop_union = head_box
    cx0, cy0, cx1, cy1 = crop_union
    head_w = hx1 - hx0
    head_h = hy1 - hy0
    crop_bbox = clip_bbox(
        (
            cx0 - head_w * 0.10,
            hy0 - head_h * 0.08,
            cx1 + head_w * 0.10,
            max(cy1, hy1 + head_h * 0.28) + head_h * 0.04,
        ),
        width=w,
        height=h,
    )

    head_contour = contour_from_mask(head_mask, num_points=96, closed=True)
    hair_contour = contour_from_mask(hair_mask, num_points=96, closed=True)
    face_contour = contour_from_mask(face_mask, num_points=80, closed=True)
    clothes_contour = contour_from_mask(clothes_mask, num_points=72, closed=True)
    neck_contour = contour_from_mask(neck_mask, num_points=24, closed=True)
    front_hair_contour = contour_from_mask(front_hair_mask, num_points=40, closed=True)

    if not face_contour:
        face_contour = [landmark_to_pixel(point, w, h) for point in select_points(landmarks, FACE_CONTOUR_IDX)]
        face_contour = smooth_points(resample_polyline(face_contour, 80, closed=True), passes=2, closed=True)

    if not head_contour:
        head_contour = [landmark_to_pixel(point, w, h) for point in select_points(landmarks, FACE_CONTOUR_IDX)]
        head_contour = smooth_points(resample_polyline(head_contour, 96, closed=True), passes=2, closed=True)

    return SegmentationData(
        face_mask=face_mask,
        body_skin_mask=body_skin_mask,
        clothes_mask=clothes_mask,
        hair_mask=hair_mask,
        head_mask=head_mask,
        neck_mask=neck_mask,
        front_hair_mask=front_hair_mask,
        crop_bbox=crop_bbox,
        head_bbox=head_box,
        head_contour=head_contour,
        hair_contour=hair_contour,
        face_contour=face_contour,
        clothes_contour=clothes_contour,
        neck_contour=neck_contour,
        front_hair_contour=front_hair_contour,
        multiclass_mask=multiclass_mask,
    )


def detect_face(
    image_path: Path,
    model_path: Path,
    min_confidence: float,
) -> tuple[np.ndarray, list[tuple[float, float]], dict[str, float]]:
    file_bytes = image_path.read_bytes()
    image_array = cv2.imdecode(np.frombuffer(file_bytes, dtype=np.uint8), cv2.IMREAD_COLOR)
    if image_array is None:
        raise RuntimeError(f"Could not decode image: {image_path}")

    rgb = cv2.cvtColor(image_array, cv2.COLOR_BGR2RGB)
    mp_image = mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb)
    options = vision.FaceLandmarkerOptions(
        base_options=BaseOptions(model_asset_path=str(model_path)),
        num_faces=1,
        min_face_detection_confidence=min_confidence,
        min_face_presence_confidence=min_confidence,
        output_face_blendshapes=True,
    )
    with vision.FaceLandmarker.create_from_options(options) as landmarker:
        result = landmarker.detect(mp_image)

    if not result.face_landmarks:
        raise RuntimeError("No face was detected in the input image.")

    landmarks = [(point.x, point.y) for point in result.face_landmarks[0]]
    return rgb, landmarks, blendshape_scores(result)


def render_avatar_svg(
    size: int,
    image_rgb: np.ndarray,
    landmarks: list[tuple[float, float]],
    segmentation: SegmentationData,
    measurements: AvatarMeasurements,
    presets: AvatarPresets,
    palette: AvatarPalette,
) -> tuple[str, dict[str, object]]:
    h, w = segmentation.face_mask.shape[:2]
    mapper, mapping_info = build_canvas_mapper(segmentation.crop_bbox, size)
    crop_x0, crop_y0, crop_x1, crop_y1 = segmentation.crop_bbox
    crop_rgb = image_rgb[crop_y0:crop_y1, crop_x0:crop_x1]
    portrait_rgb = stylize_portrait_crop(crop_rgb)

    subject_mask = make_binary(segmentation.head_mask | segmentation.neck_mask | segmentation.clothes_mask)
    subject_mask = fill_holes(close_mask(subject_mask, adaptive_kernel(image_rgb.shape, 0.012, minimum=5, maximum=41)))
    crop_subject_mask = subject_mask[crop_y0:crop_y1, crop_x0:crop_x1].astype(np.float32)
    portrait_mask = cv2.resize(
        crop_subject_mask,
        (portrait_rgb.shape[1], portrait_rgb.shape[0]),
        interpolation=cv2.INTER_CUBIC,
    )
    portrait_mask = cv2.GaussianBlur(
        portrait_mask,
        (0, 0),
        sigmaX=max(1.2, portrait_rgb.shape[1] * 0.006),
        sigmaY=max(1.2, portrait_rgb.shape[0] * 0.006),
    )
    portrait_mask = np.clip((portrait_mask - 0.02) / 0.96, 0.0, 1.0)
    portrait_alpha = np.uint8(np.clip(portrait_mask * 255.0, 0, 255))
    portrait_rgba = np.dstack([portrait_rgb, portrait_alpha])
    portrait_uri = image_data_uri(portrait_rgba, max_dim=960)

    texture_rgb = cv2.bilateralFilter(portrait_rgb, d=7, sigmaColor=48, sigmaSpace=24)
    texture_uri = image_data_uri(texture_rgb, quality=86)
    texture_x = mapping_info["offset_x"]
    texture_y = mapping_info["offset_y"]
    texture_w = (crop_x1 - crop_x0) * mapping_info["scale"]
    texture_h = (crop_y1 - crop_y0) * mapping_info["scale"]

    bust_mask = subject_mask
    bust_mask = fill_holes(close_mask(bust_mask, adaptive_kernel(image_rgb.shape, 0.020, minimum=9, maximum=71)))
    bust_mask = largest_component(bust_mask)
    bust_contour = contour_from_mask(bust_mask, num_points=140, closed=True)

    def map_landmark(index: int) -> tuple[float, float]:
        return mapper(landmark_to_pixel(landmarks[index], w, h))

    head_points = smooth_points([mapper(point) for point in segmentation.head_contour], passes=1, closed=True)
    hair_points = (
        smooth_points([mapper(point) for point in segmentation.hair_contour], passes=1, closed=True)
        if segmentation.hair_contour
        else []
    )
    face_points = smooth_points([mapper(point) for point in segmentation.face_contour], passes=1, closed=True)
    clothes_points = (
        smooth_points([mapper(point) for point in segmentation.clothes_contour], passes=1, closed=True)
        if segmentation.clothes_contour
        else []
    )
    neck_points = (
        smooth_points([mapper(point) for point in segmentation.neck_contour], passes=1, closed=True)
        if segmentation.neck_contour
        else []
    )
    front_hair_points = (
        smooth_points([mapper(point) for point in segmentation.front_hair_contour], passes=1, closed=True)
        if segmentation.front_hair_contour
        else []
    )
    bust_points = smooth_points([mapper(point) for point in bust_contour], passes=1, closed=True) if bust_contour else head_points

    left_brow = [map_landmark(index) for index in LEFT_BROW_IDX]
    right_brow = [map_landmark(index) for index in RIGHT_BROW_IDX]
    left_eye = [map_landmark(index) for index in LEFT_EYE_IDX]
    right_eye = [map_landmark(index) for index in RIGHT_EYE_IDX]
    left_upper_lid = [map_landmark(index) for index in LEFT_UPPER_LID_IDX]
    right_upper_lid = [map_landmark(index) for index in RIGHT_UPPER_LID_IDX]
    left_iris = [map_landmark(index) for index in LEFT_IRIS_IDX]
    right_iris = [map_landmark(index) for index in RIGHT_IRIS_IDX]
    nose_bridge = [map_landmark(index) for index in NOSE_BRIDGE_IDX]
    nose_base = [map_landmark(index) for index in NOSE_BASE_IDX]
    outer_lip = [map_landmark(index) for index in OUTER_LIP_IDX]
    inner_lip = [map_landmark(index) for index in INNER_LIP_IDX]
    upper_forehead = map_landmark(10)
    temple_left = map_landmark(127)
    temple_right = map_landmark(356)
    cheek_left = map_landmark(93)
    cheek_right = map_landmark(323)
    face_side_left = map_landmark(132)
    face_side_right = map_landmark(361)
    jaw_left = map_landmark(172)
    jaw_right = map_landmark(397)
    chin = map_landmark(152)
    mouth_left = map_landmark(61)
    mouth_right = map_landmark(291)
    lower_lip_center = map_landmark(17)
    nose_root = map_landmark(168)
    nose_left = map_landmark(98)
    nose_right = map_landmark(327)

    face_cx, face_cy = average_xy(face_points)
    head_cx, head_cy = average_xy(head_points)
    face_min_x, face_min_y, face_max_x, face_max_y = point_bounds(face_points)
    head_min_x, head_min_y, head_max_x, head_max_y = point_bounds(head_points)
    face_w = face_max_x - face_min_x
    face_h = face_max_y - face_min_y
    head_w = head_max_x - head_min_x
    head_h = head_max_y - head_min_y
    hair_shape_points = hair_points if len(hair_points) >= 3 else head_points
    if len(hair_points) >= 12:
        trimmed_hair = [
            point
            for point in hair_points
            if point[1] <= face_min_y + face_h * 0.16
            or point[0] <= face_min_x + face_w * 0.10
            or point[0] >= face_max_x - face_w * 0.10
        ]
        if len(trimmed_hair) >= 10:
            hair_shape_points = smooth_points(resample_polyline(trimmed_hair, 48, True), passes=1, closed=True)
    hair_cx, hair_cy = average_xy(hair_shape_points)
    hair_min_x, hair_min_y, hair_max_x, hair_max_y = point_bounds(hair_shape_points)
    hair_w = hair_max_x - hair_min_x
    hair_h = hair_max_y - hair_min_y
    stroke_w = max(2.2, head_w * 0.010)

    left_eye_center = average_xy(left_iris)
    right_eye_center = average_xy(right_iris)
    mouth_center = average_xy(inner_lip)
    brow_left_center = average_xy(left_brow)
    brow_right_center = average_xy(right_brow)
    eye_distance = math.hypot(right_eye_center[0] - left_eye_center[0], right_eye_center[1] - left_eye_center[1])
    head_rotation_deg = math.degrees(math.atan2(right_eye_center[1] - left_eye_center[1], right_eye_center[0] - left_eye_center[0]))
    mouth_inner_h = max(point[1] for point in inner_lip) - min(point[1] for point in inner_lip)
    mouth_is_open = mouth_inner_h > face_h * 0.015 or presets.expression == "open"
    left_eye_bounds = point_bounds(left_eye)
    right_eye_bounds = point_bounds(right_eye)
    mouth_min_x, mouth_min_y, mouth_max_x, mouth_max_y = point_bounds(outer_lip)
    mouth_w = mouth_max_x - mouth_min_x
    mouth_h = mouth_max_y - mouth_min_y
    nose_min_x, nose_min_y, nose_max_x, nose_max_y = point_bounds(nose_bridge + nose_base)
    nose_w = nose_max_x - nose_min_x
    pupil_r = max(3.0, eye_distance * 0.046)

    neck_top_y = face_max_y - face_h * 0.02
    neck_base_y_generic = face_max_y + head_h * 0.16
    neck_points = smooth_points(
        [
            (face_cx - face_w * 0.18, neck_top_y),
            (face_cx + face_w * 0.18, neck_top_y),
            (face_cx + face_w * 0.30, neck_base_y_generic),
            (face_cx, neck_base_y_generic + head_h * 0.04),
            (face_cx - face_w * 0.30, neck_base_y_generic),
        ],
        passes=1,
        closed=True,
    )
    torso_top_y = neck_base_y_generic - head_h * 0.06
    torso_bot_y = min(size * 0.82, neck_base_y_generic + head_h * 0.18)
    clothes_points = smooth_points(
        [
            (face_cx - face_w * 0.30, torso_top_y),
            (face_cx - head_w * 0.58, neck_base_y_generic + head_h * 0.04),
            (face_cx - head_w * 0.78, torso_bot_y - head_h * 0.04),
            (face_cx - head_w * 0.44, torso_bot_y),
            (face_cx + head_w * 0.44, torso_bot_y),
            (face_cx + head_w * 0.78, torso_bot_y - head_h * 0.04),
            (face_cx + head_w * 0.58, neck_base_y_generic + head_h * 0.04),
            (face_cx + face_w * 0.30, torso_top_y),
        ],
        passes=1,
        closed=True,
    )
    bust_points = smooth_points(
        [
            (face_cx - face_w * 0.36, torso_top_y - head_h * 0.02),
            (face_cx - head_w * 0.70, neck_base_y_generic + head_h * 0.04),
            (face_cx - head_w * 0.86, torso_bot_y - head_h * 0.04),
            (face_cx - head_w * 0.52, torso_bot_y + head_h * 0.01),
            (face_cx + head_w * 0.52, torso_bot_y + head_h * 0.01),
            (face_cx + head_w * 0.86, torso_bot_y - head_h * 0.04),
            (face_cx + head_w * 0.70, neck_base_y_generic + head_h * 0.04),
            (face_cx + face_w * 0.36, torso_top_y - head_h * 0.02),
        ],
        passes=1,
        closed=True,
    )

    def side_band_anchor(side: str, y_center: float, y_span: float) -> tuple[float, float]:
        candidates = [point for point in head_points if y_center - y_span <= point[1] <= y_center + y_span]
        if not candidates:
            candidates = head_points
        return min(candidates, key=lambda point: point[0]) if side == "left" else max(candidates, key=lambda point: point[0])

    face_path = closed_smooth_path(face_points)
    head_path = closed_smooth_path(head_points)
    hair_path = closed_smooth_path(hair_shape_points)
    bust_path = closed_smooth_path(bust_points)
    left_eye_path = closed_smooth_path(left_eye)
    right_eye_path = closed_smooth_path(right_eye)
    mouth_outer_path = closed_smooth_path(outer_lip)
    mouth_inner_path = closed_smooth_path(inner_lip)
    clothes_path = closed_smooth_path(clothes_points) if len(clothes_points) >= 3 else ""
    neck_path = closed_smooth_path(neck_points) if len(neck_points) >= 3 else ""
    front_hair_path = closed_smooth_path(front_hair_points) if len(front_hair_points) >= 3 else ""
    left_brow_path = open_smooth_path(left_brow)
    right_brow_path = open_smooth_path(right_brow)
    left_upper_lid_path = open_smooth_path(left_upper_lid)
    right_upper_lid_path = open_smooth_path(right_upper_lid)
    nose_bridge_path = open_smooth_path(nose_bridge)
    nose_base_path = open_smooth_path(nose_base)
    philtrum_top = map_landmark(0)
    nose_tip = map_landmark(2)
    philtrum_path = open_smooth_path([nose_tip, midpoint(nose_tip, philtrum_top), philtrum_top])

    blush_color = rgb_to_hex(blend_rgb(hex_to_rgb(palette.lip), hex_to_rgb(palette.skin_light), 0.56))
    card_glow = rgb_to_hex(blend_rgb(hex_to_rgb(palette.background_accent), (255, 255, 255), 0.14))
    under_eye_color = rgb_to_hex(blend_rgb(hex_to_rgb(palette.skin_shadow), hex_to_rgb(palette.shadow), 0.32))
    hair_sheen = rgb_to_hex(blend_rgb(hex_to_rgb(palette.hair_highlight), (255, 255, 255), 0.14))
    lip_gloss = rgb_to_hex(blend_rgb(hex_to_rgb(palette.lip_highlight), (255, 255, 255), 0.24))
    mouth_inner_dark = rgb_to_hex(blend_rgb(hex_to_rgb(palette.lip_dark), (56, 23, 34), 0.46))
    tongue_color = rgb_to_hex(blend_rgb(hex_to_rgb(palette.lip), (175, 96, 104), 0.40))
    teeth_color = rgb_to_hex(blend_rgb(hex_to_rgb(palette.eye_white), (255, 255, 255), 0.32))
    under_lip_shadow = rgb_to_hex(blend_rgb(hex_to_rgb(palette.skin_shadow), hex_to_rgb(palette.lip_dark), 0.28))
    nose_light = rgb_to_hex(blend_rgb(hex_to_rgb(palette.highlight), hex_to_rgb(palette.skin_light), 0.42))
    plane_light = rgb_to_hex(blend_rgb(hex_to_rgb(palette.skin_light), (255, 255, 255), 0.14))
    plane_mid = rgb_to_hex(blend_rgb(hex_to_rgb(palette.skin_shadow), hex_to_rgb(palette.skin), 0.26))
    plane_dark = rgb_to_hex(blend_rgb(hex_to_rgb(palette.skin_deep_shadow), hex_to_rgb(palette.shadow), 0.22))
    frame_color = rgb_to_hex(blend_rgb(hex_to_rgb(palette.stroke), hex_to_rgb(palette.skin_deep_shadow), 0.32))
    frame_highlight = rgb_to_hex(blend_rgb(hex_to_rgb(palette.highlight), hex_to_rgb(palette.skin_light), 0.26))
    ear_fill = rgb_to_hex(blend_rgb(hex_to_rgb(palette.skin), hex_to_rgb(palette.skin_shadow), 0.18))
    ear_inner = rgb_to_hex(blend_rgb(hex_to_rgb(palette.skin_deep_shadow), hex_to_rgb(palette.lip_dark), 0.18))
    neck_plane_color = rgb_to_hex(blend_rgb(hex_to_rgb(palette.skin_shadow), hex_to_rgb(palette.skin_deep_shadow), 0.34))
    collar_color = rgb_to_hex(blend_rgb(hex_to_rgb(palette.shirt_shadow), hex_to_rgb(palette.stroke), 0.24))
    collar_shadow_color = rgb_to_hex(blend_rgb(hex_to_rgb(palette.shirt_shadow), hex_to_rgb(palette.stroke), 0.52))
    bust_outline = rgb_to_hex(blend_rgb(hex_to_rgb(frame_color), hex_to_rgb(palette.shadow), 0.28))
    bust_fill = rgb_to_hex(blend_rgb(hex_to_rgb(card_glow), hex_to_rgb(palette.background), 0.46))
    face_outline = rgb_to_hex(blend_rgb(hex_to_rgb(frame_color), hex_to_rgb(palette.stroke), 0.28))
    shirt_outline = rgb_to_hex(blend_rgb(hex_to_rgb(collar_color), hex_to_rgb(palette.stroke), 0.22))
    left_cheek = (face_cx - face_w * 0.23, face_cy + face_h * 0.12)
    right_cheek = (face_cx + face_w * 0.23, face_cy + face_h * 0.12)
    forehead_glow = (face_cx - face_w * 0.10, face_cy - face_h * 0.28)
    jaw_shadow_y = face_max_y - face_h * 0.05
    upper_forehead_left = lerp_point(temple_left, upper_forehead, 0.56)
    upper_forehead_right = lerp_point(temple_right, upper_forehead, 0.56)
    lower_chin_left = lerp_point(jaw_left, chin, 0.58)
    lower_chin_right = lerp_point(jaw_right, chin, 0.58)
    mouth_mid_left = lerp_point(mouth_left, mouth_center, 0.34)
    mouth_mid_right = lerp_point(mouth_right, mouth_center, 0.34)

    left_ear_top = side_band_anchor("left", face_cy - face_h * 0.10, face_h * 0.11)
    left_ear_mid = side_band_anchor("left", face_cy + face_h * 0.02, face_h * 0.08)
    left_ear_bottom = side_band_anchor("left", face_cy + face_h * 0.18, face_h * 0.10)
    right_ear_top = side_band_anchor("right", face_cy - face_h * 0.10, face_h * 0.11)
    right_ear_mid = side_band_anchor("right", face_cy + face_h * 0.02, face_h * 0.08)
    right_ear_bottom = side_band_anchor("right", face_cy + face_h * 0.18, face_h * 0.10)

    left_ear_attach_top = lerp_point(temple_left, left_ear_top, 0.42)
    left_ear_attach_mid = lerp_point(face_side_left, left_ear_mid, 0.34)
    left_ear_attach_bottom = lerp_point(jaw_left, left_ear_bottom, 0.40)
    right_ear_attach_top = lerp_point(temple_right, right_ear_top, 0.42)
    right_ear_attach_mid = lerp_point(face_side_right, right_ear_mid, 0.34)
    right_ear_attach_bottom = lerp_point(jaw_right, right_ear_bottom, 0.40)

    left_ear_points = [
        left_ear_attach_top,
        left_ear_top,
        lerp_point(left_ear_top, left_ear_mid, 0.42),
        left_ear_mid,
        lerp_point(left_ear_bottom, left_ear_mid, 0.48),
        left_ear_bottom,
        left_ear_attach_bottom,
        left_ear_attach_mid,
    ]
    right_ear_points = [
        right_ear_attach_top,
        right_ear_top,
        lerp_point(right_ear_top, right_ear_mid, 0.42),
        right_ear_mid,
        lerp_point(right_ear_bottom, right_ear_mid, 0.48),
        right_ear_bottom,
        right_ear_attach_bottom,
        right_ear_attach_mid,
    ]
    left_ear_path = polygon_path(left_ear_points)
    right_ear_path = polygon_path(right_ear_points)
    left_ear_inner_path = open_smooth_path([
        midpoint(left_ear_attach_top, left_ear_top),
        lerp_point(left_ear_mid, left_ear_top, 0.34),
        midpoint(left_ear_mid, left_ear_attach_mid),
        lerp_point(left_ear_mid, left_ear_bottom, 0.42),
        midpoint(left_ear_attach_bottom, left_ear_bottom),
    ])
    right_ear_inner_path = open_smooth_path([
        midpoint(right_ear_attach_top, right_ear_top),
        lerp_point(right_ear_mid, right_ear_top, 0.34),
        midpoint(right_ear_mid, right_ear_attach_mid),
        lerp_point(right_ear_mid, right_ear_bottom, 0.42),
        midpoint(right_ear_attach_bottom, right_ear_bottom),
    ])

    face_frame_points = [
        temple_left,
        upper_forehead_left,
        upper_forehead,
        upper_forehead_right,
        temple_right,
        cheek_right,
        jaw_right,
        chin,
        jaw_left,
        cheek_left,
    ]
    face_frame_path = polygon_path(face_frame_points)
    left_forehead_plane = polygon_path([temple_left, upper_forehead_left, nose_root, brow_left_center, cheek_left])
    right_forehead_plane = polygon_path([temple_right, upper_forehead_right, nose_root, brow_right_center, cheek_right])
    left_cheek_plane = polygon_path([cheek_left, nose_left, mouth_mid_left, jaw_left, lower_chin_left])
    right_cheek_plane = polygon_path([cheek_right, nose_right, mouth_mid_right, jaw_right, lower_chin_right])
    nose_plane_path = polygon_path([nose_root, nose_right, nose_tip, nose_left])
    chin_plane_path = polygon_path([mouth_mid_left, lower_lip_center, mouth_mid_right, lower_chin_right, chin, lower_chin_left])

    neck_base_y = max(point[1] for point in neck_points) if neck_points else face_max_y + head_h * 0.18
    neck_top_left = lerp_point(jaw_left, chin, 0.28)
    neck_top_right = lerp_point(jaw_right, chin, 0.28)
    neck_center_base = (face_cx, neck_base_y)
    neck_left_base = (face_cx - face_w * 0.19, neck_base_y)
    neck_right_base = (face_cx + face_w * 0.19, neck_base_y)
    collar_y = neck_base_y - head_h * 0.004
    shoulder_y = neck_base_y + head_h * 0.060
    collar_left = (face_cx - face_w * 0.31, collar_y)
    collar_right = (face_cx + face_w * 0.31, collar_y)
    shoulder_left = (face_cx - head_w * 0.50, shoulder_y)
    shoulder_right = (face_cx + head_w * 0.50, shoulder_y)
    throat_plane_path = polygon_path([neck_top_left, neck_top_right, neck_right_base, neck_center_base, neck_left_base])
    left_neck_plane_path = polygon_path([neck_top_left, collar_left, neck_left_base, neck_center_base])
    right_neck_plane_path = polygon_path([neck_top_right, collar_right, neck_right_base, neck_center_base])
    chest_frame_path = polygon_path([shoulder_left, collar_left, neck_left_base, neck_right_base, collar_right, shoulder_right])
    shirt_support_path = polygon_path([collar_left, neck_left_base, neck_right_base, collar_right, shoulder_right, shoulder_left])
    upper_torso_path = polygon_path([
        (collar_left[0] - face_w * 0.10, collar_left[1] + head_h * 0.02),
        neck_left_base,
        neck_right_base,
        (collar_right[0] + face_w * 0.10, collar_right[1] + head_h * 0.02),
        shoulder_right,
        shoulder_left,
    ])
    collar_band_left = (face_cx - face_w * 0.27, neck_base_y + head_h * 0.030)
    collar_band_right = (face_cx + face_w * 0.27, neck_base_y + head_h * 0.030)
    collar_wrap_left = lerp_point(neck_top_left, neck_left_base, 0.44)
    collar_wrap_right = lerp_point(neck_top_right, neck_right_base, 0.44)
    collar_band_path = polygon_path([collar_wrap_left, neck_left_base, neck_right_base, collar_wrap_right, collar_band_right, collar_band_left])
    collar_rim_path = open_smooth_path([collar_left, neck_left_base, neck_center_base, neck_right_base, collar_right])
    collar_band_rim_path = open_smooth_path([collar_band_left, neck_left_base, neck_center_base, neck_right_base, collar_band_right])
    left_clavicle_path = open_smooth_path([shoulder_left, collar_left, neck_center_base])
    right_clavicle_path = open_smooth_path([neck_center_base, collar_right, shoulder_right])
    collar_opening_path = open_smooth_path([collar_left, collar_wrap_left, neck_center_base, collar_wrap_right, collar_right])
    hair_coverage = float(segmentation.hair_mask.sum()) / max(1.0, float(segmentation.face_mask.sum()))
    show_ears = hair_coverage < 0.24
    background_wave = (
        f'M {-size * 0.08:.1f} {size * 0.78:.1f} '
        f'C {size * 0.14:.1f} {size * 0.63:.1f}, {size * 0.36:.1f} {size * 0.90:.1f}, {size * 0.70:.1f} {size * 0.76:.1f} '
        f'S {size * 1.05:.1f} {size * 0.68:.1f}, {size * 1.08:.1f} {size * 0.60:.1f} '
        f'L {size * 1.08:.1f} {size * 1.02:.1f} L {-size * 0.08:.1f} {size * 1.02:.1f} Z'
    )

    defs: list[str] = [
        "<defs>",
        f'<filter id="portrait-shadow" x="-20%" y="-20%" width="140%" height="140%">'
        f'<feDropShadow dx="0" dy="{stroke_w * 0.55:.1f}" stdDeviation="{max(1.4, stroke_w * 0.62):.1f}" flood-color="{palette.shadow}" flood-opacity="0.18" />'
        "</filter>",
        f'<linearGradient id="bg-gradient" x1="0" y1="0" x2="{size:.1f}" y2="{size:.1f}" gradientUnits="userSpaceOnUse">'
        f'<stop offset="0%" stop-color="{palette.background_accent}" />'
        f'<stop offset="100%" stop-color="{palette.background}" />'
        "</linearGradient>",
        f'<radialGradient id="halo-gradient" cx="{head_cx:.1f}" cy="{head_cy - face_h * 0.10:.1f}" r="{head_w * 0.84:.1f}" gradientUnits="userSpaceOnUse">'
        f'<stop offset="0%" stop-color="{card_glow}" stop-opacity="0.82" />'
        f'<stop offset="100%" stop-color="{palette.background}" stop-opacity="0" />'
        "</radialGradient>",
        f'<radialGradient id="skin-gradient" cx="{face_cx - face_w * 0.14:.1f}" cy="{face_cy - face_h * 0.24:.1f}" r="{face_h * 0.86:.1f}" gradientUnits="userSpaceOnUse">'
        f'<stop offset="0%" stop-color="{palette.skin_light}" />'
        f'<stop offset="58%" stop-color="{palette.skin}" />'
        f'<stop offset="100%" stop-color="{palette.skin_shadow}" />'
        "</radialGradient>",
        f'<linearGradient id="hair-gradient" x1="{hair_cx - hair_w * 0.24:.1f}" y1="{hair_min_y:.1f}" x2="{hair_cx + hair_w * 0.18:.1f}" y2="{hair_max_y:.1f}" gradientUnits="userSpaceOnUse">'
        f'<stop offset="0%" stop-color="{palette.hair_highlight}" />'
        f'<stop offset="42%" stop-color="{palette.hair}" />'
        f'<stop offset="100%" stop-color="{palette.hair_shadow}" />'
        "</linearGradient>",
        f'<linearGradient id="shirt-gradient" x1="{face_cx:.1f}" y1="{face_max_y:.1f}" x2="{face_cx:.1f}" y2="{size:.1f}" gradientUnits="userSpaceOnUse">'
        f'<stop offset="0%" stop-color="{palette.shirt_highlight}" />'
        f'<stop offset="55%" stop-color="{palette.shirt}" />'
        f'<stop offset="100%" stop-color="{palette.shirt_shadow}" />'
        "</linearGradient>",
        f'<linearGradient id="lip-gradient" x1="{mouth_center[0]:.1f}" y1="{mouth_min_y:.1f}" x2="{mouth_center[0]:.1f}" y2="{mouth_max_y:.1f}" gradientUnits="userSpaceOnUse">'
        f'<stop offset="0%" stop-color="{palette.lip_dark}" />'
        f'<stop offset="48%" stop-color="{palette.lip}" />'
        f'<stop offset="100%" stop-color="{palette.lip_highlight}" />'
        "</linearGradient>",
        f'<linearGradient id="sclera-gradient" x1="0" y1="0" x2="0" y2="1">'
        f'<stop offset="0%" stop-color="#ffffff" />'
        f'<stop offset="100%" stop-color="{palette.eye_white}" />'
        "</linearGradient>",
        f'<radialGradient id="left-iris-gradient" cx="{left_eye_center[0] - pupil_r * 0.40:.1f}" cy="{left_eye_center[1] - pupil_r * 0.45:.1f}" r="{pupil_r * 3.0:.1f}" gradientUnits="userSpaceOnUse">'
        f'<stop offset="0%" stop-color="{palette.highlight}" />'
        f'<stop offset="28%" stop-color="{palette.iris}" />'
        f'<stop offset="70%" stop-color="{palette.iris_dark}" />'
        f'<stop offset="100%" stop-color="{palette.stroke}" />'
        "</radialGradient>",
        f'<radialGradient id="right-iris-gradient" cx="{right_eye_center[0] - pupil_r * 0.40:.1f}" cy="{right_eye_center[1] - pupil_r * 0.45:.1f}" r="{pupil_r * 3.0:.1f}" gradientUnits="userSpaceOnUse">'
        f'<stop offset="0%" stop-color="{palette.highlight}" />'
        f'<stop offset="28%" stop-color="{palette.iris}" />'
        f'<stop offset="70%" stop-color="{palette.iris_dark}" />'
        f'<stop offset="100%" stop-color="{palette.stroke}" />'
        "</radialGradient>",
        f'<clipPath id="bust-clip"><path d="{bust_path}" /></clipPath>',
        f'<clipPath id="head-clip"><path d="{head_path}" /></clipPath>',
        f'<clipPath id="hair-clip"><path d="{hair_path}" /></clipPath>',
        f'<clipPath id="face-clip"><path d="{face_path}" /></clipPath>',
        f'<clipPath id="left-eye-clip"><path d="{left_eye_path}" /></clipPath>',
        f'<clipPath id="right-eye-clip"><path d="{right_eye_path}" /></clipPath>',
        f'<clipPath id="mouth-clip"><path d="{mouth_outer_path}" /></clipPath>',
        f'<clipPath id="inner-mouth-clip"><path d="{mouth_inner_path}" /></clipPath>',
    ]
    if clothes_path:
        defs.append(f'<clipPath id="clothes-clip"><path d="{clothes_path}" /></clipPath>')
    if neck_path:
        defs.append(f'<clipPath id="neck-clip"><path d="{neck_path}" /></clipPath>')
    if front_hair_path:
        defs.append(f'<clipPath id="front-hair-clip"><path d="{front_hair_path}" /></clipPath>')
    defs.append("</defs>")

    def clipped_texture(clip_id: str, opacity: float) -> str:
        return (
            f'<g clip-path="url(#{clip_id})">'
            f'<image x="{texture_x:.1f}" y="{texture_y:.1f}" width="{texture_w:.1f}" height="{texture_h:.1f}" '
            f'href="{texture_uri}" preserveAspectRatio="none" opacity="{opacity:.2f}" />'
            f"</g>"
        )

    clavicle_markup = (
        f'<g clip-path="url(#clothes-clip)">'
        f'<path d="{left_clavicle_path}" fill="none" stroke="{collar_color}" stroke-width="{stroke_w * 0.22:.1f}" stroke-linecap="round" opacity="0.34" />'
        f'<path d="{right_clavicle_path}" fill="none" stroke="{collar_color}" stroke-width="{stroke_w * 0.22:.1f}" stroke-linecap="round" opacity="0.34" />'
        f"</g>"
        if clothes_path
        else (
            f'<path d="{left_clavicle_path}" fill="none" stroke="{collar_color}" stroke-width="{stroke_w * 0.22:.1f}" stroke-linecap="round" opacity="0.34" />'
            f'<path d="{right_clavicle_path}" fill="none" stroke="{collar_color}" stroke-width="{stroke_w * 0.22:.1f}" stroke-linecap="round" opacity="0.34" />'
        )
    )

    def build_eye_markup(
        side: str,
        eye_path: str,
        eye_center: tuple[float, float],
        eye_bounds: tuple[float, float, float, float],
        upper_lid_path: str,
        iris_gradient_id: str,
    ) -> str:
        eye_min_x, eye_min_y, eye_max_x, eye_max_y = eye_bounds
        eye_w = eye_max_x - eye_min_x
        eye_h = eye_max_y - eye_min_y
        iris_rx = max(4.2, eye_w * 0.27)
        iris_ry = max(iris_rx * 1.05, eye_h * 0.92)
        local_pupil_r = max(2.8, iris_rx * 0.46)
        return (
            f'<path id="{side}-eye" d="{eye_path}" fill="url(#sclera-gradient)" stroke="{palette.stroke}" stroke-width="{stroke_w * 0.34:.1f}" stroke-linejoin="round" />'
            f'<g clip-path="url(#{side}-eye-clip)">'
            f'<ellipse cx="{eye_center[0]:.1f}" cy="{eye_center[1] + eye_h * 0.02:.1f}" rx="{iris_rx:.1f}" ry="{iris_ry:.1f}" fill="url(#{iris_gradient_id})" />'
            f'<ellipse cx="{eye_center[0]:.1f}" cy="{eye_min_y + eye_h * 0.18:.1f}" rx="{eye_w * 0.56:.1f}" ry="{max(2.8, eye_h * 0.55):.1f}" fill="{under_eye_color}" opacity="0.18" />'
            f'<ellipse cx="{eye_center[0]:.1f}" cy="{eye_max_y - eye_h * 0.14:.1f}" rx="{eye_w * 0.42:.1f}" ry="{max(2.0, eye_h * 0.18):.1f}" fill="{palette.highlight}" opacity="0.10" />'
            f'<circle id="{side}-pupil" cx="{eye_center[0]:.1f}" cy="{eye_center[1]:.1f}" r="{local_pupil_r:.1f}" fill="{palette.stroke}" />'
            f'<circle cx="{eye_center[0]:.1f}" cy="{eye_center[1]:.1f}" r="{iris_rx * 0.82:.1f}" fill="none" stroke="{palette.iris_dark}" stroke-width="{stroke_w * 0.16:.1f}" opacity="0.75" />'
            f'<circle cx="{eye_center[0] - iris_rx * 0.28:.1f}" cy="{eye_center[1] - iris_rx * 0.24:.1f}" r="{local_pupil_r * 0.42:.1f}" fill="#ffffff" opacity="0.94" />'
            f'<circle cx="{eye_center[0] + iris_rx * 0.18:.1f}" cy="{eye_center[1] + iris_rx * 0.05:.1f}" r="{local_pupil_r * 0.16:.1f}" fill="#ffffff" opacity="0.42" />'
            f"</g>"
            f'<path d="{eye_path}" fill="none" stroke="{palette.highlight}" stroke-width="{stroke_w * 0.12:.1f}" opacity="0.28" />'
            f'<path d="{upper_lid_path}" fill="none" stroke="{palette.stroke}" stroke-width="{stroke_w * 0.62:.1f}" stroke-linecap="round" />'
            f'<path d="{upper_lid_path}" fill="none" stroke="{palette.hair_shadow}" stroke-width="{stroke_w * 0.26:.1f}" stroke-linecap="round" opacity="0.28" transform="translate(0 {stroke_w * 0.18:.1f})" />'
        )

    layers: list[str] = []
    layers.append("".join(defs))
    layers.append(
        f'<g id="background">'
        f'<rect width="{size}" height="{size}" fill="url(#bg-gradient)" />'
        f'<ellipse cx="{head_cx:.1f}" cy="{head_cy - face_h * 0.08:.1f}" rx="{head_w * 0.88:.1f}" ry="{head_w * 0.74:.1f}" fill="url(#halo-gradient)" opacity="0.84" />'
        f'<path d="{background_wave}" fill="{card_glow}" opacity="0.18" />'
        f"</g>"
    )
    layers.append(
        f'<g id="portrait-base" filter="url(#portrait-shadow)">'
        f'<path d="{bust_path}" fill="{bust_fill}" opacity="0.92" />'
        f'<g clip-path="url(#bust-clip)">'
        f'<image x="{texture_x:.1f}" y="{texture_y:.1f}" width="{texture_w:.1f}" height="{texture_h:.1f}" href="{portrait_uri}" preserveAspectRatio="none" opacity="0.0" />'
        f"</g>"
        f'<path d="{bust_path}" fill="none" stroke="{bust_outline}" stroke-width="{stroke_w * 0.18:.1f}" opacity="0.42" />'
        f"</g>"
    )
    if clothes_path:
        layers.append(
            f'<g id="clothes" filter="url(#portrait-shadow)">'
            f'<path d="{clothes_path}" fill="url(#shirt-gradient)" opacity="0.88" />'
            f'<path d="{clothes_path}" fill="none" stroke="{shirt_outline}" stroke-width="{stroke_w * 0.18:.1f}" stroke-linejoin="round" opacity="0.36" />'
            f'{clipped_texture("clothes-clip", 0.0)}'
            f'<g clip-path="url(#clothes-clip)">'
            f'<path d="{shirt_support_path}" fill="{palette.shirt_shadow}" opacity="0.14" />'
            f'<ellipse cx="{face_cx:.1f}" cy="{face_max_y + head_h * 0.18:.1f}" rx="{head_w * 0.56:.1f}" ry="{head_h * 0.22:.1f}" fill="{palette.shirt_shadow}" opacity="0.22" />'
            f'<ellipse cx="{face_cx - head_w * 0.10:.1f}" cy="{face_max_y + head_h * 0.02:.1f}" rx="{head_w * 0.40:.1f}" ry="{head_h * 0.16:.1f}" fill="{palette.shirt_highlight}" opacity="0.18" />'
            f'<path d="{collar_opening_path}" fill="none" stroke="{palette.shirt_highlight}" stroke-width="{stroke_w * 0.24:.1f}" stroke-linecap="round" stroke-linejoin="round" opacity="0.72" />'
            f'<path d="{collar_opening_path}" fill="none" stroke="{collar_shadow_color}" stroke-width="{stroke_w * 0.12:.1f}" stroke-linecap="round" stroke-linejoin="round" opacity="0.34" transform="translate(0 {stroke_w * 0.18:.1f})" />'
            f'<path d="{left_clavicle_path}" fill="none" stroke="{collar_color}" stroke-width="{stroke_w * 0.18:.1f}" stroke-linecap="round" opacity="0.20" />'
            f'<path d="{right_clavicle_path}" fill="none" stroke="{collar_color}" stroke-width="{stroke_w * 0.18:.1f}" stroke-linecap="round" opacity="0.20" />'
            f"</g>"
            f"</g>"
        )
    if neck_path:
        layers.append(
            f'<g id="neck">'
            f'<path d="{neck_path}" fill="url(#skin-gradient)" stroke="{face_outline}" stroke-width="{stroke_w * 0.14:.1f}" opacity="0.96" />'
            f'{clipped_texture("neck-clip", 0.0)}'
            f'<g clip-path="url(#neck-clip)">'
            f'<ellipse cx="{face_cx:.1f}" cy="{face_max_y + head_h * 0.03:.1f}" rx="{face_w * 0.22:.1f}" ry="{head_h * 0.05:.1f}" fill="{palette.skin_deep_shadow}" opacity="0.16" />'
            f"</g>"
            f"</g>"
        )
        layers.append(
            f'<g id="neck-frame">'
            f'<g clip-path="url(#neck-clip)">'
            f'<path d="{throat_plane_path}" fill="{neck_plane_color}" opacity="0.22" />'
            f'<path d="{left_neck_plane_path}" fill="{plane_dark}" opacity="0.16" />'
            f'<path d="{right_neck_plane_path}" fill="{plane_dark}" opacity="0.16" />'
            f'<path d="{throat_plane_path}" fill="none" stroke="{frame_highlight}" stroke-width="{stroke_w * 0.12:.1f}" stroke-linejoin="round" opacity="0.20" />'
            f"</g>"
            f"{clavicle_markup}"
            f'<path d="{collar_band_path}" fill="{palette.shirt_highlight}" opacity="0.18" />'
            f'<path d="{collar_rim_path}" fill="none" stroke="{collar_shadow_color}" stroke-width="{stroke_w * 0.14:.1f}" stroke-linecap="round" stroke-linejoin="round" opacity="0.18" />'
            f"</g>"
        )
    layers.append(
        f'<g id="hair-back" filter="url(#portrait-shadow)">'
        f'<path d="{hair_path}" fill="url(#hair-gradient)" stroke="{palette.stroke}" stroke-width="{stroke_w * 0.18:.1f}" stroke-linejoin="round" opacity="0.92" />'
        f'{clipped_texture("hair-clip", 0.0)}'
        f'<g clip-path="url(#hair-clip)">'
        f'<ellipse cx="{hair_cx - hair_w * 0.18:.1f}" cy="{hair_min_y + hair_h * 0.22:.1f}" rx="{hair_w * 0.30:.1f}" ry="{hair_h * 0.24:.1f}" fill="{hair_sheen}" opacity="0.20" />'
        f'<ellipse cx="{hair_cx + hair_w * 0.10:.1f}" cy="{hair_max_y - hair_h * 0.12:.1f}" rx="{hair_w * 0.54:.1f}" ry="{hair_h * 0.20:.1f}" fill="{palette.hair_shadow}" opacity="0.18" />'
        f"</g>"
        f"</g>"
    )
    if show_ears:
        layers.append(
            f'<g id="ears">'
            f'<path id="left-ear" d="{left_ear_path}" fill="{ear_fill}" stroke="{frame_color}" stroke-width="{stroke_w * 0.24:.1f}" stroke-linejoin="round" opacity="0.96" />'
            f'<path id="right-ear" d="{right_ear_path}" fill="{ear_fill}" stroke="{frame_color}" stroke-width="{stroke_w * 0.24:.1f}" stroke-linejoin="round" opacity="0.96" />'
            f'<path d="{left_ear_inner_path}" fill="none" stroke="{ear_inner}" stroke-width="{stroke_w * 0.16:.1f}" stroke-linecap="round" opacity="0.60" />'
            f'<path d="{right_ear_inner_path}" fill="none" stroke="{ear_inner}" stroke-width="{stroke_w * 0.16:.1f}" stroke-linecap="round" opacity="0.60" />'
            f"</g>"
        )
    layers.append(
        f'<g id="face-base" filter="url(#portrait-shadow)">'
        f'<path id="face-shape" d="{face_path}" fill="url(#skin-gradient)" stroke="{face_outline}" stroke-width="{stroke_w * 0.18:.1f}" stroke-linejoin="round" opacity="0.98" />'
        f'{clipped_texture("face-clip", 0.0)}'
        f'<path d="{face_path}" fill="none" stroke="{palette.highlight}" stroke-width="{stroke_w * 0.10:.1f}" opacity="0.24" />'
        f'<g clip-path="url(#face-clip)">'
        f'<ellipse cx="{forehead_glow[0]:.1f}" cy="{forehead_glow[1]:.1f}" rx="{face_w * 0.28:.1f}" ry="{face_h * 0.20:.1f}" fill="{palette.highlight}" opacity="0.16" />'
        f'<ellipse cx="{face_cx - face_w * 0.26:.1f}" cy="{face_cy - face_h * 0.14:.1f}" rx="{face_w * 0.14:.1f}" ry="{face_h * 0.20:.1f}" fill="{palette.skin_shadow}" opacity="0.12" />'
        f'<ellipse cx="{face_cx + face_w * 0.26:.1f}" cy="{face_cy - face_h * 0.14:.1f}" rx="{face_w * 0.14:.1f}" ry="{face_h * 0.20:.1f}" fill="{palette.skin_shadow}" opacity="0.12" />'
        f'<ellipse cx="{left_cheek[0]:.1f}" cy="{left_cheek[1]:.1f}" rx="{face_w * 0.16:.1f}" ry="{face_h * 0.11:.1f}" fill="{blush_color}" opacity="0.10" />'
        f'<ellipse cx="{right_cheek[0]:.1f}" cy="{right_cheek[1]:.1f}" rx="{face_w * 0.16:.1f}" ry="{face_h * 0.11:.1f}" fill="{blush_color}" opacity="0.10" />'
        f'<ellipse cx="{left_eye_center[0]:.1f}" cy="{left_eye_center[1] + face_h * 0.08:.1f}" rx="{face_w * 0.10:.1f}" ry="{face_h * 0.06:.1f}" fill="{under_eye_color}" opacity="0.10" />'
        f'<ellipse cx="{right_eye_center[0]:.1f}" cy="{right_eye_center[1] + face_h * 0.08:.1f}" rx="{face_w * 0.10:.1f}" ry="{face_h * 0.06:.1f}" fill="{under_eye_color}" opacity="0.10" />'
        f'<ellipse cx="{face_cx:.1f}" cy="{jaw_shadow_y:.1f}" rx="{face_w * 0.26:.1f}" ry="{face_h * 0.10:.1f}" fill="{palette.shadow}" opacity="0.12" />'
        f'<ellipse cx="{mouth_center[0]:.1f}" cy="{mouth_center[1] + mouth_h * 0.95:.1f}" rx="{mouth_w * 0.30:.1f}" ry="{mouth_h * 0.18:.1f}" fill="{under_lip_shadow}" opacity="0.14" />'
        f"</g>"
        f"</g>"
    )
    layers.append(
        f'<g id="face-planes" clip-path="url(#face-clip)">'
        f'<path d="{left_forehead_plane}" fill="{plane_mid}" opacity="0.18" />'
        f'<path d="{right_forehead_plane}" fill="{plane_mid}" opacity="0.18" />'
        f'<path d="{left_cheek_plane}" fill="{plane_dark}" opacity="0.22" />'
        f'<path d="{right_cheek_plane}" fill="{plane_dark}" opacity="0.22" />'
        f'<path d="{nose_plane_path}" fill="{plane_light}" opacity="0.20" />'
        f'<path d="{chin_plane_path}" fill="{plane_mid}" opacity="0.16" />'
        f'<path d="{face_frame_path}" fill="none" stroke="{frame_color}" stroke-width="{stroke_w * 0.10:.1f}" stroke-linejoin="round" opacity="0.14" />'
        f'<path d="{face_frame_path}" fill="none" stroke="{frame_highlight}" stroke-width="{stroke_w * 0.05:.1f}" stroke-linejoin="round" opacity="0.08" />'
        f"</g>"
    )
    brow_width = stroke_w * {"arched": 0.92, "soft": 0.84, "flat": 0.78}.get(presets.brows, 0.84)
    layers.append(
        f'<g id="brows" opacity="1.0">'
        f'<path d="{left_brow_path}" fill="none" stroke="{palette.skin_deep_shadow}" stroke-width="{brow_width * 1.36:.1f}" stroke-linecap="round" stroke-linejoin="round" opacity="0.28" transform="translate(0 {stroke_w * 0.34:.1f})" />'
        f'<path d="{right_brow_path}" fill="none" stroke="{palette.skin_deep_shadow}" stroke-width="{brow_width * 1.36:.1f}" stroke-linecap="round" stroke-linejoin="round" opacity="0.28" transform="translate(0 {stroke_w * 0.34:.1f})" />'
        f'<path id="left-brow" d="{left_brow_path}" fill="none" stroke="{palette.hair_shadow}" stroke-width="{brow_width:.1f}" stroke-linecap="round" stroke-linejoin="round" />'
        f'<path id="right-brow" d="{right_brow_path}" fill="none" stroke="{palette.hair_shadow}" stroke-width="{brow_width:.1f}" stroke-linecap="round" stroke-linejoin="round" />'
        f"</g>"
    )
    layers.append(
        f'<g id="eyes" opacity="0.92">'
        f'{build_eye_markup("left", left_eye_path, left_eye_center, left_eye_bounds, left_upper_lid_path, "left-iris-gradient")}'
        f'{build_eye_markup("right", right_eye_path, right_eye_center, right_eye_bounds, right_upper_lid_path, "right-iris-gradient")}'
        f'</g>'
    )
    layers.append(
        f'<g id="nose" opacity="0.95">'
        f'<path d="{nose_bridge_path}" fill="none" stroke="{palette.skin_deep_shadow}" stroke-width="{stroke_w * 0.42:.1f}" stroke-linecap="round" stroke-linejoin="round" opacity="0.26" transform="translate({stroke_w * 0.18:.1f} {stroke_w * 0.44:.1f})" />'
        f'<path id="nose-bridge" d="{nose_bridge_path}" fill="none" stroke="{palette.skin_shadow}" stroke-width="{stroke_w * 0.34:.1f}" stroke-linecap="round" stroke-linejoin="round" />'
        f'<path d="{nose_bridge_path}" fill="none" stroke="{nose_light}" stroke-width="{stroke_w * 0.14:.1f}" stroke-linecap="round" stroke-linejoin="round" opacity="0.58" />'
        f'<path id="nose-base" d="{nose_base_path}" fill="none" stroke="{palette.skin_deep_shadow}" stroke-width="{stroke_w * 0.28:.1f}" stroke-linecap="round" stroke-linejoin="round" opacity="0.76" />'
        f'<ellipse cx="{nose_tip[0]:.1f}" cy="{nose_tip[1] + face_h * 0.01:.1f}" rx="{max(4.0, nose_w * 0.16):.1f}" ry="{max(2.8, face_h * 0.018):.1f}" fill="{palette.highlight}" opacity="0.22" />'
        f'<path d="{philtrum_path}" fill="none" stroke="{palette.skin_shadow}" stroke-width="{stroke_w * 0.15:.1f}" stroke-linecap="round" opacity="0.52" />'
        f"</g>"
    )
    layers.append(
        f'<g id="mouth" opacity="0.92">'
        f'<path id="outer-lip" d="{mouth_outer_path}" fill="url(#lip-gradient)" stroke="{palette.lip_dark}" stroke-width="{stroke_w * 0.26:.1f}" opacity="0.98" stroke-linejoin="round" />'
        f'<g clip-path="url(#mouth-clip)">'
        f'<ellipse cx="{mouth_center[0]:.1f}" cy="{mouth_center[1] - mouth_h * 0.18:.1f}" rx="{mouth_w * 0.46:.1f}" ry="{mouth_h * 0.34:.1f}" fill="{palette.lip_dark}" opacity="0.18" />'
        f'<ellipse cx="{mouth_center[0]:.1f}" cy="{mouth_center[1] + mouth_h * 0.20:.1f}" rx="{mouth_w * 0.34:.1f}" ry="{max(2.2, mouth_h * 0.14):.1f}" fill="{lip_gloss}" opacity="0.46" />'
        f"</g>"
        + (
            f'<path id="inner-mouth" d="{mouth_inner_path}" fill="{mouth_inner_dark}" opacity="0.96" />'
            f'<g clip-path="url(#inner-mouth-clip)">'
            f'<rect x="{mouth_center[0] - mouth_w * 0.21:.1f}" y="{mouth_center[1] - mouth_inner_h * 0.54:.1f}" width="{mouth_w * 0.42:.1f}" height="{max(3.2, mouth_inner_h * 0.34):.1f}" rx="{max(1.6, mouth_inner_h * 0.10):.1f}" fill="{teeth_color}" opacity="0.84" />'
            f'<ellipse cx="{mouth_center[0]:.1f}" cy="{mouth_center[1] + mouth_inner_h * 0.34:.1f}" rx="{mouth_w * 0.21:.1f}" ry="{max(3.0, mouth_inner_h * 0.46):.1f}" fill="{tongue_color}" opacity="0.90" />'
            f"</g>"
            if mouth_is_open
            else f'<path id="inner-mouth" d="{mouth_inner_path}" fill="none" stroke="{palette.lip_dark}" stroke-width="{stroke_w * 0.18:.1f}" opacity="0.72" />'
        )
        + "</g>"
    )
    if front_hair_path:
        layers.append(
            f'<g id="hair-front">'
            f'<path d="{front_hair_path}" fill="url(#hair-gradient)" stroke="{palette.stroke}" stroke-width="{stroke_w * 0.18:.1f}" opacity="0.90" stroke-linejoin="round" />'
            f'{clipped_texture("front-hair-clip", 0.0)}'
            f'<g clip-path="url(#front-hair-clip)">'
            f'<ellipse cx="{head_cx - head_w * 0.10:.1f}" cy="{head_min_y + head_h * 0.22:.1f}" rx="{head_w * 0.22:.1f}" ry="{head_h * 0.16:.1f}" fill="{hair_sheen}" opacity="0.22" />'
            f'<ellipse cx="{head_cx + head_w * 0.08:.1f}" cy="{face_cy - face_h * 0.24:.1f}" rx="{head_w * 0.18:.1f}" ry="{head_h * 0.11:.1f}" fill="{palette.hair_shadow}" opacity="0.16" />'
            f"</g>"
            f"</g>"
        )

    svg = (
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" viewBox="0 0 {size} {size}">'
        + "".join(layers)
        + "</svg>"
    )

    rig = {
        "render_style": "game_stylized_v6_illustrated",
        "canvas": {"size": size},
        "crop": mapping_info,
        "layer_order": [
            "background",
            "clothes",
            "neck",
            "neck-frame",
            "hair-back",
            "ears",
            "face-base",
            "face-planes",
            "brows",
            "eyes",
            "nose",
            "mouth",
            "hair-front",
        ],
        "anchors": {
            "head_center": {"x": round(head_cx, 2), "y": round(head_cy, 2)},
            "face_center": {"x": round(face_cx, 2), "y": round(face_cy, 2)},
            "left_eye": {"x": round(left_eye_center[0], 2), "y": round(left_eye_center[1], 2)},
            "right_eye": {"x": round(right_eye_center[0], 2), "y": round(right_eye_center[1], 2)},
            "left_brow": {"x": round(brow_left_center[0], 2), "y": round(brow_left_center[1], 2)},
            "right_brow": {"x": round(brow_right_center[0], 2), "y": round(brow_right_center[1], 2)},
            "nose_tip": {"x": round(map_landmark(2)[0], 2), "y": round(map_landmark(2)[1], 2)},
            "mouth_center": {"x": round(mouth_center[0], 2), "y": round(mouth_center[1], 2)},
            "chin": {"x": round(map_landmark(152)[0], 2), "y": round(map_landmark(152)[1], 2)},
        },
        "metrics": {
            "head_width": round(head_w, 2),
            "face_width": round(face_w, 2),
            "face_height": round(face_h, 2),
            "eye_distance": round(eye_distance, 2),
            "rotation_degrees": round(head_rotation_deg, 3),
            "blink_hint": round(1.0 - clamp(measurements.eye_open_ratio / 0.42, 0.0, 1.0), 3),
            "jaw_open_hint": round(clamp(measurements.mouth_open_ratio / 0.060, 0.0, 1.0), 3),
            "smile_hint": round(clamp((measurements.smile_ratio + 0.02) / 0.12, 0.0, 1.0), 3),
        },
        "paths": {
            "head": points_to_dicts(head_points),
            "hair": points_to_dicts(hair_shape_points),
            "face": points_to_dicts(face_points),
            "left_brow": points_to_dicts(left_brow),
            "right_brow": points_to_dicts(right_brow),
            "left_eye": points_to_dicts(left_eye),
            "right_eye": points_to_dicts(right_eye),
            "outer_lip": points_to_dicts(outer_lip),
            "inner_lip": points_to_dicts(inner_lip),
            "left_ear": points_to_dicts(left_ear_points),
            "right_ear": points_to_dicts(right_ear_points),
            "face_frame": points_to_dicts(face_frame_points),
            "neck_frame": points_to_dicts([neck_top_left, neck_left_base, neck_center_base, neck_right_base, neck_top_right]),
            "front_hair": points_to_dicts(front_hair_points),
            "clothes": points_to_dicts(clothes_points),
        },
        "animation_presets": {
            "mouth": mouth_variants(outer_lip, inner_lip, mouth_center),
            "left_eye": eye_variants(left_eye, average_xy(left_eye)),
            "right_eye": eye_variants(right_eye, average_xy(right_eye)),
        },
    }
    return svg, rig




def render_avatar_svg_vector_head(
    size: int,
    image_rgb: np.ndarray,
    landmarks: list[tuple[float, float]],
    segmentation: SegmentationData,
    measurements: AvatarMeasurements,
    presets: AvatarPresets,
    palette: AvatarPalette,
) -> tuple[str, dict[str, object]]:
    h, w = segmentation.face_mask.shape[:2]
    mapper, mapping_info = build_canvas_mapper(segmentation.crop_bbox, size)
    p = lambda idx: mapper(landmark_to_pixel(landmarks[idx], w, h))
    mix = lambda a, b, t: rgb_to_hex(blend_rgb(hex_to_rgb(a), hex_to_rgb(b), t))

    def style_points(
        points: list[tuple[float, float]],
        center: tuple[float, float],
        scale_x: float,
        scale_y: float,
        upper_shift: float = 0.0,
        lower_shift: float = 0.0,
    ) -> list[tuple[float, float]]:
        cx, cy = center
        result: list[tuple[float, float]] = []
        for x, y in points:
            nx = cx + (x - cx) * scale_x
            ny = cy + (y - cy) * scale_y
            ny += upper_shift if y < cy else lower_shift
            result.append((nx, ny))
        return result

    head = smooth_points([mapper(pt) for pt in segmentation.head_contour], passes=1, closed=True)
    face_seed = smooth_points([mapper(pt) for pt in segmentation.face_contour], passes=1, closed=True) if segmentation.face_contour else head
    hair_seed = smooth_points([mapper(pt) for pt in segmentation.hair_contour], passes=1, closed=True) if segmentation.hair_contour else []
    front_seed = smooth_points([mapper(pt) for pt in segmentation.front_hair_contour], passes=1, closed=True) if segmentation.front_hair_contour else []

    lx, ly = average_xy([p(i) for i in LEFT_IRIS_IDX])
    rx, ry = average_xy([p(i) for i in RIGHT_IRIS_IDX])
    mx, my = average_xy([p(i) for i in INNER_LIP_IDX])
    outer_lip_lm = [p(i) for i in OUTER_LIP_IDX]
    inner_lip_lm = [p(i) for i in INNER_LIP_IDX]
    bx_l = average_xy([p(i) for i in LEFT_BROW_IDX])
    bx_r = average_xy([p(i) for i in RIGHT_BROW_IDX])
    temple_l, temple_r = p(127), p(356)
    cheek_l, cheek_r = p(93), p(323)
    jaw_l, jaw_r = p(172), p(397)
    chin = p(152)
    nose_root, nose_tip, nose_l, nose_r = p(168), p(2), p(98), p(327)
    mouth_l, mouth_r, upper_lip = p(61), p(291), p(0)
    upper_lip_center = average_xy([upper_lip, (mx, my)])

    fx0, fy0, fx1, fy1 = point_bounds(face_seed)
    hx0, hy0, hx1, hy1 = point_bounds(head)
    fc = average_xy(face_seed)
    hc = average_xy(head)
    face_w, face_h = fx1 - fx0, fy1 - fy0
    head_w, head_h = hx1 - hx0, hy1 - hy0
    stroke = max(2.4, head_w * 0.010)
    rot = math.degrees(math.atan2(ry - ly, rx - lx))
    eye_dist = math.hypot(rx - lx, ry - ly)

    face = []
    for x, y in resample_polyline(face_seed, 32, True):
        dx = x - fc[0]
        dy = y - fc[1]
        nx = fc[0] + dx * (1.02 if dy < 0 else 1.06)
        ny = fc[1] + dy * (0.98 if dy < 0 else 0.94)
        if dy > face_h * 0.34:
            ny -= face_h * 0.015
        face.append((nx, ny))
    face = smooth_points(face, passes=1, closed=True)
    fx0, fy0, fx1, fy1 = point_bounds(face)
    face_w, face_h = fx1 - fx0, fy1 - fy0

    hair_cov = float(segmentation.hair_mask.sum()) / max(1.0, float(segmentation.face_mask.sum()))
    facial_hair = estimate_facial_hair(image_rgb, landmarks, segmentation)
    has_hair = hair_cov > 0.16 and len(hair_seed) >= 8
    show_ears = hair_cov < 0.55
    show_beard = facial_hair['beard'] > 0.24
    show_mustache = facial_hair['mustache'] > 0.22

    hair = []
    if has_hair:
        hair_samples = []
        for x, y in resample_polyline(hair_seed, 36, True):
            if y <= fy0 + face_h * 0.20 or x <= fx0 + face_w * 0.10 or x >= fx1 - face_w * 0.10:
                top = clamp((fy0 + face_h * 0.12 - y) / max(head_h * 0.42, EPS), 0.0, 1.0)
                hair_samples.append((hc[0] + (x - hc[0]) * 1.02, y - top * head_h * 0.03))
        if len(hair_samples) >= 8:
            hair = smooth_points(resample_polyline(hair_samples, 24, True), passes=1, closed=True)
    front_hair = []
    if hair:
        hair_top = min(hair, key=lambda point: point[1])
        hair_left_peak = min((point for point in hair if point[0] < fc[0]), key=lambda point: point[1], default=hair_top)
        hair_right_peak = min((point for point in hair if point[0] > fc[0]), key=lambda point: point[1], default=hair_top)
        front_hair = smooth_points([
            (temple_l[0] - face_w * 0.04, temple_l[1] - face_h * 0.04),
            hair_left_peak,
            hair_top,
            hair_right_peak,
            (temple_r[0] + face_w * 0.02, temple_r[1] - face_h * 0.02),
            (fc[0] + face_w * 0.14, fy0 + face_h * 0.06),
            (fc[0] - face_w * 0.10, fy0 + face_h * 0.04),
        ], passes=1, closed=True)

    neck_top = fy1 - face_h * 0.02
    if segmentation.neck_contour:
        neck_seed = smooth_points([mapper(pt) for pt in segmentation.neck_contour], passes=1, closed=True)
        _, _, _, neck_seed_bottom = point_bounds(neck_seed)
    else:
        neck_seed_bottom = fy1 + head_h * 0.18
    neck_base = min(neck_seed_bottom, fy1 + head_h * 0.18)
    neck = smooth_points([
        (fc[0] - face_w * 0.18, neck_top),
        (fc[0] + face_w * 0.18, neck_top),
        (fc[0] + face_w * 0.30, neck_base),
        (fc[0] + face_w * 0.10, neck_base + head_h * 0.04),
        (fc[0] - face_w * 0.10, neck_base + head_h * 0.04),
        (fc[0] - face_w * 0.30, neck_base),
    ], passes=1, closed=True)
    if segmentation.clothes_contour:
        torso = smooth_points([mapper(pt) for pt in segmentation.clothes_contour], passes=2, closed=True)
        torso = resample_polyline(torso, 44, True)
        tx0, ty0, tx1, ty1 = point_bounds(torso)
        torso_mid_x = (tx0 + tx1) / 2.0
        torso = smooth_points([
            (
                torso_mid_x + (x - torso_mid_x) * (1.06 if y > ty0 + (ty1 - ty0) * 0.18 else 1.02),
                y,
            )
            for x, y in torso
        ], passes=1, closed=True)
        torso_top = ty0
        torso_bot = ty1
    else:
        torso_top = neck_base - head_h * 0.02
        shoulder_y = neck_base + head_h * 0.06
        torso_bot = min(size * 0.92, neck_base + head_h * 0.34)
        torso = smooth_points([
            (fc[0] - face_w * 0.34, torso_top),
            (fc[0] - head_w * 0.78, shoulder_y),
            (fc[0] - head_w * 0.84, torso_bot - head_h * 0.08),
            (fc[0] - head_w * 0.48, torso_bot),
            (fc[0] + head_w * 0.48, torso_bot),
            (fc[0] + head_w * 0.84, torso_bot - head_h * 0.08),
            (fc[0] + head_w * 0.78, shoulder_y),
            (fc[0] + face_w * 0.34, torso_top),
        ], passes=1, closed=True)
    collar_outer = smooth_points(oval_points((fc[0], neck_base + head_h * 0.01), face_w * 0.48, head_h * 0.11, 18), passes=1, closed=True)
    collar_inner = smooth_points(oval_points((fc[0], neck_base + head_h * 0.01), face_w * 0.31, head_h * 0.055, 18), passes=1, closed=True)

    ear_rx, ear_ry = face_w * 0.085, face_h * 0.14
    ear_l = smooth_points(oval_points((fx0 - ear_rx * 0.05, ly + face_h * 0.05), ear_rx, ear_ry, 12), passes=1, closed=True)
    ear_r = smooth_points(oval_points((fx1 + ear_rx * 0.05, ry + face_h * 0.05), ear_rx, ear_ry, 12), passes=1, closed=True)

    def mk_eye(center, raw):
        ex0, ey0, ex1, ey1 = point_bounds(raw)
        ew = max(ex1 - ex0, face_w * 0.16)
        eh = max(ey1 - ey0, face_h * 0.045) * clamp(0.98 + measurements.eye_open_ratio * 0.62, 0.94, 1.28)
        tilt = clamp((raw[-1][1] - raw[0][1]) / max(ew, EPS), -0.10, 0.10)
        cx, cy = center
        pts = smooth_points([
            (cx - ew * 0.62, cy + tilt * ew * 0.18),
            (cx - ew * 0.24, cy - eh * 0.52 + tilt * ew * 0.18),
            (cx + ew * 0.22, cy - eh * 0.44 - tilt * ew * 0.04),
            (cx + ew * 0.62, cy - tilt * ew * 0.18),
            (cx + ew * 0.20, cy + eh * 0.32),
            (cx - ew * 0.26, cy + eh * 0.34),
        ], passes=1, closed=True)
        return pts, ew, eh

    def mk_brow(center, ew, side):
        cx, cy = center
        arch = {'arched': -face_h * 0.022, 'soft': -face_h * 0.014, 'flat': -face_h * 0.006}.get(presets.brows, -face_h * 0.012)
        slant = -face_h * 0.006 if side == 'left' else face_h * 0.006
        bh = max(face_h * 0.040, stroke * 0.92)
        return smooth_points([
            (cx - ew * 0.68, cy + bh * 0.18),
            (cx - ew * 0.22, cy - bh * 0.56 + arch),
            (cx + ew * 0.64, cy - bh * 0.18 + slant),
            (cx + ew * 0.50, cy + bh * 0.34),
        ], passes=1, closed=True)

    eye_l0 = [p(i) for i in LEFT_EYE_IDX]
    eye_r0 = [p(i) for i in RIGHT_EYE_IDX]
    eye_l, eye_lw, eye_lh = mk_eye((lx, ly), eye_l0)
    eye_r, eye_rw, eye_rh = mk_eye((rx, ry), eye_r0)
    brow_l = mk_brow(bx_l, eye_lw, 'left')
    brow_r = mk_brow(bx_r, eye_rw, 'right')

    outer = smooth_points(style_points(outer_lip_lm, (mx, my), 0.84, 1.00 if presets.expression == 'open' else 0.62, -face_h * 0.004, face_h * 0.004), passes=1, closed=True)
    inner = smooth_points(style_points(inner_lip_lm, (mx, my), 0.66, 1.18 if presets.expression == 'open' else 0.16, -face_h * 0.004, face_h * 0.004), passes=1, closed=True)
    mx0, my0, mx1, my1 = point_bounds(outer)
    mouth_w, mouth_h = mx1 - mx0, my1 - my0
    mouth_inner_h = max(pt[1] for pt in inner) - min(pt[1] for pt in inner)
    mouth_open = mouth_inner_h > face_h * 0.030 or presets.expression == 'open'

    beard, mustache = [], []
    if show_beard:
        beard = smooth_points([
            (fx0 + face_w * 0.18, my + face_h * 0.04),
            (mouth_l[0] - mouth_w * 0.14, my + mouth_h * 0.16),
            (fc[0] - face_w * 0.20, fy1 - face_h * 0.05),
            (fc[0] - face_w * 0.08, chin[1] - face_h * 0.02),
            chin,
            (fc[0] + face_w * 0.08, chin[1] - face_h * 0.02),
            (fc[0] + face_w * 0.20, fy1 - face_h * 0.05),
            (mouth_r[0] + mouth_w * 0.14, my + mouth_h * 0.16),
            (fx1 - face_w * 0.18, my + face_h * 0.04),
        ], passes=1, closed=True)
    if show_mustache:
        mustache = smooth_points([
            (mouth_l[0] - mouth_w * 0.04, nose_tip[1] + face_h * 0.04),
            (mx - mouth_w * 0.22, nose_tip[1] + face_h * 0.01),
            (mx - mouth_w * 0.08, upper_lip[1] + face_h * 0.01),
            (mx, upper_lip[1] + face_h * 0.02),
            (mx + mouth_w * 0.08, upper_lip[1] + face_h * 0.01),
            (mx + mouth_w * 0.22, nose_tip[1] + face_h * 0.01),
            (mouth_r[0] + mouth_w * 0.04, nose_tip[1] + face_h * 0.04),
            (mx + mouth_w * 0.10, upper_lip[1] + face_h * 0.06),
            (mx, upper_lip[1] + face_h * 0.07),
            (mx - mouth_w * 0.10, upper_lip[1] + face_h * 0.06),
        ], passes=1, closed=True)

    bg_a, bg_b = mix(palette.background_accent, '#ffffff', 0.34), mix(palette.background, palette.background_accent, 0.12)
    face_edge, shirt_edge = mix(palette.stroke, palette.skin_shadow, 0.30), mix(palette.stroke, palette.shirt_shadow, 0.28)
    hair_edge, brow_fill = mix(palette.stroke, palette.hair_shadow, 0.24), mix(palette.hair_shadow, palette.stroke, 0.12)
    skin_hi, side_shadow = mix(palette.highlight, palette.skin_light, 0.38), mix(palette.skin_shadow, palette.shadow, 0.40)
    neck_shadow, hair_fill = mix(palette.skin_shadow, palette.skin_deep_shadow, 0.34), mix(palette.hair, palette.hair_shadow, 0.10)
    hair_shadow = mix(palette.hair_shadow, palette.stroke, 0.16)
    shirt_fill, shirt_shadow = mix(palette.shirt, palette.shirt_highlight, 0.10), mix(palette.shirt_shadow, palette.stroke, 0.16)
    ear_fill, ear_inner = mix(palette.skin, palette.skin_shadow, 0.10), mix(palette.skin_deep_shadow, palette.lip_dark, 0.14)
    lip_fill, lip_dark = mix(palette.lip, palette.skin_light, 0.10), mix(palette.lip_dark, palette.stroke, 0.10)
    beard_fill = mix(palette.hair_shadow, palette.stroke, 0.18)

    face_path, neck_path, torso_path = closed_smooth_path(face), closed_smooth_path(neck), closed_smooth_path(torso)
    hair_path = closed_smooth_path(hair) if hair else ''
    front_path = closed_smooth_path(front_hair) if front_hair else ''
    ear_l_path, ear_r_path = closed_smooth_path(ear_l), closed_smooth_path(ear_r)
    eye_l_path, eye_r_path = closed_smooth_path(eye_l), closed_smooth_path(eye_r)
    brow_l_path, brow_r_path = closed_smooth_path(brow_l), closed_smooth_path(brow_r)
    outer_path, inner_path = closed_smooth_path(outer), closed_smooth_path(inner)
    beard_path = closed_smooth_path(beard) if beard else ''
    mustache_path = closed_smooth_path(mustache) if mustache else ''
    collar_outer_path, collar_inner_path = closed_smooth_path(collar_outer), closed_smooth_path(collar_inner)
    shoulder_shadow_path = closed_smooth_path(smooth_points(oval_points((fc[0], torso_top + head_h * 0.16), head_w * 0.62, head_h * 0.16, 18), passes=1, closed=True))
    neck_shadow_path = polygon_path([(fc[0] - face_w * 0.08, neck_top + head_h * 0.02), (fc[0] + face_w * 0.08, neck_top + head_h * 0.02), (fc[0] + face_w * 0.12, neck_base), (fc[0], neck_base + head_h * 0.02), (fc[0] - face_w * 0.12, neck_base)])
    left_panel = polygon_path([(fx0 + face_w * 0.04, fy0 + face_h * 0.08), (fc[0] - face_w * 0.04, fy0 + face_h * 0.05), (nose_root[0] - face_w * 0.03, nose_root[1] + face_h * 0.05), (mx - mouth_w * 0.34, my + face_h * 0.08), (fc[0] - face_w * 0.18, fy1 - face_h * 0.06), (fx0 + face_w * 0.02, fc[1] + face_h * 0.18)])
    right_panel = polygon_path([(fx1 - face_w * 0.04, fy0 + face_h * 0.08), (fc[0] + face_w * 0.04, fy0 + face_h * 0.05), (nose_root[0] + face_w * 0.03, nose_root[1] + face_h * 0.05), (mx + mouth_w * 0.34, my + face_h * 0.08), (fc[0] + face_w * 0.18, fy1 - face_h * 0.06), (fx1 - face_w * 0.02, fc[1] + face_h * 0.18)])
    center_panel = polygon_path([(fc[0] - face_w * 0.10, fy0 + face_h * 0.10), (fc[0] + face_w * 0.10, fy0 + face_h * 0.12), (nose_r[0], nose_tip[1] + face_h * 0.05), (mx + mouth_w * 0.14, my + face_h * 0.05), (mx - mouth_w * 0.14, my + face_h * 0.05), (nose_l[0], nose_tip[1] + face_h * 0.05)])
    hair_chunk_l = polygon_path([(hc[0] - head_w * 0.28, hy0 + head_h * 0.18), (hc[0] - head_w * 0.08, hy0 + head_h * 0.02), (hc[0], fy0 + face_h * 0.10), (hc[0] - head_w * 0.16, fy0 + face_h * 0.20)]) if hair else ''
    hair_chunk_r = polygon_path([(hc[0] + head_w * 0.04, hy0 + head_h * 0.12), (hc[0] + head_w * 0.36, hy0 + head_h * 0.08), (hc[0] + head_w * 0.28, fc[1] + face_h * 0.10), (hc[0] + head_w * 0.10, fc[1] - face_h * 0.02)]) if hair else ''

    defs = [
        '<defs>',
        f'<filter id="soft-shadow" x="-25%" y="-25%" width="150%" height="150%"><feDropShadow dx="0" dy="{stroke * 0.54:.1f}" stdDeviation="{max(1.8, stroke * 0.62):.1f}" flood-color="{palette.shadow}" flood-opacity="0.18" /></filter>',
        f'<linearGradient id="bg-gradient" x1="0" y1="0" x2="0" y2="{size:.1f}" gradientUnits="userSpaceOnUse"><stop offset="0%" stop-color="{bg_a}" /><stop offset="100%" stop-color="{bg_b}" /></linearGradient>',
        f'<linearGradient id="skin-gradient" x1="{fc[0] - face_w * 0.34:.1f}" y1="{fy0:.1f}" x2="{fc[0] + face_w * 0.30:.1f}" y2="{fy1:.1f}" gradientUnits="userSpaceOnUse"><stop offset="0%" stop-color="{palette.skin_light}" /><stop offset="55%" stop-color="{palette.skin}" /><stop offset="100%" stop-color="{palette.skin_shadow}" /></linearGradient>',
        f'<linearGradient id="shirt-gradient" x1="{fc[0]:.1f}" y1="{torso_top:.1f}" x2="{fc[0]:.1f}" y2="{torso_bot:.1f}" gradientUnits="userSpaceOnUse"><stop offset="0%" stop-color="{mix(palette.shirt_highlight, palette.highlight, 0.12)}" /><stop offset="45%" stop-color="{shirt_fill}" /><stop offset="100%" stop-color="{shirt_shadow}" /></linearGradient>',
        f'<linearGradient id="lip-gradient" x1="{mx:.1f}" y1="{my0:.1f}" x2="{mx:.1f}" y2="{my1:.1f}" gradientUnits="userSpaceOnUse"><stop offset="0%" stop-color="{mix(palette.lip_highlight, "#ffffff", 0.18)}" /><stop offset="42%" stop-color="{lip_fill}" /><stop offset="100%" stop-color="{lip_dark}" /></linearGradient>',
        f'<radialGradient id="left-iris-gradient" cx="{lx - eye_lw * 0.10:.1f}" cy="{ly - eye_lh * 0.24:.1f}" r="{eye_lw * 0.54:.1f}" gradientUnits="userSpaceOnUse"><stop offset="0%" stop-color="{palette.highlight}" /><stop offset="30%" stop-color="{palette.iris}" /><stop offset="100%" stop-color="{mix(palette.iris_dark, palette.stroke, 0.12)}" /></radialGradient>',
        f'<radialGradient id="right-iris-gradient" cx="{rx - eye_rw * 0.10:.1f}" cy="{ry - eye_rh * 0.24:.1f}" r="{eye_rw * 0.54:.1f}" gradientUnits="userSpaceOnUse"><stop offset="0%" stop-color="{palette.highlight}" /><stop offset="30%" stop-color="{palette.iris}" /><stop offset="100%" stop-color="{mix(palette.iris_dark, palette.stroke, 0.12)}" /></radialGradient>',
        f'<clipPath id="face-clip"><path d="{face_path}" /></clipPath>',
        f'<clipPath id="left-eye-clip"><path d="{eye_l_path}" /></clipPath>',
        f'<clipPath id="right-eye-clip"><path d="{eye_r_path}" /></clipPath>',
        f'<clipPath id="mouth-clip"><path d="{outer_path}" /></clipPath>',
        f'<clipPath id="inner-mouth-clip"><path d="{inner_path}" /></clipPath>',
    ]
    if hair:
        defs += [
            f'<linearGradient id="hair-gradient" x1="{hc[0] - head_w * 0.32:.1f}" y1="{hy0:.1f}" x2="{hc[0] + head_w * 0.24:.1f}" y2="{hy1:.1f}" gradientUnits="userSpaceOnUse"><stop offset="0%" stop-color="{mix(palette.hair_highlight, palette.skin_light, 0.10)}" /><stop offset="42%" stop-color="{hair_fill}" /><stop offset="100%" stop-color="{hair_shadow}" /></linearGradient>',
            f'<clipPath id="hair-clip"><path d="{hair_path}" /></clipPath>',
        ]
        if front_path:
            defs.append(f'<clipPath id="front-hair-clip"><path d="{front_path}" /></clipPath>')
    defs.append('</defs>')

    eye_markup = lambda side, path, pts, cx, cy, ew, eh, grad: (
        f'<path id="{side}-eye" d="{path}" fill="{palette.eye_white}" stroke="{face_edge}" stroke-width="{stroke * 0.14:.1f}" stroke-linejoin="round" />'
        f'<g clip-path="url(#{side}-eye-clip)"><ellipse cx="{cx:.1f}" cy="{cy:.1f}" rx="{max(4.2, ew * 0.21):.1f}" ry="{max(max(4.2, ew * 0.21) * 0.98, eh * 0.54):.1f}" fill="url(#{grad})" /><circle cx="{cx:.1f}" cy="{cy:.1f}" r="{max(2.2, max(4.2, ew * 0.21) * 0.42):.1f}" fill="{palette.stroke}" /><circle cx="{cx - ew * 0.06:.1f}" cy="{cy - eh * 0.14:.1f}" r="{max(1.2, ew * 0.05):.1f}" fill="#ffffff" opacity="0.95" /></g>'
        f'<path d="{open_smooth_path(pts[:4])}" fill="none" stroke="{hair_shadow if hair else face_edge}" stroke-width="{stroke * 0.28:.1f}" stroke-linecap="round" />'
    )

    layers = [
        ''.join(defs),
        f'<g id="background"><rect width="{size}" height="{size}" fill="url(#bg-gradient)" /><ellipse cx="{hc[0]:.1f}" cy="{hc[1] - head_h * 0.02:.1f}" rx="{head_w * 0.98:.1f}" ry="{head_h * 0.90:.1f}" fill="{mix(palette.background_accent, palette.highlight, 0.34)}" opacity="0.20" /></g>',
        f'<g id="torso" filter="url(#soft-shadow)"><path id="shirt" d="{torso_path}" fill="url(#shirt-gradient)" stroke="{shirt_edge}" stroke-width="{stroke * 0.20:.1f}" stroke-linejoin="round" /><path d="{shoulder_shadow_path}" fill="{shirt_shadow}" opacity="0.10" /><path d="{collar_outer_path}" fill="{shirt_shadow}" opacity="0.12" /><path d="{collar_inner_path}" fill="none" stroke="{mix(palette.shirt_highlight, palette.highlight, 0.12)}" stroke-width="{stroke * 0.22:.1f}" opacity="0.42" /></g>',
        f'<g id="neck"><path id="neck-shape" d="{neck_path}" fill="url(#skin-gradient)" stroke="{face_edge}" stroke-width="{stroke * 0.16:.1f}" stroke-linejoin="round" /><path d="{neck_shadow_path}" fill="{neck_shadow}" opacity="0.22" /></g>',
    ]
    if hair:
        layers.append(f'<g id="hair-back" filter="url(#soft-shadow)"><path id="hair-shape" d="{hair_path}" fill="url(#hair-gradient)" stroke="{hair_edge}" stroke-width="{stroke * 0.20:.1f}" stroke-linejoin="round" /><g clip-path="url(#hair-clip)"><path d="{hair_chunk_l}" fill="{mix(palette.hair_highlight, palette.skin_light, 0.10)}" opacity="0.82" /><path d="{hair_chunk_r}" fill="{hair_shadow}" opacity="0.76" /></g></g>')
    if show_ears:
        layers.append(f'<g id="ears"><path id="left-ear" d="{ear_l_path}" fill="{ear_fill}" stroke="{face_edge}" stroke-width="{stroke * 0.16:.1f}" stroke-linejoin="round" /><path id="right-ear" d="{ear_r_path}" fill="{ear_fill}" stroke="{face_edge}" stroke-width="{stroke * 0.16:.1f}" stroke-linejoin="round" /><path d="{open_smooth_path([ear_l[1], ear_l[3], ear_l[5], ear_l[7]])}" fill="none" stroke="{ear_inner}" stroke-width="{stroke * 0.12:.1f}" opacity="0.56" /><path d="{open_smooth_path([ear_r[1], ear_r[3], ear_r[5], ear_r[7]])}" fill="none" stroke="{ear_inner}" stroke-width="{stroke * 0.12:.1f}" opacity="0.56" /></g>')
    layers.append(f'<g id="face" filter="url(#soft-shadow)"><path id="face-shape" d="{face_path}" fill="url(#skin-gradient)" stroke="{face_edge}" stroke-width="{stroke * 0.20:.1f}" stroke-linejoin="round" /><g clip-path="url(#face-clip)"><path d="{left_panel}" fill="{side_shadow}" opacity="0.16" /><path d="{right_panel}" fill="{side_shadow}" opacity="0.12" /><path d="{center_panel}" fill="{skin_hi}" opacity="0.18" /><ellipse cx="{fc[0]:.1f}" cy="{fy0 + face_h * 0.13:.1f}" rx="{face_w * 0.17:.1f}" ry="{face_h * 0.10:.1f}" fill="{mix(palette.highlight, "#ffffff", 0.26)}" opacity="0.16" /></g></g>')
    if beard or mustache:
        beard_parts = ['<g id="beard" clip-path="url(#face-clip)">']
        if beard_path:
            beard_parts.append(f'<path id="beard-shape" d="{beard_path}" fill="{beard_fill}" opacity="0.98" />')
        if mustache_path:
            beard_parts.append(f'<path id="mustache-shape" d="{mustache_path}" fill="{beard_fill}" opacity="0.98" />')
        beard_parts.append('</g>')
        layers.append(''.join(beard_parts))
    layers += [
        f'<g id="brows"><path id="left-brow" d="{brow_l_path}" fill="{brow_fill}" stroke="{hair_edge if hair else face_edge}" stroke-width="{stroke * 0.10:.1f}" stroke-linejoin="round" /><path id="right-brow" d="{brow_r_path}" fill="{brow_fill}" stroke="{hair_edge if hair else face_edge}" stroke-width="{stroke * 0.10:.1f}" stroke-linejoin="round" /></g>',
        f'<g id="eyes">{eye_markup("left", eye_l_path, eye_l, lx, ly, eye_lw, eye_lh, "left-iris-gradient")}{eye_markup("right", eye_r_path, eye_r, rx, ry, eye_rw, eye_rh, "right-iris-gradient")}</g>',
        f'<g id="nose"><path id="nose-bridge" d="{open_smooth_path([nose_root, lerp_point(nose_root, nose_tip, 0.56), nose_tip])}" fill="none" stroke="{mix(palette.skin_shadow, palette.skin_deep_shadow, 0.18)}" stroke-width="{stroke * 0.18:.1f}" stroke-linecap="round" /><path d="{open_smooth_path([nose_l, nose_tip, nose_r])}" fill="none" stroke="{mix(palette.skin_deep_shadow, palette.shadow, 0.10)}" stroke-width="{stroke * 0.20:.1f}" stroke-linecap="round" stroke-linejoin="round" opacity="0.82" /><path d="{open_smooth_path([nose_tip, midpoint(nose_tip, upper_lip_center), upper_lip_center])}" fill="none" stroke="{mix(palette.skin_shadow, palette.shadow, 0.20)}" stroke-width="{stroke * 0.10:.1f}" stroke-linecap="round" opacity="0.52" /></g>',
    ]
    mouth_parts = [f'<g id="mouth"><path id="outer-lip" d="{outer_path}" fill="url(#lip-gradient)" stroke="{lip_dark}" stroke-width="{stroke * 0.14:.1f}" stroke-linejoin="round" /><g clip-path="url(#mouth-clip)"><ellipse cx="{mx:.1f}" cy="{my + mouth_h * 0.18:.1f}" rx="{mouth_w * 0.28:.1f}" ry="{max(2.0, mouth_h * 0.14):.1f}" fill="{mix(palette.lip_highlight, "#ffffff", 0.18)}" opacity="0.58" /></g>']
    if mouth_open:
        mouth_parts.append(f'<path id="inner-mouth" d="{inner_path}" fill="{mix(lip_dark, palette.shadow, 0.30)}" opacity="0.96" /><g clip-path="url(#inner-mouth-clip)"><rect x="{mx - mouth_w * 0.18:.1f}" y="{my - mouth_inner_h * 0.50:.1f}" width="{mouth_w * 0.36:.1f}" height="{max(3.0, mouth_inner_h * 0.28):.1f}" rx="{max(1.4, mouth_inner_h * 0.10):.1f}" fill="{mix(palette.eye_white, "#ffffff", 0.24)}" opacity="0.88" /><ellipse cx="{mx:.1f}" cy="{my + mouth_inner_h * 0.26:.1f}" rx="{mouth_w * 0.20:.1f}" ry="{max(2.8, mouth_inner_h * 0.42):.1f}" fill="{mix(palette.lip, "#b76b74", 0.30)}" opacity="0.92" /></g>')
    else:
        mouth_parts.append(f'<path id="inner-mouth" d="{inner_path}" fill="none" stroke="{lip_dark}" stroke-width="{stroke * 0.10:.1f}" opacity="0.78" />')
    mouth_parts.append('</g>')
    layers.append(''.join(mouth_parts))
    if hair and front_path:
        layers.append(f'<g id="hair-front"><path d="{front_path}" fill="url(#hair-gradient)" stroke="{hair_edge}" stroke-width="{stroke * 0.16:.1f}" stroke-linejoin="round" /><g clip-path="url(#front-hair-clip)"><path d="{hair_chunk_l}" fill="{mix(palette.hair_highlight, palette.skin_light, 0.10)}" opacity="0.58" /><path d="{hair_chunk_r}" fill="{hair_shadow}" opacity="0.54" /></g></g>')

    svg = f'<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" viewBox="0 0 {size} {size}">{"".join(layers)}</svg>'
    rig = {
        'render_style': 'vector_talking_head_v1',
        'canvas': {'size': size},
        'crop': mapping_info,
        'layer_order': ['background', 'torso', 'neck', 'hair-back', 'ears', 'face', 'beard', 'brows', 'eyes', 'nose', 'mouth', 'hair-front'],
        'anchors': {
            'head_center': {'x': round(hc[0], 2), 'y': round(hc[1], 2)},
            'face_center': {'x': round(fc[0], 2), 'y': round(fc[1], 2)},
            'left_eye': {'x': round(lx, 2), 'y': round(ly, 2)},
            'right_eye': {'x': round(rx, 2), 'y': round(ry, 2)},
            'mouth_center': {'x': round(mx, 2), 'y': round(my, 2)},
            'chin': {'x': round(chin[0], 2), 'y': round(chin[1], 2)},
            'neck_base': {'x': round(fc[0], 2), 'y': round(neck_base, 2)},
        },
        'metrics': {
            'head_width': round(head_w, 2), 'face_width': round(face_w, 2), 'face_height': round(face_h, 2),
            'eye_distance': round(eye_dist, 2), 'rotation_degrees': round(rot, 3),
            'blink_hint': round(1.0 - clamp(measurements.eye_open_ratio / 0.42, 0.0, 1.0), 3),
            'jaw_open_hint': round(clamp(measurements.mouth_open_ratio / 0.060, 0.0, 1.0), 3),
            'smile_hint': round(clamp((measurements.smile_ratio + 0.02) / 0.12, 0.0, 1.0), 3),
            'hair_coverage': round(hair_cov, 3),
        },
        'facial_hair': facial_hair,
        'paths': {
            'head': points_to_dicts(head), 'hair': points_to_dicts(hair), 'face': points_to_dicts(face),
            'left_brow': points_to_dicts(brow_l), 'right_brow': points_to_dicts(brow_r),
            'left_eye': points_to_dicts(eye_l), 'right_eye': points_to_dicts(eye_r),
            'outer_lip': points_to_dicts(outer), 'inner_lip': points_to_dicts(inner),
            'left_ear': points_to_dicts(ear_l), 'right_ear': points_to_dicts(ear_r),
            'neck': points_to_dicts(neck), 'torso': points_to_dicts(torso),
            'front_hair': points_to_dicts(front_hair), 'beard': points_to_dicts(beard), 'mustache': points_to_dicts(mustache),
        },
        'animation_presets': {
            'mouth': mouth_variants(outer, inner, (mx, my)),
            'left_eye': eye_variants(eye_l, average_xy(eye_l)),
            'right_eye': eye_variants(eye_r, average_xy(eye_r)),
        },
    }
    return svg, rig


def render_avatar_svg_reference_head(
    size: int,
    image_rgb: np.ndarray,
    landmarks: list[tuple[float, float]],
    segmentation: SegmentationData,
    measurements: AvatarMeasurements,
    presets: AvatarPresets,
    palette: AvatarPalette,
) -> tuple[str, dict[str, object]]:
    h, w = segmentation.face_mask.shape[:2]
    mapper, mapping_info = build_canvas_mapper(segmentation.crop_bbox, size)
    p = lambda idx: mapper(landmark_to_pixel(landmarks[idx], w, h))
    mix = lambda a, b, t: rgb_to_hex(blend_rgb(hex_to_rgb(a), hex_to_rgb(b), t))

    def style_points(
        points: list[tuple[float, float]],
        center: tuple[float, float],
        scale_x: float,
        scale_y: float,
        upper_shift: float = 0.0,
        lower_shift: float = 0.0,
    ) -> list[tuple[float, float]]:
        cx, cy = center
        result: list[tuple[float, float]] = []
        for x, y in points:
            nx = cx + (x - cx) * scale_x
            ny = cy + (y - cy) * scale_y
            ny += upper_shift if y < cy else lower_shift
            result.append((nx, ny))
        return result

    face_seed = smooth_points([mapper(pt) for pt in segmentation.face_contour], passes=1, closed=True) if segmentation.face_contour else smooth_points([p(i) for i in FACE_CONTOUR_IDX], passes=1, closed=True)
    fc = average_xy(face_seed)
    fx0, fy0, fx1, fy1 = point_bounds(face_seed)
    face_w = fx1 - fx0
    face_h = fy1 - fy0

    lx, ly = average_xy([p(i) for i in LEFT_IRIS_IDX])
    rx, ry = average_xy([p(i) for i in RIGHT_IRIS_IDX])
    bx_l = average_xy([p(i) for i in LEFT_BROW_IDX])
    bx_r = average_xy([p(i) for i in RIGHT_BROW_IDX])
    nose_root, nose_tip, nose_l, nose_r = p(168), p(2), p(98), p(327)
    mouth_l, mouth_r, upper_lip = p(61), p(291), p(0)
    chin = p(152)
    mx, my = average_xy([p(i) for i in INNER_LIP_IDX])
    mouth_center = (mx, my)

    hair_cov = float(segmentation.hair_mask.sum()) / max(1.0, float(segmentation.face_mask.sum()))
    facial_hair = estimate_facial_hair(image_rgb, landmarks, segmentation)
    show_beard = facial_hair["beard"] > 0.22
    show_mustache = facial_hair["mustache"] > 0.18
    hair_points = [mapper(pt) for pt in segmentation.hair_contour] if segmentation.hair_contour else []
    quiff_dir = 1.0
    if hair_points:
        quiff_dir = 1.0 if min(hair_points, key=lambda point: point[1])[0] > fc[0] else -1.0

    face = smooth_points([
        (fc[0] - face_w * 0.34, fy0 + face_h * 0.14),
        (fc[0] - face_w * 0.22, fy0 - face_h * 0.02),
        (fc[0] - face_w * 0.04, fy0 - face_h * 0.06),
        (fc[0] + face_w * 0.16, fy0 - face_h * 0.02),
        (fc[0] + face_w * 0.32, fy0 + face_h * 0.12),
        (fc[0] + face_w * 0.34, fc[1] + face_h * 0.04),
        (fc[0] + face_w * 0.24, fy1 - face_h * 0.08),
        (fc[0] + face_w * 0.06, fy1 + face_h * 0.02),
        (fc[0] - face_w * 0.10, fy1 - face_h * 0.02),
        (fc[0] - face_w * 0.28, fy1 - face_h * 0.12),
        (fc[0] - face_w * 0.34, fc[1] + face_h * 0.04),
    ], passes=1, closed=True)
    fc = average_xy(face)
    fx0, fy0, fx1, fy1 = point_bounds(face)
    face_w = fx1 - fx0
    face_h = fy1 - fy0
    head_w = face_w * 1.18
    head_h = face_h * 1.26
    stroke = max(2.2, face_w * 0.012)

    neck_base = fy1 + head_h * 0.10
    neck = smooth_points([
        (fc[0] - face_w * 0.16, fy1 - face_h * 0.02),
        (fc[0] + face_w * 0.16, fy1 - face_h * 0.02),
        (fc[0] + face_w * 0.28, neck_base),
        (fc[0], neck_base + head_h * 0.05),
        (fc[0] - face_w * 0.28, neck_base),
    ], passes=1, closed=True)
    torso = smooth_points([
        (fc[0] - head_w * 0.20, neck_base + head_h * 0.02),
        (fc[0] - head_w * 0.88, neck_base + head_h * 0.26),
        (fc[0] - head_w * 0.64, size * 0.86),
        (fc[0] + head_w * 0.64, size * 0.86),
        (fc[0] + head_w * 0.88, neck_base + head_h * 0.26),
        (fc[0] + head_w * 0.20, neck_base + head_h * 0.02),
    ], passes=1, closed=True)
    collar_outer = smooth_points(oval_points((fc[0], neck_base + head_h * 0.04), face_w * 0.42, head_h * 0.10, 18), passes=1, closed=True)
    collar_inner = smooth_points(oval_points((fc[0], neck_base + head_h * 0.04), face_w * 0.26, head_h * 0.050, 18), passes=1, closed=True)

    ear_rx, ear_ry = face_w * 0.090, face_h * 0.13
    ear_l = smooth_points(oval_points((fx0 - ear_rx * 0.05, ly + face_h * 0.04), ear_rx, ear_ry, 12), passes=1, closed=True)
    ear_r = smooth_points(oval_points((fx1 + ear_rx * 0.05, ry + face_h * 0.04), ear_rx, ear_ry, 12), passes=1, closed=True)
    hair = smooth_points([
        (fx0 - face_w * 0.06, fy0 + face_h * 0.22),
        (fc[0] - face_w * 0.34, fy0 - face_h * 0.22),
        (fc[0] - face_w * 0.12, fy0 - face_h * 0.34),
        (fc[0] + face_w * 0.06 * quiff_dir, fy0 - face_h * 0.42),
        (fc[0] + face_w * 0.28, fy0 - face_h * 0.26),
        (fx1 + face_w * 0.12, fy0 + face_h * 0.02),
        (fx1 + face_w * 0.06, fy0 + face_h * 0.22),
        (fc[0] + face_w * 0.14, fy0 + face_h * 0.06),
        (fc[0] - face_w * 0.10, fy0 + face_h * 0.04),
    ], passes=1, closed=True) if hair_cov > 0.10 else []
    front_hair = smooth_points([
        (fc[0] - face_w * 0.12, fy0 + face_h * 0.05),
        (fc[0] + face_w * 0.04 * quiff_dir, fy0 - face_h * 0.20),
        (fc[0] + face_w * 0.18, fy0 + face_h * 0.06),
        (fc[0] + face_w * 0.12, fy0 + face_h * 0.14),
        (fc[0] - face_w * 0.10, fy0 + face_h * 0.12),
    ], passes=1, closed=True) if hair else []

    def mk_eye(center: tuple[float, float]) -> tuple[list[tuple[float, float]], float, float]:
        cx, cy = center
        ew = face_w * 0.20
        eh = face_h * 0.048 * clamp(0.94 + measurements.eye_open_ratio * 0.40, 0.92, 1.08)
        points = [
            (cx - ew * 0.58, cy),
            (cx - ew * 0.18, cy - eh * 0.52),
            (cx + ew * 0.22, cy - eh * 0.30),
            (cx + ew * 0.58, cy),
            (cx + ew * 0.18, cy + eh * 0.18),
            (cx - ew * 0.22, cy + eh * 0.18),
        ]
        return smooth_points(points, passes=1, closed=True), ew, eh

    def mk_brow(center: tuple[float, float], eye_w_local: float, side: str) -> list[tuple[float, float]]:
        cx, cy = center
        slant = -face_h * 0.006 if side == "left" else face_h * 0.006
        return smooth_points([
            (cx - eye_w_local * 0.70, cy + face_h * 0.01),
            (cx - eye_w_local * 0.20, cy - face_h * 0.05),
            (cx + eye_w_local * 0.64, cy - face_h * 0.02 + slant),
            (cx + eye_w_local * 0.50, cy + face_h * 0.03),
        ], passes=1, closed=True)

    eye_l, eye_lw, eye_lh = mk_eye((lx, ly))
    eye_r, eye_rw, eye_rh = mk_eye((rx, ry))
    brow_l = mk_brow(bx_l, eye_lw, "left")
    brow_r = mk_brow(bx_r, eye_rw, "right")
    outer = smooth_points(style_points([p(i) for i in OUTER_LIP_IDX], mouth_center, 0.82, 0.68 if presets.expression != "open" else 1.00, -face_h * 0.002, face_h * 0.002), passes=1, closed=True)
    inner = smooth_points(style_points([p(i) for i in INNER_LIP_IDX], mouth_center, 0.68, 0.12 if presets.expression != "open" else 1.00, -face_h * 0.002, face_h * 0.002), passes=1, closed=True)
    mx0, my0, mx1, my1 = point_bounds(outer)
    mouth_w = mx1 - mx0
    mouth_h = my1 - my0
    mouth_inner_h = max(point[1] for point in inner) - min(point[1] for point in inner)
    mouth_open = mouth_inner_h > face_h * 0.026 or presets.expression == "open"
    beard = smooth_points([
        (fx0 + face_w * 0.12, fc[1] + face_h * 0.16),
        (mouth_l[0] - mouth_w * 0.10, my + mouth_h * 0.12),
        (fc[0] - face_w * 0.16, fy1 - face_h * 0.04),
        (fc[0], chin[1] + face_h * 0.02),
        (fc[0] + face_w * 0.16, fy1 - face_h * 0.04),
        (mouth_r[0] + mouth_w * 0.10, my + mouth_h * 0.12),
        (fx1 - face_w * 0.12, fc[1] + face_h * 0.16),
        (fc[0] + face_w * 0.16, my + mouth_h * 0.02),
        (fc[0] - face_w * 0.16, my + mouth_h * 0.02),
    ], passes=1, closed=True) if show_beard else []
    mustache = smooth_points([
        (mouth_l[0] - mouth_w * 0.03, nose_tip[1] + face_h * 0.04),
        (mx - mouth_w * 0.18, nose_tip[1]),
        (mx - mouth_w * 0.06, upper_lip[1]),
        (mx, upper_lip[1] + face_h * 0.02),
        (mx + mouth_w * 0.06, upper_lip[1]),
        (mx + mouth_w * 0.18, nose_tip[1]),
        (mouth_r[0] + mouth_w * 0.03, nose_tip[1] + face_h * 0.04),
        (mx + mouth_w * 0.08, upper_lip[1] + face_h * 0.05),
        (mx, upper_lip[1] + face_h * 0.06),
        (mx - mouth_w * 0.08, upper_lip[1] + face_h * 0.05),
    ], passes=1, closed=True) if show_mustache else []

    def transform_point(
        point: tuple[float, float],
        center: tuple[float, float],
        scale_x: float,
        scale_y: float,
        shift_x: float,
        shift_y: float,
    ) -> tuple[float, float]:
        return (
            center[0] + (point[0] - center[0]) * scale_x + shift_x,
            center[1] + (point[1] - center[1]) * scale_y + shift_y,
        )

    def transform_shape(
        points: list[tuple[float, float]],
        center: tuple[float, float],
        scale_x: float,
        scale_y: float,
        shift_x: float,
        shift_y: float,
    ) -> list[tuple[float, float]]:
        return [transform_point(point, center, scale_x, scale_y, shift_x, shift_y) for point in points]

    base_center = fc
    shift_x = size * 0.50 - fc[0]
    shift_y = size * 0.46 - fc[1]
    face = smooth_points(transform_shape(face, base_center, 1.24, 1.18, shift_x, shift_y), passes=1, closed=True)
    neck = smooth_points(transform_shape(neck, base_center, 1.16, 1.18, shift_x, shift_y), passes=1, closed=True)
    torso = smooth_points(transform_shape(torso, (base_center[0], neck_base), 1.38, 1.30, shift_x, shift_y), passes=1, closed=True)
    ear_l = smooth_points(transform_shape(ear_l, base_center, 1.16, 1.14, shift_x, shift_y), passes=1, closed=True)
    ear_r = smooth_points(transform_shape(ear_r, base_center, 1.16, 1.14, shift_x, shift_y), passes=1, closed=True)
    eye_l = smooth_points(transform_shape(eye_l, base_center, 1.16, 1.12, shift_x, shift_y), passes=1, closed=True)
    eye_r = smooth_points(transform_shape(eye_r, base_center, 1.16, 1.12, shift_x, shift_y), passes=1, closed=True)
    brow_l = smooth_points(transform_shape(brow_l, base_center, 1.18, 1.14, shift_x, shift_y), passes=1, closed=True)
    brow_r = smooth_points(transform_shape(brow_r, base_center, 1.18, 1.14, shift_x, shift_y), passes=1, closed=True)
    outer = smooth_points(transform_shape(outer, base_center, 1.18, 1.12, shift_x, shift_y), passes=1, closed=True)
    inner = smooth_points(transform_shape(inner, base_center, 1.18, 1.12, shift_x, shift_y), passes=1, closed=True)
    if beard:
        beard = smooth_points(transform_shape(beard, (fc[0], my), 1.12, 0.92, shift_x, shift_y), passes=1, closed=True)
    if mustache:
        mustache = smooth_points(transform_shape(mustache, (fc[0], my), 1.12, 1.00, shift_x, shift_y), passes=1, closed=True)
    if hair:
        hair = smooth_points(transform_shape(hair, (fc[0], fy0), 1.12, 0.78, shift_x, shift_y), passes=1, closed=True)
    if front_hair:
        front_hair = smooth_points(transform_shape(front_hair, (fc[0], fy0), 1.08, 0.74, shift_x, shift_y), passes=1, closed=True)
    nose_root = transform_point(nose_root, base_center, 1.16, 1.14, shift_x, shift_y)
    nose_tip = transform_point(nose_tip, base_center, 1.16, 1.14, shift_x, shift_y)
    nose_l = transform_point(nose_l, base_center, 1.16, 1.14, shift_x, shift_y)
    nose_r = transform_point(nose_r, base_center, 1.16, 1.14, shift_x, shift_y)
    mouth_l = transform_point(mouth_l, base_center, 1.18, 1.12, shift_x, shift_y)
    mouth_r = transform_point(mouth_r, base_center, 1.18, 1.12, shift_x, shift_y)
    upper_lip = transform_point(upper_lip, base_center, 1.18, 1.12, shift_x, shift_y)
    chin = transform_point(chin, base_center, 1.16, 1.18, shift_x, shift_y)
    lx, ly = transform_point((lx, ly), base_center, 1.16, 1.12, shift_x, shift_y)
    rx, ry = transform_point((rx, ry), base_center, 1.16, 1.12, shift_x, shift_y)
    bx_l = transform_point(bx_l, base_center, 1.18, 1.14, shift_x, shift_y)
    bx_r = transform_point(bx_r, base_center, 1.18, 1.14, shift_x, shift_y)
    mx, my = transform_point((mx, my), base_center, 1.18, 1.12, shift_x, shift_y)
    mouth_center = (mx, my)
    fc = average_xy(face)
    fx0, fy0, fx1, fy1 = point_bounds(face)
    face_w = fx1 - fx0
    face_h = fy1 - fy0
    neck_base = max(point[1] for point in neck)
    eye_lw = point_bounds(eye_l)[2] - point_bounds(eye_l)[0]
    eye_lh = point_bounds(eye_l)[3] - point_bounds(eye_l)[1]
    eye_rw = point_bounds(eye_r)[2] - point_bounds(eye_r)[0]
    eye_rh = point_bounds(eye_r)[3] - point_bounds(eye_r)[1]
    mx0, my0, mx1, my1 = point_bounds(outer)
    mouth_w = mx1 - mx0
    mouth_h = my1 - my0
    mouth_inner_h = max(point[1] for point in inner) - min(point[1] for point in inner)

    face_path, neck_path, torso_path = closed_smooth_path(face), closed_smooth_path(neck), closed_smooth_path(torso)
    hair_path = closed_smooth_path(hair) if hair else ""
    front_hair_path = closed_smooth_path(front_hair) if front_hair else ""
    eye_l_path, eye_r_path = closed_smooth_path(eye_l), closed_smooth_path(eye_r)
    brow_l_path, brow_r_path = closed_smooth_path(brow_l), closed_smooth_path(brow_r)
    outer_path, inner_path = closed_smooth_path(outer), closed_smooth_path(inner)
    beard_path = closed_smooth_path(beard) if beard else ""
    mustache_path = closed_smooth_path(mustache) if mustache else ""
    ear_l_path, ear_r_path = closed_smooth_path(ear_l), closed_smooth_path(ear_r)

    defs = [
        "<defs>",
        f'<filter id="soft-shadow" x="-25%" y="-25%" width="150%" height="150%"><feDropShadow dx="0" dy="{stroke * 0.5:.1f}" stdDeviation="{max(1.6, stroke * 0.55):.1f}" flood-color="{palette.shadow}" flood-opacity="0.18" /></filter>',
        f'<linearGradient id="bg-grad" x1="0" y1="0" x2="0" y2="{size:.1f}" gradientUnits="userSpaceOnUse"><stop offset="0%" stop-color="{mix(palette.background_accent, "#ffffff", 0.38)}" /><stop offset="100%" stop-color="{mix(palette.background, palette.background_accent, 0.16)}" /></linearGradient>',
        f'<linearGradient id="skin-grad" x1="{fx0:.1f}" y1="{fy0:.1f}" x2="{fx1:.1f}" y2="{fy1:.1f}" gradientUnits="userSpaceOnUse"><stop offset="0%" stop-color="{mix(palette.skin_light, "#fff0dd", 0.18)}" /><stop offset="55%" stop-color="{mix(palette.skin, palette.skin_light, 0.06)}" /><stop offset="100%" stop-color="{mix(palette.skin_shadow, palette.skin_deep_shadow, 0.18)}" /></linearGradient>',
        f'<linearGradient id="hair-grad" x1="{fc[0]:.1f}" y1="{fy0 - face_h * 0.40:.1f}" x2="{fc[0]:.1f}" y2="{fy0 + face_h * 0.26:.1f}" gradientUnits="userSpaceOnUse"><stop offset="0%" stop-color="{mix(palette.hair_highlight, palette.skin_light, 0.10)}" /><stop offset="42%" stop-color="{mix(palette.hair, palette.hair_shadow, 0.08)}" /><stop offset="100%" stop-color="{mix(palette.hair_shadow, palette.stroke, 0.16)}" /></linearGradient>',
        f'<linearGradient id="shirt-grad" x1="{fc[0]:.1f}" y1="{neck_base:.1f}" x2="{fc[0]:.1f}" y2="{size * 0.86:.1f}" gradientUnits="userSpaceOnUse"><stop offset="0%" stop-color="{mix(palette.shirt_highlight, palette.highlight, 0.16)}" /><stop offset="100%" stop-color="{mix(palette.shirt_shadow, palette.stroke, 0.12)}" /></linearGradient>',
        f'<linearGradient id="lip-grad" x1="{mx:.1f}" y1="{my0:.1f}" x2="{mx:.1f}" y2="{my1:.1f}" gradientUnits="userSpaceOnUse"><stop offset="0%" stop-color="{mix(palette.lip_highlight, "#ffffff", 0.18)}" /><stop offset="100%" stop-color="{mix(palette.lip_dark, palette.stroke, 0.10)}" /></linearGradient>',
        f'<clipPath id="face-clip-r"><path d="{face_path}" /></clipPath>',
        f'<clipPath id="mouth-clip-r"><path d="{outer_path}" /></clipPath>',
        "</defs>",
    ]

    layers = [
        "".join(defs),
        f'<g id="background"><rect width="{size}" height="{size}" fill="url(#bg-grad)" /></g>',
        f'<g id="torso" filter="url(#soft-shadow)"><path id="shirt" d="{torso_path}" fill="url(#shirt-grad)" /><path d="{closed_smooth_path(collar_outer)}" fill="{mix(palette.shirt_shadow, palette.stroke, 0.14)}" opacity="0.20" /><path d="{closed_smooth_path(collar_inner)}" fill="none" stroke="{mix(palette.highlight, palette.shirt_highlight, 0.12)}" stroke-width="{stroke * 0.16:.1f}" opacity="0.58" /></g>',
        f'<g id="neck"><path id="neck-shape" d="{neck_path}" fill="url(#skin-grad)" /></g>',
    ]
    if hair:
        layers.append(f'<g id="hair-back"><path id="hair-shape" d="{hair_path}" fill="url(#hair-grad)" filter="url(#soft-shadow)" /></g>')
    layers.append(f'<g id="ears"><path id="left-ear" d="{ear_l_path}" fill="{mix(palette.skin, palette.skin_shadow, 0.08)}" /><path id="right-ear" d="{ear_r_path}" fill="{mix(palette.skin, palette.skin_shadow, 0.08)}" /></g>')
    layers.append(f'<g id="face" filter="url(#soft-shadow)"><path id="face-shape" d="{face_path}" fill="url(#skin-grad)" stroke="{mix(palette.stroke, palette.skin_shadow, 0.24)}" stroke-width="{stroke * 0.16:.1f}" /><g clip-path="url(#face-clip-r)"><path d="{polygon_path([(fx1 - face_w * 0.08, fy0 + face_h * 0.10), (fc[0] + face_w * 0.06, fy0 + face_h * 0.08), (nose_r[0], nose_tip[1] + face_h * 0.04), (mx + mouth_w * 0.24, my + face_h * 0.04), (fc[0] + face_w * 0.16, fy1 - face_h * 0.06), (fx1 - face_w * 0.04, fc[1] + face_h * 0.14)])}" fill="{mix(palette.skin_shadow, palette.shadow, 0.32)}" opacity="0.16" /><ellipse cx="{fc[0] - face_w * 0.10:.1f}" cy="{fy0 + face_h * 0.18:.1f}" rx="{face_w * 0.16:.1f}" ry="{face_h * 0.10:.1f}" fill="{mix(palette.highlight, "#ffffff", 0.26)}" opacity="0.16" /></g></g>')
    if beard_path or mustache_path:
        beard_parts = ['<g id="beard">']
        if beard_path:
            beard_parts.append(f'<path id="beard-shape" d="{beard_path}" fill="{mix(palette.hair_shadow, palette.stroke, 0.14)}" />')
        if mustache_path:
            beard_parts.append(f'<path id="mustache-shape" d="{mustache_path}" fill="{mix(palette.hair_shadow, palette.stroke, 0.14)}" />')
        beard_parts.append('</g>')
        layers.append("".join(beard_parts))
    inner_mouth_markup = (
        f'<path id="inner-mouth" d="{inner_path}" fill="{mix(palette.lip_dark, palette.shadow, 0.24)}" opacity="0.84" />'
        if mouth_open
        else f'<path id="inner-mouth" d="{inner_path}" fill="none" stroke="{mix(palette.lip_dark, palette.stroke, 0.10)}" stroke-width="{stroke * 0.08:.1f}" opacity="0.70" />'
    )
    layers.extend([
        f'<g id="brows"><path id="left-brow" d="{brow_l_path}" fill="{mix(palette.hair_shadow, palette.stroke, 0.10)}" /><path id="right-brow" d="{brow_r_path}" fill="{mix(palette.hair_shadow, palette.stroke, 0.10)}" /></g>',
        f'<g id="eyes"><path id="left-eye" d="{eye_l_path}" fill="{palette.eye_white}" /><ellipse cx="{lx:.1f}" cy="{ly:.1f}" rx="{eye_lw * 0.18:.1f}" ry="{eye_lh * 0.70:.1f}" fill="{palette.stroke}" /><path id="right-eye" d="{eye_r_path}" fill="{palette.eye_white}" /><ellipse cx="{rx:.1f}" cy="{ry:.1f}" rx="{eye_rw * 0.18:.1f}" ry="{eye_rh * 0.70:.1f}" fill="{palette.stroke}" /></g>',
        f'<g id="nose"><path id="nose-bridge" d="{open_smooth_path([nose_root, lerp_point(nose_root, nose_tip, 0.56), nose_tip])}" fill="none" stroke="{mix(palette.skin_shadow, palette.skin_deep_shadow, 0.20)}" stroke-width="{stroke * 0.14:.1f}" stroke-linecap="round" /><path d="{open_smooth_path([nose_l, nose_tip, nose_r])}" fill="none" stroke="{mix(palette.skin_deep_shadow, palette.shadow, 0.12)}" stroke-width="{stroke * 0.16:.1f}" stroke-linecap="round" /></g>',
        f'<g id="mouth"><path id="outer-lip" d="{outer_path}" fill="url(#lip-grad)" stroke="{mix(palette.lip_dark, palette.stroke, 0.10)}" stroke-width="{stroke * 0.10:.1f}" /><g clip-path="url(#mouth-clip-r)"><ellipse cx="{mx:.1f}" cy="{my + mouth_h * 0.12:.1f}" rx="{mouth_w * 0.22:.1f}" ry="{max(1.4, mouth_h * 0.10):.1f}" fill="{mix(palette.lip_highlight, "#ffffff", 0.18)}" opacity="0.46" /></g>{inner_mouth_markup}</g>',
    ])
    if front_hair:
        layers.append(f'<g id="hair-front"><path d="{front_hair_path}" fill="url(#hair-grad)" /></g>')

    svg = f'<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" viewBox="0 0 {size} {size}">{"".join(layers)}</svg>'
    rig = {
        "render_style": "reference_vector_head_v1",
        "canvas": {"size": size},
        "crop": mapping_info,
        "layer_order": ["background", "torso", "neck", "hair-back", "ears", "face", "beard", "brows", "eyes", "nose", "mouth", "hair-front"],
        "anchors": {"head_center": {"x": round(fc[0], 2), "y": round(fc[1] - face_h * 0.10, 2)}, "face_center": {"x": round(fc[0], 2), "y": round(fc[1], 2)}, "left_eye": {"x": round(lx, 2), "y": round(ly, 2)}, "right_eye": {"x": round(rx, 2), "y": round(ry, 2)}, "mouth_center": {"x": round(mx, 2), "y": round(my, 2)}, "chin": {"x": round(chin[0], 2), "y": round(chin[1], 2)}, "neck_base": {"x": round(fc[0], 2), "y": round(neck_base, 2)}},
        "metrics": {"head_width": round(head_w, 2), "face_width": round(face_w, 2), "face_height": round(face_h, 2), "eye_distance": round(math.hypot(rx - lx, ry - ly), 2), "rotation_degrees": round(math.degrees(math.atan2(ry - ly, rx - lx)), 3), "blink_hint": round(1.0 - clamp(measurements.eye_open_ratio / 0.42, 0.0, 1.0), 3), "jaw_open_hint": round(clamp(measurements.mouth_open_ratio / 0.060, 0.0, 1.0), 3), "smile_hint": round(clamp((measurements.smile_ratio + 0.02) / 0.12, 0.0, 1.0), 3), "hair_coverage": round(hair_cov, 3)},
        "facial_hair": facial_hair,
        "paths": {"hair": points_to_dicts(hair), "face": points_to_dicts(face), "left_brow": points_to_dicts(brow_l), "right_brow": points_to_dicts(brow_r), "left_eye": points_to_dicts(eye_l), "right_eye": points_to_dicts(eye_r), "outer_lip": points_to_dicts(outer), "inner_lip": points_to_dicts(inner), "left_ear": points_to_dicts(ear_l), "right_ear": points_to_dicts(ear_r), "neck": points_to_dicts(neck), "torso": points_to_dicts(torso), "front_hair": points_to_dicts(front_hair), "beard": points_to_dicts(beard), "mustache": points_to_dicts(mustache)},
        "animation_presets": {"mouth": mouth_variants(outer, inner, mouth_center), "left_eye": eye_variants(eye_l, average_xy(eye_l)), "right_eye": eye_variants(eye_r, average_xy(eye_r))},
    }
    return svg, rig




def render_avatar_svg_hybrid_cutout(
    size: int,
    image_rgb: np.ndarray,
    landmarks: list[tuple[float, float]],
    segmentation: SegmentationData,
    measurements: AvatarMeasurements,
    presets: AvatarPresets,
    palette: AvatarPalette,
) -> tuple[str, dict[str, object]]:
    h, w = segmentation.face_mask.shape[:2]
    mapper, mapping_info = build_canvas_mapper(segmentation.crop_bbox, size)
    crop_x0, crop_y0, crop_x1, crop_y1 = segmentation.crop_bbox

    def map_landmark(index: int) -> tuple[float, float]:
        return mapper(landmark_to_pixel(landmarks[index], w, h))

    def mix(color_a: str, color_b: str, amount: float) -> str:
        return rgb_to_hex(blend_rgb(hex_to_rgb(color_a), hex_to_rgb(color_b), amount))

    def style_points(
        points: list[tuple[float, float]],
        center: tuple[float, float],
        scale_x: float,
        scale_y: float,
        upper_shift: float = 0.0,
        lower_shift: float = 0.0,
    ) -> list[tuple[float, float]]:
        cx, cy = center
        result: list[tuple[float, float]] = []
        for x, y in points:
            nx = cx + (x - cx) * scale_x
            ny = cy + (y - cy) * scale_y
            ny += upper_shift if y < cy else lower_shift
            result.append((nx, ny))
        return result

    def eye_shape(
        points: list[tuple[float, float]],
        center: tuple[float, float],
        face_w_local: float,
        face_h_local: float,
    ) -> tuple[list[tuple[float, float]], float, float]:
        min_x, min_y, max_x, max_y = point_bounds(points)
        width = max(max_x - min_x, face_w_local * 0.15)
        height = max(max_y - min_y, face_h_local * 0.040)
        height *= clamp(0.96 + measurements.eye_open_ratio * 0.46, 0.92, 1.22)
        tilt = clamp((points[-1][1] - points[0][1]) / max(width, EPS), -0.10, 0.10)
        cx, cy = center
        shape = [
            (cx - width * 0.60, cy + tilt * width * 0.14),
            (cx - width * 0.24, cy - height * 0.56 + tilt * width * 0.12),
            (cx + width * 0.24, cy - height * 0.42 - tilt * width * 0.02),
            (cx + width * 0.60, cy - tilt * width * 0.14),
            (cx + width * 0.18, cy + height * 0.30),
            (cx - width * 0.22, cy + height * 0.30),
        ]
        return smooth_points(shape, passes=1, closed=True), width, height

    def brow_shape(
        center: tuple[float, float],
        width: float,
        face_h_local: float,
        side: str,
    ) -> list[tuple[float, float]]:
        cx, cy = center
        arch = {"arched": -face_h_local * 0.020, "soft": -face_h_local * 0.012, "flat": -face_h_local * 0.006}.get(presets.brows, -face_h_local * 0.010)
        slant = -face_h_local * 0.005 if side == "left" else face_h_local * 0.005
        brow_h = max(face_h_local * 0.036, width * 0.08)
        pts = [
            (cx - width * 0.68, cy + brow_h * 0.18),
            (cx - width * 0.20, cy - brow_h * 0.64 + arch),
            (cx + width * 0.64, cy - brow_h * 0.18 + slant),
            (cx + width * 0.52, cy + brow_h * 0.30),
        ]
        return smooth_points(pts, passes=1, closed=True)

    crop_rgb = image_rgb[crop_y0:crop_y1, crop_x0:crop_x1]
    poster_rgb = stylize_portrait_crop(crop_rgb)
    crop_face_mask = segmentation.face_mask[crop_y0:crop_y1, crop_x0:crop_x1].astype(np.float32)
    if crop_face_mask.size:
        face_alpha = cv2.resize(crop_face_mask, (poster_rgb.shape[1], poster_rgb.shape[0]), interpolation=cv2.INTER_CUBIC)
        face_alpha = cv2.GaussianBlur(face_alpha, (0, 0), sigmaX=max(1.2, poster_rgb.shape[1] * 0.006), sigmaY=max(1.2, poster_rgb.shape[1] * 0.006))
        face_alpha = np.clip((face_alpha - 0.04) / 0.92, 0.0, 1.0)
        poster_bgr = cv2.cvtColor(poster_rgb, cv2.COLOR_RGB2BGR)
        face_smooth_bgr = cv2.bilateralFilter(poster_bgr, d=9, sigmaColor=34, sigmaSpace=34)
        face_smooth_bgr = cv2.GaussianBlur(face_smooth_bgr, (0, 0), sigmaX=0.9, sigmaY=0.9)
        poster_rgb = np.uint8(
            np.clip(
                poster_rgb.astype(np.float32) * (1.0 - face_alpha[..., None] * 0.40)
                + cv2.cvtColor(face_smooth_bgr, cv2.COLOR_BGR2RGB).astype(np.float32) * (face_alpha[..., None] * 0.40),
                0,
                255,
            )
        )
    subject_mask = make_binary(segmentation.head_mask | segmentation.neck_mask | segmentation.clothes_mask)
    subject_mask = fill_holes(close_mask(subject_mask, adaptive_kernel(image_rgb.shape, 0.014, minimum=7, maximum=55)))
    subject_mask = largest_component(subject_mask)
    crop_subject_mask = subject_mask[crop_y0:crop_y1, crop_x0:crop_x1].astype(np.float32)
    poster_alpha = cv2.resize(crop_subject_mask, (poster_rgb.shape[1], poster_rgb.shape[0]), interpolation=cv2.INTER_CUBIC)
    poster_alpha = cv2.GaussianBlur(
        poster_alpha,
        (0, 0),
        sigmaX=max(1.8, poster_rgb.shape[1] * 0.008),
        sigmaY=max(1.8, poster_rgb.shape[0] * 0.008),
    )
    poster_alpha = np.clip((poster_alpha - 0.03) / 0.94, 0.0, 1.0)
    portrait_rgba = np.dstack([poster_rgb, np.uint8(np.clip(poster_alpha * 255.0, 0, 255))])
    portrait_uri = image_data_uri(portrait_rgba, max_dim=960)
    texture_x = mapping_info["offset_x"]
    texture_y = mapping_info["offset_y"]
    texture_w = (crop_x1 - crop_x0) * mapping_info["scale"]
    texture_h = (crop_y1 - crop_y0) * mapping_info["scale"]

    bust_contour = contour_from_mask(subject_mask, num_points=180, closed=True)
    bust_points = smooth_points([mapper(point) for point in bust_contour], passes=1, closed=True)
    bust_path = closed_smooth_path(bust_points)

    head_points = smooth_points([mapper(point) for point in segmentation.head_contour], passes=1, closed=True)
    face_source = (
        [mapper(point) for point in segmentation.face_contour]
        if segmentation.face_contour
        else [map_landmark(index) for index in FACE_CONTOUR_IDX]
    )
    face_points = smooth_points(face_source, passes=1, closed=True)
    hair_points = (
        smooth_points([mapper(point) for point in segmentation.hair_contour], passes=1, closed=True)
        if segmentation.hair_contour
        else []
    )

    face_cx, face_cy = average_xy(face_points)
    head_cx, head_cy = average_xy(head_points)
    face_min_x, face_min_y, face_max_x, face_max_y = point_bounds(face_points)
    head_min_x, head_min_y, head_max_x, head_max_y = point_bounds(head_points)
    face_w = face_max_x - face_min_x
    face_h = face_max_y - face_min_y
    head_w = head_max_x - head_min_x
    head_h = head_max_y - head_min_y
    stroke_w = max(2.0, head_w * 0.008)

    left_eye_raw = [map_landmark(index) for index in LEFT_EYE_IDX]
    right_eye_raw = [map_landmark(index) for index in RIGHT_EYE_IDX]
    left_iris = [map_landmark(index) for index in LEFT_IRIS_IDX]
    right_iris = [map_landmark(index) for index in RIGHT_IRIS_IDX]
    left_eye_center = average_xy(left_iris)
    right_eye_center = average_xy(right_iris)
    left_eye_points = smooth_points(style_points(left_eye_raw, left_eye_center, 0.94, 0.94), passes=1, closed=True)
    right_eye_points = smooth_points(style_points(right_eye_raw, right_eye_center, 0.94, 0.94), passes=1, closed=True)
    left_eye_w = point_bounds(left_eye_points)[2] - point_bounds(left_eye_points)[0]
    left_eye_h = point_bounds(left_eye_points)[3] - point_bounds(left_eye_points)[1]
    right_eye_w = point_bounds(right_eye_points)[2] - point_bounds(right_eye_points)[0]
    right_eye_h = point_bounds(right_eye_points)[3] - point_bounds(right_eye_points)[1]
    left_eye_path = closed_smooth_path(left_eye_points)
    right_eye_path = closed_smooth_path(right_eye_points)

    left_brow_center = average_xy([map_landmark(index) for index in LEFT_BROW_IDX])
    right_brow_center = average_xy([map_landmark(index) for index in RIGHT_BROW_IDX])
    left_brow_points = brow_shape(left_brow_center, left_eye_w * 0.78, face_h, "left")
    right_brow_points = brow_shape(right_brow_center, right_eye_w * 0.78, face_h, "right")
    left_brow_path = closed_smooth_path(left_brow_points)
    right_brow_path = closed_smooth_path(right_brow_points)

    outer_lip_raw = [map_landmark(index) for index in OUTER_LIP_IDX]
    inner_lip_raw = [map_landmark(index) for index in INNER_LIP_IDX]
    mouth_center = average_xy(inner_lip_raw)
    outer_lip = smooth_points(
        style_points(
            outer_lip_raw,
            mouth_center,
            scale_x=0.82,
            scale_y=0.62 if presets.expression != "open" else 0.92,
            upper_shift=-face_h * 0.004,
            lower_shift=face_h * 0.002,
        ),
        passes=1,
        closed=True,
    )
    inner_lip = smooth_points(
        style_points(
            inner_lip_raw,
            mouth_center,
            scale_x=0.68,
            scale_y=0.10 if presets.expression != "open" else 0.92,
            upper_shift=-face_h * 0.002,
            lower_shift=face_h * 0.002,
        ),
        passes=1,
        closed=True,
    )
    mouth_outer_path = closed_smooth_path(outer_lip)
    mouth_inner_path = closed_smooth_path(inner_lip)
    mouth_min_x, mouth_min_y, mouth_max_x, mouth_max_y = point_bounds(outer_lip)
    mouth_w = mouth_max_x - mouth_min_x
    mouth_h = mouth_max_y - mouth_min_y
    mouth_inner_h = max(point[1] for point in inner_lip) - min(point[1] for point in inner_lip)
    mouth_is_open = mouth_inner_h > face_h * 0.025 or presets.expression == "open"

    nose_root = map_landmark(168)
    nose_tip = map_landmark(2)
    nose_left = map_landmark(98)
    nose_right = map_landmark(327)
    philtrum_top = map_landmark(0)
    nose_bridge_path = open_smooth_path([nose_root, lerp_point(nose_root, nose_tip, 0.56), nose_tip])
    nose_base_path = open_smooth_path([nose_left, nose_tip, nose_right])
    philtrum_path = open_smooth_path([nose_tip, midpoint(nose_tip, philtrum_top), philtrum_top])

    hair_coverage = float(segmentation.hair_mask.sum()) / max(1.0, float(segmentation.face_mask.sum()))
    facial_hair = estimate_facial_hair(image_rgb, landmarks, segmentation)
    show_hair_outline = hair_coverage > 0.16 and len(hair_points) >= 8
    show_beard = facial_hair["beard"] > 0.30
    show_mustache = facial_hair["mustache"] > 0.32

    mouth_left = map_landmark(61)
    mouth_right = map_landmark(291)
    chin = map_landmark(152)
    beard_points: list[tuple[float, float]] = []
    mustache_points: list[tuple[float, float]] = []
    if show_beard:
        beard_points = smooth_points(
            [
                (face_min_x + face_w * 0.14, face_cy + face_h * 0.08),
                (mouth_left[0] - mouth_w * 0.10, mouth_center[1] - mouth_h * 0.06),
                (mouth_left[0] - mouth_w * 0.04, mouth_center[1] + mouth_h * 0.18),
                (face_cx - face_w * 0.18, face_max_y - face_h * 0.04),
                (face_cx - face_w * 0.06, chin[1]),
                chin,
                (face_cx + face_w * 0.06, chin[1]),
                (face_cx + face_w * 0.18, face_max_y - face_h * 0.04),
                (mouth_right[0] + mouth_w * 0.04, mouth_center[1] + mouth_h * 0.18),
                (mouth_right[0] + mouth_w * 0.10, mouth_center[1] - mouth_h * 0.06),
                (face_max_x - face_w * 0.14, face_cy + face_h * 0.08),
            ],
            passes=1,
            closed=True,
        )
    if show_mustache:
        mustache_points = smooth_points(
            [
                (mouth_left[0] - mouth_w * 0.05, nose_tip[1] + face_h * 0.03),
                (mouth_center[0] - mouth_w * 0.22, nose_tip[1] + face_h * 0.00),
                (mouth_center[0] - mouth_w * 0.08, philtrum_top[1] + face_h * 0.01),
                (mouth_center[0], philtrum_top[1] + face_h * 0.03),
                (mouth_center[0] + mouth_w * 0.08, philtrum_top[1] + face_h * 0.01),
                (mouth_center[0] + mouth_w * 0.22, nose_tip[1] + face_h * 0.00),
                (mouth_right[0] + mouth_w * 0.05, nose_tip[1] + face_h * 0.03),
                (mouth_center[0] + mouth_w * 0.12, philtrum_top[1] + face_h * 0.09),
                (mouth_center[0], philtrum_top[1] + face_h * 0.10),
                (mouth_center[0] - mouth_w * 0.12, philtrum_top[1] + face_h * 0.09),
            ],
            passes=1,
            closed=True,
        )
    beard_path = closed_smooth_path(beard_points) if beard_points else ""
    mustache_path = closed_smooth_path(mustache_points) if mustache_points else ""

    face_path = closed_smooth_path(face_points)
    hair_path = closed_smooth_path(hair_points) if hair_points else ""
    lip_gloss = mix(palette.lip_highlight, "#ffffff", 0.22)
    face_outline = mix(palette.stroke, palette.shadow, 0.18)
    hair_outline = mix(palette.stroke, palette.hair_shadow, 0.20)
    brow_fill = mix(palette.hair_shadow, palette.stroke, 0.10)
    wash_top = mix(palette.skin_light, "#ffffff", 0.24)
    wash_bottom = mix(palette.skin, palette.skin_light, 0.08)
    beard_fill = mix(palette.hair_shadow, palette.stroke, 0.10)
    bg_top = mix(palette.background_accent, "#ffffff", 0.24)
    bg_bottom = mix(palette.background, palette.background_accent, 0.06)
    iris_ring = mix(palette.iris_dark, palette.stroke, 0.10)
    nose_stroke = mix(palette.skin_deep_shadow, palette.shadow, 0.12)
    mouth_dark = mix(palette.lip_dark, palette.stroke, 0.10)
    teeth_color = mix(palette.eye_white, "#ffffff", 0.22)
    tongue_color = mix(palette.lip, "#b76b74", 0.28)

    defs: list[str] = [
        "<defs>",
        f'<filter id="soft-shadow" x="-25%" y="-25%" width="150%" height="150%"><feDropShadow dx="0" dy="{stroke_w * 0.55:.1f}" stdDeviation="{max(1.6, stroke_w * 0.55):.1f}" flood-color="{palette.shadow}" flood-opacity="0.20" /></filter>',
        f'<linearGradient id="bg-gradient" x1="0" y1="0" x2="0" y2="{size:.1f}" gradientUnits="userSpaceOnUse"><stop offset="0%" stop-color="{bg_top}" /><stop offset="100%" stop-color="{bg_bottom}" /></linearGradient>',
        f'<radialGradient id="halo-gradient" cx="{head_cx:.1f}" cy="{head_cy:.1f}" r="{head_w * 0.95:.1f}" gradientUnits="userSpaceOnUse"><stop offset="0%" stop-color="{mix(palette.background_accent, palette.highlight, 0.26)}" stop-opacity="0.40" /><stop offset="100%" stop-color="{bg_bottom}" stop-opacity="0" /></radialGradient>',
        f'<linearGradient id="face-wash" x1="{face_cx:.1f}" y1="{face_min_y:.1f}" x2="{face_cx:.1f}" y2="{face_max_y:.1f}" gradientUnits="userSpaceOnUse"><stop offset="0%" stop-color="{wash_top}" /><stop offset="100%" stop-color="{wash_bottom}" /></linearGradient>',
        f'<linearGradient id="lip-gradient" x1="{mouth_center[0]:.1f}" y1="{mouth_min_y:.1f}" x2="{mouth_center[0]:.1f}" y2="{mouth_max_y:.1f}" gradientUnits="userSpaceOnUse"><stop offset="0%" stop-color="{lip_gloss}" /><stop offset="42%" stop-color="{palette.lip}" /><stop offset="100%" stop-color="{mouth_dark}" /></linearGradient>',
        f'<radialGradient id="left-iris-gradient" cx="{left_eye_center[0] - left_eye_w * 0.06:.1f}" cy="{left_eye_center[1] - left_eye_h * 0.16:.1f}" r="{left_eye_w * 0.38:.1f}" gradientUnits="userSpaceOnUse"><stop offset="0%" stop-color="{palette.highlight}" /><stop offset="34%" stop-color="{palette.iris}" /><stop offset="100%" stop-color="{iris_ring}" /></radialGradient>',
        f'<radialGradient id="right-iris-gradient" cx="{right_eye_center[0] - right_eye_w * 0.06:.1f}" cy="{right_eye_center[1] - right_eye_h * 0.16:.1f}" r="{right_eye_w * 0.38:.1f}" gradientUnits="userSpaceOnUse"><stop offset="0%" stop-color="{palette.highlight}" /><stop offset="34%" stop-color="{palette.iris}" /><stop offset="100%" stop-color="{iris_ring}" /></radialGradient>',
        f'<clipPath id="bust-clip"><path d="{bust_path}" /></clipPath>',
        f'<clipPath id="face-clip"><path d="{face_path}" /></clipPath>',
        f'<clipPath id="left-eye-clip"><path d="{left_eye_path}" /></clipPath>',
        f'<clipPath id="right-eye-clip"><path d="{right_eye_path}" /></clipPath>',
        f'<clipPath id="mouth-clip"><path d="{mouth_outer_path}" /></clipPath>',
        f'<clipPath id="inner-mouth-clip"><path d="{mouth_inner_path}" /></clipPath>',
    ]
    if show_hair_outline:
        defs.append(f'<clipPath id="hair-clip"><path d="{hair_path}" /></clipPath>')
    defs.append("</defs>")

    def build_eye_markup(
        side: str,
        eye_path: str,
        eye_points: list[tuple[float, float]],
        eye_center: tuple[float, float],
        eye_w_local: float,
        eye_h_local: float,
        iris_gradient_id: str,
    ) -> str:
        upper_lid_path = open_smooth_path(eye_points[:4])
        iris_rx = max(3.8, eye_w_local * 0.19)
        iris_ry = max(iris_rx * 0.95, eye_h_local * 0.52)
        pupil_r = max(2.0, iris_rx * 0.42)
        return (
            f'<path id="{side}-eye" d="{eye_path}" fill="{palette.eye_white}" stroke="none" opacity="0.10" />'
            f'<g clip-path="url(#{side}-eye-clip)">'
            f'<ellipse cx="{eye_center[0]:.1f}" cy="{eye_center[1]:.1f}" rx="{iris_rx * 0.52:.1f}" ry="{iris_ry * 0.52:.1f}" fill="url(#{iris_gradient_id})" opacity="0.24" />'
            f'<circle id="{side}-pupil" cx="{eye_center[0]:.1f}" cy="{eye_center[1]:.1f}" r="{max(1.2, pupil_r * 0.48):.1f}" fill="{palette.stroke}" opacity="0.20" />'
            f'<circle cx="{eye_center[0] - iris_rx * 0.12:.1f}" cy="{eye_center[1] - iris_ry * 0.12:.1f}" r="{max(0.8, pupil_r * 0.18):.1f}" fill="#ffffff" opacity="0.50" />'
            f'</g>'
            f'<path d="{upper_lid_path}" fill="none" stroke="{mix(palette.hair_shadow, palette.stroke, 0.06)}" stroke-width="{stroke_w * 0.10:.1f}" stroke-linecap="round" opacity="0.42" />'
        )

    layers: list[str] = [
        "".join(defs),
        f'<g id="background"><rect width="{size}" height="{size}" fill="url(#bg-gradient)" /><ellipse cx="{head_cx:.1f}" cy="{head_cy:.1f}" rx="{head_w * 1.05:.1f}" ry="{head_h * 1.10:.1f}" fill="url(#halo-gradient)" /></g>',
        f'<g id="bust" filter="url(#soft-shadow)"><image x="{texture_x:.1f}" y="{texture_y:.1f}" width="{texture_w:.1f}" height="{texture_h:.1f}" href="{portrait_uri}" preserveAspectRatio="none" clip-path="url(#bust-clip)" opacity="0.98" /><path d="{bust_path}" fill="none" stroke="{face_outline}" stroke-width="{stroke_w * 0.08:.1f}" opacity="0.22" /></g>',
        f'<g id="face-balance" clip-path="url(#face-clip)"><path d="{face_path}" fill="url(#face-wash)" opacity="0.04" /><ellipse cx="{left_eye_center[0]:.1f}" cy="{left_eye_center[1]:.1f}" rx="{left_eye_w * 0.30:.1f}" ry="{left_eye_h * 0.38:.1f}" fill="{wash_top}" opacity="0.05" /><ellipse cx="{right_eye_center[0]:.1f}" cy="{right_eye_center[1]:.1f}" rx="{right_eye_w * 0.30:.1f}" ry="{right_eye_h * 0.38:.1f}" fill="{wash_top}" opacity="0.05" /><ellipse cx="{mouth_center[0]:.1f}" cy="{mouth_center[1]:.1f}" rx="{mouth_w * 0.26:.1f}" ry="{max(4.0, mouth_h * 0.44):.1f}" fill="{mix(palette.skin, palette.skin_light, 0.06)}" opacity="0.04" /></g>',
    ]
    if show_hair_outline:
        layers.append(
            f'<g id="hair"><path d="{hair_path}" fill="{mix(palette.hair, palette.hair_shadow, 0.10)}" opacity="0.06" /><path d="{hair_path}" fill="none" stroke="{hair_outline}" stroke-width="{stroke_w * 0.10:.1f}" opacity="0.24" /></g>'
        )
    if beard_path or mustache_path:
        beard_parts = ['<g id="beard" clip-path="url(#face-clip)">']
        if beard_path:
            beard_parts.append(f'<path d="{beard_path}" fill="{beard_fill}" opacity="0.08" />')
        if mustache_path:
            beard_parts.append(f'<path d="{mustache_path}" fill="{beard_fill}" opacity="0.08" />')
        beard_parts.append('</g>')
        layers.append(''.join(beard_parts))
    layers.extend([
        f'<g id="brows" opacity="0.18"><path id="left-brow" d="{left_brow_path}" fill="{brow_fill}" stroke="{face_outline}" stroke-width="{stroke_w * 0.05:.1f}" stroke-linejoin="round" /><path id="right-brow" d="{right_brow_path}" fill="{brow_fill}" stroke="{face_outline}" stroke-width="{stroke_w * 0.05:.1f}" stroke-linejoin="round" /></g>',
        f'<g id="eyes">{build_eye_markup("left", left_eye_path, left_eye_points, left_eye_center, left_eye_w, left_eye_h, "left-iris-gradient")}{build_eye_markup("right", right_eye_path, right_eye_points, right_eye_center, right_eye_w, right_eye_h, "right-iris-gradient")}</g>',
        f'<g id="nose" opacity="0.36"><path d="{nose_bridge_path}" fill="none" stroke="{nose_stroke}" stroke-width="{stroke_w * 0.10:.1f}" stroke-linecap="round" /><path d="{nose_base_path}" fill="none" stroke="{nose_stroke}" stroke-width="{stroke_w * 0.12:.1f}" stroke-linecap="round" stroke-linejoin="round" opacity="0.64" /><path d="{philtrum_path}" fill="none" stroke="{mix(palette.skin_shadow, palette.shadow, 0.16)}" stroke-width="{stroke_w * 0.07:.1f}" stroke-linecap="round" opacity="0.34" /></g>',
    ])

    mouth_parts = [
        f'<g id="mouth">',
        f'<path id="outer-lip" d="{mouth_outer_path}" fill="url(#lip-gradient)" stroke="{mouth_dark}" stroke-width="{stroke_w * 0.06:.1f}" stroke-linejoin="round" opacity="0.28" />',
        f'<g clip-path="url(#mouth-clip)"><ellipse cx="{mouth_center[0]:.1f}" cy="{mouth_center[1] + mouth_h * 0.12:.1f}" rx="{mouth_w * 0.22:.1f}" ry="{max(1.2, mouth_h * 0.10):.1f}" fill="{lip_gloss}" opacity="0.14" /></g>',
    ]
    if mouth_is_open:
        mouth_parts.append(f'<path id="inner-mouth" d="{mouth_inner_path}" fill="{mix(mouth_dark, palette.shadow, 0.24)}" opacity="0.32" />')
        mouth_parts.append(f'<g clip-path="url(#inner-mouth-clip)"><rect x="{mouth_center[0] - mouth_w * 0.16:.1f}" y="{mouth_center[1] - mouth_inner_h * 0.40:.1f}" width="{mouth_w * 0.32:.1f}" height="{max(2.6, mouth_inner_h * 0.22):.1f}" rx="{max(1.0, mouth_inner_h * 0.08):.1f}" fill="{teeth_color}" opacity="0.24" /><ellipse cx="{mouth_center[0]:.1f}" cy="{mouth_center[1] + mouth_inner_h * 0.24:.1f}" rx="{mouth_w * 0.16:.1f}" ry="{max(2.2, mouth_inner_h * 0.30):.1f}" fill="{tongue_color}" opacity="0.24" /></g>')
    else:
        mouth_parts.append(f'<path id="inner-mouth" d="{mouth_inner_path}" fill="none" stroke="{mouth_dark}" stroke-width="{stroke_w * 0.05:.1f}" opacity="0.18" />')
    mouth_parts.append('</g>')
    layers.append(''.join(mouth_parts))

    svg = (
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" viewBox="0 0 {size} {size}">'
        + ''.join(layers)
        + '</svg>'
    )

    rig = {
        "render_style": "hybrid_cutout_v2_cv",
        "canvas": {"size": size},
        "crop": mapping_info,
        "layer_order": [
            "background",
            "bust",
            "face-balance",
            "hair",
            "beard",
            "brows",
            "eyes",
            "nose",
            "mouth",
        ],
        "anchors": {
            "head_center": {"x": round(head_cx, 2), "y": round(head_cy, 2)},
            "face_center": {"x": round(face_cx, 2), "y": round(face_cy, 2)},
            "left_eye": {"x": round(left_eye_center[0], 2), "y": round(left_eye_center[1], 2)},
            "right_eye": {"x": round(right_eye_center[0], 2), "y": round(right_eye_center[1], 2)},
            "left_brow": {"x": round(left_brow_center[0], 2), "y": round(left_brow_center[1], 2)},
            "right_brow": {"x": round(right_brow_center[0], 2), "y": round(right_brow_center[1], 2)},
            "nose_tip": {"x": round(nose_tip[0], 2), "y": round(nose_tip[1], 2)},
            "mouth_center": {"x": round(mouth_center[0], 2), "y": round(mouth_center[1], 2)},
            "chin": {"x": round(chin[0], 2), "y": round(chin[1], 2)},
        },
        "metrics": {
            "head_width": round(head_w, 2),
            "face_width": round(face_w, 2),
            "face_height": round(face_h, 2),
            "eye_distance": round(math.hypot(right_eye_center[0] - left_eye_center[0], right_eye_center[1] - left_eye_center[1]), 2),
            "rotation_degrees": round(math.degrees(math.atan2(right_eye_center[1] - left_eye_center[1], right_eye_center[0] - left_eye_center[0])), 3),
            "blink_hint": round(1.0 - clamp(measurements.eye_open_ratio / 0.42, 0.0, 1.0), 3),
            "jaw_open_hint": round(clamp(measurements.mouth_open_ratio / 0.060, 0.0, 1.0), 3),
            "smile_hint": round(clamp((measurements.smile_ratio + 0.02) / 0.12, 0.0, 1.0), 3),
            "hair_coverage": round(hair_coverage, 3),
        },
        "facial_hair": facial_hair,
        "paths": {
            "bust": points_to_dicts(bust_points),
            "head": points_to_dicts(head_points),
            "face": points_to_dicts(face_points),
            "hair": points_to_dicts(hair_points),
            "left_brow": points_to_dicts(left_brow_points),
            "right_brow": points_to_dicts(right_brow_points),
            "left_eye": points_to_dicts(left_eye_points),
            "right_eye": points_to_dicts(right_eye_points),
            "outer_lip": points_to_dicts(outer_lip),
            "inner_lip": points_to_dicts(inner_lip),
            "beard": points_to_dicts(beard_points),
            "mustache": points_to_dicts(mustache_points),
        },
        "animation_presets": {
            "mouth": mouth_variants(outer_lip, inner_lip, mouth_center),
            "left_eye": eye_variants(left_eye_points, average_xy(left_eye_points)),
            "right_eye": eye_variants(right_eye_points, average_xy(right_eye_points)),
        },
    }
    return svg, rig


def build_avatar(
    image_path: Path,
    output_dir: Path,
    model_path: Path,
    selfie_model_path: Path,
    hair_model_path: Path,
    size: int,
    min_confidence: float,
    save_debug_overlay: bool = True,
) -> tuple[Path, Path, Path | None, Path | None]:
    ensure_models([model_path, selfie_model_path, hair_model_path])

    rgb, landmarks, blendshapes = detect_face(image_path, model_path, min_confidence)
    segmentation = segment_portrait(rgb, landmarks, selfie_model_path, hair_model_path)
    measurements = extract_measurements(landmarks)
    presets = classify_presets(measurements, blendshapes)
    palette = estimate_palette(rgb, landmarks, segmentation)
    identity = identity_signature(landmarks)

    output_dir.mkdir(parents=True, exist_ok=True)
    stem = image_path.stem
    svg_path = output_dir / f"{stem}_avatar.svg"
    meta_path = output_dir / f"{stem}_avatar.json"
    landmarks_debug_path = output_dir / f"{stem}_landmarks_debug.jpg" if save_debug_overlay else None
    segmentation_debug_path = output_dir / f"{stem}_segmentation_debug.jpg" if save_debug_overlay else None

    svg, rig = render_avatar_svg(
        size=size,
        image_rgb=rgb,
        landmarks=landmarks,
        segmentation=segmentation,
        measurements=measurements,
        presets=presets,
        palette=palette,
    )
    svg_path.write_text(svg, encoding="utf-8")

    if landmarks_debug_path is not None:
        write_landmark_debug_overlay(rgb, landmarks, landmarks_debug_path)
    if segmentation_debug_path is not None:
        write_segmentation_debug_overlay(rgb, segmentation, segmentation_debug_path)

    metadata = {
        "image_path": str(image_path),
        "render_style": "game_stylized_v6_illustrated",
        "model_path": str(model_path),
        "selfie_multiclass_model_path": str(selfie_model_path),
        "hair_segmenter_model_path": str(hair_model_path),
        "canvas_size": size,
        "measurements": asdict(measurements),
        "presets": asdict(presets),
        "palette": asdict(palette),
        "blendshapes": blendshapes,
        "identity_signature": identity,
        "rig": rig,
        "debug_assets": {
            "landmarks": str(landmarks_debug_path) if landmarks_debug_path is not None else None,
            "segmentation": str(segmentation_debug_path) if segmentation_debug_path is not None else None,
        },
    }
    meta_path.write_text(json.dumps(metadata, indent=2), encoding="utf-8")
    return svg_path, meta_path, landmarks_debug_path, segmentation_debug_path


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Create a layered, animation-friendly 2D cartoon avatar from a face photo."
    )
    parser.add_argument("--image", type=Path, required=True, help="Path to the source face image.")
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=DEFAULT_OUTPUT_DIR,
        help=f"Directory for SVG and JSON outputs. Defaults to {DEFAULT_OUTPUT_DIR}.",
    )
    parser.add_argument(
        "--model-path",
        type=Path,
        default=DEFAULT_MODEL_PATH,
        help=f"Path to the MediaPipe face landmarker task file. Defaults to {DEFAULT_MODEL_PATH}.",
    )
    parser.add_argument(
        "--selfie-model-path",
        type=Path,
        default=DEFAULT_SELFIE_MULTICLASS_MODEL_PATH,
        help=f"Path to the MediaPipe selfie multiclass segmenter. Defaults to {DEFAULT_SELFIE_MULTICLASS_MODEL_PATH}.",
    )
    parser.add_argument(
        "--hair-model-path",
        type=Path,
        default=DEFAULT_HAIR_SEGMENTER_MODEL_PATH,
        help=f"Path to the MediaPipe hair segmenter. Defaults to {DEFAULT_HAIR_SEGMENTER_MODEL_PATH}.",
    )
    parser.add_argument(
        "--size",
        type=int,
        default=1024,
        help="Square output size in pixels for the SVG canvas.",
    )
    parser.add_argument(
        "--min-confidence",
        type=float,
        default=0.35,
        help="Minimum detection and presence confidence.",
    )
    parser.add_argument(
        "--no-debug-overlay",
        action="store_true",
        help="Skip saving the landmark and segmentation diagnostic images.",
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    if not args.image.exists():
        raise FileNotFoundError(f"Input image not found: {args.image}")

    svg_path, meta_path, landmarks_debug_path, segmentation_debug_path = build_avatar(
        image_path=args.image,
        output_dir=args.output_dir,
        model_path=args.model_path,
        selfie_model_path=args.selfie_model_path,
        hair_model_path=args.hair_model_path,
        size=args.size,
        min_confidence=args.min_confidence,
        save_debug_overlay=not args.no_debug_overlay,
    )
    print(f"Avatar SVG: {svg_path}")
    print(f"Avatar metadata: {meta_path}")
    if landmarks_debug_path is not None:
        print(f"Landmark debug overlay: {landmarks_debug_path}")
    if segmentation_debug_path is not None:
        print(f"Segmentation debug overlay: {segmentation_debug_path}")


if __name__ == "__main__":
    main()
