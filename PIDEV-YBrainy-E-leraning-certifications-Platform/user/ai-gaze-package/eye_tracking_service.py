"""
Eye Tracking HTTP Service
Wraps live_eye_screen_tracker.py with a FastAPI HTTP interface for Java integration.
"""
from __future__ import annotations

import argparse
import json
import threading
import time
import traceback
from collections import deque
from dataclasses import dataclass, asdict
from datetime import datetime
from pathlib import Path
from typing import Dict, List, Optional

import cv2
import mediapipe as mp
import numpy as np
import uvicorn
from fastapi import FastAPI, HTTPException, BackgroundTasks
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel

# Import from the existing tracker
from live_eye_screen_tracker import (
    AwayAlert,
    GazeDecision,
    CalibrationModel,
    ensure_landmarker_model,
    extract_gaze_features,
)


app = FastAPI(title="Eye Tracking Service", version="1.0.0")
TRACKER_SOURCE = "live_eye_screen_tracker.py"

# CORS for Angular/Java frontend
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


# In-memory session storage (userId -> session data)
active_sessions: Dict[str, "TrackingSession"] = {}
sessions_lock = threading.Lock()


def build_face_landmarker(model_path: Path = Path("models/face_landmarker_v2.task")):
    """Build the MediaPipe face landmarker used by the live tracker module."""
    resolved_model = ensure_landmarker_model(model_path)
    base_options = mp.tasks.BaseOptions(model_asset_path=str(resolved_model))
    vision = mp.tasks.vision
    options = vision.FaceLandmarkerOptions(
        base_options=base_options,
        running_mode=vision.RunningMode.VIDEO,
        num_faces=1,
        min_face_detection_confidence=0.5,
        min_face_presence_confidence=0.5,
        min_tracking_confidence=0.5,
    )
    return vision.FaceLandmarker.create_from_options(options)


class ScreenGazeTracker:
    """Small HTTP-friendly wrapper around live_eye_screen_tracker.py primitives."""

    def __init__(
        self,
        face_landmarker,
        away_alert: AwayAlert,
        calibration_sec: float = 2.0,
        camera_index: int = 0,
        frame_width: int = 1280,
        frame_height: int = 720,
        smoothing_window: int = 6,
    ) -> None:
        self.face_landmarker = face_landmarker
        self.away_alert = away_alert
        self.camera = cv2.VideoCapture(camera_index)
        self.camera.set(cv2.CAP_PROP_FRAME_WIDTH, frame_width)
        self.camera.set(cv2.CAP_PROP_FRAME_HEIGHT, frame_height)
        if not self.camera.isOpened():
            raise RuntimeError(f"Could not open webcam index {camera_index}")

        target_frames = max(8, int(max(calibration_sec, 0.5) * 15))
        self.calibration = CalibrationModel(
            target_frames=target_frames,
            min_horizontal_threshold=0.10,
            min_vertical_threshold=0.12,
            min_face_threshold=0.07,
        )
        self.focus_history: deque[float] = deque(maxlen=max(1, smoothing_window))

    def process_frame(self):
        ok, frame = self.camera.read()
        if not ok:
            time.sleep(0.05)
            return None

        rgb_frame = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
        mp_image = mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb_frame)
        timestamp_ms = int(time.monotonic() * 1000)
        results = self.face_landmarker.detect_for_video(mp_image, timestamp_ms)

        if not results.face_landmarks:
            decision = GazeDecision(
                looking_at_screen=False,
                focus_score=0.0,
                horizontal_deviation=1.0,
                vertical_deviation=1.0,
                face_deviation=1.0,
                dominant_ratio=2.0,
            )
            self.away_alert.trigger()
            return None, decision, True

        features = extract_gaze_features(results.face_landmarks[0], frame.shape)
        if not self.calibration.ready:
            self.calibration.observe(features)
            decision = GazeDecision(
                looking_at_screen=True,
                focus_score=0.0,
                horizontal_deviation=0.0,
                vertical_deviation=0.0,
                face_deviation=0.0,
                dominant_ratio=0.0,
            )
            return features, decision, False

        decision = self.calibration.decide(features)
        self.focus_history.append(decision.focus_score)
        smoothed_focus = float(np.mean(self.focus_history)) if self.focus_history else decision.focus_score
        smoothed_decision = GazeDecision(
            looking_at_screen=decision.looking_at_screen and smoothed_focus >= 0.45,
            focus_score=smoothed_focus * 100.0,
            horizontal_deviation=decision.horizontal_deviation,
            vertical_deviation=decision.vertical_deviation,
            face_deviation=decision.face_deviation,
            dominant_ratio=decision.dominant_ratio,
        )

        alert_triggered = False
        if smoothed_decision.looking_at_screen:
            self.calibration.update_when_stable(features)
        else:
            self.away_alert.trigger()
            alert_triggered = True
        return features, smoothed_decision, alert_triggered

    def close(self) -> None:
        try:
            self.face_landmarker.close()
        except Exception:
            pass
        try:
            self.camera.release()
        except Exception:
            pass


@dataclass
class TrackingSession:
    user_id: str
    tracker: ScreenGazeTracker
    thread: threading.Thread
    start_time: datetime
    stop_event: threading.Event
    measurements: deque
    focus_scores: deque
    last_focus_score: float
    total_away_time: float
    away_start_time: Optional[float]


class StartTrackingRequest(BaseModel):
    user_id: str
    alert_enabled: bool = True
    alert_frequency: int = 1
    alert_duration_ms: int = 200
    alert_cooldown_sec: float = 3.0
    calibration_sec: float = 2.0


class TrackingStatus(BaseModel):
    user_id: str
    active: bool
    duration_seconds: float
    current_focus_score: float
    average_focus_score: float
    looking_at_screen: bool
    total_away_time_seconds: float
    measurements_count: int


class TrackingResult(BaseModel):
    user_id: str
    duration_seconds: float
    average_focus_score: float
    min_focus_score: float
    max_focus_score: float
    total_away_time_seconds: float
    away_incidents_count: int
    final_assessment: str


def run_tracker_session(session: TrackingSession):
    """Background thread that runs the eye tracker."""
    try:
        tracker = session.tracker
        stop_event = session.stop_event
        
        while not stop_event.is_set():
            try:
                result = tracker.process_frame()
                if result is not None:
                    features, decision, away_alert_triggered = result
                    
                    # Store measurement
                    session.measurements.append({
                        "timestamp": time.time(),
                        "focus_score": decision.focus_score,
                        "looking_at_screen": decision.looking_at_screen,
                        "horizontal_deviation": decision.horizontal_deviation,
                        "vertical_deviation": decision.vertical_deviation,
                    })
                    
                    # Track focus scores
                    session.focus_scores.append(decision.focus_score)
                    session.last_focus_score = decision.focus_score
                    
                    # Track away time
                    if not decision.looking_at_screen:
                        if session.away_start_time is None:
                            session.away_start_time = time.time()
                    else:
                        if session.away_start_time is not None:
                            session.total_away_time += time.time() - session.away_start_time
                            session.away_start_time = None
                            
            except Exception as e:
                print(f"Tracker error: {e}")
                time.sleep(0.1)
                
    except Exception as e:
        print(f"Session thread error: {e}")
        traceback.print_exc()
    finally:
        try:
            session.tracker.close()
        except Exception:
            pass


@app.post("/start", response_model=TrackingStatus)
def start_tracking(request: StartTrackingRequest, background_tasks: BackgroundTasks):
    """Start eye tracking for a user."""
    with sessions_lock:
        # Stop existing session if any
        if request.user_id in active_sessions:
            old_session = active_sessions[request.user_id]
            old_session.stop_event.set()
            old_session.thread.join(timeout=2.0)
            del active_sessions[request.user_id]
        
        # Create alert config
        away_alert = AwayAlert(
            enabled=request.alert_enabled,
            frequency=request.alert_frequency,
            duration_ms=request.alert_duration_ms,
            cooldown_sec=request.alert_cooldown_sec,
        )
        
        # Create tracker
        try:
            tracker = ScreenGazeTracker(
                face_landmarker=build_face_landmarker(),
                away_alert=away_alert,
                calibration_sec=request.calibration_sec,
            )
        except Exception as e:
            raise HTTPException(status_code=500, detail=f"Failed to initialize tracker: {e}")
        
        # Create session
        stop_event = threading.Event()
        session = TrackingSession(
            user_id=request.user_id,
            tracker=tracker,
            thread=None,  # Will set after creation
            start_time=datetime.now(),
            stop_event=stop_event,
            measurements=deque(maxlen=10000),
            focus_scores=deque(maxlen=1000),
            last_focus_score=0.0,
            total_away_time=0.0,
            away_start_time=None,
        )
        
        # Start tracker thread
        thread = threading.Thread(target=run_tracker_session, args=(session,), daemon=True)
        session.thread = thread
        thread.start()
        
        active_sessions[request.user_id] = session
        
        return TrackingStatus(
            user_id=request.user_id,
            active=True,
            duration_seconds=0.0,
            current_focus_score=0.0,
            average_focus_score=0.0,
            looking_at_screen=False,
            total_away_time_seconds=0.0,
            measurements_count=0,
        )


@app.get("/status/{user_id}", response_model=TrackingStatus)
def get_status(user_id: str):
    """Get current tracking status for a user."""
    with sessions_lock:
        if user_id not in active_sessions:
            raise HTTPException(status_code=404, detail="No active tracking session found")
        
        session = active_sessions[user_id]
        duration = (datetime.now() - session.start_time).total_seconds()
        
        avg_focus = sum(session.focus_scores) / len(session.focus_scores) if session.focus_scores else 0.0
        
        # Calculate ongoing away time
        total_away = session.total_away_time
        if session.away_start_time is not None:
            total_away += time.time() - session.away_start_time
        
        return TrackingStatus(
            user_id=user_id,
            active=session.thread.is_alive(),
            duration_seconds=duration,
            current_focus_score=session.last_focus_score,
            average_focus_score=avg_focus,
            looking_at_screen=session.away_start_time is None,
            total_away_time_seconds=total_away,
            measurements_count=len(session.measurements),
        )


@app.post("/stop/{user_id}", response_model=TrackingResult)
def stop_tracking(user_id: str):
    """Stop tracking and return final results."""
    with sessions_lock:
        if user_id not in active_sessions:
            raise HTTPException(status_code=404, detail="No active tracking session found")
        
        session = active_sessions[user_id]
        
        # Stop the tracker
        session.stop_event.set()
        session.thread.join(timeout=5.0)
        
        # Calculate final metrics
        duration = (datetime.now() - session.start_time).total_seconds()
        
        focus_list = list(session.focus_scores)
        avg_focus = sum(focus_list) / len(focus_list) if focus_list else 0.0
        min_focus = min(focus_list) if focus_list else 0.0
        max_focus = max(focus_list) if focus_list else 0.0
        
        # Final away time calculation
        total_away = session.total_away_time
        if session.away_start_time is not None:
            total_away += time.time() - session.away_start_time
        
        # Count away incidents
        away_incidents = 0
        was_away = False
        for m in session.measurements:
            looking = m.get("looking_at_screen", True)
            if not looking and not was_away:
                away_incidents += 1
                was_away = True
            elif looking:
                was_away = False
        
        # Assessment
        if avg_focus >= 80:
            assessment = "HIGHLY_ATTENTIVE"
        elif avg_focus >= 60:
            assessment = "ATTENTIVE"
        elif avg_focus >= 40:
            assessment = "MODERATE"
        elif avg_focus >= 20:
            assessment = "DISTRACTED"
        else:
            assessment = "HIGHLY_DISTRACTED"
        
        result = TrackingResult(
            user_id=user_id,
            duration_seconds=duration,
            average_focus_score=avg_focus,
            min_focus_score=min_focus,
            max_focus_score=max_focus,
            total_away_time_seconds=total_away,
            away_incidents_count=away_incidents,
            final_assessment=assessment,
        )
        
        # Cleanup
        del active_sessions[user_id]
        
        return result


@app.get("/health")
def health_check():
    """Health check endpoint."""
    return {
        "status": "healthy",
        "active_sessions": len(active_sessions),
        "tracker_source": TRACKER_SOURCE,
    }


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Eye Tracking HTTP Service")
    parser.add_argument("--port", type=int, default=5001, help="HTTP port")
    parser.add_argument("--host", type=str, default="0.0.0.0", help="HTTP host")
    args = parser.parse_args()
    
    print(f"Starting Eye Tracking Service on {args.host}:{args.port}")
    uvicorn.run(app, host=args.host, port=args.port)
