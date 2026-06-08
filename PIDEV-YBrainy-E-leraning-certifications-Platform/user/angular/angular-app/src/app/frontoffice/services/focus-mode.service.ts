import { Injectable } from '@angular/core';
import { BehaviorSubject, Subscription } from 'rxjs';
import { GazeTrackingStatus } from '../models/course.models';
import { CourseApiService } from './course-api.service';
import { UserSessionService } from '../../tracking/user-session.service';

export interface FocusModeState {
  enabled: boolean;
  starting: boolean;
  active: boolean;
  warning: boolean;
  unavailable: string;
  sessionId: string;
  focusScore: number;
  awaySeconds: number;
  durationSeconds: number;
}

const EMPTY_FOCUS_MODE_STATE: FocusModeState = {
  enabled: false,
  starting: false,
  active: false,
  warning: false,
  unavailable: '',
  sessionId: '',
  focusScore: 0,
  awaySeconds: 0,
  durationSeconds: 0,
};

@Injectable({ providedIn: 'root' })
export class FocusModeService {
  private readonly stateSubject = new BehaviorSubject<FocusModeState>(EMPTY_FOCUS_MODE_STATE);
  private readonly sub = new Subscription();
  private readonly warningAfterMs = 15000;
  private pollInterval: ReturnType<typeof setInterval> | null = null;
  private awayStartedAt: number | null = null;
  private lastBeepAt = 0;
  private runToken = 0;

  readonly state$ = this.stateSubject.asObservable();

  constructor(
    private api: CourseApiService,
    private userSession: UserSessionService
  ) {}

  get current(): FocusModeState {
    return this.stateSubject.value;
  }

  get isEnabled(): boolean {
    return this.current.enabled;
  }

  toggle(enabled: boolean): void {
    if (enabled) {
      this.start();
      return;
    }
    this.stop();
  }

  start(): void {
    const studentId = this.userSession.get()?.userId ?? 0;
    if (!studentId || studentId <= 0) {
      this.patchState({ unavailable: 'Sign in to use Focus Mode.', enabled: false, starting: false });
      return;
    }
    if (this.current.enabled || this.current.starting) return;

    const token = ++this.runToken;
    this.clearPolling();
    this.awayStartedAt = null;
    this.lastBeepAt = 0;
    this.patchState({
      ...EMPTY_FOCUS_MODE_STATE,
      enabled: true,
      starting: true,
    });

    this.sub.add(
      this.api.startGazeTracking({
        studentId,
        courseId: 0,
        quizId: 0,
        alertEnabled: false,
        calibrationSec: 2,
      }).subscribe({
        next: (status) => {
          if (token !== this.runToken || !this.current.enabled) {
            this.api.stopGazeTracking(status.user_id, studentId, 0, 0).subscribe({ error: () => undefined });
            return;
          }
          this.handleStatus(status);
          this.patchState({ starting: false, sessionId: status.user_id });
          this.startPolling(studentId);
        },
        error: () => {
          if (token !== this.runToken) return;
          this.clearPolling();
          this.awayStartedAt = null;
          this.patchState({
            ...EMPTY_FOCUS_MODE_STATE,
            unavailable: 'Focus Mode is unavailable.',
          });
        },
      })
    );
  }

  stop(): void {
    this.runToken++;
    const sessionId = this.current.sessionId;
    const studentId = this.userSession.get()?.userId ?? 0;
    this.clearPolling();
    this.awayStartedAt = null;
    this.patchState(EMPTY_FOCUS_MODE_STATE);

    if (sessionId) {
      this.api.stopGazeTracking(sessionId, studentId || undefined, 0, 0).subscribe({ error: () => undefined });
    }
  }

  private startPolling(studentId: number): void {
    this.clearPolling();
    this.pollInterval = setInterval(() => {
      const sessionId = this.current.sessionId;
      if (!sessionId || !this.current.enabled) return;
      this.sub.add(
        this.api.getGazeStatus(sessionId, studentId, 0, 0).subscribe({
          next: (status) => this.handleStatus(status),
          error: () => {
            this.patchState({
              active: false,
              warning: false,
              unavailable: 'Focus Mode status is unavailable.',
            });
          },
        })
      );
    }, 3000);
  }

  private handleStatus(status: GazeTrackingStatus): void {
    const now = Date.now();
    let warning = this.current.warning;
    let awaySeconds = this.current.awaySeconds;

    if (!status.active) {
      this.awayStartedAt = null;
      warning = false;
      awaySeconds = 0;
    } else if (status.looking_at_screen !== false) {
      this.awayStartedAt = null;
      warning = false;
      awaySeconds = 0;
    } else {
      if (!this.awayStartedAt) {
        this.awayStartedAt = now;
      }
      awaySeconds = Math.max(0, Math.floor((now - this.awayStartedAt) / 1000));
      if (now - this.awayStartedAt >= this.warningAfterMs) {
        warning = true;
        this.triggerBeep();
      }
    }

    this.patchState({
      enabled: true,
      starting: false,
      active: status.active,
      warning,
      unavailable: '',
      sessionId: status.user_id,
      focusScore: Math.round(Number(status.current_focus_score ?? 0)),
      awaySeconds,
      durationSeconds: Math.round(Number(status.duration_seconds ?? 0)),
    });
  }

  private triggerBeep(): void {
    const now = Date.now();
    if (now - this.lastBeepAt < 5000) return;
    this.lastBeepAt = now;
    const maybeWindow = window as unknown as { webkitAudioContext?: typeof AudioContext };
    const AudioContextCtor = window.AudioContext || maybeWindow.webkitAudioContext;
    if (!AudioContextCtor) return;

    const audioContext = new AudioContextCtor();
    const oscillator = audioContext.createOscillator();
    const gain = audioContext.createGain();
    oscillator.type = 'sine';
    oscillator.frequency.value = 880;
    gain.gain.setValueAtTime(0.001, audioContext.currentTime);
    gain.gain.exponentialRampToValueAtTime(0.16, audioContext.currentTime + 0.02);
    gain.gain.exponentialRampToValueAtTime(0.001, audioContext.currentTime + 0.28);
    oscillator.connect(gain);
    gain.connect(audioContext.destination);
    oscillator.start();
    oscillator.stop(audioContext.currentTime + 0.3);
    setTimeout(() => void audioContext.close(), 360);
  }

  private clearPolling(): void {
    if (this.pollInterval) {
      clearInterval(this.pollInterval);
      this.pollInterval = null;
    }
  }

  private patchState(patch: Partial<FocusModeState>): void {
    this.stateSubject.next({ ...this.stateSubject.value, ...patch });
  }
}
