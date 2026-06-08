import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { BehaviorRequest } from '../../../models/personality.model';
import { PersonalityService } from '../../../services/personality.service';

@Component({
  selector: 'app-behavior-form',
  standalone: true,
  imports: [CommonModule, RouterModule, ReactiveFormsModule],
  template: `
    <div class="behavior-form">
      <div class="header">
        <button class="back-btn" (click)="goBack()">Back</button>
        <h2>{{ isEdit ? 'Edit' : 'Create' }} Behavior Profile</h2>
      </div>

      <form [formGroup]="form" (ngSubmit)="onSubmit()">
        <div class="grid">
          <div class="form-group"><label>User ID</label><input type="number" formControlName="userId"></div>
          <div class="form-group"><label>Focus Score %</label><input type="number" formControlName="focusScorePct" min="0" max="100"></div>
          <div class="form-group"><label>Agitation Level %</label><input type="number" formControlName="agitationLevelPct" min="0" max="100"></div>
          <div class="form-group"><label>Engagement Index %</label><input type="number" formControlName="engagementIndexPct" min="0" max="100"></div>
          <div class="form-group"><label>Learning Pace Percentile</label><input type="number" formControlName="learningPacePercentile" min="0" max="100"></div>
          <div class="form-group"><label>Fraud Probability Score %</label><input type="number" formControlName="fraudProbabilityScore" min="0" max="100"></div>
        </div>

        <div class="form-actions">
          <button type="button" class="cancel-btn" (click)="goBack()">Cancel</button>
          <button type="submit" class="submit-btn" [disabled]="form.invalid || loading">{{ isEdit ? 'Update' : 'Create' }} Behavior</button>
        </div>
      </form>
    </div>
  `,
  styles: [`
    .behavior-form { max-width: 900px; margin: 0 auto; }
    .header { display: flex; align-items: center; gap: 16px; margin-bottom: 24px; }
    .grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; background: white; padding: 24px; border-radius: 12px; box-shadow: 0 2px 8px rgba(0,0,0,0.08); }
    .form-group { display: flex; flex-direction: column; gap: 6px; }
    .form-group input { padding: 10px 14px; border: 1px solid #e2e8f0; border-radius: 8px; }
    .back-btn, .cancel-btn { padding: 10px 16px; border: none; border-radius: 8px; background: #edf2f7; cursor: pointer; }
    .submit-btn { padding: 10px 16px; border: none; border-radius: 8px; background: #667eea; color: white; cursor: pointer; }
    .form-actions { display: flex; justify-content: flex-end; gap: 12px; margin-top: 20px; }
  `]
})
export class BehaviorFormComponent implements OnInit {
  form: FormGroup;
  isEdit = false;
  behaviorId: string | null = null;
  loading = false;

  constructor(
    private fb: FormBuilder,
    private route: ActivatedRoute,
    private router: Router,
    private personalityService: PersonalityService
  ) {
    this.form = this.fb.group({
      userId: ['', Validators.required],
      focusScorePct: [50, [Validators.required, Validators.min(0), Validators.max(100)]],
      agitationLevelPct: [20, [Validators.required, Validators.min(0), Validators.max(100)]],
      engagementIndexPct: [60, [Validators.required, Validators.min(0), Validators.max(100)]],
      learningPacePercentile: [50, [Validators.required, Validators.min(0), Validators.max(100)]],
      fraudProbabilityScore: [10, [Validators.required, Validators.min(0), Validators.max(100)]]
    });
  }

  ngOnInit(): void {
    this.behaviorId = this.route.snapshot.paramMap.get('id');
    const queryUserId = this.route.snapshot.queryParamMap.get('userId');
    if (queryUserId) this.form.patchValue({ userId: Number(queryUserId) });
    if (this.behaviorId) {
      this.isEdit = true;
      this.loadBehavior(this.behaviorId);
    }
  }

  loadBehavior(id: string): void {
    this.loading = true;
    this.personalityService.getBehaviorById(id).subscribe({
      next: (behavior) => {
        this.form.patchValue({
          userId: behavior.userId,
          focusScorePct: behavior.focusScorePct,
          agitationLevelPct: behavior.agitationLevelPct,
          engagementIndexPct: behavior.engagementIndexPct,
          learningPacePercentile: behavior.learningPacePercentile,
          fraudProbabilityScore: behavior.fraudProbabilityScore
        });
        this.loading = false;
      },
      error: (err) => {
        this.loading = false;
        console.error('Failed to load behavior', err);
      }
    });
  }

  onSubmit(): void {
    if (this.form.invalid) return;

    const request: BehaviorRequest = {
      userId: Number(this.form.value.userId),
      focusScorePct: Number(this.form.value.focusScorePct),
      agitationLevelPct: Number(this.form.value.agitationLevelPct),
      engagementIndexPct: Number(this.form.value.engagementIndexPct),
      learningPacePercentile: Number(this.form.value.learningPacePercentile),
      fraudProbabilityScore: Number(this.form.value.fraudProbabilityScore)
    };

    this.loading = true;
    const operation = this.isEdit && this.behaviorId
      ? this.personalityService.updateBehavior(this.behaviorId, request)
      : this.personalityService.createBehavior(request);

    operation.subscribe({
      next: (behavior) => {
        this.router.navigate(['/dashboard/personality/behaviors/detail', behavior.behaviorId]);
      },
      error: (err) => {
        this.loading = false;
        console.error('Failed to save behavior', err);
        alert(`Error: ${err.error?.message || 'Failed to save behavior'}`);
      }
    });
  }

  goBack(): void {
    this.router.navigate(['/dashboard/personality/behaviors']);
  }
}
