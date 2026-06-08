import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { Behavior } from '../../../models/personality.model';
import { PersonalityService } from '../../../services/personality.service';

@Component({
  selector: 'app-behavior-detail',
  standalone: true,
  imports: [CommonModule, RouterModule],
  template: `
    <div class="behavior-detail" *ngIf="behavior">
      <div class="header">
        <button class="back-btn" (click)="goBack()">Back</button>
        <h2>Behavior Profile for User #{{ behavior.userId }}</h2>
        <button class="edit-btn" (click)="edit()">Edit</button>
      </div>

      <div class="cards">
        <div class="card">
          <h3>Engagement Metrics</h3>
          <div class="metric-row"><span>Focus Score</span><strong>{{ behavior.focusScorePct | number:'1.0-0' }}%</strong></div>
          <div class="metric-row"><span>Engagement Index</span><strong>{{ behavior.engagementIndexPct | number:'1.0-0' }}%</strong></div>
          <div class="metric-row"><span>Learning Pace</span><strong>{{ behavior.learningPacePercentile | number:'1.0-0' }}%</strong></div>
        </div>

        <div class="card">
          <h3>Risk Signals</h3>
          <div class="metric-row"><span>Agitation Level</span><strong>{{ behavior.agitationLevelPct | number:'1.0-0' }}%</strong></div>
          <div class="metric-row"><span>Fraud Probability</span><strong>{{ behavior.fraudProbabilityScore | number:'1.0-0' }}%</strong></div>
          <div class="metric-row"><span>Last Interaction</span><strong>{{ behavior.lastInteraction | date:'medium' }}</strong></div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .behavior-detail { max-width: 900px; margin: 0 auto; }
    .header { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-bottom: 24px; flex-wrap: wrap; }
    .cards { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; }
    .card { background: white; padding: 24px; border-radius: 12px; box-shadow: 0 2px 8px rgba(0,0,0,0.08); }
    .metric-row { display: flex; justify-content: space-between; gap: 12px; padding: 12px 0; border-bottom: 1px solid #edf2f7; }
    .back-btn, .edit-btn { padding: 10px 16px; border: none; border-radius: 8px; cursor: pointer; }
    .back-btn { background: #edf2f7; }
    .edit-btn { background: #667eea; color: white; }
  `]
})
export class BehaviorDetailComponent implements OnInit {
  behavior: Behavior | null = null;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private personalityService: PersonalityService
  ) {}

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.personalityService.getBehaviorById(id).subscribe({
        next: (behavior) => { this.behavior = behavior; },
        error: (err) => console.error('Failed to load behavior', err)
      });
    }
  }

  goBack(): void {
    this.router.navigate(['/dashboard/personality/behaviors']);
  }

  edit(): void {
    if (this.behavior) {
      this.router.navigate(['/dashboard/personality/behaviors/edit', this.behavior.behaviorId]);
    }
  }
}
