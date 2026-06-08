import { Component, Input, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AiGenerationService, MlPredictResponse } from '../../services/ai-generation.service';

@Component({
  selector: 'app-ml-predict-badge',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './ml-predict-badge.component.html',
  styleUrls: ['./ml-predict-badge.component.scss']
})
export class MlPredictBadgeComponent implements OnInit, OnDestroy {
  @Input() title: string = '';
  @Input() body: string = '';
  @Input() tags: string = '';

  result: MlPredictResponse | null = null;
  loading = true;
  showDetail = false;
  private destroyed = false;

  constructor(private aiService: AiGenerationService) {}

  ngOnInit(): void {
    this.aiService.mlPredict(this.title, this.body, this.tags).subscribe({
      next: (r) => {
        if (!this.destroyed) {
          this.result = r;
          this.loading = false;
        }
      },
      error: () => {
        if (!this.destroyed) {
          this.loading = false;
        }
      }
    });
  }

  ngOnDestroy(): void {
    this.destroyed = true;
  }

  get labelDisplay(): string {
    if (!this.result) return '';
    switch (this.result.label) {
      case 'HQ':       return 'High Quality';
      case 'LQ_CLOSE': return 'Should Close';
      case 'LQ_EDIT':  return 'Needs Editing';
      default:         return this.result.label;
    }
  }

  get icon(): string {
    if (!this.result) return '';
    switch (this.result.label) {
      case 'HQ':       return '✦';
      case 'LQ_CLOSE': return '✕';
      case 'LQ_EDIT':  return '✎';
      default:         return '?';
    }
  }

  get confidencePct(): number {
    return this.result ? Math.round(this.result.confidence * 100) : 0;
  }

  toggleDetail(): void {
    this.showDetail = !this.showDetail;
  }
}
