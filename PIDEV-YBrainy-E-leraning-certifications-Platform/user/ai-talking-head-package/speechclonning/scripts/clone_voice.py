#!/usr/bin/env python
"""Clone a voice from reference audio using a Coqui cloning-capable model."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import tempfile
import time
import wave
from pathlib import Path

import librosa
import numpy as np
import soundfile as sf
import torch


DEFAULT_MODEL = "tts_models/multilingual/multi-dataset/xtts_v2"
XTTS_OUTPUT_SR = 24000
REFERENCE_LOAD_SR = 22050


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--text", required=True, help="Text to synthesize in the cloned voice.")
    parser.add_argument(
        "--speaker-wav",
        type=Path,
        nargs="+",
        required=True,
        help="One or more reference audio files containing the target voice.",
    )
    parser.add_argument(
        "--out",
        type=Path,
        default=Path("outputs/voice_clone.wav"),
        help="Output wav path.",
    )
    parser.add_argument(
        "--language",
        default="en",
        help="Language code for the generated speech. Default: en",
    )
    parser.add_argument(
        "--model",
        default=DEFAULT_MODEL,
        help=f"Coqui model name. Default: {DEFAULT_MODEL}",
    )
    parser.add_argument(
        "--device",
        choices=["auto", "cuda", "cpu"],
        default="auto",
        help="Inference device. Default: auto",
    )
    parser.add_argument(
        "--split-sentences",
        action="store_true",
        help="Split long text into sentences before synthesis.",
    )
    parser.add_argument(
        "--agree-to-coqui-cpml",
        action="store_true",
        help="Confirm that you accept Coqui's CPML terms for models that require it.",
    )
    parser.add_argument(
        "--max-ref-seconds",
        type=float,
        default=8.0,
        help="Maximum prepared duration for each reference clip. Default: 8.0",
    )
    parser.add_argument(
        "--trim-db",
        type=float,
        default=30.0,
        help="Silence trimming threshold for reference audio. Use a negative value to disable. Default: 30",
    )
    parser.add_argument(
        "--normalize-ref",
        action="store_true",
        help="Peak-normalize prepared reference audio.",
    )
    parser.add_argument(
        "--gpt-cond-len",
        type=int,
        default=6,
        help="XTTS GPT conditioning length in seconds. Default: 6",
    )
    parser.add_argument(
        "--gpt-cond-chunk-len",
        type=int,
        default=6,
        help="XTTS GPT conditioning chunk length in seconds. Default: 6",
    )
    parser.add_argument(
        "--temperature",
        type=float,
        default=0.65,
        help="XTTS sampling temperature. Lower values are more stable. Default: 0.65",
    )
    parser.add_argument(
        "--top-k",
        type=int,
        default=40,
        help="XTTS top-k sampling. Lower values are more conservative. Default: 40",
    )
    parser.add_argument(
        "--top-p",
        type=float,
        default=0.8,
        help="XTTS top-p sampling. Lower values are more conservative. Default: 0.8",
    )
    parser.add_argument(
        "--repetition-penalty",
        type=float,
        default=8.0,
        help="Penalty against repetitive output. Default: 8.0",
    )
    parser.add_argument(
        "--speed",
        type=float,
        default=1.0,
        help="Speech speed multiplier. Default: 1.0",
    )
    parser.add_argument(
        "--stream",
        action="store_true",
        help="Use XTTS streaming decode and assemble the output from chunks.",
    )
    parser.add_argument(
        "--stream-chunk-size",
        type=int,
        default=20,
        help="XTTS streaming chunk size. Default: 20",
    )
    parser.add_argument(
        "--stream-overlap",
        type=int,
        default=1024,
        help="XTTS overlap length for streaming decode. Default: 1024",
    )
    parser.add_argument(
        "--report-timings",
        action="store_true",
        help="Print load, conditioning, and synthesis timing information.",
    )
    parser.add_argument(
        "--conditioning-cache-dir",
        type=Path,
        default=Path("outputs/conditioning_cache"),
        help="Directory used to persist XTTS conditioning latents across runs.",
    )
    parser.add_argument(
        "--cache-conditioning",
        dest="cache_conditioning",
        action="store_true",
        default=True,
        help="Cache XTTS conditioning latents for repeated use. Default: enabled",
    )
    parser.add_argument(
        "--no-cache-conditioning",
        dest="cache_conditioning",
        action="store_false",
        help="Disable XTTS conditioning cache.",
    )
    parser.add_argument(
        "--disable-xtts-optimizations",
        action="store_true",
        help="Force the generic Coqui API path instead of XTTS-specific conditioning and streaming.",
    )
    args = parser.parse_args()

    if args.max_ref_seconds <= 0:
        raise ValueError("--max-ref-seconds must be positive.")
    if args.gpt_cond_len <= 0 or args.gpt_cond_chunk_len <= 0:
        raise ValueError("--gpt-cond-len and --gpt-cond-chunk-len must be positive.")
    if args.gpt_cond_chunk_len > args.gpt_cond_len:
        raise ValueError("--gpt-cond-chunk-len must be less than or equal to --gpt-cond-len.")
    if args.speed <= 0:
        raise ValueError("--speed must be positive.")
    if args.stream_chunk_size <= 0:
        raise ValueError("--stream-chunk-size must be positive.")
    if args.stream_overlap < 0:
        raise ValueError("--stream-overlap must be zero or greater.")

    return args


def resolve_device(device_arg: str) -> str:
    if device_arg == "auto":
        return "cuda" if torch.cuda.is_available() else "cpu"
    if device_arg == "cuda" and not torch.cuda.is_available():
        raise RuntimeError("CUDA was requested, but no CUDA device is available.")
    return device_arg


def prepare_reference_audio(
    reference_path: Path,
    output_path: Path,
    *,
    max_ref_seconds: float,
    trim_db: float | None,
    normalize_ref: bool,
) -> float:
    audio, _ = librosa.load(reference_path, sr=REFERENCE_LOAD_SR, mono=True)
    if audio.size == 0:
        raise ValueError(f"Reference audio is empty: {reference_path}")

    if trim_db is not None:
        intervals = librosa.effects.split(audio, top_db=trim_db)
        if len(intervals) > 0:
            audio = np.concatenate([audio[start:end] for start, end in intervals])

    max_samples = int(max_ref_seconds * REFERENCE_LOAD_SR)
    audio = audio[:max_samples]
    if audio.size == 0:
        raise ValueError(f"Reference audio became empty after trimming: {reference_path}")

    if normalize_ref:
        peak = float(np.max(np.abs(audio)))
        if peak > 0:
            audio = 0.8 * (audio / peak)

    sf.write(output_path, audio.astype(np.float32), REFERENCE_LOAD_SR)
    return float(audio.shape[0]) / float(REFERENCE_LOAD_SR)


def build_conditioning_cache_path(
    cache_dir: Path,
    *,
    model_name: str,
    reference_paths: list[Path],
    max_ref_seconds: float,
    trim_db: float | None,
    normalize_ref: bool,
    gpt_cond_len: int,
    gpt_cond_chunk_len: int,
) -> Path:
    payload = {
        "model_name": model_name,
        "reference_paths": [
            {
                "path": str(path),
                "size": path.stat().st_size,
                "mtime_ns": path.stat().st_mtime_ns,
            }
            for path in reference_paths
        ],
        "max_ref_seconds": max_ref_seconds,
        "trim_db": trim_db,
        "normalize_ref": normalize_ref,
        "gpt_cond_len": gpt_cond_len,
        "gpt_cond_chunk_len": gpt_cond_chunk_len,
    }
    digest = hashlib.sha256(json.dumps(payload, sort_keys=True).encode("utf-8")).hexdigest()
    return cache_dir / f"{digest[:24]}.pt"


def get_xtts_conditioning(
    model,
    prepared_reference_paths: list[Path],
    cache_path: Path | None,
    *,
    max_ref_seconds: float,
    gpt_cond_len: int,
    gpt_cond_chunk_len: int,
) -> tuple[torch.Tensor, torch.Tensor, bool]:
    if cache_path is not None and cache_path.exists():
        try:
            cached = torch.load(cache_path, map_location="cpu", weights_only=True)
        except TypeError:
            cached = torch.load(cache_path, map_location="cpu")
        return cached["gpt_cond_latent"], cached["speaker_embedding"], True

    gpt_cond_latent, speaker_embedding = model.get_conditioning_latents(
        audio_path=[str(path) for path in prepared_reference_paths],
        max_ref_length=max(1, int(np.ceil(max_ref_seconds))),
        gpt_cond_len=gpt_cond_len,
        gpt_cond_chunk_len=gpt_cond_chunk_len,
        librosa_trim_db=None,
        sound_norm_refs=False,
        load_sr=REFERENCE_LOAD_SR,
    )
    gpt_cond_latent = gpt_cond_latent.detach().cpu()
    speaker_embedding = speaker_embedding.detach().cpu()

    if cache_path is not None:
        cache_path.parent.mkdir(parents=True, exist_ok=True)
        torch.save(
            {
                "gpt_cond_latent": gpt_cond_latent,
                "speaker_embedding": speaker_embedding,
            },
            cache_path,
        )

    return gpt_cond_latent, speaker_embedding, False


def audio_duration_seconds(path: Path) -> float:
    with wave.open(str(path), "rb") as wav_file:
        return float(wav_file.getnframes()) / float(wav_file.getframerate())


def synthesize_with_xtts(
    model,
    *,
    text: str,
    language: str,
    gpt_cond_latent: torch.Tensor,
    speaker_embedding: torch.Tensor,
    split_sentences: bool,
    temperature: float,
    top_k: int,
    top_p: float,
    repetition_penalty: float,
    speed: float,
    stream: bool,
    stream_chunk_size: int,
    stream_overlap: int,
) -> tuple[np.ndarray, float | None]:
    inference_kwargs = {
        "text": text,
        "language": language,
        "gpt_cond_latent": gpt_cond_latent,
        "speaker_embedding": speaker_embedding,
        "temperature": temperature,
        "top_k": top_k,
        "top_p": top_p,
        "repetition_penalty": repetition_penalty,
        "speed": speed,
        "enable_text_splitting": split_sentences,
    }

    if not stream:
        result = model.inference(**inference_kwargs)
        return np.asarray(result["wav"], dtype=np.float32), None

    chunks = []
    first_chunk_seconds = None
    stream_started_at = time.perf_counter()
    for chunk in model.inference_stream(
        **inference_kwargs,
        stream_chunk_size=stream_chunk_size,
        overlap_wav_len=stream_overlap,
    ):
        if first_chunk_seconds is None:
            first_chunk_seconds = time.perf_counter() - stream_started_at
        chunks.append(chunk.detach().cpu().numpy())

    if not chunks:
        raise RuntimeError("XTTS streaming inference produced no audio chunks.")

    return np.concatenate(chunks).astype(np.float32), first_chunk_seconds


def main() -> None:
    args = parse_args()
    reference_paths = [path.resolve() for path in args.speaker_wav]
    missing_paths = [str(path) for path in reference_paths if not path.exists()]
    if missing_paths:
        raise FileNotFoundError(f"Reference audio not found: {', '.join(missing_paths)}")

    output_path = args.out.resolve()
    output_path.parent.mkdir(parents=True, exist_ok=True)

    device = resolve_device(args.device)
    if args.agree_to_coqui_cpml:
        os.environ["COQUI_TOS_AGREED"] = "1"

    trim_db = None if args.trim_db < 0 else args.trim_db

    from TTS.api import TTS

    load_started_at = time.perf_counter()
    tts = TTS(model_name=args.model, progress_bar=False)
    if device == "cuda":
        tts = tts.to("cuda")
    model_load_seconds = time.perf_counter() - load_started_at

    model = tts.synthesizer.tts_model
    supports_xtts_optimizations = (
        hasattr(model, "get_conditioning_latents")
        and hasattr(model, "inference")
        and hasattr(model, "inference_stream")
    )
    use_xtts_optimizations = supports_xtts_optimizations and not args.disable_xtts_optimizations

    prepared_durations = []
    cache_hit = False
    conditioning_seconds = None
    synthesis_seconds = None
    first_chunk_seconds = None

    with tempfile.TemporaryDirectory(prefix="xtts_refprep_") as temp_dir_str:
        temp_dir = Path(temp_dir_str)
        prepared_reference_paths = []
        for index, reference_path in enumerate(reference_paths, start=1):
            prepared_reference_path = temp_dir / f"ref_{index:02d}.wav"
            prepared_duration = prepare_reference_audio(
                reference_path,
                prepared_reference_path,
                max_ref_seconds=args.max_ref_seconds,
                trim_db=trim_db,
                normalize_ref=args.normalize_ref,
            )
            prepared_reference_paths.append(prepared_reference_path)
            prepared_durations.append(prepared_duration)

        if use_xtts_optimizations:
            cache_path = None
            if args.cache_conditioning:
                cache_path = build_conditioning_cache_path(
                    args.conditioning_cache_dir.resolve(),
                    model_name=args.model,
                    reference_paths=reference_paths,
                    max_ref_seconds=args.max_ref_seconds,
                    trim_db=trim_db,
                    normalize_ref=args.normalize_ref,
                    gpt_cond_len=args.gpt_cond_len,
                    gpt_cond_chunk_len=args.gpt_cond_chunk_len,
                )

            conditioning_started_at = time.perf_counter()
            gpt_cond_latent, speaker_embedding, cache_hit = get_xtts_conditioning(
                model,
                prepared_reference_paths,
                cache_path,
                max_ref_seconds=args.max_ref_seconds,
                gpt_cond_len=args.gpt_cond_len,
                gpt_cond_chunk_len=args.gpt_cond_chunk_len,
            )
            conditioning_seconds = time.perf_counter() - conditioning_started_at

            synthesis_started_at = time.perf_counter()
            wav, first_chunk_seconds = synthesize_with_xtts(
                model,
                text=args.text,
                language=args.language,
                gpt_cond_latent=gpt_cond_latent,
                speaker_embedding=speaker_embedding,
                split_sentences=args.split_sentences,
                temperature=args.temperature,
                top_k=args.top_k,
                top_p=args.top_p,
                repetition_penalty=args.repetition_penalty,
                speed=args.speed,
                stream=args.stream,
                stream_chunk_size=args.stream_chunk_size,
                stream_overlap=args.stream_overlap,
            )
            synthesis_seconds = time.perf_counter() - synthesis_started_at
            sf.write(output_path, wav, XTTS_OUTPUT_SR)
        else:
            speaker_wav_arg = [str(path) for path in prepared_reference_paths]
            if len(speaker_wav_arg) == 1:
                speaker_wav_arg = speaker_wav_arg[0]

            synthesis_started_at = time.perf_counter()
            tts.tts_to_file(
                text=args.text,
                speaker_wav=speaker_wav_arg,
                language=args.language,
                file_path=str(output_path),
                split_sentences=args.split_sentences,
            )
            synthesis_seconds = time.perf_counter() - synthesis_started_at

    output_duration = audio_duration_seconds(output_path)

    print(f"Model: {args.model}")
    print(f"Device: {device}")
    print(f"ReferenceCount: {len(reference_paths)}")
    print(f"ReferencePreparedSeconds: {', '.join(f'{duration:.2f}' for duration in prepared_durations)}")
    print(f"OptimizedPath: {use_xtts_optimizations}")
    if use_xtts_optimizations:
        print(f"ConditioningCacheHit: {cache_hit}")
    print(f"Output: {output_path}")
    print(f"OutputSeconds: {output_duration:.3f}")

    if args.report_timings:
        print(f"ModelLoadSeconds: {model_load_seconds:.3f}")
        if conditioning_seconds is not None:
            print(f"ConditioningSeconds: {conditioning_seconds:.3f}")
        if synthesis_seconds is not None:
            print(f"SynthesisSeconds: {synthesis_seconds:.3f}")
            if output_duration > 0:
                print(f"RealTimeFactor: {synthesis_seconds / output_duration:.3f}")
        if first_chunk_seconds is not None:
            print(f"FirstChunkSeconds: {first_chunk_seconds:.3f}")


if __name__ == "__main__":
    main()
