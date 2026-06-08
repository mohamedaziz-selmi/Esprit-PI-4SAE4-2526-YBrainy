import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { PersonalityService } from '../../../services/personality.service';
import { Personality } from '../../../models/personality.model';

@Component({
  selector: 'app-personality-list',
  standalone: true,
  imports: [CommonModule, RouterModule, FormsModule],
  template: `
    <div class="personality-list">
      <div class="stats-grid">
        <div class="stat-card">
          <h3>{{ personalities.length }}</h3>
          <p>Total Profiles</p>
        </div>
        <div class="stat-card visual">
          <h3>{{ visualCount }}</h3>
          <p>Visual Learners</p>
        </div>
        <div class="stat-card auditory">
          <h3>{{ auditoryCount }}</h3>
          <p>Auditory Learners</p>
        </div>
        <div class="stat-card kinesthetic">
          <h3>{{ kinestheticCount }}</h3>
          <p>Kinesthetic Learners</p>
        </div>
        <div class="stat-card alert">
          <h3>{{ watchCount }}</h3>
          <p>Needs Review</p>
        </div>
      </div>

      <div class="filters">
        <input
          type="text"
          [(ngModel)]="searchTerm"
          placeholder="Search by user id..."
          class="search-input"
        />
        <select [(ngModel)]="styleFilter" class="filter-select">
          <option value="">All Learning Styles</option>
          <option value="VISUAL">Visual</option>
          <option value="AUDITORY">Auditory</option>
          <option value="KINESTHETIC">Kinesthetic</option>
        </select>
        <button class="refresh-btn" (click)="loadPersonalities()">Refresh</button>
      </div>

      <table class="personality-table">
        <thead>
          <tr>
            <th>User</th>
            <th>Learning Style</th>
            <th>Career Alignment</th>
            <th>Cognitive Load</th>
            <th>Status</th>
            <th>Actions</th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let p of filteredPersonalities" [class.at-risk]="isWatchlist(p)">
            <td>
              <div class="student-info">
                <span class="avatar">User</span>
                <span>User #{{ p.userId }}</span>
              </div>
            </td>
            <td>
              <span class="style-badge" [class]="getDominantStyle(p)">
                {{ getDominantStyle(p) }}
              </span>
            </td>
            <td>
              <div class="progress-bar">
                <div class="progress" [style.width.%]="p.careerAlignmentScore"></div>
                <span>{{ p.careerAlignmentScore | number:'1.0-0' }}%</span>
              </div>
            </td>
            <td>{{ p.cognitiveLoadTolerance | number:'1.0-0' }}%</td>
            <td>
              <span class="status-badge" [class]="getStatus(p)">
                {{ getStatus(p) }}
              </span>
            </td>
            <td class="actions">
              <button (click)="viewDetail(p.personalityId)" title="View">View</button>
              <button (click)="editPersonality(p.personalityId)" title="Edit">Edit</button>
              <button (click)="deletePersonality(p.personalityId)" title="Delete" class="delete">Delete</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  `,
  styles: [`
    .stats-grid {
      display: grid;
      grid-template-columns: repeat(5, 1fr);
      gap: 15px;
      margin-bottom: 25px;
    }
    .stat-card {
      background: white;
      padding: 20px;
      border-radius: 12px;
      text-align: center;
      box-shadow: 0 2px 8px rgba(0,0,0,0.08);
    }
    .stat-card h3 {
      font-size: 2rem;
      margin: 0;
      color: #667eea;
    }
    .stat-card p {
      margin: 5px 0 0;
      color: #718096;
    }
    .stat-card.visual h3 { color: #e53e3e; }
    .stat-card.auditory h3 { color: #38a169; }
    .stat-card.kinesthetic h3 { color: #3182ce; }
    .stat-card.alert h3 { color: #dd6b20; }

    .filters {
      display: flex;
      gap: 15px;
      margin-bottom: 20px;
    }
    .search-input, .filter-select {
      padding: 10px 15px;
      border: 1px solid #e2e8f0;
      border-radius: 8px;
      font-size: 14px;
    }
    .search-input { flex: 1; }
    .refresh-btn {
      padding: 10px 20px;
      background: #667eea;
      color: white;
      border: none;
      border-radius: 8px;
      cursor: pointer;
    }

    .personality-table {
      width: 100%;
      border-collapse: collapse;
    }
    .personality-table th {
      text-align: left;
      padding: 15px;
      background: #f7fafc;
      font-weight: 600;
      color: #4a5568;
    }
    .personality-table td {
      padding: 15px;
      border-bottom: 1px solid #e2e8f0;
    }
    .personality-table tr:hover {
      background: #f7fafc;
    }
    .personality-table tr.at-risk {
      background: #fff5f5;
    }

    .student-info {
      display: flex;
      align-items: center;
      gap: 10px;
    }
    .avatar {
      font-size: 0.85rem;
      font-weight: 600;
      background: #edf2f7;
      border-radius: 999px;
      padding: 6px 10px;
    }

    .style-badge {
      padding: 5px 12px;
      border-radius: 20px;
      font-size: 12px;
      font-weight: 500;
      text-transform: uppercase;
    }
    .style-badge.VISUAL { background: #fed7d7; color: #c53030; }
    .style-badge.AUDITORY { background: #c6f6d5; color: #276749; }
    .style-badge.KINESTHETIC { background: #bee3f8; color: #2c5282; }

    .progress-bar {
      display: flex;
      align-items: center;
      gap: 10px;
    }
    .progress-bar .progress {
      height: 8px;
      background: #667eea;
      border-radius: 4px;
      transition: width 0.3s;
      min-width: 24px;
    }
    .progress-bar span {
      font-size: 12px;
      color: #718096;
      min-width: 40px;
    }

    .status-badge {
      padding: 5px 12px;
      border-radius: 20px;
      font-size: 12px;
    }
    .status-badge.GOOD { background: #c6f6d5; color: #276749; }
    .status-badge.WARNING { background: #fef5e7; color: #c05621; }
    .status-badge.CRITICAL { background: #fed7d7; color: #c53030; }

    .actions button {
      padding: 5px 10px;
      margin-right: 5px;
      border: none;
      border-radius: 5px;
      cursor: pointer;
      background: #edf2f7;
    }
    .actions button:hover { background: #e2e8f0; }
    .actions button.delete:hover { background: #fc8181; color: white; }
  `]
})
export class PersonalityListComponent implements OnInit {
  personalities: Personality[] = [];
  searchTerm = '';
  styleFilter = '';
  loading = false;
  error = '';

  constructor(
    private personalityService: PersonalityService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.loadPersonalities();
  }

  loadPersonalities(): void {
    this.loading = true;
    this.personalityService.getAllPersonalities().subscribe({
      next: (data) => {
        this.personalities = data;
        this.loading = false;
      },
      error: (err) => {
        this.error = 'Failed to load personalities';
        this.loading = false;
        console.error(err);
      }
    });
  }

  get filteredPersonalities(): Personality[] {
    return this.personalities.filter((p) => {
      const matchesSearch = !this.searchTerm || p.userId.toString().includes(this.searchTerm.trim());
      const matchesStyle = !this.styleFilter || this.getDominantStyle(p) === this.styleFilter;
      return matchesSearch && matchesStyle;
    });
  }

  get visualCount(): number {
    return this.personalities.filter((p) => this.getDominantStyle(p) === 'VISUAL').length;
  }

  get auditoryCount(): number {
    return this.personalities.filter((p) => this.getDominantStyle(p) === 'AUDITORY').length;
  }

  get kinestheticCount(): number {
    return this.personalities.filter((p) => this.getDominantStyle(p) === 'KINESTHETIC').length;
  }

  get watchCount(): number {
    return this.personalities.filter((p) => this.isWatchlist(p)).length;
  }

  getDominantStyle(p: Personality): 'VISUAL' | 'AUDITORY' | 'KINESTHETIC' {
    const max = Math.max(p.visualLearningPct, p.auditoryLearningPct, p.kinestheticLearningPct);
    if (max === p.visualLearningPct) return 'VISUAL';
    if (max === p.auditoryLearningPct) return 'AUDITORY';
    return 'KINESTHETIC';
  }

  getStatus(p: Personality): 'GOOD' | 'WARNING' | 'CRITICAL' {
    if (p.careerAlignmentScore < 35 || p.cognitiveLoadTolerance < 25) return 'CRITICAL';
    if (p.careerAlignmentScore < 55 || p.cognitiveLoadTolerance < 45) return 'WARNING';
    return 'GOOD';
  }

  isWatchlist(p: Personality): boolean {
    return this.getStatus(p) !== 'GOOD';
  }

  viewDetail(id: string): void {
    this.router.navigate(['/dashboard/personality/detail', id]);
  }

  editPersonality(id: string): void {
    this.router.navigate(['/dashboard/personality/edit', id]);
  }

  deletePersonality(id: string): void {
    if (confirm('Are you sure you want to delete this personality profile?')) {
      this.personalityService.deletePersonality(id).subscribe({
        next: () => this.loadPersonalities(),
        error: (err) => console.error('Delete failed', err)
      });
    }
  }
}
