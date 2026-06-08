from __future__ import annotations

import argparse
import hashlib
import json
import os
import subprocess
import sys
import tempfile
import threading
import time
import wave
import webbrowser
from dataclasses import dataclass
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlparse
from urllib.request import urlopen

import numpy as np

from talking_head_popup import (
    DEFAULT_OUTPUT_DIR,
    ROOT,
    build_head_only_svg,
    build_widget_html,
    ensure_avatar_assets,
)

DEFAULT_TEXT = "Hello there. I am your floating talking head popup, and this is an XTTS offline test line."
DEFAULT_TITLE = "Talking Head XTTS Demo"
DEFAULT_FRAME_HZ = 30


@dataclass
class ServerState:
    html_bytes: bytes
    html_path: Path
    image_path: Path | None
    audio_dir: Path
    remote_asset_dir: Path
    speech_root: Path
    tts_python: Path
    synth_script: Path
    tts_engine: str
    tts_label: str
    checkpoint: Path | None
    config: Path | None
    run_dir: Path | None
    speaker_wav: list[Path]
    xtts_model: str
    xtts_language: str
    xtts_device: str
    xtts_max_ref_seconds: float
    xtts_trim_db: float
    xtts_normalize_reference: bool
    xtts_split_sentences: bool
    xtts_agree_to_coqui_cpml: bool
    xtts_report_timings: bool
    xtts_disable_conditioning_cache: bool
    cpu: bool
    frame_hz: int
    disable_cache: bool
    synth_lock: threading.Lock
    render_lock: threading.Lock
    xtts_runtime: object | None
    output_dir: Path
    title: str
    text: str
    viewport_size: int
    avatar_size: int
    min_confidence: float
    no_debug_overlay: bool
    build_if_missing: bool


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Serve the talking-head popup with offline TTS audio generation."
    )
    parser.add_argument("--image", type=Path, default=None, help="Source image used to locate avatar assets.")
    parser.add_argument("--avatar-svg", type=Path, default=None, help="Use an existing avatar SVG file.")
    parser.add_argument("--avatar-json", type=Path, default=None, help="Use an existing avatar metadata JSON file.")
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=DEFAULT_OUTPUT_DIR,
        help=f"Directory for generated popup outputs. Defaults to {DEFAULT_OUTPUT_DIR}.",
    )
    parser.add_argument("--text", type=str, default=DEFAULT_TEXT, help="Initial text shown in the page.")
    parser.add_argument("--title", type=str, default=DEFAULT_TITLE, help="Title shown in the control panel.")
    parser.add_argument("--viewport-size", type=int, default=640, help="Square pixel size for the head SVG.")
    parser.add_argument("--avatar-size", type=int, default=1024, help="Avatar canvas size if assets must be built.")
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
    parser.add_argument("--host", type=str, default="127.0.0.1", help="Host interface to bind. Default: 127.0.0.1")
    parser.add_argument("--port", type=int, default=8765, help="Port to listen on. Default: 8765")
    parser.add_argument(
        "--tts-engine",
        choices=["xtts", "fastspeech2"],
        default="xtts",
        help="Backend used for speech generation. Default: xtts",
    )
    parser.add_argument(
        "--speech-root",
        type=Path,
        default=ROOT.parent / "speechclonning",
        help="Path to the speechclonning workspace. Defaults to the sibling speechclonning folder.",
    )
    parser.add_argument(
        "--tts-python",
        type=Path,
        default=None,
        help="Explicit Python executable to use for TTS synthesis.",
    )
    parser.add_argument("--checkpoint", type=Path, default=None, help="Explicit FastSpeech2 checkpoint path.")
    parser.add_argument("--config", type=Path, default=None, help="Explicit FastSpeech2 config path.")
    parser.add_argument("--run-dir", type=Path, default=None, help="Explicit FastSpeech2 run directory.")
    parser.add_argument("--cpu", action="store_true", help="Force CPU synthesis.")
    parser.add_argument(
        "--speaker-wav",
        type=Path,
        nargs="+",
        default=None,
        help="Reference wav(s) for XTTS voice cloning. Defaults to speechclonning\\yassin.wav if present.",
    )
    parser.add_argument("--language", default="en", help="XTTS language code. Default: en")
    parser.add_argument(
        "--model",
        default="tts_models/multilingual/multi-dataset/xtts_v2",
        help="XTTS model name. Default: tts_models/multilingual/multi-dataset/xtts_v2",
    )
    parser.add_argument(
        "--device",
        choices=["auto", "cuda", "cpu"],
        default="auto",
        help="XTTS inference device. Default: auto",
    )
    parser.add_argument(
        "--max-ref-seconds",
        type=float,
        default=8.0,
        help="Maximum prepared duration per XTTS reference clip. Default: 8.0",
    )
    parser.add_argument(
        "--trim-db",
        type=float,
        default=30.0,
        help="Silence trimming threshold for XTTS reference prep. Use a negative value to disable. Default: 30",
    )
    parser.add_argument(
        "--normalize-reference",
        action="store_true",
        help="Peak-normalize XTTS reference audio after trimming.",
    )
    parser.add_argument(
        "--split-sentences",
        action="store_true",
        help="Enable sentence splitting in XTTS generation.",
    )
    parser.add_argument(
        "--agree-to-coqui-cpml",
        action="store_true",
        help="Confirm Coqui CPML acceptance for XTTS in non-interactive runs.",
    )
    parser.add_argument(
        "--report-timings",
        action="store_true",
        help="Print XTTS timing details from the clone script.",
    )
    parser.add_argument(
        "--no-conditioning-cache",
        action="store_true",
        help="Disable XTTS conditioning-cache reuse inside clone_voice.py.",
    )
    parser.add_argument(
        "--no-warmup",
        action="store_true",
        help="Skip loading the XTTS model at server startup.",
    )
    parser.add_argument("--frame-hz", type=int, default=DEFAULT_FRAME_HZ, help="Audio energy frame rate.")
    parser.add_argument("--disable-cache", action="store_true", help="Regenerate audio even for repeated text.")
    parser.add_argument("--open", action="store_true", help="Open the demo in the default browser.")
    return parser.parse_args()


def resolve_tts_python(args: argparse.Namespace) -> Path:
    if args.tts_python is not None:
        tts_python = args.tts_python.resolve()
    else:
        tts_python = (args.speech_root / ".venv311" / "Scripts" / "python.exe").resolve()
    if not tts_python.exists():
        raise FileNotFoundError(f"TTS Python executable was not found: {tts_python}")
    return tts_python


def resolve_optional_path(path: Path | None) -> Path | None:
    return path.resolve() if path is not None else None


def tts_label_for_engine(engine: str) -> str:
    return "XTTS v2" if engine == "xtts" else "FastSpeech2"


def tts_hint_for_engine(engine: str) -> str:
    if engine == "xtts":
        return "This demo sends text to the local XTTS clone backend, plays the generated wav, and drives the mouth from the returned audio energy. Fresh lines can take a while to generate."
    return "This demo sends text to the local FastSpeech2 backend, plays the generated wav, and drives the mouth from the returned audio energy."


def resolve_speaker_wavs(args: argparse.Namespace, speech_root: Path) -> list[Path]:
    if args.speaker_wav:
        candidates = [path.resolve() for path in args.speaker_wav]
    else:
        preferred = speech_root / "yassin.wav"
        if preferred.exists():
            candidates = [preferred.resolve()]
        else:
            wavs = sorted(speech_root.glob("*.wav"))
            candidates = [wavs[0].resolve()] if wavs else []

    if not candidates:
        raise FileNotFoundError(
            "No XTTS reference wav was found. Provide --speaker-wav or add a wav file under speechclonning."
        )

    missing = [str(path) for path in candidates if not path.exists()]
    if missing:
        raise FileNotFoundError(f"XTTS reference audio not found: {', '.join(missing)}")
    return candidates


def make_page_args(
    state: ServerState,
    *,
    image_path: Path | None,
    title: str,
    text: str,
) -> argparse.Namespace:
    return argparse.Namespace(
        image=image_path,
        avatar_svg=None,
        avatar_json=None,
        output_dir=state.output_dir,
        text=text,
        title=title,
        viewport_size=state.viewport_size,
        avatar_size=state.avatar_size,
        min_confidence=state.min_confidence,
        no_debug_overlay=state.no_debug_overlay,
        build_if_missing=state.build_if_missing,
        tts_engine=state.tts_engine,
    )


def download_remote_asset(url: str, cache_dir: Path, default_extension: str) -> Path:
    parsed = urlparse(url)
    suffix = Path(parsed.path).suffix.lower() or default_extension
    if not suffix.startswith("."):
        suffix = "." + suffix
    asset_name = hashlib.sha1(url.encode("utf-8")).hexdigest()[:20] + suffix
    target_path = cache_dir / asset_name
    if target_path.exists():
        return target_path

    cache_dir.mkdir(parents=True, exist_ok=True)
    with urlopen(url, timeout=30) as response, target_path.open("wb") as handle:
        handle.write(response.read())
    return target_path


def resolve_local_or_remote_asset(raw_value: str, cache_dir: Path, default_extension: str) -> Path:
    trimmed = str(raw_value or "").strip()
    if not trimmed:
        raise FileNotFoundError("Missing asset location.")

    parsed = urlparse(trimmed)
    if parsed.scheme in {"http", "https"}:
        return download_remote_asset(trimmed, cache_dir, default_extension)

    path = Path(trimmed).expanduser().resolve()
    if not path.exists():
        raise FileNotFoundError(f"Asset not found: {path}")
    return path


def normalize_string_list(raw_value: object) -> list[str]:
    if isinstance(raw_value, list):
        return [str(item).strip() for item in raw_value if str(item).strip()]
    if raw_value is None:
        return []
    trimmed = str(raw_value).strip()
    return [trimmed] if trimmed else []


def resolve_override_speaker_wavs(
    raw_urls: object,
    raw_paths: object,
    state: ServerState,
) -> list[Path]:
    override_paths: list[Path] = []
    for value in normalize_string_list(raw_urls):
        override_paths.append(
            resolve_local_or_remote_asset(value, state.remote_asset_dir / "speaker_wavs", ".wav")
        )
    for value in normalize_string_list(raw_paths):
        override_paths.append(
            resolve_local_or_remote_asset(value, state.remote_asset_dir / "speaker_wavs", ".wav")
        )
    return override_paths


def build_page(
    args: argparse.Namespace,
    tts_config_overrides: dict[str, object] | None = None,
) -> tuple[bytes, Path]:
    image_path, avatar_svg_path, avatar_json_path = ensure_avatar_assets(args)
    metadata = json.loads(avatar_json_path.read_text(encoding="utf-8"))
    avatar_svg = avatar_svg_path.read_text(encoding="utf-8")
    head_svg = build_head_only_svg(avatar_svg, metadata, args.viewport_size)
    tts_label = tts_label_for_engine(args.tts_engine)
    widget_html = build_widget_html(
        title=args.title,
        initial_text=args.text,
        head_svg=head_svg,
        metadata=metadata,
        tts_config={
            "mode": args.tts_engine,
            "endpoint": "/api/tts",
            "label": tts_label,
            "hint": tts_hint_for_engine(args.tts_engine),
            **(tts_config_overrides or {}),
        },
    )

    args.output_dir.mkdir(parents=True, exist_ok=True)
    stem = image_path.stem
    head_svg_path = args.output_dir / f"{stem}_talking_head.svg"
    html_path = args.output_dir / f"{stem}_talking_head_{args.tts_engine}.html"
    head_svg_path.write_text(head_svg, encoding="utf-8")
    html_path.write_text(widget_html, encoding="utf-8")
    return widget_html.encode("utf-8"), html_path


def build_state(args: argparse.Namespace) -> ServerState:
    speech_root = args.speech_root.resolve()
    synth_script_name = "clone_voice.py" if args.tts_engine == "xtts" else "synthesize_fastspeech2.py"
    synth_script = (speech_root / "scripts" / synth_script_name).resolve()
    if not synth_script.exists():
        raise FileNotFoundError(f"TTS synth script was not found: {synth_script}")

    html_bytes, html_path = build_page(args)
    audio_dir = args.output_dir.resolve() / "tts_audio"
    audio_dir.mkdir(parents=True, exist_ok=True)
    speaker_wav = resolve_speaker_wavs(args, speech_root) if args.tts_engine == "xtts" else []
    default_image_path = resolve_optional_path(args.image)
    if default_image_path is None:
        bundled_image = (ROOT / "yassin.jpg").resolve()
        default_image_path = bundled_image if bundled_image.exists() else None
    remote_asset_dir = args.output_dir.resolve() / "remote_assets"
    remote_asset_dir.mkdir(parents=True, exist_ok=True)

    return ServerState(
        html_bytes=html_bytes,
        html_path=html_path,
        image_path=default_image_path,
        audio_dir=audio_dir,
        remote_asset_dir=remote_asset_dir,
        speech_root=speech_root,
        tts_python=resolve_tts_python(args),
        synth_script=synth_script,
        tts_engine=args.tts_engine,
        tts_label=tts_label_for_engine(args.tts_engine),
        checkpoint=resolve_optional_path(args.checkpoint),
        config=resolve_optional_path(args.config),
        run_dir=resolve_optional_path(args.run_dir),
        speaker_wav=speaker_wav,
        xtts_model=str(args.model),
        xtts_language=str(args.language),
        xtts_device=str(args.device),
        xtts_max_ref_seconds=float(args.max_ref_seconds),
        xtts_trim_db=float(args.trim_db),
        xtts_normalize_reference=bool(args.normalize_reference),
        xtts_split_sentences=bool(args.split_sentences),
        xtts_agree_to_coqui_cpml=bool(args.agree_to_coqui_cpml),
        xtts_report_timings=bool(args.report_timings),
        xtts_disable_conditioning_cache=bool(args.no_conditioning_cache),
        cpu=bool(args.cpu),
        frame_hz=max(1, int(args.frame_hz)),
        disable_cache=bool(args.disable_cache),
        synth_lock=threading.Lock(),
        render_lock=threading.Lock(),
        xtts_runtime=None,
        output_dir=args.output_dir.resolve(),
        title=str(args.title),
        text=str(args.text),
        viewport_size=max(64, int(args.viewport_size)),
        avatar_size=max(128, int(args.avatar_size)),
        min_confidence=float(args.min_confidence),
        no_debug_overlay=bool(args.no_debug_overlay),
        build_if_missing=bool(args.build_if_missing),
    )


def build_request_page(state: ServerState, parsed) -> bytes:
    query = parse_qs(parsed.query, keep_blank_values=False)
    raw_image_value = next(
        (
            value
            for key in ("image_path", "image_url")
            for value in query.get(key, [])
            if str(value).strip()
        ),
        "",
    )
    image_path = state.image_path
    if raw_image_value:
        try:
            image_path = resolve_local_or_remote_asset(
                raw_image_value,
                state.remote_asset_dir / "images",
                ".jpg",
            )
        except Exception as error:  # noqa: BLE001
            print(f"Warning: could not resolve talking-head image override '{raw_image_value}': {error}")

    title = next((str(value).strip() for value in query.get("title", []) if str(value).strip()), state.title)
    text = next((str(value).strip() for value in query.get("text", []) if str(value).strip()), state.text)

    speaker_wav_urls = normalize_string_list(query.get("speaker_wav_url"))
    speaker_wav_paths = normalize_string_list(query.get("speaker_wav_path"))
    tts_config_overrides: dict[str, object] = {}
    if speaker_wav_urls:
        tts_config_overrides["speakerWavUrls"] = speaker_wav_urls
    if speaker_wav_paths:
        tts_config_overrides["speakerWavPaths"] = speaker_wav_paths

    page_args = make_page_args(
        state,
        image_path=image_path,
        title=title,
        text=text,
    )
    with state.render_lock:
        try:
            html_bytes, _ = build_page(page_args, tts_config_overrides=tts_config_overrides)
        except Exception as error:  # noqa: BLE001
            print(f"Warning: failed to build request-specific talking-head page: {error}")
            return state.html_bytes
    return html_bytes


def audio_key(text: str, state: ServerState, speaker_wav: list[Path] | None = None) -> str:
    effective_speaker_wav = speaker_wav if speaker_wav is not None else state.speaker_wav
    payload: dict[str, object] = {
        "engine": state.tts_engine,
        "text": text,
    }
    if state.tts_engine == "xtts":
        payload.update(
            {
                "speaker_wav": [
                    {
                        "path": str(path),
                        "size": path.stat().st_size,
                        "mtime_ns": path.stat().st_mtime_ns,
                    }
                    for path in effective_speaker_wav
                ],
                "model": state.xtts_model,
                "language": state.xtts_language,
                "device": state.xtts_device,
                "max_ref_seconds": state.xtts_max_ref_seconds,
                "trim_db": state.xtts_trim_db,
                "normalize_reference": state.xtts_normalize_reference,
                "split_sentences": state.xtts_split_sentences,
                "agree_to_coqui_cpml": state.xtts_agree_to_coqui_cpml,
                "disable_conditioning_cache": state.xtts_disable_conditioning_cache,
            }
        )
    else:
        payload.update(
            {
                "checkpoint": str(state.checkpoint) if state.checkpoint is not None else "",
                "config": str(state.config) if state.config is not None else "",
                "run_dir": str(state.run_dir) if state.run_dir is not None else "",
                "cpu": state.cpu,
            }
        )
    encoded = json.dumps(payload, sort_keys=True, separators=(",", ":")).encode("utf-8")
    return hashlib.sha1(encoded).hexdigest()[:16]


def build_synth_command(
    text: str,
    output_path: Path,
    state: ServerState,
    speaker_wav: list[Path] | None = None,
) -> list[str]:
    effective_speaker_wav = speaker_wav if speaker_wav is not None else state.speaker_wav
    command = [str(state.tts_python), str(state.synth_script), "--text", text, "--out", str(output_path)]
    if state.tts_engine == "xtts":
        command.extend(["--speaker-wav", *[str(path) for path in effective_speaker_wav]])
        command.extend(["--language", state.xtts_language, "--model", state.xtts_model, "--device", state.xtts_device])
        command.extend(["--max-ref-seconds", str(state.xtts_max_ref_seconds), "--trim-db", str(state.xtts_trim_db)])
        if state.xtts_normalize_reference:
            command.append("--normalize-ref")
        if state.xtts_split_sentences:
            command.append("--split-sentences")
        if state.xtts_agree_to_coqui_cpml:
            command.append("--agree-to-coqui-cpml")
        if state.xtts_report_timings:
            command.append("--report-timings")
        if state.xtts_disable_conditioning_cache:
            command.append("--no-cache-conditioning")
        return command

    if state.checkpoint is not None:
        command.extend(["--checkpoint", str(state.checkpoint)])
    if state.config is not None:
        command.extend(["--config", str(state.config)])
    if state.run_dir is not None:
        command.extend(["--run-dir", str(state.run_dir)])
    if state.cpu:
        command.append("--cpu")
    return command


def load_clone_voice_module(state: ServerState):
    scripts_dir = str((state.speech_root / "scripts").resolve())
    if scripts_dir not in sys.path:
        sys.path.insert(0, scripts_dir)
    import clone_voice  # type: ignore

    return clone_voice


def ensure_xtts_runtime(state: ServerState):
    if state.xtts_runtime is not None:
        return state.xtts_runtime

    clone_voice = load_clone_voice_module(state)
    if state.xtts_agree_to_coqui_cpml:
        os.environ["COQUI_TOS_AGREED"] = "1"

    from TTS.api import TTS

    device_arg = "cpu" if state.cpu else state.xtts_device
    device = clone_voice.resolve_device(device_arg)
    load_started_at = time.perf_counter()
    tts = TTS(model_name=state.xtts_model, progress_bar=False)
    if device == "cuda":
        tts = tts.to("cuda")
    model_load_seconds = time.perf_counter() - load_started_at

    state.xtts_runtime = {
        "module": clone_voice,
        "tts": tts,
        "model": tts.synthesizer.tts_model,
        "device": device,
        "model_load_seconds": model_load_seconds,
    }
    return state.xtts_runtime


def smooth(values: list[float]) -> list[float]:
    if not values:
        return []
    smoothed: list[float] = []
    for index in range(len(values)):
        start = max(0, index - 1)
        end = min(len(values), index + 2)
        window = values[start:end]
        smoothed.append(sum(window) / len(window))
    return smoothed


def analyze_audio(wav_path: Path, frame_hz: int) -> tuple[float, list[float]]:
    with wave.open(str(wav_path), "rb") as wav_file:
        sample_rate = wav_file.getframerate()
        sample_width = wav_file.getsampwidth()
        channel_count = wav_file.getnchannels()
        frame_count = wav_file.getnframes()
        raw_frames = wav_file.readframes(frame_count)

    if sample_rate <= 0 or frame_count <= 0:
        return 0.0, []

    duration_seconds = frame_count / float(sample_rate)
    if not raw_frames:
        return duration_seconds, []

    dtype_by_width = {
        1: np.uint8,
        2: np.int16,
        4: np.int32,
    }
    dtype = dtype_by_width.get(sample_width)
    if dtype is None:
        return duration_seconds, []

    samples = np.frombuffer(raw_frames, dtype=dtype)
    if channel_count > 1:
        samples = samples.reshape(-1, channel_count).mean(axis=1)

    if sample_width == 1:
        samples = (samples.astype(np.float32) - 128.0) / 128.0
    else:
        scale = float(2 ** (sample_width * 8 - 1))
        samples = samples.astype(np.float32) / scale

    frame_size = max(1, int(sample_rate / frame_hz))
    energies: list[float] = []
    for start in range(0, samples.shape[0], frame_size):
        chunk = samples[start : start + frame_size]
        if chunk.size == 0:
            continue
        rms = float(np.sqrt(np.mean(np.square(chunk), dtype=np.float64)))
        energies.append(rms)

    energies = smooth(energies)
    if not energies:
        return duration_seconds, []

    peak = max(energies)
    if peak <= 1e-6:
        return duration_seconds, [0.0 for _ in energies]

    normalized = []
    for value in energies:
        ratio = max(0.0, min(1.0, value / peak))
        lifted = ratio**0.62
        normalized.append(round(max(0.0, lifted - 0.02), 4))
    return duration_seconds, normalized


def synthesize_xtts_in_process(
    text: str,
    output_path: Path,
    state: ServerState,
    speaker_wav: list[Path] | None = None,
) -> None:
    runtime = ensure_xtts_runtime(state)
    clone_voice = runtime["module"]
    model = runtime["model"]
    trim_db = None if state.xtts_trim_db < 0 else state.xtts_trim_db
    effective_speaker_wav = speaker_wav if speaker_wav is not None else state.speaker_wav

    with tempfile.TemporaryDirectory(prefix="xtts_refprep_") as temp_dir_str:
        temp_dir = Path(temp_dir_str)
        prepared_reference_paths = []
        prepared_durations = []
        for index, reference_path in enumerate(effective_speaker_wav, start=1):
            prepared_reference_path = temp_dir / f"ref_{index:02d}.wav"
            prepared_duration = clone_voice.prepare_reference_audio(
                reference_path,
                prepared_reference_path,
                max_ref_seconds=state.xtts_max_ref_seconds,
                trim_db=trim_db,
                normalize_ref=state.xtts_normalize_reference,
            )
            prepared_reference_paths.append(prepared_reference_path)
            prepared_durations.append(prepared_duration)

        cache_path = None
        if not state.xtts_disable_conditioning_cache:
            cache_path = clone_voice.build_conditioning_cache_path(
                (state.speech_root / "outputs" / "conditioning_cache").resolve(),
                model_name=state.xtts_model,
                reference_paths=effective_speaker_wav,
                max_ref_seconds=state.xtts_max_ref_seconds,
                trim_db=trim_db,
                normalize_ref=state.xtts_normalize_reference,
                gpt_cond_len=6,
                gpt_cond_chunk_len=6,
            )

        conditioning_started_at = time.perf_counter()
        gpt_cond_latent, speaker_embedding, cache_hit = clone_voice.get_xtts_conditioning(
            model,
            prepared_reference_paths,
            cache_path,
            max_ref_seconds=state.xtts_max_ref_seconds,
            gpt_cond_len=6,
            gpt_cond_chunk_len=6,
        )
        conditioning_seconds = time.perf_counter() - conditioning_started_at

        synthesis_started_at = time.perf_counter()
        wav, _ = clone_voice.synthesize_with_xtts(
            model,
            text=text,
            language=state.xtts_language,
            gpt_cond_latent=gpt_cond_latent,
            speaker_embedding=speaker_embedding,
            split_sentences=state.xtts_split_sentences,
            temperature=0.65,
            top_k=40,
            top_p=0.8,
            repetition_penalty=8.0,
            speed=1.0,
            stream=False,
            stream_chunk_size=20,
            stream_overlap=1024,
        )
        synthesis_seconds = time.perf_counter() - synthesis_started_at
        clone_voice.sf.write(output_path, wav, clone_voice.XTTS_OUTPUT_SR)

    if state.xtts_report_timings:
        print(f"XTTS ModelLoadSeconds: {runtime['model_load_seconds']:.3f}")
        print(f"XTTS ConditioningSeconds: {conditioning_seconds:.3f}")
        print(f"XTTS SynthesisSeconds: {synthesis_seconds:.3f}")
        print(f"XTTS ConditioningCacheHit: {cache_hit}")
        print(f"XTTS ReferencePreparedSeconds: {', '.join(f'{duration:.2f}' for duration in prepared_durations)}")


def synthesize_audio(
    text: str,
    state: ServerState,
    speaker_wav: list[Path] | None = None,
) -> dict[str, object]:
    effective_speaker_wav = speaker_wav if speaker_wav is not None else state.speaker_wav
    file_id = audio_key(text, state, effective_speaker_wav)
    final_output = state.audio_dir / f"{state.tts_engine}_{file_id}.wav"

    with state.synth_lock:
        if state.disable_cache or not final_output.exists():
            temp_output = state.audio_dir / f"{state.tts_engine}_{file_id}.tmp.wav"
            if temp_output.exists():
                temp_output.unlink()

            if state.tts_engine == "xtts":
                try:
                    synthesize_xtts_in_process(
                        text=text,
                        output_path=temp_output,
                        state=state,
                        speaker_wav=effective_speaker_wav,
                    )
                except Exception:
                    if temp_output.exists():
                        temp_output.unlink()
                    raise
            else:
                command = build_synth_command(
                    text=text,
                    output_path=temp_output,
                    state=state,
                    speaker_wav=effective_speaker_wav,
                )
                completed = subprocess.run(
                    command,
                    cwd=str(state.speech_root),
                    capture_output=True,
                    text=True,
                    check=False,
                )
                if completed.returncode != 0:
                    error_message = "\n".join(
                        part.strip() for part in [completed.stderr, completed.stdout] if part and part.strip()
                    ).strip()
                    if temp_output.exists():
                        temp_output.unlink()
                    raise RuntimeError(error_message or f"{state.tts_label} synthesis failed.")
            temp_output.replace(final_output)

    duration_seconds, energy_timeline = analyze_audio(final_output, state.frame_hz)
    return {
        "audio_url": f"/audio/{final_output.name}",
        "duration_seconds": round(duration_seconds, 4),
        "energy_timeline": energy_timeline,
        "frame_hz": state.frame_hz,
    }


class TalkingHeadRequestHandler(BaseHTTPRequestHandler):
    server_version = "TalkingHeadTTS/1.0"

    @property
    def state(self) -> ServerState:
        return self.server.state  # type: ignore[attr-defined]

    def do_GET(self) -> None:
        parsed = urlparse(self.path)
        if parsed.path in {"", "/"}:
            custom_query = parse_qs(parsed.query, keep_blank_values=False)
            has_request_overrides = any(
                key in custom_query
                for key in ("image_path", "image_url", "speaker_wav_url", "speaker_wav_path", "title", "text")
            )
            html_bytes = build_request_page(self.state, parsed) if has_request_overrides else self.state.html_bytes
            self.send_response(HTTPStatus.OK)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Content-Length", str(len(html_bytes)))
            self.end_headers()
            self.wfile.write(html_bytes)
            return

        if parsed.path.startswith("/audio/"):
            file_name = Path(parsed.path).name
            audio_path = (self.state.audio_dir / file_name).resolve()
            if not audio_path.exists() or audio_path.parent != self.state.audio_dir.resolve():
                self.send_error(HTTPStatus.NOT_FOUND, "Audio file not found.")
                return

            audio_bytes = audio_path.read_bytes()
            self.send_response(HTTPStatus.OK)
            self.send_header("Content-Type", "audio/wav")
            self.send_header("Content-Length", str(len(audio_bytes)))
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            self.wfile.write(audio_bytes)
            return

        self.send_error(HTTPStatus.NOT_FOUND, "Not found.")

    def do_POST(self) -> None:
        parsed = urlparse(self.path)
        if parsed.path == "/api/shutdown":
            encoded = b"Shutting down talking-head server."
            self.send_response(HTTPStatus.OK)
            self.send_header("Content-Type", "text/plain; charset=utf-8")
            self.send_header("Content-Length", str(len(encoded)))
            self.end_headers()
            self.wfile.write(encoded)
            threading.Thread(target=self.server.shutdown, daemon=True).start()
            return

        if parsed.path != "/api/tts":
            self.send_error(HTTPStatus.NOT_FOUND, "Not found.")
            return

        content_length = int(self.headers.get("Content-Length", "0") or "0")
        raw_body = self.rfile.read(content_length)
        try:
            payload = json.loads(raw_body.decode("utf-8"))
        except json.JSONDecodeError:
            self.send_error(HTTPStatus.BAD_REQUEST, "Request body must be valid JSON.")
            return

        text = str(payload.get("text", "")).strip()
        if not text:
            self.send_error(HTTPStatus.BAD_REQUEST, "Provide a non-empty text field.")
            return
        if len(text) > 600:
            self.send_error(HTTPStatus.BAD_REQUEST, "Text is too long for this local demo. Keep it under 600 characters.")
            return

        speaker_wav_override = None
        if self.state.tts_engine == "xtts":
            try:
                resolved_override = resolve_override_speaker_wavs(
                    payload.get("speaker_wav_url"),
                    payload.get("speaker_wav_path"),
                    self.state,
                )
                speaker_wav_override = resolved_override or None
            except Exception as error:  # noqa: BLE001
                print(f"Warning: failed to resolve XTTS speaker override: {error}")

        try:
            result = synthesize_audio(text=text, state=self.state, speaker_wav=speaker_wav_override)
        except Exception as error:  # noqa: BLE001
            message = str(error).strip() or f"{self.state.tts_label} synthesis failed."
            self.send_response(HTTPStatus.INTERNAL_SERVER_ERROR)
            self.send_header("Content-Type", "text/plain; charset=utf-8")
            encoded = message.encode("utf-8")
            self.send_header("Content-Length", str(len(encoded)))
            self.end_headers()
            self.wfile.write(encoded)
            return

        encoded = json.dumps(result, separators=(",", ":")).encode("utf-8")
        self.send_response(HTTPStatus.OK)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(encoded)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(encoded)

    def log_message(self, format: str, *args: object) -> None:
        print(f"[server] {self.address_string()} - {format % args}")


def main() -> None:
    args = parse_args()
    state = build_state(args)
    if state.tts_engine == "xtts" and not args.no_warmup:
        print(f"Warming up {state.tts_label} before serving...")
        ensure_xtts_runtime(state)
    server = ThreadingHTTPServer((args.host, args.port), TalkingHeadRequestHandler)
    server.state = state  # type: ignore[attr-defined]
    url = f"http://{args.host}:{args.port}/"

    print(f"Talking-head HTML written to: {state.html_path}")
    print(f"Serving {state.tts_label} talking head at: {url}")
    print(f"Speech root: {state.speech_root}")
    print(f"TTS Python: {state.tts_python}")
    if state.tts_engine == "xtts":
        print(f"Reference wavs: {', '.join(str(path) for path in state.speaker_wav)}")

    if args.open:
        webbrowser.open(url)

    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nShutting down talking-head server...")
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
