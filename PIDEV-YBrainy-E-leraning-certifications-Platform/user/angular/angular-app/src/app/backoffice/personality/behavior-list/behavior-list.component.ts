import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { Behavior } from '../../../models/personality.model';
import { PersonalityService } from '../../../services/personality.service';

@Component({
  selector: 'app-behavior-list',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  template: `
    <div class="behavior-list">
      <div class="toolbar">
        <input type="text" [(ngModel)]="searchTerm" placeholder="Search by user id..." class="search-input" />
        <button class="primary-btn" (click)="router.navigate(['/dashboard/personality/behaviors/create'])">Add Behavior</button>
      </div>

      <table class="behavior-table">
        <thead>
          <tr>
            <th>User</th><th>Focus</th><th>Agitation</th><th>Engagement</th><th>Pace</th><th>Fraud</th><th>Last Interaction</th><th>Actions</th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let behavior of filteredBehaviors">
            <td>User #{{ behavior.userId }}</td>
            <td>{{ behavior.focusScorePct | number:'1.0-0' }}%</td>
            <td>{{ behavior.agitationLevelPct | number:'1.0-0' }}%</td>
            <td>{{ behavior.engagementIndexPct | number:'1.0-0' }}%</td>
            <td>{{ behavior.learningPacePercentile | number:'1.0-0' }}%</td>
            <td>{{ behavior.fraudProbabilityScore | number:'1.0-0' }}%</td>
            <td>{{ behavior.lastInteraction | date:'medium' }}</td>
            <td class="actions">
              <button (click)="viewDetail(behavior.behaviorId)">View</button>
              <button (click)="editBehavior(behavior.behaviorId)">Edit</button>
              <button class="delete" (click)="deleteBehavior(behavior.behaviorId)">Delete</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  `,
  styles: [`
    .toolbar { display: flex; justify-content: space-between; gap: 12px; margin-bottom: 20px; flex-wrap: wrap; }
    .search-input { flex: 1; min-width: 220px; padding: 10px 14px; border: 1px solid #e2e8f0; border-radius: 8px; }
    .primary-btn { padding: 10px 18px; border: none; border-radius: 8px; background: #667eea; color: white; cursor: pointer; }
    .behavior-table { width: 100%; border-collapse: collapse; }
    .behavior-table th, .behavior-table td { padding: 14px; border-bottom: 1px solid #e2e8f0; text-align: left; }
    .behavior-table th { background: #f7fafc; color: #4a5568; font-weight: 600; }
    .actions button { margin-right: 6px; padding: 6px 10px; border: none; border-radius: 6px; background: #edf2f7; cursor: pointer; }
    .actions .delete:hover { background: #fc8181; color: white; }
  `]
})
export class BehaviorListComponent implements OnInit {
  behaviors: Behavior[] = [];
  searchTerm = '';

  constructor(
    private personalityService: PersonalityService,
    public router: Router
  ) {}

  ngOnInit(): void {
    this.loadBehaviors();
  }

  get filteredBehaviors(): Behavior[] {
    return this.behaviors.filter((behavior) =>
      !this.searchTerm || behavior.userId.toString().includes(this.searchTerm.trim())
    );
  }

  loadBehaviors(): void {
    this.personalityService.getAllBehaviors().subscribe({
      next: (behaviors) => { this.behaviors = behaviors; },
      error: (err) => console.error('Failed to load behaviors', err)
    });
  }

  viewDetail(id: string): void {
    this.router.navigate(['/dashboard/personality/behaviors/detail', id]);
  }

  editBehavior(id: string): void {
    this.router.navigate(['/dashboard/personality/behaviors/edit', id]);
  }

  deleteBehavior(id: string): void {
    if (confirm('Delete this behavior profile?')) {
      this.personalityService.deleteBehavior(id).subscribe({
        next: () => this.loadBehaviors(),
        error: (err) => console.error('Failed to delete behavior', err)
      });
    }
  }
}
