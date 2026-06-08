from __future__ import annotations

import argparse
import html
import json
import math
import webbrowser
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Iterable


ROOT = Path(__file__).resolve().parent
DEFAULT_OUTPUT_DIR = ROOT / "avatar_outputs"
SVG_NS = "http://www.w3.org/2000/svg"
ET.register_namespace("", SVG_NS)

HEAD_LAYER_IDS = {
    "hair-back",
    "ears",
    "face-base",
    "face-planes",
    "brows",
    "eyes",
    "nose",
    "mouth",
    "hair-front",
}


def clamp(value: float, low: float, high: float) -> float:
    return max(low, min(high, value))


def midpoint(a: tuple[float, float], b: tuple[float, float]) -> tuple[float, float]:
    return ((a[0] + b[0]) / 2.0, (a[1] + b[1]) / 2.0)


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


def points_from_dicts(points: Iterable[dict[str, float]]) -> list[tuple[float, float]]:
    return [(float(point["x"]), float(point["y"])) for point in points]


def point_bounds(points: list[tuple[float, float]]) -> tuple[float, float, float, float]:
    xs = [point[0] for point in points]
    ys = [point[1] for point in points]
    return min(xs), min(ys), max(xs), max(ys)


def union_bounds(boxes: Iterable[tuple[float, float, float, float] | None]) -> tuple[float, float, float, float]:
    valid = [box for box in boxes if box is not None]
    if not valid:
        raise ValueError("Could not compute bounds for the talking head.")
    return (
        min(box[0] for box in valid),
        min(box[1] for box in valid),
        max(box[2] for box in valid),
        max(box[3] for box in valid),
    )


def child_id(element: ET.Element) -> str | None:
    return element.attrib.get("id")


def find_direct_child_by_id(parent: ET.Element, target_id: str) -> ET.Element | None:
    for child in list(parent):
        if child_id(child) == target_id:
            return child
    return None


def derive_avatar_paths(args: argparse.Namespace) -> tuple[Path, Path, Path]:
    image_path = args.image
    if image_path is None:
        if (ROOT / "yassin.jpg").exists():
            image_path = ROOT / "yassin.jpg"
        else:
            raise FileNotFoundError("No image was provided and yassin.jpg was not found.")
    if not image_path.exists():
        raise FileNotFoundError(f"Image not found: {image_path}")

    output_dir = args.output_dir
    stem = image_path.stem
    svg_path = output_dir / f"{stem}_avatar.svg"
    json_path = output_dir / f"{stem}_avatar.json"
    return image_path, svg_path, json_path


def ensure_avatar_assets(args: argparse.Namespace) -> tuple[Path, Path, Path]:
    if args.avatar_svg is not None and args.avatar_json is not None:
        image_path = args.image if args.image is not None else ROOT / "yassin.jpg"
        return image_path, args.avatar_svg, args.avatar_json

    image_path, svg_path, json_path = derive_avatar_paths(args)
    if svg_path.exists() and json_path.exists():
        return image_path, svg_path, json_path

    if not args.build_if_missing:
        raise FileNotFoundError(
            f"Avatar assets are missing. Expected {svg_path.name} and {json_path.name} in {svg_path.parent}."
        )

    from generate_cartoon_avatar import (
        DEFAULT_HAIR_SEGMENTER_MODEL_PATH,
        DEFAULT_MODEL_PATH,
        DEFAULT_SELFIE_MULTICLASS_MODEL_PATH,
        build_avatar,
    )

    build_avatar(
        image_path=image_path,
        output_dir=args.output_dir,
        model_path=DEFAULT_MODEL_PATH,
        selfie_model_path=DEFAULT_SELFIE_MULTICLASS_MODEL_PATH,
        hair_model_path=DEFAULT_HAIR_SEGMENTER_MODEL_PATH,
        size=args.avatar_size,
        min_confidence=args.min_confidence,
        save_debug_overlay=not args.no_debug_overlay,
    )
    return image_path, svg_path, json_path


def compute_head_viewbox(metadata: dict[str, object]) -> tuple[float, float, float, float]:
    rig = metadata["rig"]
    paths = rig["paths"]
    candidate_keys = ["head", "hair", "face", "front_hair", "left_ear", "right_ear", "outer_lip"]
    boxes: list[tuple[float, float, float, float] | None] = []
    for key in candidate_keys:
        raw_points = paths.get(key) or []
        if not raw_points:
            boxes.append(None)
            continue
        points = points_from_dicts(raw_points)
        if len(points) < 2:
            boxes.append(None)
            continue
        boxes.append(point_bounds(points))

    min_x, min_y, max_x, max_y = union_bounds(boxes)
    width = max_x - min_x
    height = max_y - min_y
    pad_x = width * 0.12
    pad_top = height * 0.16
    pad_bottom = height * 0.12
    x0 = min_x - pad_x
    y0 = min_y - pad_top
    x1 = max_x + pad_x
    y1 = max_y + pad_bottom

    side = max(x1 - x0, y1 - y0)
    cx = (x0 + x1) / 2.0
    cy = (y0 + y1) / 2.0
    return (cx - side / 2.0, cy - side / 2.0, side, side)


def split_eye_groups(head_root: ET.Element) -> None:
    eyes_group = find_direct_child_by_id(head_root, "eyes")
    if eyes_group is None:
        return
    children = list(eyes_group)
    if not children:
        return

    left_group = ET.Element(f"{{{SVG_NS}}}g", {"id": "left-eye-group"})
    right_group = ET.Element(f"{{{SVG_NS}}}g", {"id": "right-eye-group"})
    current_side = "left"

    for child in children:
        current_id = child.attrib.get("id", "")
        if current_id.startswith("right-"):
            current_side = "right"
        eyes_group.remove(child)
        if current_side == "left":
            left_group.append(child)
        else:
            right_group.append(child)

    eyes_group.append(left_group)
    eyes_group.append(right_group)


def build_head_only_svg(svg_text: str, metadata: dict[str, object], viewport_size: int) -> str:
    root = ET.fromstring(svg_text)

    for child in list(root):
        child_tag = child.tag.split("}")[-1]
        if child_tag != "g":
            continue
        if child_id(child) not in HEAD_LAYER_IDS:
            root.remove(child)

    split_candidates = [child for child in list(root) if child.tag.split("}")[-1] == "g"]
    head_root = ET.Element(f"{{{SVG_NS}}}g", {"id": "head-root"})
    for child in split_candidates:
        root.remove(child)
        head_root.append(child)
    root.append(head_root)
    split_eye_groups(head_root)

    for parent in head_root.iter():
        for child in list(parent):
            if child.tag.split("}")[-1] != "image":
                continue
            opacity = float(child.attrib.get("opacity", "1") or "1")
            if opacity <= 0.001:
                parent.remove(child)

    view_x, view_y, view_w, view_h = compute_head_viewbox(metadata)
    root.set("width", str(viewport_size))
    root.set("height", str(viewport_size))
    root.set("viewBox", f"{view_x:.2f} {view_y:.2f} {view_w:.2f} {view_h:.2f}")
    root.set("preserveAspectRatio", "xMidYMid meet")
    root.set("class", "talking-head-svg")

    return ET.tostring(root, encoding="unicode")


def build_widget_html(
    *,
    title: str,
    initial_text: str,
    head_svg: str,
    metadata: dict[str, object],
    tts_config: dict[str, object] | None = None,
) -> str:
    rig = metadata["rig"]
    palette = metadata["palette"]
    resolved_tts_config = {"mode": "browser", **(tts_config or {})}
    tts_mode = str(resolved_tts_config.get("mode", "browser") or "browser").lower()
    tts_label = str(resolved_tts_config.get("label", "") or "local TTS")
    hint_text = (
        str(
            resolved_tts_config.get(
                "hint",
                f"This demo sends text to the local {tts_label} backend, plays the generated wav, and drives the mouth from the returned audio energy.",
            )
        )
        if tts_mode != "browser"
        else "This demo uses the browser voice engine, so the exact voice depends on the browser and operating system."
    )
    script_payload = {
        "rig": {
            "anchors": rig["anchors"],
            "metrics": rig["metrics"],
            "animation_presets": rig["animation_presets"],
        },
        "palette": {
            "lip_dark": palette["lip_dark"],
            "shadow": palette["shadow"],
            "hair_shadow": palette["hair_shadow"],
            "highlight": palette["highlight"],
        },
        "initialText": initial_text,
        "title": title,
        "tts": resolved_tts_config,
    }
    payload_json = json.dumps(script_payload, separators=(",", ":"))
    escaped_text = html.escape(initial_text)
    escaped_hint = html.escape(hint_text)

    return f"""<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>{html.escape(title)}</title>
  <style>
    :root {{
      --panel-bg: rgba(15, 18, 28, 0.78);
      --panel-border: rgba(255, 255, 255, 0.10);
      --panel-text: #eef3ff;
      --panel-muted: rgba(238, 243, 255, 0.72);
      --accent: #87b3ff;
      --accent-strong: #b7cbff;
      --bubble-bg: rgba(12, 17, 30, 0.92);
      --bubble-border: rgba(255, 255, 255, 0.08);
      --shadow: rgba(7, 10, 19, 0.38);
    }}

    * {{
      box-sizing: border-box;
    }}

    body {{
      margin: 0;
      min-height: 100vh;
      font-family: "Segoe UI", "Trebuchet MS", sans-serif;
      color: var(--panel-text);
      background:
        radial-gradient(circle at 20% 20%, rgba(79, 104, 164, 0.42), transparent 28%),
        radial-gradient(circle at 78% 24%, rgba(120, 86, 162, 0.28), transparent 24%),
        radial-gradient(circle at 50% 80%, rgba(83, 133, 176, 0.20), transparent 28%),
        linear-gradient(180deg, #060913 0%, #0b1020 42%, #0d1327 100%);
      overflow: hidden;
    }}

    body::before {{
      content: "";
      position: fixed;
      inset: 0;
      background-image:
        radial-gradient(circle at 12% 18%, rgba(255,255,255,0.85) 0 1px, transparent 1.2px),
        radial-gradient(circle at 68% 14%, rgba(255,255,255,0.8) 0 1px, transparent 1.2px),
        radial-gradient(circle at 84% 42%, rgba(255,255,255,0.7) 0 1px, transparent 1.2px),
        radial-gradient(circle at 28% 68%, rgba(255,255,255,0.76) 0 1px, transparent 1.2px),
        radial-gradient(circle at 70% 78%, rgba(255,255,255,0.82) 0 1px, transparent 1.2px),
        radial-gradient(circle at 38% 36%, rgba(255,255,255,0.62) 0 1px, transparent 1.2px);
      opacity: 0.64;
      pointer-events: none;
    }}

    .demo-root {{
      position: fixed;
      inset: 0;
      display: grid;
      grid-template-columns: minmax(260px, 360px) 1fr;
      gap: 20px;
      padding: 24px;
      align-items: end;
    }}

    .control-panel {{
      align-self: stretch;
      display: flex;
      flex-direction: column;
      gap: 14px;
      padding: 18px;
      border: 1px solid var(--panel-border);
      border-radius: 22px;
      background: var(--panel-bg);
      backdrop-filter: blur(18px);
      box-shadow: 0 18px 60px var(--shadow);
      max-width: 360px;
    }}

    .control-panel h1 {{
      margin: 0;
      font-size: 1.02rem;
      letter-spacing: 0.04em;
      text-transform: uppercase;
      color: var(--accent-strong);
    }}

    .control-panel p {{
      margin: 0;
      color: var(--panel-muted);
      line-height: 1.45;
      font-size: 0.94rem;
    }}

    .control-panel textarea {{
      width: 100%;
      min-height: 150px;
      resize: vertical;
      border: 1px solid rgba(255, 255, 255, 0.10);
      border-radius: 16px;
      background: rgba(255, 255, 255, 0.05);
      color: var(--panel-text);
      padding: 14px 15px;
      font: inherit;
      line-height: 1.45;
      outline: none;
    }}

    .control-panel textarea:focus {{
      border-color: rgba(135, 179, 255, 0.68);
      box-shadow: 0 0 0 3px rgba(135, 179, 255, 0.16);
    }}

    .button-row {{
      display: flex;
      flex-wrap: wrap;
      gap: 10px;
    }}

    .button-row button {{
      appearance: none;
      border: none;
      border-radius: 999px;
      padding: 11px 16px;
      font: inherit;
      font-weight: 700;
      letter-spacing: 0.02em;
      color: #06111f;
      background: linear-gradient(135deg, #e7f0ff, #9ab9ff);
      cursor: pointer;
      box-shadow: 0 10px 24px rgba(77, 119, 218, 0.28);
    }}

    .button-row button.secondary {{
      color: var(--panel-text);
      background: rgba(255, 255, 255, 0.08);
      box-shadow: none;
      border: 1px solid rgba(255, 255, 255, 0.09);
    }}

    .button-row button:disabled {{
      cursor: wait;
      opacity: 0.78;
      filter: saturate(0.7);
    }}

    .hint {{
      font-size: 0.86rem;
      color: var(--panel-muted);
    }}

    .hint.error {{
      color: #ffd0d0;
    }}

    .stage {{
      position: relative;
      min-height: 100%;
      display: flex;
      justify-content: flex-end;
      align-items: flex-end;
      overflow: hidden;
    }}

    .popup {{
      position: relative;
      width: min(42vw, 420px);
      min-width: 260px;
      margin-right: 2vw;
      margin-bottom: 4vh;
      pointer-events: none;
    }}

    .speech-bubble {{
      position: absolute;
      left: 50%;
      top: 0;
      transform: translate(-50%, -88%);
      width: min(420px, 92vw);
      padding: 14px 16px;
      border-radius: 18px;
      border: 1px solid var(--bubble-border);
      background: var(--bubble-bg);
      color: var(--panel-text);
      box-shadow: 0 18px 40px rgba(0, 0, 0, 0.24);
      backdrop-filter: blur(14px);
      line-height: 1.4;
      font-size: 0.95rem;
      opacity: 0.95;
    }}

    .speech-bubble:after {{
      content: "";
      position: absolute;
      left: 50%;
      bottom: -10px;
      transform: translateX(-50%) rotate(45deg);
      width: 18px;
      height: 18px;
      background: var(--bubble-bg);
      border-right: 1px solid var(--bubble-border);
      border-bottom: 1px solid var(--bubble-border);
    }}

    .avatar-shadow {{
      position: absolute;
      left: 50%;
      bottom: 1.5%;
      width: 42%;
      height: 7.5%;
      transform: translateX(-50%);
      background: radial-gradient(ellipse at center, rgba(0, 0, 0, 0.30), rgba(0, 0, 0, 0));
      filter: blur(10px);
      opacity: 0.72;
    }}

    .avatar-shell {{
      position: relative;
      z-index: 2;
      filter: drop-shadow(0 28px 44px rgba(0, 0, 0, 0.24));
      will-change: transform;
      transform-origin: 50% 78%;
      pointer-events: auto;
    }}

    .avatar-shell svg {{
      display: block;
      width: 100%;
      height: auto;
      overflow: visible;
    }}

    .avatar-shell [id="head-root"] {{
      will-change: transform;
    }}

    @media (max-width: 900px) {{
      .demo-root {{
        grid-template-columns: 1fr;
        align-items: start;
      }}

      .control-panel {{
        max-width: none;
      }}

      .stage {{
        min-height: 52vh;
        justify-content: center;
      }}

      .popup {{
        width: min(68vw, 360px);
        margin: 0;
      }}
    }}
  </style>
</head>
<body>
  <div class="demo-root">
    <section class="control-panel">
      <h1>{html.escape(title)}</h1>
      <p>Type a line, press speak, and the popup head will float with smoother lip-sync and occasional blinking. Later on your site you can call <code>window.talkingHead.say(...)</code> directly.</p>
      <textarea id="textInput">{escaped_text}</textarea>
      <div class="button-row">
        <button id="speakBtn" type="button">Speak</button>
        <button id="stopBtn" type="button" class="secondary">Stop</button>
      </div>
      <p class="hint" id="hintText">{escaped_hint}</p>
    </section>

    <section class="stage">
      <div class="popup" id="popup">
        <div class="speech-bubble" id="speechBubble">{escaped_text}</div>
        <div class="avatar-shadow" id="avatarShadow"></div>
        <div class="avatar-shell" id="avatarShell">
          {head_svg}
        </div>
      </div>
    </section>
  </div>

  <script>
    const widgetData = {payload_json};

    const svg = document.querySelector(".talking-head-svg");
    const headRoot = svg.querySelector("#head-root") || svg;
    const avatarShell = document.getElementById("avatarShell");
    const avatarShadow = document.getElementById("avatarShadow");
    const textInput = document.getElementById("textInput");
    const speechBubble = document.getElementById("speechBubble");
    const speakButton = document.getElementById("speakBtn");
    const stopButton = document.getElementById("stopBtn");
    const hintText = document.getElementById("hintText");

    const leftEyeGroup = svg.querySelector("#left-eye-group");
    const rightEyeGroup = svg.querySelector("#right-eye-group");
    const mouthOuter = svg.querySelector("#outer-lip");
    const mouthInner = svg.querySelector("#inner-mouth");
    const mouthClip = svg.querySelector("#mouth-clip path");
    const innerMouthClip = svg.querySelector("#inner-mouth-clip path");

    const rig = widgetData.rig;
    const palette = widgetData.palette;
    const ttsConfig = widgetData.tts || {{ mode: "browser" }};
    const ttsMode = String(ttsConfig.mode || "browser").toLowerCase();
    const mouthPresets = rig.animation_presets.mouth;
    const eyeCenters = new Map();

    const mouthDefaults = {{
      innerStroke: mouthInner.getAttribute("stroke") || palette.lip_dark,
      innerStrokeWidth: mouthInner.getAttribute("stroke-width") || "2.0",
      innerOpacity: mouthInner.getAttribute("opacity") || "0.72",
      outerOpacity: mouthOuter.getAttribute("opacity") || "0.98",
    }};
    const neutralPreset = mouthPresets.neutral || mouthPresets.rest;

    let speaking = false;
    let currentUtterance = null;
    let currentAudio = null;
    let currentAbortController = null;
    let activePlayback = null;
    let mouthTimer = null;
    let boundaryTimer = null;
    let finishTimer = null;
    let speakRequestToken = 0;
    let currentOuter = neutralPreset.outer.map((point) => ({{ x: Number(point.x), y: Number(point.y) }}));
    let currentInner = neutralPreset.inner.map((point) => ({{ x: Number(point.x), y: Number(point.y) }}));
    let targetOuter = neutralPreset.outer.map((point) => ({{ x: Number(point.x), y: Number(point.y) }}));
    let targetInner = neutralPreset.inner.map((point) => ({{ x: Number(point.x), y: Number(point.y) }}));
    let currentOpen = 0;
    let visemeOpen = 0.15;
    let audioDrivenOpen = 0;
    let speechEnergy = 0;
    let blinkAmount = 0;
    let blinkTarget = 0;
    let blinkCooldown = performance.now() + 2800 + Math.random() * 2600;

    function midpoint(a, b) {{
      return {{ x: (a.x + b.x) / 2, y: (a.y + b.y) / 2 }};
    }}

    function closedSmoothPath(points) {{
      if (!points || points.length < 3) {{
        return "";
      }}
      const start = midpoint(points[points.length - 1], points[0]);
      const commands = [`M ${{start.x.toFixed(1)}} ${{start.y.toFixed(1)}}`];
      for (let index = 0; index < points.length; index += 1) {{
        const point = points[index];
        const end = midpoint(point, points[(index + 1) % points.length]);
        commands.push(`Q ${{point.x.toFixed(1)}} ${{point.y.toFixed(1)}} ${{end.x.toFixed(1)}} ${{end.y.toFixed(1)}}`);
      }}
      commands.push("Z");
      return commands.join(" ");
    }}

    function setHint(message, isError = false) {{
      hintText.textContent = message;
      hintText.classList.toggle("error", Boolean(isError));
    }}

    function setSpeakBusy(isBusy, busyLabel = "Generating...") {{
      speakButton.disabled = isBusy;
      speakButton.textContent = isBusy ? busyLabel : "Speak";
    }}

    function mixHex(a, b, amount) {{
      const parse = (value) => {{
        const clean = value.replace("#", "");
        return [
          parseInt(clean.slice(0, 2), 16),
          parseInt(clean.slice(2, 4), 16),
          parseInt(clean.slice(4, 6), 16),
        ];
      }};
      const [ar, ag, ab] = parse(a);
      const [br, bg, bb] = parse(b);
      const t = Math.max(0, Math.min(1, amount));
      const toHex = (value) => value.toString(16).padStart(2, "0");
      const r = Math.round(ar * (1 - t) + br * t);
      const g = Math.round(ag * (1 - t) + bg * t);
      const b2 = Math.round(ab * (1 - t) + bb * t);
      return `#${{toHex(r)}}${{toHex(g)}}${{toHex(b2)}}`;
    }}

    function eyeCenter(group) {{
      if (!group) {{
        return {{ x: 0, y: 0 }};
      }}
      if (eyeCenters.has(group)) {{
        return eyeCenters.get(group);
      }}
      let center = {{ x: 0, y: 0 }};
      try {{
        const box = group.getBBox();
        center = {{ x: box.x + box.width / 2, y: box.y + box.height / 2 }};
      }} catch (_error) {{
        center = {{ x: 0, y: 0 }};
      }}
      eyeCenters.set(group, center);
      return center;
    }}

    function setEyeScale(group, openness) {{
      if (!group) {{
        return;
      }}
      const center = eyeCenter(group);
      const scaleY = Math.max(0.05, openness);
      const squeezeShift = (1 - scaleY) * 3.2;
      group.setAttribute(
        "transform",
        `translate(${{center.x}} ${{center.y + squeezeShift}}) scale(1 ${{scaleY}}) translate(${{-center.x}} ${{-center.y}})`,
      );
    }}

    function applyBlink(level) {{
      const openness = 1 - level * 0.97;
      setEyeScale(leftEyeGroup, openness);
      setEyeScale(rightEyeGroup, openness);
    }}

    function clonePoints(points) {{
      return points.map((point) => ({{ x: Number(point.x), y: Number(point.y) }}));
    }}

    function blendPointLists(fromPoints, toPoints, amount) {{
      if (!Array.isArray(fromPoints) || !Array.isArray(toPoints) || fromPoints.length !== toPoints.length) {{
        return clonePoints(toPoints || []);
      }}
      return fromPoints.map((point, index) => {{
        const target = toPoints[index];
        return {{
          x: point.x + (target.x - point.x) * amount,
          y: point.y + (target.y - point.y) * amount,
        }};
      }});
    }}

    function setTargetMouth(name) {{
      const preset = mouthPresets[name] || mouthPresets.neutral || mouthPresets.rest;
      if (!preset) {{
        return;
      }}

      targetOuter = clonePoints(preset.outer);
      targetInner = clonePoints(preset.inner);
      visemeOpen = {{
        open: 1.0,
        aa: 0.95,
        oo: 0.88,
        wide: 0.42,
        ee: 0.32,
        fv: 0.20,
        smile: 0.18,
        narrow: 0.10,
        mbp: 0.05,
        rest: 0.10,
        neutral: 0.15,
      }}[name] ?? 0.10;
    }}

    function setAudioDrivenOpen(value) {{
      const numeric = Number(value);
      audioDrivenOpen = Number.isFinite(numeric) ? Math.max(0, Math.min(1, numeric)) : 0;
    }}

    function applyMouthState() {{
      const outerPath = closedSmoothPath(currentOuter);
      const innerPath = closedSmoothPath(currentInner);
      mouthOuter.setAttribute("d", outerPath);
      mouthClip.setAttribute("d", outerPath);
      mouthInner.setAttribute("d", innerPath);
      innerMouthClip.setAttribute("d", innerPath);

      const fillOpacity = Math.max(0.14, currentOpen * 0.94);
      const strokeOpacity = Math.max(0.06, 0.60 - currentOpen * 0.50);
      mouthOuter.setAttribute("opacity", String((0.92 + currentOpen * 0.06).toFixed(3)));
      mouthInner.setAttribute("fill", mixHex(palette.lip_dark, palette.shadow, 0.34));
      mouthInner.setAttribute("fill-opacity", String(fillOpacity.toFixed(3)));
      mouthInner.setAttribute("stroke", mouthDefaults.innerStroke);
      mouthInner.setAttribute("stroke-width", mouthDefaults.innerStrokeWidth);
      mouthInner.setAttribute("stroke-opacity", String(strokeOpacity.toFixed(3)));
      mouthInner.setAttribute("opacity", "1.0");
    }}

    function charToViseme(char) {{
      if (!char) {{
        return "neutral";
      }}
      if (/[bmp]/.test(char)) {{
        return "mbp";
      }}
      if (/[fv]/.test(char)) {{
        return "fv";
      }}
      if (/[ouqw]/.test(char)) {{
        return "oo";
      }}
      if (/[eiy]/.test(char)) {{
        return "ee";
      }}
      if (/[a]/.test(char)) {{
        return "aa";
      }}
      if (/[.!?]/.test(char)) {{
        return "rest";
      }}
      if (/[,;:]/.test(char)) {{
        return "rest";
      }}
      if (/\s/.test(char)) {{
        return "rest";
      }}
      if (/[cgjklnrstxzdh]/.test(char)) {{
        return "wide";
      }}
      return "neutral";
    }}

    function buildVisemeFrames(text) {{
      const cleaned = (text || "").toLowerCase();
      const frames = [];
      for (const char of cleaned) {{
        const viseme = charToViseme(char);
        if (!viseme) {{
          continue;
        }}
        if (frames.length === 0 || frames[frames.length - 1] !== viseme) {{
          frames.push(viseme);
        }}
        if (/[aeiouy]/.test(char)) {{
          frames.push(viseme);
        }}
        if (/[.!?,;:\s]/.test(char)) {{
          frames.push("rest");
        }}
      }}
      return frames.filter(Boolean).length ? frames : ["neutral"];
    }}

    function clearSpeechTimers() {{
      if (mouthTimer) {{
        clearInterval(mouthTimer);
        mouthTimer = null;
      }}
      if (boundaryTimer) {{
        clearTimeout(boundaryTimer);
        boundaryTimer = null;
      }}
      if (finishTimer) {{
        clearTimeout(finishTimer);
        finishTimer = null;
      }}
    }}

    function stopMouthTimeline() {{
      clearSpeechTimers();
      setTargetMouth("neutral");
    }}

    function startMouthTimeline(text, rate = 1) {{
      stopMouthTimeline();
      const frames = buildVisemeFrames(text);
      let cursor = 0;
      const stepMs = Math.max(62, 150 / Math.max(rate, 0.35));
      mouthTimer = window.setInterval(() => {{
        const frame = frames[Math.min(cursor, frames.length - 1)];
        setTargetMouth(frame);
        cursor += 1;
        if (cursor >= frames.length) {{
          clearInterval(mouthTimer);
          mouthTimer = null;
          boundaryTimer = window.setTimeout(() => {{
            if (!speaking) {{
              setTargetMouth("neutral");
            }} else {{
              setTargetMouth("rest");
            }}
          }}, stepMs);
        }}
      }}, stepMs);
    }}

    function stopAudioPlayback() {{
      if (currentAudio) {{
        currentAudio.pause();
        currentAudio.removeAttribute("src");
        currentAudio.load();
      }}
      currentAudio = null;
      activePlayback = null;
      setAudioDrivenOpen(0);
    }}

    function notifyHost(type, detail = {{}}) {{
      if (window.parent === window) {{
        return;
      }}
      try {{
        window.parent.postMessage({{ type, ...detail }}, "*");
      }} catch (error) {{
        console.debug("Host notification failed.", error);
      }}
    }}

    function finishSpeech() {{
      speaking = false;
      currentUtterance = null;
      clearSpeechTimers();
      stopAudioPlayback();
      setTargetMouth("neutral");
      setSpeakBusy(false);
      notifyHost("talking-head:stopped");
      if (ttsMode !== "browser") {{
        setHint(
          ttsConfig.hint
            || `This demo sends text to the local ${{ttsConfig.label || "TTS"}} backend, plays the generated wav, and drives the mouth from the returned audio energy.`,
        );
      }}
    }}

    function stopSpeech(invalidateRequest = true) {{
      if (invalidateRequest) {{
        speakRequestToken += 1;
      }}
      speaking = false;
      stopMouthTimeline();
      speechEnergy = 0;
      if (currentAbortController) {{
        currentAbortController.abort();
        currentAbortController = null;
      }}
      if ("speechSynthesis" in window) {{
        window.speechSynthesis.cancel();
      }}
      stopAudioPlayback();
      currentUtterance = null;
      setSpeakBusy(false);
      notifyHost("talking-head:stopped");
    }}

    function playbackStateFromResponse(text, payload, audio) {{
      return {{
        audio,
        duration: Number(payload.duration_seconds || 0),
        energyTimeline: Array.isArray(payload.energy_timeline)
          ? payload.energy_timeline
              .map((value) => Number(value))
              .filter((value) => Number.isFinite(value))
              .map((value) => Math.max(0, Math.min(1, value)))
          : [],
        frameHz: Math.max(1, Number(payload.frame_hz) || 30),
        visemes: buildVisemeFrames(text),
        lastViseme: null,
      }};
    }}

    function updatePlaybackAnimation() {{
      if (!activePlayback) {{
        if (!speaking) {{
          setAudioDrivenOpen(0);
        }}
        return;
      }}

      const audio = activePlayback.audio;
      let currentTime = 0;
      let duration = Number(activePlayback.duration) || 0;
      if (audio) {{
        currentTime = Number(audio.currentTime) || 0;
        duration = Number(audio.duration) || duration;
      }}

      if (duration > 0 && activePlayback.visemes.length) {{
        const progress = Math.max(0, Math.min(0.999, currentTime / duration));
        const index = Math.min(activePlayback.visemes.length - 1, Math.floor(progress * activePlayback.visemes.length));
        const viseme = activePlayback.visemes[index] || "neutral";
        if (viseme !== activePlayback.lastViseme) {{
          setTargetMouth(viseme);
          activePlayback.lastViseme = viseme;
        }}
      }}

      if (activePlayback.energyTimeline.length) {{
        const energyIndex = Math.min(
          activePlayback.energyTimeline.length - 1,
          Math.floor(currentTime * activePlayback.frameHz),
        );
        setAudioDrivenOpen(activePlayback.energyTimeline[energyIndex] || 0);
      }} else {{
        setAudioDrivenOpen(0);
      }}
    }}

    async function synthesizeWithFastSpeech2(content) {{
      if (!ttsConfig.endpoint) {{
        throw new Error("No FastSpeech2 endpoint was configured for this page.");
      }}

      const requestPayload = {{
        text: content,
      }};
      if (Array.isArray(ttsConfig.speakerWavUrls) && ttsConfig.speakerWavUrls.length) {{
        requestPayload.speaker_wav_url = ttsConfig.speakerWavUrls;
      }}
      if (Array.isArray(ttsConfig.speakerWavPaths) && ttsConfig.speakerWavPaths.length) {{
        requestPayload.speaker_wav_path = ttsConfig.speakerWavPaths;
      }}

      currentAbortController = new AbortController();
      const response = await fetch(ttsConfig.endpoint, {{
        method: "POST",
        headers: {{
          "Content-Type": "application/json",
        }},
        body: JSON.stringify(requestPayload),
        signal: currentAbortController.signal,
      }});
      currentAbortController = null;

      if (!response.ok) {{
        const message = (await response.text()).trim();
        throw new Error(message || `FastSpeech2 request failed with status ${{response.status}}.`);
      }}

      return response.json();
    }}

    async function say(text, options = {{}}) {{
      const content = (text || "").trim();
      if (!content) {{
        return;
      }}

      speechBubble.textContent = content;
      textInput.value = content;
      stopSpeech(false);
      speaking = true;
      notifyHost("talking-head:started", {{ text: content }});
      const requestToken = speakRequestToken + 1;
      speakRequestToken = requestToken;

      if (ttsMode !== "browser") {{
        try {{
          setSpeakBusy(true, "Generating...");
          const waitNote = ttsMode === "xtts" ? " This can take a bit for a fresh line." : "";
          setHint(`Generating audio with ${{ttsConfig.label || "the local TTS model"}}...${{waitNote}}`);
          const payload = await synthesizeWithFastSpeech2(content);
          if (requestToken !== speakRequestToken) {{
            return;
          }}

          const audioUrl = String(payload.audio_url || "");
          if (!audioUrl) {{
            throw new Error("FastSpeech2 returned no audio URL.");
          }}

          const audio = new Audio(audioUrl);
          audio.preload = "auto";
          currentAudio = audio;
          currentUtterance = audio;
          activePlayback = playbackStateFromResponse(content, payload, audio);
          audio.onended = () => {{
            if (requestToken === speakRequestToken) {{
              finishSpeech();
            }}
          }};
          audio.onerror = () => {{
            if (requestToken === speakRequestToken) {{
              setHint("The generated audio could not be played back.", true);
              finishSpeech();
            }}
          }};

          setSpeakBusy(true, "Playing...");
          await audio.play();
          setSpeakBusy(false);
          setHint(
            `Playing generated audio from ${{ttsConfig.label || "the local TTS backend"}}.`,
          );
          return;
        }} catch (error) {{
          if (error && error.name === "AbortError") {{
            return;
          }}
          console.error(error);
          setHint(error?.message || "FastSpeech2 synthesis failed.", true);
          finishSpeech();
          return;
        }}
      }}

      const rate = Number(options.rate || 1);
      startMouthTimeline(content, rate);

      if (!("speechSynthesis" in window)) {{
        const silentDuration = Math.max(1200, content.length * 90);
        finishTimer = window.setTimeout(() => {{
          finishSpeech();
        }}, silentDuration);
        return;
      }}

      const utterance = new SpeechSynthesisUtterance(content);
      utterance.rate = clamp(options.rate ?? 1, 0.65, 1.35);
      utterance.pitch = clamp(options.pitch ?? 1, 0.75, 1.25);
      utterance.volume = clamp(options.volume ?? 1, 0.0, 1.0);

      const voices = window.speechSynthesis.getVoices();
      if (options.voiceName) {{
        const chosen = voices.find((voice) => voice.name === options.voiceName);
        if (chosen) {{
          utterance.voice = chosen;
        }}
      }}

      utterance.onboundary = (event) => {{
        if (typeof event.charIndex !== "number") {{
          return;
        }}
        const char = content[Math.min(event.charIndex, content.length - 1)] || " ";
        const viseme = charToViseme(char);
        if (viseme) {{
          if (mouthTimer) {{
            clearInterval(mouthTimer);
            mouthTimer = null;
          }}
          setTargetMouth(viseme);
        }}
      }};

      utterance.onend = () => {{
        finishSpeech();
      }};

      utterance.onerror = () => {{
        finishSpeech();
      }};

      currentUtterance = utterance;
      window.speechSynthesis.cancel();
      window.speechSynthesis.speak(utterance);
    }}

    function animate(now) {{
      updatePlaybackAnimation();
      const t = now * 0.001;
      currentOuter = blendPointLists(currentOuter, targetOuter, 0.24);
      currentInner = blendPointLists(currentInner, targetInner, 0.28);
      const desiredOpen = speaking
        ? Math.max(visemeOpen * 0.55, Math.min(1, visemeOpen * 0.28 + audioDrivenOpen * 0.92))
        : 0;
      currentOpen += (desiredOpen - currentOpen) * 0.22;
      const targetEnergy = speaking ? currentOpen : 0;
      speechEnergy += (targetEnergy - speechEnergy) * 0.18;
      applyMouthState();

      if (blinkTarget === 0 && now > blinkCooldown) {{
        blinkTarget = 1;
      }}
      blinkAmount += (blinkTarget - blinkAmount) * 0.34;
      if (blinkTarget === 1 && blinkAmount > 0.90) {{
        blinkTarget = 0;
        blinkCooldown = now + 3200 + Math.random() * 4200;
      }}
      if (blinkTarget === 0 && blinkAmount < 0.03) {{
        blinkAmount = 0;
      }}
      applyBlink(blinkAmount);

      const bob = Math.sin(t * 1.12) * 7 + speechEnergy * (1.4 + Math.sin(t * 8.6) * 1.8);
      const sway = Math.sin(t * 0.86) * 0.9 + speechEnergy * Math.sin(t * 4.2) * 0.28;
      const scale = 1 + speechEnergy * 0.016;
      avatarShell.style.transform = `translateY(${{bob.toFixed(2)}}px) rotate(${{sway.toFixed(2)}}deg) scale(${{scale.toFixed(3)}})`;

      const shadowScaleX = 1 + Math.sin(t * 1.12) * 0.04 + speechEnergy * 0.06;
      const shadowScaleY = 1 - Math.sin(t * 1.12) * 0.05 - speechEnergy * 0.08;
      avatarShadow.style.transform = `translateX(-50%) scale(${{shadowScaleX.toFixed(3)}}, ${{shadowScaleY.toFixed(3)}})`;
      avatarShadow.style.opacity = `${{0.58 + (1 - shadowScaleY) * 1.2}}`;

      requestAnimationFrame(animate);
    }}

    window.talkingHead = {{
      say,
      stop: stopSpeech,
      setText(text) {{
        textInput.value = text;
        speechBubble.textContent = text;
      }},
    }};

    window.addEventListener("message", (event) => {{
      const data = event?.data;
      if (!data || typeof data !== "object") {{
        return;
      }}

      const type = String(data.type || "");
      if (type === "talking-head:say") {{
        say(String(data.text || ""));
        return;
      }}

      if (type === "talking-head:setText") {{
        window.talkingHead.setText(String(data.text || ""));
        return;
      }}

      if (type === "talking-head:stop") {{
        stopSpeech();
      }}
    }});

    speakButton.addEventListener("click", () => say(textInput.value));
    stopButton.addEventListener("click", stopSpeech);
    textInput.addEventListener("input", () => {{
      speechBubble.textContent = textInput.value.trim() || " ";
    }});

    if ("speechSynthesis" in window) {{
      window.speechSynthesis.onvoiceschanged = () => {{}};
    }}

    if (ttsMode !== "browser") {{
      setHint(
        ttsConfig.hint
          || `This demo sends text to the local ${{ttsConfig.label || "TTS"}} backend, plays the generated wav, and drives the mouth from the returned audio energy.`,
      );
    }}

    setTargetMouth("neutral");
    applyMouthState();
    applyBlink(0);
    speechBubble.textContent = widgetData.initialText;
    notifyHost("talking-head:ready", {{
      title: widgetData.title,
      ttsMode,
      ttsLabel: ttsConfig.label || "",
    }});
    requestAnimationFrame(animate);
  </script>
</body>
</html>
"""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Generate a head-only floating talking-head popup demo from an existing avatar rig."
    )
    parser.add_argument(
        "--image",
        type=Path,
        default=None,
        help="Source image used to locate or build avatar assets. Defaults to yassin.jpg if present.",
    )
    parser.add_argument("--avatar-svg", type=Path, default=None, help="Use an existing avatar SVG file.")
    parser.add_argument("--avatar-json", type=Path, default=None, help="Use an existing avatar metadata JSON file.")
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=DEFAULT_OUTPUT_DIR,
        help=f"Directory for generated popup outputs. Defaults to {DEFAULT_OUTPUT_DIR}.",
    )
    parser.add_argument(
        "--text",
        type=str,
        default="Hello there. I am your floating talking head popup, and this is a test line.",
        help="Initial text shown in the demo and used when you press Speak.",
    )
    parser.add_argument(
        "--title",
        type=str,
        default="Talking Head Popup Demo",
        help="Title shown inside the demo control panel.",
    )
    parser.add_argument(
        "--viewport-size",
        type=int,
        default=640,
        help="Square pixel size for the generated head-only SVG.",
    )
    parser.add_argument(
        "--avatar-size",
        type=int,
        default=1024,
        help="If avatar assets need to be built first, this size is used for the avatar SVG canvas.",
    )
    parser.add_argument(
        "--min-confidence",
        type=float,
        default=0.35,
        help="Face detection confidence used only if avatar assets need to be built first.",
    )
    parser.add_argument(
        "--no-debug-overlay",
        action="store_true",
        help="Skip debug overlay generation if the avatar needs to be built first.",
    )
    parser.add_argument(
        "--no-build-if-missing",
        dest="build_if_missing",
        action="store_false",
        help="Fail instead of generating avatar assets when SVG/JSON are missing.",
    )
    parser.add_argument("--open", action="store_true", help="Open the generated HTML demo in the default browser.")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    image_path, avatar_svg_path, avatar_json_path = ensure_avatar_assets(args)

    metadata = json.loads(avatar_json_path.read_text(encoding="utf-8"))
    avatar_svg = avatar_svg_path.read_text(encoding="utf-8")
    head_svg = build_head_only_svg(avatar_svg, metadata, args.viewport_size)
    widget_html = build_widget_html(
        title=args.title,
        initial_text=args.text,
        head_svg=head_svg,
        metadata=metadata,
    )

    args.output_dir.mkdir(parents=True, exist_ok=True)
    stem = image_path.stem
    head_svg_path = args.output_dir / f"{stem}_talking_head.svg"
    html_path = args.output_dir / f"{stem}_talking_head_popup.html"

    head_svg_path.write_text(head_svg, encoding="utf-8")
    html_path.write_text(widget_html, encoding="utf-8")

    print(f"Head-only SVG written to: {head_svg_path}")
    print(f"Talking-head popup HTML written to: {html_path}")

    if args.open:
        webbrowser.open(html_path.resolve().as_uri())


if __name__ == "__main__":
    main()
