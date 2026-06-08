import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { CourseApiService } from '../../services/course-api.service';
import { ApiLeaderboardEntry, ApiQuiz, ApiQuestion, ApiQuizResult, GazeTrackingStatus } from '../../models/course.models';
import { FocusModeService } from '../../services/focus-mode.service';
import { GazePreferenceService } from '../../services/gaze-preference.service';
import { UserSessionService } from '../../../tracking/user-session.service';

@Component({
  selector: 'app-quiz-page',
  standalone: false,
  templateUrl: './quiz-page.component.html',
  styleUrls: ['./quiz-page.component.css'],
})
export class QuizPageComponent implements OnInit, OnDestroy {
  courseId = 0;

  get studentId(): number { return this.userSession.get()?.userId ?? 0; }
  get isStudent(): boolean { return (this.userSession.get()?.role ?? 'STUDENT') === 'STUDENT'; }

  isEnrolled = false;
  enrollmentChecked = false;

  quizzes: ApiQuiz[] = [];
  selectedQuiz: ApiQuiz | null = null;
  questions: ApiQuestion[] = [];
  questionsLoading = false;
  responses = new Map<number, number | null>();
  quizResult: ApiQuizResult | null = null;
  loading = false;
  error = '';
  currentQuestionIndex = 0;

  timeLeft: number = 0;
  timerInterval: any = null;
  timerExpired: boolean = false;

  showReview: boolean = false;
  showAnswerReview: boolean = false;
  submitting: boolean = false;

  leaderboard: ApiLeaderboardEntry[] = [];
  showLeaderboard: boolean = false;
  leaderboardLoading: boolean = false;

  arenaMode: boolean = false;
  championHovered: boolean = false;
  validationError: string | null = null;

  gazeEnabled = true;
  gazeActive = false;
  gazeWarning = false;
  gazeUnavailable = '';
  gazeStatus: GazeTrackingStatus | null = null;
  gazeSessionId = '';
  private gazePollInterval: ReturnType<typeof setInterval> | null = null;
  private gazeAwayStartedAt: number | null = null;
  private lastGazeBeepAt = 0;
  private readonly gazeWarningAfterMs = 15000;
  private readonly sub = new Subscription();

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private api: CourseApiService,
    private focusMode: FocusModeService,
    private gazePreference: GazePreferenceService,
    private userSession: UserSessionService
  ) {}

  ngOnInit(): void {
    this.gazeEnabled = this.gazePreference.current;
    this.sub.add(
      this.gazePreference.enabled$.subscribe((enabled) => {
        const wasEnabled = this.gazeEnabled;
        this.gazeEnabled = enabled;
        if (enabled && !wasEnabled) {
          this.startGazeTracking();
        } else if (!enabled && wasEnabled) {
          this.stopGazeTracking();
        }
      })
    );

    this.courseId = +(this.route.snapshot.paramMap.get('courseId') ?? '0');
    if (!this.isStudent) {
      this.isEnrolled = true;
      this.enrollmentChecked = true;
      this.loadQuizzes();
      return;
    }
    try {
      const studentId = this.requireAuth();
      this.api.getStudentEnrollments(studentId).subscribe({
        next: (enrollments) => {
          this.isEnrolled = enrollments.some(e => e.courseId === this.courseId);
          this.enrollmentChecked = true;
          if (this.isEnrolled) this.loadQuizzes();
        },
        error: () => { this.isEnrolled = false; this.enrollmentChecked = true; },
      });
    } catch {
      this.isEnrolled = false;
      this.enrollmentChecked = true;
    }
  }

  loadQuizzes(): void {
    this.loading = true;
    this.error = '';
    this.api.getQuizzesByCourse(this.courseId).subscribe({
      next: (data) => { this.quizzes = data; this.loading = false; },
      error: (err) => {
        console.error('Could not load quizzes', err);
        this.error = 'Could not load quizzes.';
        this.loading = false;
      },
    });
  }

  selectQuiz(quiz: ApiQuiz): void {
    this.stopGazeTracking();
    this.selectedQuiz = quiz;
    this.quizResult = null;
    this.responses.clear();
    this.questions = [];
    this.currentQuestionIndex = 0;
    this.showReview = false;
    this.error = '';
    this.questionsLoading = true;
    this.api.getQuizQuestions(this.courseId, quiz.id).subscribe({
      next: (qs) => {
        this.questions = qs;
        this.questionsLoading = false;
        this.stopTimer();
        if (quiz.timeLimitMinutes && quiz.timeLimitMinutes > 0) {
          this.startTimer(quiz.timeLimitMinutes);
        }
        this.startGazeTracking();
      },
      error: () => {
        this.questionsLoading = false;
        this.error = 'Questions unavailable.';
      },
    });
  }

  selectAnswer(questionId: number, answerId: number): void {
    this.responses.set(questionId, answerId);
  }

  isSelected(questionId: number, answerId: number): boolean {
    return this.responses.get(questionId) === answerId;
  }

  get currentQuestion(): ApiQuestion | null {
    return this.questions[this.currentQuestionIndex] ?? null;
  }

  isLastQuestion(): boolean {
    return this.questions.length > 0 && this.currentQuestionIndex === this.questions.length - 1;
  }

  canProceed(): boolean {
    if (!this.currentQuestion) return false;
    return this.responses.has(this.currentQuestion.id);
  }

  nextQuestion(): void {
    if (this.currentQuestionIndex < this.questions.length - 1) {
      this.currentQuestionIndex++;
    }
  }

  prevQuestion(): void {
    if (this.currentQuestionIndex > 0) {
      this.currentQuestionIndex--;
    }
  }

  toggleAnswerReview(): void {
    this.showAnswerReview = !this.showAnswerReview;
  }

  tryAgain(): void {
    if (!this.selectedQuiz) return;
    this.stopGazeTracking();
    this.arenaMode = false;
    this.quizResult = null;
    this.responses.clear();
    this.currentQuestionIndex = 0;
    this.showReview = false;
    this.showAnswerReview = false;
    this.showLeaderboard = false;
    this.questionsLoading = true;
    this.api.getQuizQuestions(this.courseId, this.selectedQuiz.id).subscribe({
      next: (qs) => {
        this.questions = qs;
        this.questionsLoading = false;
        this.stopTimer();
        if (this.selectedQuiz?.timeLimitMinutes && this.selectedQuiz.timeLimitMinutes > 0) {
          this.startTimer(this.selectedQuiz.timeLimitMinutes);
        }
        this.startGazeTracking();
      },
      error: () => {
        this.questionsLoading = false;
        this.error = 'Questions unavailable.';
      },
    });
  }

  submitQuiz(): void {
    if (!this.selectedQuiz) return;
    const unanswered = this.questions.filter(q => !this.responses.has(q.id));
    if (unanswered.length > 0) {
      this.validationError = `Please answer all questions. ${unanswered.length} question(s) remaining.`;
      const firstUnanswered = document.getElementById(`question-${unanswered[0].id}`);
      if (firstUnanswered) firstUnanswered.scrollIntoView({ behavior: 'smooth', block: 'center' });
      return;
    }
    this.validationError = null;
    this.stopTimer();
    this.stopGazeTracking();
    try {
      const studentId = this.requireAuth();
      this.submitting = true;
      const responsesArray = this.questions.map((q) => ({
        questionId: q.id,
        selectedOptionId: this.responses.get(q.id) ?? null,
      }));
      this.api.submitQuiz(this.courseId, this.selectedQuiz.id, studentId, responsesArray).subscribe({
      next: (r) => {
        this.quizResult = r;
        this.arenaMode = true;
        this.showReview = false;
        this.submitting = false;
        this.leaderboardLoading = true;
        this.api.getLeaderboard(this.courseId, this.selectedQuiz!.id).subscribe({
          next: (data) => {
            this.leaderboard = data;
            this.leaderboardLoading = false;
            setTimeout(() => this.showLeaderboard = true, 800);
          },
          error: () => {
            this.leaderboard = [];
            this.leaderboardLoading = false;
          },
        });
      },
      error: (err) => {
        this.error = err instanceof Error && err.message
          ? `Submission failed: ${err.message}`
          : 'Submission failed.';
        this.submitting = false;
      },
      });
    } catch {
      return; // redirected to login
    }
  }

  startTimer(minutes: number): void {
    this.timeLeft = minutes * 60;
    this.timerExpired = false;
    this.timerInterval = setInterval(() => {
      this.timeLeft--;
      if (this.timeLeft <= 0) {
        clearInterval(this.timerInterval);
        this.timerExpired = true;
        this.submitQuiz();
      }
    }, 1000);
  }

  get formattedTime(): string {
    const m = Math.floor(this.timeLeft / 60);
    const s = this.timeLeft % 60;
    return `${m}:${s.toString().padStart(2, '0')}`;
  }

  stopTimer(): void {
    if (this.timerInterval) {
      clearInterval(this.timerInterval);
      this.timerInterval = null;
    }
  }

  ngOnDestroy(): void {
    this.stopTimer();
    this.stopGazeTracking();
    this.sub.unsubscribe();
  }

  reviewAnswers(): void {
    this.showReview = true;
  }

  backToExam(): void {
    this.showReview = false;
  }

  goToQuestion(index: number): void {
    this.currentQuestionIndex = index;
    this.showReview = false;
  }

  getSelectedOptionText(questionId: number): string {
    const selectedId = this.responses.get(questionId);
    if (selectedId === null || selectedId === undefined) return 'Not answered';
    const question = this.questions.find(q => q.id === questionId);
    if (!question) return 'Not answered';
    const option = question.options.find(o => o.id === selectedId);
    return option ? option.optionText : 'Not answered';
  }

  getPlayerRankClass(rankTitle: string): string {
    const map: Record<string, string> = {
      'LEGEND': 'tk-legend',
      'CHAMPION': 'tk-champion',
      'FIGHTER': 'tk-fighter',
      'CHALLENGER': 'tk-challenger',
    };
    return map[rankTitle] || 'tk-challenger';
  }

  getRankIcon(rankTitle: string): string {
    const map: Record<string, string> = {
      'LEGEND': '🔥',
      'CHAMPION': '⚔️',
      'FIGHTER': '🥊',
      'CHALLENGER': '🎮',
    };
    return map[rankTitle] || '🎮';
  }

  onChampionHover(state: boolean): void {
    this.championHovered = state;
  }

  onCardHover(event: MouseEvent, entering: boolean): void {
    const card = event.currentTarget as HTMLElement;
    if (entering) {
      card.style.transform = 'translateY(-8px) scale(1.03)';
      card.style.zIndex = '10';
    } else {
      card.style.transform = '';
      card.style.zIndex = '';
    }
  }

  back(): void {
    this.stopGazeTracking();
    this.router.navigate(['/courses', this.courseId]);
  }

  cancelQuiz(): void {
    this.stopTimer();
    this.stopGazeTracking();
    this.selectedQuiz = null;
    this.questions = [];
    this.responses.clear();
    this.quizResult = null;
    this.showReview = false;
    this.showAnswerReview = false;
    this.validationError = null;
  }

  toggleGazeTracking(enabled: boolean): void {
    this.gazePreference.setEnabled(enabled);
  }

  get gazeFocusScore(): number {
    return Math.round(Number(this.gazeStatus?.current_focus_score ?? 0));
  }

  get gazeAwaySeconds(): number {
    if (!this.gazeAwayStartedAt) return 0;
    return Math.max(0, Math.floor((Date.now() - this.gazeAwayStartedAt) / 1000));
  }

  private startGazeTracking(): void {
    if (!this.gazeEnabled || !this.selectedQuiz || this.quizResult || this.questions.length === 0) return;
    if (!this.isStudent) return;
    if (this.focusMode.isEnabled) {
      this.focusMode.stop();
    }

    let studentId: number;
    try {
      studentId = this.requireAuth();
    } catch {
      return;
    }

    this.clearGazePolling();
    this.gazeUnavailable = '';
    this.gazeWarning = false;
    this.gazeAwayStartedAt = null;
    this.gazeStatus = null;

    this.sub.add(
      this.api.startGazeTracking({
        studentId,
        courseId: this.courseId,
        quizId: this.selectedQuiz.id,
        alertEnabled: false,
        calibrationSec: 2,
      }).subscribe({
        next: (status) => {
          this.gazeSessionId = status.user_id;
          this.gazeActive = status.active;
          this.handleGazeStatus(status);
          this.startGazePolling();
        },
        error: () => {
          this.gazeActive = false;
          this.gazeUnavailable = 'Eye tracking is unavailable.';
        },
      })
    );
  }

  private startGazePolling(): void {
    this.clearGazePolling();
    this.gazePollInterval = setInterval(() => {
      if (!this.gazeSessionId || !this.selectedQuiz) return;
      this.sub.add(
        this.api.getGazeStatus(this.gazeSessionId, this.studentId, this.courseId, this.selectedQuiz.id).subscribe({
          next: (status) => this.handleGazeStatus(status),
          error: () => {
            this.gazeActive = false;
            this.gazeUnavailable = 'Eye tracking status is unavailable.';
          },
        })
      );
    }, 3000);
  }

  private stopGazeTracking(): void {
    this.clearGazePolling();
    const sessionId = this.gazeSessionId;
    const quizId = this.selectedQuiz?.id;
    this.gazeSessionId = '';
    this.gazeActive = false;
    this.gazeWarning = false;
    this.gazeAwayStartedAt = null;
    this.gazeStatus = null;

    if (sessionId) {
      this.api.stopGazeTracking(sessionId, this.studentId || undefined, this.courseId || undefined, quizId).subscribe({
        error: () => {
          this.gazeUnavailable = '';
        },
      });
    }
  }

  private clearGazePolling(): void {
    if (this.gazePollInterval) {
      clearInterval(this.gazePollInterval);
      this.gazePollInterval = null;
    }
  }

  private handleGazeStatus(status: GazeTrackingStatus): void {
    this.gazeStatus = status;
    this.gazeActive = status.active;
    if (!status.active) {
      this.gazeWarning = false;
      this.gazeAwayStartedAt = null;
      return;
    }

    if (status.looking_at_screen !== false) {
      this.gazeWarning = false;
      this.gazeAwayStartedAt = null;
      return;
    }

    if (!this.gazeAwayStartedAt) {
      this.gazeAwayStartedAt = Date.now();
      return;
    }

    if (Date.now() - this.gazeAwayStartedAt >= this.gazeWarningAfterMs) {
      this.triggerDistractionAlert();
    }
  }

  private triggerDistractionAlert(): void {
    this.gazeWarning = true;
    const now = Date.now();
    if (now - this.lastGazeBeepAt < 5000) return;
    this.lastGazeBeepAt = now;
    this.playBeep();
  }

  private playBeep(): void {
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

  private requireAuth(): number {
    const userId = this.userSession.get()?.userId;
    if (!userId || userId <= 0) {
      this.router.navigate(['/login']);
      throw new Error('Authentication required');
    }
    return userId;
  }
}
