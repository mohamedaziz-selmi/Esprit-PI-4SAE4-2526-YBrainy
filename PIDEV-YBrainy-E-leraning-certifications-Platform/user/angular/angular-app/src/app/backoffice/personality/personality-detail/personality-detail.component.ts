import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { EMPTY, catchError } from 'rxjs';
import { Behavior, Personality } from '../../../models/personality.model';
import { PersonalityService } from '../../../services/personality.service';

@Component({
  selector: 'app-personality-detail',
  standalone: true,
  imports: [CommonModule, RouterModule],
  template: `
    <div class="personality-detail" *ngIf="personality">
      <div class="header">
        <button class="back-btn" (click)="goBack()">Back</button>
        <h2>Student Profile #{{ personality.userId }}</h2>
        <div class="actions">
          <button class="edit-btn" (click)="edit()">Edit Profile</button>
        </div>
      </div>

      <div class="content-grid">
        <div class="card learning-styles">
          <h3>Learning Style Profile</h3>
          <div class="style-bars">
            <div class="style-bar">
              <label>Visual</label>
              <div class="bar"><div class="fill visual" [style.width.%]="personality.visualLearningPct"></div></div>
              <span>{{ personality.visualLearningPct | number:'1.0-0' }}%</span>
            </div>
            <div class="style-bar">
              <label>Auditory</label>
              <div class="bar"><div class="fill auditory" [style.width.%]="personality.auditoryLearningPct"></div></div>
              <span>{{ personality.auditoryLearningPct | number:'1.0-0' }}%</span>
            </div>
            <div class="style-bar">
              <label>Kinesthetic</label>
              <div class="bar"><div class="fill kinesthetic" [style.width.%]="personality.kinestheticLearningPct"></div></div>
              <span>{{ personality.kinestheticLearningPct | number:'1.0-0' }}%</span>
            </div>
          </div>
          <div class="dominant-style">
            <p>Dominant Style: <strong>{{ getDominantStyle() }}</strong></p>
            <p class="suggestion">{{ getStyleSuggestion() }}</p>
          </div>
        </div>

        <div class="card career">
          <h3>Career Alignment</h3>
          <div class="score-circle">
            <span class="score">{{ personality.careerAlignmentScore | number:'1.0-0' }}%</span>
            <label>Alignment Score</label>
          </div>
          <div class="career-goals">
            <h4>Career Goals</h4>
            <ul>
              <li *ngFor="let goal of personality.careerGoals">{{ goal }}</li>
            </ul>
          </div>
        </div>

        <div class="card behavior">
          <div class="behavior-header">
            <h3>Behavior Profile</h3>
            <button *ngIf="behavior" class="link-btn" (click)="openBehavior()">Open Behavior</button>
            <button *ngIf="!behavior" class="link-btn" (click)="createBehavior()">Create Behavior</button>
          </div>

          <ng-container *ngIf="behavior; else noBehavior">
            <div class="metrics-grid">
              <div class="metric">
                <label>Focus Score</label>
                <div class="metric-bar"><div class="fill good" [style.width.%]="behavior?.focusScorePct || 0"></div></div>
                <span>{{ behavior?.focusScorePct | number:'1.0-0' }}%</span>
              </div>
              <div class="metric">
                <label>Agitation Level</label>
                <div class="metric-bar">
                  <div class="fill"
                    [class.good]="(behavior?.agitationLevelPct || 0) < 30"
                    [class.warning]="(behavior?.agitationLevelPct || 0) >= 30 && (behavior?.agitationLevelPct || 0) < 70"
                    [class.critical]="(behavior?.agitationLevelPct || 0) >= 70"
                    [style.width.%]="behavior?.agitationLevelPct || 0"></div>
                </div>
                <span>{{ behavior?.agitationLevelPct | number:'1.0-0' }}%</span>
              </div>
              <div class="metric">
                <label>Engagement</label>
                <div class="metric-bar"><div class="fill good" [style.width.%]="behavior?.engagementIndexPct || 0"></div></div>
                <span>{{ behavior?.engagementIndexPct | number:'1.0-0' }}%</span>
              </div>
              <div class="metric">
                <label>Fraud Risk</label>
                <div class="metric-bar">
                  <div class="fill"
                    [class.good]="(behavior?.fraudProbabilityScore || 0) < 20"
                    [class.warning]="(behavior?.fraudProbabilityScore || 0) >= 20 && (behavior?.fraudProbabilityScore || 0) < 50"
                    [class.critical]="(behavior?.fraudProbabilityScore || 0) >= 50"
                    [style.width.%]="behavior?.fraudProbabilityScore || 0"></div>
                </div>
                <span>{{ behavior?.fraudProbabilityScore | number:'1.0-0' }}%</span>
              </div>
            </div>
            <div class="last-interaction">
              Last updated: {{ behavior?.lastInteraction | date:'medium' }}
            </div>
          </ng-container>

          <ng-template #noBehavior>
            <p class="empty-state">No behavior profile is linked to this user yet.</p>
          </ng-template>
        </div>

        <div class="card cognitive">
          <h3>Cognitive Profile</h3>
          <div class="cognitive-load">
            <label>Cognitive Load Tolerance</label>
            <div class="tolerance-bar"><div class="fill" [style.width.%]="personality.cognitiveLoadTolerance"></div></div>
            <span>{{ personality.cognitiveLoadTolerance | number:'1.0-0' }}%</span>
          </div>
          <p class="info">This student can handle {{ getCognitiveDescription() }} complexity tasks.</p>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .personality-detail { max-width: 1200px; margin: 0 auto; }
    .header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 25px; padding-bottom: 15px; border-bottom: 1px solid #e2e8f0; gap: 16px; flex-wrap: wrap; }
    .header h2 { margin: 0; color: #2d3748; }
    .back-btn { padding: 8px 16px; background: #edf2f7; border: none; border-radius: 6px; cursor: pointer; }
    .edit-btn, .link-btn { padding: 10px 20px; background: #667eea; color: white; border: none; border-radius: 8px; cursor: pointer; }
    .content-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 20px; }
    .card { background: white; padding: 25px; border-radius: 12px; box-shadow: 0 2px 8px rgba(0,0,0,0.08); }
    .card h3 { margin-top: 0; color: #2d3748; font-size: 1.1rem; }
    .behavior-header { display: flex; justify-content: space-between; align-items: center; gap: 12px; margin-bottom: 16px; flex-wrap: wrap; }
    .style-bars { display: flex; flex-direction: column; gap: 15px; }
    .style-bar { display: flex; align-items: center; gap: 10px; }
    .style-bar label { width: 100px; font-size: 14px; }
    .style-bar .bar { flex: 1; height: 20px; background: #edf2f7; border-radius: 10px; overflow: hidden; }
    .style-bar .fill { height: 100%; transition: width 0.5s ease; }
    .style-bar .fill.visual { background: linear-gradient(90deg, #fc8181, #e53e3e); }
    .style-bar .fill.auditory { background: linear-gradient(90deg, #9ae6b4, #38a169); }
    .style-bar .fill.kinesthetic { background: linear-gradient(90deg, #90cdf4, #3182ce); }
    .style-bar span { width: 50px; text-align: right; font-weight: 600; }
    .dominant-style { margin-top: 20px; padding: 15px; background: #f7fafc; border-radius: 8px; }
    .dominant-style p { margin: 0; color: #4a5568; }
    .dominant-style .suggestion { margin-top: 8px; font-size: 14px; color: #667eea; font-style: italic; }
    .score-circle { text-align: center; padding: 30px; }
    .score-circle .score { display: block; font-size: 3rem; font-weight: bold; color: #667eea; }
    .career-goals ul { list-style: none; padding: 0; }
    .career-goals li { padding: 8px 12px; background: #edf2f7; margin-bottom: 8px; border-radius: 6px; font-size: 14px; }
    .metrics-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 15px; }
    .metric { display: flex; flex-direction: column; gap: 5px; }
    .metric label { font-size: 12px; color: #718096; }
    .metric-bar { height: 8px; background: #edf2f7; border-radius: 4px; overflow: hidden; }
    .metric-bar .fill { height: 100%; transition: width 0.3s; }
    .metric-bar .fill.good { background: #48bb78; }
    .metric-bar .fill.warning { background: #ed8936; }
    .metric-bar .fill.critical { background: #f56565; }
    .metric span { font-size: 14px; font-weight: 600; }
    .last-interaction { margin-top: 15px; font-size: 12px; color: #a0aec0; }
    .empty-state { color: #718096; margin: 0; }
    .cognitive-load { margin-bottom: 15px; }
    .tolerance-bar { height: 20px; background: #edf2f7; border-radius: 10px; overflow: hidden; margin: 10px 0; }
    .tolerance-bar .fill { height: 100%; background: linear-gradient(90deg, #667eea, #764ba2); transition: width 0.5s; }
    .info { color: #718096; font-size: 14px; margin-top: 15px; }
  `]
})
export class PersonalityDetailComponent implements OnInit {
  personality: Personality | null = null;
  behavior: Behavior | null = null;
  loading = false;
  error = '';

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private personalityService: PersonalityService
  ) {}

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) this.loadPersonality(id);
  }

  loadPersonality(id: string): void {
    this.loading = true;
    this.personalityService.getPersonalityById(id).subscribe({
      next: (data) => {
        this.personality = data;
        this.loading = false;
        this.loadBehavior(data.userId);
      },
      error: (err) => {
        this.error = 'Failed to load personality';
        this.loading = false;
        console.error(err);
      }
    });
  }

  loadBehavior(userId: number): void {
    this.personalityService.getBehaviorByUserId(userId)
      .pipe(
        catchError((err) => {
          console.warn('No behavior profile linked for this user yet.', err);
          this.behavior = null;
          return EMPTY;
        })
      )
      .subscribe((behavior) => {
        this.behavior = behavior;
      });
  }

  getDominantStyle(): string {
    if (!this.personality) return '';
    const max = Math.max(
      this.personality.visualLearningPct,
      this.personality.auditoryLearningPct,
      this.personality.kinestheticLearningPct
    );
    if (max === this.personality.visualLearningPct) return 'Visual Learner';
    if (max === this.personality.auditoryLearningPct) return 'Auditory Learner';
    return 'Kinesthetic Learner';
  }

  getStyleSuggestion(): string {
    const style = this.getDominantStyle();
    switch (style) {
      case 'Visual Learner': return 'Recommend videos, diagrams, infographics, and visual mind maps.';
      case 'Auditory Learner': return 'Recommend audio lectures, discussions, podcasts, and spoken walkthroughs.';
      case 'Kinesthetic Learner': return 'Recommend hands-on labs, interactive exercises, and project-based work.';
      default: return '';
    }
  }

  getCognitiveDescription(): string {
    if (!this.personality) return '';
    const score = this.personality.cognitiveLoadTolerance;
    if (score > 80) return 'high';
    if (score > 50) return 'moderate';
    return 'low';
  }

  goBack(): void {
    this.router.navigate(['/dashboard/personality/list']);
  }

  edit(): void {
    if (this.personality) {
      this.router.navigate(['/dashboard/personality/edit', this.personality.personalityId]);
    }
  }

  openBehavior(): void {
    if (this.behavior) {
      this.router.navigate(['/dashboard/personality/behaviors/detail', this.behavior.behaviorId]);
    }
  }

  createBehavior(): void {
    if (this.personality) {
      this.router.navigate(['/dashboard/personality/behaviors/create'], {
        queryParams: { userId: this.personality.userId }
      });
    }
  }
}
