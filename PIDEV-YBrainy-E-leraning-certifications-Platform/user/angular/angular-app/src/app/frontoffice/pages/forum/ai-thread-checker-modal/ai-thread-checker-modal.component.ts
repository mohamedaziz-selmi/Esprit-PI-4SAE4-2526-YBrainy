import {
  Component,
  EventEmitter,
  Input,
  OnChanges,
  OnDestroy,
  Output,
  SimpleChanges,
} from '@angular/core';
import { Subscription } from 'rxjs';

import {
  AiAnalysisResponse,
  AiGenerationService,
  MlPredictResponse,
  ThreadQualityScore,
} from '../../../services/ai-generation.service';
import { GrammarCheckerService, GrammarResult } from '../../../services/grammar-checker.service';
import { RulesResult, ThreadRulesEngineService } from '../../../services/thread-rules-engine.service';

export type CheckerTab = 'rules' | 'grammar' | 'ai' | 'ml';

interface MlEnrichedResult {
  mlRaw: MlPredictResponse;
  verdict: 'great' | 'needs_work' | 'poor';
  explanation: string;
}

@Component({
  selector: 'app-ai-thread-checker-modal',
  standalone: false,
  templateUrl: './ai-thread-checker-modal.component.html',
  styleUrl: './ai-thread-checker-modal.component.css',
})
export class AiThreadCheckerModalComponent implements OnChanges, OnDestroy {
  @Input() title = '';
  @Input() body = '';
  @Input() visible = false;

  @Output() confirm = new EventEmitter<void>();
  @Output() closed = new EventEmitter<void>();

  activeTab: CheckerTab = 'rules';

  rulesResult: RulesResult | null = null;
  grammarResult: GrammarResult | null = null;

  aiLoading = false;
  aiResult: AiAnalysisResponse | null = null;
  aiError: string | null = null;

  mlLoading = false;
  mlEnriched: MlEnrichedResult | null = null;
  mlError: string | null = null;

  private readonly minAiScore = 5;
  private aiSub: Subscription | null = null;
  private mlSub: Subscription | null = null;

  constructor(
    private rulesEngine: ThreadRulesEngineService,
    private grammarChecker: GrammarCheckerService,
    private aiService: AiGenerationService
  ) {}

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['visible']?.currentValue === true) {
      this.runLocalChecks();
      this.activeTab = 'rules';
      this.aiResult = null;
      this.aiLoading = false;
      this.aiError = null;
      this.mlEnriched = null;
      this.mlLoading = false;
      this.mlError = null;
      this.aiSub?.unsubscribe();
      this.mlSub?.unsubscribe();
    }
  }

  ngOnDestroy(): void {
    this.aiSub?.unsubscribe();
    this.mlSub?.unsubscribe();
  }

  setTab(tab: CheckerTab): void {
    this.activeTab = tab;
  }

  runAiAnalysis(): void {
    if (this.aiLoading) return;

    this.aiLoading = true;
    this.aiError = null;
    this.aiSub?.unsubscribe();
    this.aiSub = this.aiService.analyzeThread(this.title, this.body).subscribe({
      next: (response) => {
        this.aiLoading = false;
        this.aiResult = response;
      },
      error: (error) => {
        this.aiLoading = false;
        this.aiError =
          error?.error?.error ??
          error?.error?.message ??
          error?.message ??
          'AI analysis is currently unavailable.';
      },
    });
  }

  runMlPredict(): void {
    if (this.mlLoading) return;

    this.mlLoading = true;
    this.mlError = null;
    this.mlEnriched = null;
    this.mlSub?.unsubscribe();
    this.mlSub = this.aiService.mlPredict(this.title, this.body).subscribe({
      next: (result) => {
        this.mlLoading = false;
        this.mlEnriched = {
          mlRaw: result,
          verdict: this.getVerdict(result),
          explanation: this.getVerdictExplanation(result),
        };
      },
      error: (error) => {
        this.mlLoading = false;
        this.mlError = error?.message ?? 'ML prediction is currently unavailable.';
      },
    });
  }

  get canSubmit(): boolean {
    if (!(this.rulesResult?.canSubmit ?? true)) {
      return false;
    }

    return true;
  }

  get submitBlockedByAi(): boolean {
    return !!(
      this.aiResult?.analysisAvailable &&
      this.score &&
      (this.score.overall ?? 10) < this.minAiScore
    );
  }

  get rulesOk(): boolean {
    return (this.rulesResult?.blocking.length ?? 0) === 0;
  }

  get grammarOk(): boolean {
    return (this.grammarResult?.issues.length ?? 0) === 0;
  }

  get score(): ThreadQualityScore | null {
    return this.aiResult?.score ?? null;
  }

  get verdictIcon(): string {
    switch (this.mlEnriched?.verdict) {
      case 'great':
        return 'OK';
      case 'needs_work':
        return '!';
      case 'poor':
        return 'X';
      default:
        return '';
    }
  }

  get verdictLabel(): string {
    switch (this.mlEnriched?.verdict) {
      case 'great':
        return 'Looks strong';
      case 'needs_work':
        return 'Needs some work';
      case 'poor':
        return 'Likely to struggle';
      default:
        return '';
    }
  }

  get verdictColor(): string {
    switch (this.mlEnriched?.verdict) {
      case 'great':
        return '#22c55e';
      case 'needs_work':
        return '#f59e0b';
      case 'poor':
        return '#ef4444';
      default:
        return '#64748b';
    }
  }

  scoreBar(value: number): number {
    return Math.min(100, Math.round((value / 10) * 100));
  }

  probabilityPercent(label: 'HQ' | 'LQ_CLOSE' | 'LQ_EDIT'): number {
    const value = this.mlEnriched?.mlRaw.probabilities?.[label];
    return Number.isFinite(value) ? Math.round((value ?? 0) * 100) : 0;
  }

  confidencePercent(): number {
    const confidence = this.mlEnriched?.mlRaw.confidence ?? 0;
    return Number.isFinite(confidence) ? Math.round(confidence * 100) : 0;
  }

  onConfirm(): void {
    this.confirm.emit();
  }

  onClose(): void {
    this.aiSub?.unsubscribe();
    this.mlSub?.unsubscribe();
    this.closed.emit();
  }

  trackById(_: number, item: { id: string }): string {
    return item.id;
  }

  private runLocalChecks(): void {
    this.rulesResult = this.rulesEngine.analyze(this.title, this.body);
    this.grammarResult = this.grammarChecker.check(this.body);
  }

  private getVerdict(result: MlPredictResponse): 'great' | 'needs_work' | 'poor' {
    if (!result.available) {
      return 'needs_work';
    }
    if (result.label === 'HQ' && result.confidence >= 0.55) {
      return 'great';
    }
    if (result.label === 'LQ_CLOSE') {
      return 'poor';
    }
    return 'needs_work';
  }

  private getVerdictExplanation(result: MlPredictResponse): string {
    const confidence = Number.isFinite(result.confidence) ? Math.round(result.confidence * 100) : 0;
    const hq = Number.isFinite(result.probabilities?.HQ) ? Math.round((result.probabilities?.HQ ?? 0) * 100) : 0;
    const close = Number.isFinite(result.probabilities?.LQ_CLOSE) ? Math.round((result.probabilities?.LQ_CLOSE ?? 0) * 100) : 0;
    const edit = Number.isFinite(result.probabilities?.LQ_EDIT) ? Math.round((result.probabilities?.LQ_EDIT ?? 0) * 100) : 0;

    if (!result.available) {
      return 'The ML service is offline, so this check cannot confirm thread quality right now.';
    }
    if (result.label === 'HQ') {
      return `The model is ${confidence}% confident this thread is high quality. The structure and level of detail look strong.`;
    }
    if (result.label === 'LQ_CLOSE') {
      return `The model sees a ${close}% chance this thread could be closed. Add more context, explain what you tried, and make the question more specific.`;
    }
    return `The model is unsure (HQ ${hq}%, needs editing ${edit}%, close ${close}%). Clarifying the title and expanding the body should help.`;
  }
}
