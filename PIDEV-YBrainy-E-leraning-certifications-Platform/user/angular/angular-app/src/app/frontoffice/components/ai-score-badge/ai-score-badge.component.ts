import { CommonModule } from '@angular/common';
import { Component, Input, OnChanges, SimpleChanges } from '@angular/core';
import { Subscription } from 'rxjs';
import { AiGenerationService, AiAnalysisResponse, ThreadQualityScore } from '../../services/ai-generation.service';

@Component({
  selector: 'app-ai-score-badge',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './ai-score-badge.component.html',
  styleUrl: './ai-score-badge.component.css',
})
export class AiScoreBadgeComponent implements OnChanges {
  /** Pass the thread directly if AI fields are already on it */
  @Input() aiAnalyzed = false;
  @Input() aiOverallScore: number | null = null;
  @Input() aiLabel: string | null = null;
  @Input() aiLabelColor: string | null = null;
  @Input() aiSummary: string | null = null;

  /** Pass threadId to lazy-fetch the score after async processing */
  @Input() threadId: number | null = null;

  detailOpen = false;
  fullScore: ThreadQualityScore | null = null;
  loadingDetail = false;

  private sub: Subscription | null = null;

  constructor(private aiService: AiGenerationService) {}

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['aiAnalyzed'] || changes['threadId']) {
      // If not yet analyzed but we have a threadId, poll once after a delay
      if (!this.aiAnalyzed && this.threadId != null) {
        this.schedulePolling();
      }
    }
  }

  get color(): string {
    return this.aiLabelColor ?? '#64748b';
  }

  get showBadge(): boolean {
    return this.aiAnalyzed && this.aiOverallScore != null;
  }

  toggleDetail(): void {
    if (!this.showBadge) return;
    this.detailOpen = !this.detailOpen;
    if (this.detailOpen && !this.fullScore && this.threadId != null) {
      this.fetchFullScore();
    }
  }

  closeDetail(): void {
    this.detailOpen = false;
  }

  scoreBar(value: number): number {
    return Math.min(100, Math.round((value / 10) * 100));
  }

  private schedulePolling(): void {
    // Poll once after 12 s — async scoring typically takes 3–8 s
    setTimeout(() => {
      if (!this.aiAnalyzed && this.threadId != null) {
        this.sub?.unsubscribe();
        this.sub = this.aiService.getThreadScore(this.threadId).subscribe({
          next: (res) => {
            if (res.analysisAvailable && res.score) {
              this.aiAnalyzed = true;
              this.aiOverallScore = res.score.overall;
              this.aiLabel = res.score.label;
              this.aiLabelColor = res.score.labelColor;
              this.aiSummary = res.score.summary;
              this.fullScore = res.score;
            }
          },
          error: () => { /* silent — badge just won't appear */ },
        });
      }
    }, 12000);
  }

  private fetchFullScore(): void {
    if (!this.threadId) return;
    this.loadingDetail = true;
    this.sub?.unsubscribe();
    this.sub = this.aiService.getThreadScore(this.threadId).subscribe({
      next: (res) => {
        this.loadingDetail = false;
        if (res.analysisAvailable) this.fullScore = res.score;
      },
      error: () => { this.loadingDetail = false; },
    });
  }
}
