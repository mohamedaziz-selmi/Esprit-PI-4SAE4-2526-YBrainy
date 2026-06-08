import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { FormArray, FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { PersonalityRequest } from '../../../models/personality.model';
import { PersonalityService } from '../../../services/personality.service';

@Component({
  selector: 'app-personality-form',
  standalone: true,
  imports: [CommonModule, RouterModule, ReactiveFormsModule],
  template: `
    <div class="personality-form">
      <div class="header">
        <button class="back-btn" (click)="goBack()">Back</button>
        <h2>{{ isEdit ? 'Edit' : 'Create' }} Personality Profile</h2>
      </div>

      <form [formGroup]="form" (ngSubmit)="onSubmit()">
        <div class="form-section">
          <h3>Student Information</h3>
          <div class="form-group">
            <label>User ID</label>
            <input type="number" formControlName="userId" placeholder="Enter user ID">
            <small *ngIf="form.get('userId')?.invalid && form.get('userId')?.touched">
              User ID is required
            </small>
          </div>
        </div>

        <div class="form-section">
          <h3>Learning Style Percentages (must total 100%)</h3>
          <div class="form-row">
            <div class="form-group">
              <label>Visual Learning %</label>
              <input type="number" formControlName="visualLearningPct" min="0" max="100">
            </div>
            <div class="form-group">
              <label>Auditory Learning %</label>
              <input type="number" formControlName="auditoryLearningPct" min="0" max="100">
            </div>
            <div class="form-group">
              <label>Kinesthetic Learning %</label>
              <input type="number" formControlName="kinestheticLearningPct" min="0" max="100">
            </div>
          </div>
          <div class="total-check" [class.valid]="isTotalValid()" [class.invalid]="!isTotalValid()">
            Total: {{ getTotalPercentage() }}%
          </div>
        </div>

        <div class="form-section">
          <h3>Career & Cognitive Profile</h3>
          <div class="form-row two-columns">
            <div class="form-group">
              <label>Career Alignment Score %</label>
              <input type="number" formControlName="careerAlignmentScore" min="0" max="100">
            </div>
            <div class="form-group">
              <label>Cognitive Load Tolerance %</label>
              <input type="number" formControlName="cognitiveLoadTolerance" min="0" max="100">
            </div>
          </div>
        </div>

        <div class="form-section">
          <h3>Career Goals</h3>
          <div formArrayName="careerGoals">
            <div *ngFor="let goal of careerGoals.controls; let i = index" class="goal-row">
              <input [formControlName]="i" placeholder="Enter career goal">
              <button type="button" class="remove-btn" (click)="removeGoal(i)">Remove</button>
            </div>
          </div>
          <button type="button" class="add-btn" (click)="addGoal()">Add Goal</button>
        </div>

        <div class="form-actions">
          <button type="button" class="cancel-btn" (click)="goBack()">Cancel</button>
          <button type="submit" class="submit-btn" [disabled]="form.invalid || !isTotalValid() || loading">
            {{ isEdit ? 'Update' : 'Create' }} Profile
          </button>
        </div>
      </form>
    </div>
  `,
  styles: [`
    .personality-form { max-width: 800px; margin: 0 auto; }
    .header { display: flex; align-items: center; gap: 20px; margin-bottom: 25px; padding-bottom: 15px; border-bottom: 1px solid #e2e8f0; }
    .header h2 { margin: 0; color: #2d3748; }
    .back-btn { padding: 8px 16px; background: #edf2f7; border: none; border-radius: 6px; cursor: pointer; }
    .form-section { background: white; padding: 25px; border-radius: 12px; margin-bottom: 20px; box-shadow: 0 2px 8px rgba(0,0,0,0.08); }
    .form-section h3 { margin-top: 0; color: #2d3748; font-size: 1.1rem; margin-bottom: 20px; }
    .form-row { display: grid; grid-template-columns: repeat(3, 1fr); gap: 15px; }
    .form-row.two-columns { grid-template-columns: repeat(2, 1fr); }
    .form-group { display: flex; flex-direction: column; gap: 5px; }
    .form-group label { font-size: 14px; color: #4a5568; font-weight: 500; }
    .form-group input { padding: 10px 15px; border: 1px solid #e2e8f0; border-radius: 8px; font-size: 14px; }
    .form-group small { color: #e53e3e; font-size: 12px; }
    .total-check { margin-top: 15px; padding: 10px; border-radius: 6px; font-weight: 600; text-align: center; }
    .total-check.valid { background: #c6f6d5; color: #276749; }
    .total-check.invalid { background: #fed7d7; color: #c53030; }
    .goal-row { display: flex; gap: 10px; margin-bottom: 10px; }
    .goal-row input { flex: 1; padding: 10px 15px; border: 1px solid #e2e8f0; border-radius: 8px; }
    .remove-btn { padding: 10px 15px; background: #fc8181; color: white; border: none; border-radius: 8px; cursor: pointer; }
    .add-btn { padding: 10px 20px; background: #667eea; color: white; border: none; border-radius: 8px; cursor: pointer; margin-top: 10px; }
    .form-actions { display: flex; gap: 15px; justify-content: flex-end; margin-top: 25px; }
    .cancel-btn { padding: 12px 30px; background: #edf2f7; color: #4a5568; border: none; border-radius: 8px; cursor: pointer; }
    .submit-btn { padding: 12px 30px; background: #667eea; color: white; border: none; border-radius: 8px; cursor: pointer; }
    .submit-btn:disabled { background: #cbd5e0; cursor: not-allowed; }
  `]
})
export class PersonalityFormComponent implements OnInit {
  form: FormGroup;
  isEdit = false;
  personalityId: string | null = null;
  loading = false;
  error = '';

  constructor(
    private fb: FormBuilder,
    private route: ActivatedRoute,
    private router: Router,
    private personalityService: PersonalityService
  ) {
    this.form = this.fb.group({
      userId: ['', Validators.required],
      visualLearningPct: [33, [Validators.required, Validators.min(0), Validators.max(100)]],
      auditoryLearningPct: [33, [Validators.required, Validators.min(0), Validators.max(100)]],
      kinestheticLearningPct: [34, [Validators.required, Validators.min(0), Validators.max(100)]],
      careerAlignmentScore: [50, [Validators.required, Validators.min(0), Validators.max(100)]],
      cognitiveLoadTolerance: [50, [Validators.required, Validators.min(0), Validators.max(100)]],
      careerGoals: this.fb.array([this.fb.control('')])
    });
  }

  ngOnInit(): void {
    this.personalityId = this.route.snapshot.paramMap.get('id');
    if (this.personalityId) {
      this.isEdit = true;
      this.loadPersonality(this.personalityId);
    }
  }

  get careerGoals(): FormArray {
    return this.form.get('careerGoals') as FormArray;
  }

  addGoal(): void {
    this.careerGoals.push(this.fb.control(''));
  }

  removeGoal(index: number): void {
    if (this.careerGoals.length > 1) {
      this.careerGoals.removeAt(index);
    } else {
      this.careerGoals.at(0).setValue('');
    }
  }

  getTotalPercentage(): number {
    const visual = Number(this.form.get('visualLearningPct')?.value) || 0;
    const auditory = Number(this.form.get('auditoryLearningPct')?.value) || 0;
    const kinesthetic = Number(this.form.get('kinestheticLearningPct')?.value) || 0;
    return visual + auditory + kinesthetic;
  }

  isTotalValid(): boolean {
    return this.getTotalPercentage() === 100;
  }

  loadPersonality(id: string): void {
    this.loading = true;
    this.personalityService.getPersonalityById(id).subscribe({
      next: (personality) => {
        this.form.patchValue({
          userId: personality.userId,
          visualLearningPct: personality.visualLearningPct,
          auditoryLearningPct: personality.auditoryLearningPct,
          kinestheticLearningPct: personality.kinestheticLearningPct,
          careerAlignmentScore: personality.careerAlignmentScore,
          cognitiveLoadTolerance: personality.cognitiveLoadTolerance
        });

        this.careerGoals.clear();
        if (personality.careerGoals.length === 0) {
          this.careerGoals.push(this.fb.control(''));
        } else {
          personality.careerGoals.forEach((goal) => this.careerGoals.push(this.fb.control(goal)));
        }

        this.loading = false;
      },
      error: (err) => {
        this.error = 'Failed to load personality';
        this.loading = false;
        console.error(err);
      }
    });
  }

  onSubmit(): void {
    if (this.form.invalid || !this.isTotalValid()) return;

    const request: PersonalityRequest = {
      userId: Number(this.form.value.userId),
      visualLearningPct: Number(this.form.value.visualLearningPct),
      auditoryLearningPct: Number(this.form.value.auditoryLearningPct),
      kinestheticLearningPct: Number(this.form.value.kinestheticLearningPct),
      careerAlignmentScore: Number(this.form.value.careerAlignmentScore),
      cognitiveLoadTolerance: Number(this.form.value.cognitiveLoadTolerance),
      careerGoals: this.form.value.careerGoals.filter((goal: string) => goal && goal.trim() !== '')
    };

    this.loading = true;
    const operation = this.isEdit && this.personalityId
      ? this.personalityService.updatePersonality(this.personalityId, request)
      : this.personalityService.createPersonality(request);

    operation.subscribe({
      next: () => this.router.navigate(['/dashboard/personality/list']),
      error: (err) => {
        this.error = 'Failed to save personality';
        this.loading = false;
        console.error(err);
        alert(`Error: ${err.error?.message || 'Failed to save'}`);
      }
    });
  }

  goBack(): void {
    this.router.navigate(['/dashboard/personality/list']);
  }
}
