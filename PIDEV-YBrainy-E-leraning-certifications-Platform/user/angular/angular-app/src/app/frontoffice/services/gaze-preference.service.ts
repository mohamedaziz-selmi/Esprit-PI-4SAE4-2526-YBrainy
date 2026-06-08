import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class GazePreferenceService {
  private readonly storageKey = 'yb.quiz.gaze.enabled';
  private readonly enabledSubject = new BehaviorSubject<boolean>(this.readInitialValue());

  readonly enabled$ = this.enabledSubject.asObservable();

  get current(): boolean {
    return this.enabledSubject.value;
  }

  setEnabled(enabled: boolean): void {
    localStorage.setItem(this.storageKey, String(enabled));
    if (this.enabledSubject.value !== enabled) {
      this.enabledSubject.next(enabled);
    }
  }

  private readInitialValue(): boolean {
    return localStorage.getItem(this.storageKey) !== 'false';
  }
}
