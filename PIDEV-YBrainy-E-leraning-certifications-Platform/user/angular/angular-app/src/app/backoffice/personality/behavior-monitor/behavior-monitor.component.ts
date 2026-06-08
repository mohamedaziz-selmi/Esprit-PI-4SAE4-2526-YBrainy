import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { Subject, interval } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { Behavior } from '../../../models/personality.model';
import { PersonalityService } from '../../../services/personality.service';

type BehaviorMetric = 'agitationLevelPct' | 'focusScorePct' | 'engagementIndexPct';

@Component({
  selector: 'app-behavior-monitor',
  standalone: true,
  imports: [CommonModule, RouterModule, FormsModule],
  template: `
    <div class="behavior-monitor">
      <div class="monitor-header">
        <h2>Live Behavior Monitor</h2>
        <div class="controls">
          <label class="toggle">
            <input type="checkbox" [(ngModel)]="autoRefresh" (change)="resetRefreshCycle()">
            <span>Auto-refresh (5s)</span>
          </label>
          <button class="refresh-btn" (click)="loadData()">Refresh Now</button>
        </div>
      </div>

      <div class="alert-banner" *ngIf="criticalAlerts.length > 0">
        <div class="alert-item critical" *ngFor="let alert of criticalAlerts">
          <span class="alert-text">User #{{ alert.userId }}: {{ alert.message }}</span>
          <button class="alert-action" (click)="viewBehaviorByUser(alert.userId)">Open</button>
        </div>
      </div>

      <div class="monitor-stats">
        <div class="stat online"><span class="stat-value">{{ onlineCount }}</span><span class="stat-label">Online Now</span></div>
        <div class="stat focused"><span class="stat-value">{{ focusedCount }}</span><span class="stat-label">High Focus</span></div>
        <div class="stat at-risk"><span class="stat-value">{{ atRiskCount }}</span><span class="stat-label">At Risk</span></div>
        <div class="stat fraud"><span class="stat-value">{{ fraudAlerts }}</span><span class="stat-label">Fraud Alerts</span></div>
      </div>

      <div class="monitor-table-container">
        <table class="monitor-table">
          <thead>
            <tr>
              <th>User</th><th>Status</th><th>Focus</th><th>Agitation</th><th>Engagement</th><th>Fraud Risk</th><th>Last Seen</th><th>Actions</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let behavior of behaviors" [class.critical]="isCritical(behavior)" [class.warning]="isWarning(behavior)" [class.online]="isOnline(behavior)">
              <td class="student-cell">
                <div class="student-info">
                  <span class="status-dot" [class.online]="isOnline(behavior)"></span>
                  <span class="student-id">User #{{ behavior.userId }}</span>
                </div>
              </td>
              <td><span class="status-badge" [class]="getStatus(behavior)">{{ getStatus(behavior) }}</span></td>
              <td>
                <div class="mini-bar"><div class="fill good" [style.width.%]="getMetricValue(behavior, 'focusScorePct')"></div></div>
                <span class="mini-value">{{ getMetricValue(behavior, 'focusScorePct') | number:'1.0-0' }}%</span>
              </td>
              <td>
                <div class="mini-bar">
                  <div class="fill"
                    [class.good]="getMetricValue(behavior, 'agitationLevelPct') < 30"
                    [class.warning]="getMetricValue(behavior, 'agitationLevelPct') >= 30 && getMetricValue(behavior, 'agitationLevelPct') < 70"
                    [class.critical]="getMetricValue(behavior, 'agitationLevelPct') >= 70"
                    [style.width.%]="getMetricValue(behavior, 'agitationLevelPct')"></div>
                </div>
                <span class="mini-value">{{ getMetricValue(behavior, 'agitationLevelPct') | number:'1.0-0' }}%</span>
              </td>
              <td>
                <div class="mini-bar"><div class="fill good" [style.width.%]="getMetricValue(behavior, 'engagementIndexPct')"></div></div>
                <span class="mini-value">{{ getMetricValue(behavior, 'engagementIndexPct') | number:'1.0-0' }}%</span>
              </td>
              <td><span class="risk-badge" [class]="getFraudRisk(behavior)">{{ getFraudRisk(behavior) }}</span></td>
              <td class="last-seen">{{ getLastInteraction(behavior) | date:'HH:mm:ss' }}</td>
              <td class="actions">
                <button (click)="viewBehavior(behavior.behaviorId)">View</button>
                <button (click)="editBehavior(behavior.behaviorId)">Edit</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="monitor-legend">
        <h4>Status Legend</h4>
        <div class="legend-items">
          <span class="legend-item"><span class="dot good"></span> Good</span>
          <span class="legend-item"><span class="dot warning"></span> Warning</span>
          <span class="legend-item"><span class="dot critical"></span> Critical</span>
          <span class="legend-item"><span class="dot online"></span> Online</span>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .behavior-monitor { padding: 20px; }
    .monitor-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 25px; gap: 16px; flex-wrap: wrap; }
    .monitor-header h2 { margin: 0; color: #2d3748; }
    .controls { display: flex; gap: 15px; align-items: center; flex-wrap: wrap; }
    .toggle { display: flex; align-items: center; gap: 8px; cursor: pointer; }
    .refresh-btn { padding: 10px 20px; background: #667eea; color: white; border: none; border-radius: 8px; cursor: pointer; }
    .alert-banner { margin-bottom: 20px; }
    .alert-item { display: flex; align-items: center; gap: 15px; padding: 15px 20px; border-radius: 8px; margin-bottom: 10px; }
    .alert-item.critical { background: #fed7d7; border-left: 4px solid #e53e3e; }
    .alert-text { flex: 1; color: #742a2a; font-weight: 500; }
    .alert-action { padding: 8px 16px; background: #e53e3e; color: white; border: none; border-radius: 6px; cursor: pointer; }
    .monitor-stats { display: grid; grid-template-columns: repeat(4, 1fr); gap: 15px; margin-bottom: 25px; }
    .stat { background: white; padding: 20px; border-radius: 12px; text-align: center; box-shadow: 0 2px 8px rgba(0,0,0,0.08); }
    .stat-value { display: block; font-size: 2.5rem; font-weight: bold; color: #2d3748; }
    .stat.online .stat-value { color: #48bb78; }
    .stat.focused .stat-value { color: #667eea; }
    .stat.at-risk .stat-value { color: #ed8936; }
    .stat.fraud .stat-value { color: #e53e3e; }
    .stat-label { color: #718096; font-size: 14px; }
    .monitor-table-container { background: white; border-radius: 12px; overflow: hidden; box-shadow: 0 2px 8px rgba(0,0,0,0.08); }
    .monitor-table { width: 100%; border-collapse: collapse; }
    .monitor-table th { text-align: left; padding: 15px; background: #f7fafc; font-weight: 600; color: #4a5568; font-size: 13px; text-transform: uppercase; }
    .monitor-table td { padding: 12px 15px; border-bottom: 1px solid #e2e8f0; }
    .monitor-table tr:hover { background: #f7fafc; }
    .monitor-table tr.critical { background: #fff5f5; }
    .monitor-table tr.warning { background: #fffbeb; }
    .monitor-table tr.online { border-left: 3px solid #48bb78; }
    .student-info { display: flex; align-items: center; gap: 10px; }
    .status-dot { width: 8px; height: 8px; border-radius: 50%; background: #cbd5e0; }
    .status-dot.online { background: #48bb78; box-shadow: 0 0 8px #48bb78; }
    .status-badge { padding: 5px 12px; border-radius: 20px; font-size: 12px; font-weight: 600; }
    .status-badge.GOOD { background: #c6f6d5; color: #276749; }
    .status-badge.WARNING { background: #fef3c7; color: #d97706; }
    .status-badge.CRITICAL { background: #fecaca; color: #dc2626; }
    .mini-bar { width: 60px; height: 6px; background: #e2e8f0; border-radius: 3px; overflow: hidden; display: inline-block; margin-right: 8px; }
    .mini-bar .fill { height: 100%; border-radius: 3px; transition: width 0.3s; }
    .mini-bar .fill.good { background: #48bb78; }
    .mini-bar .fill.warning { background: #ed8936; }
    .mini-bar .fill.critical { background: #f56565; }
    .risk-badge { padding: 4px 10px; border-radius: 12px; font-size: 11px; font-weight: 600; }
    .risk-badge.LOW { background: #c6f6d5; color: #276749; }
    .risk-badge.MEDIUM { background: #fef3c7; color: #d97706; }
    .risk-badge.HIGH { background: #fecaca; color: #dc2626; }
    .actions button { padding: 6px 10px; margin-right: 5px; border: none; border-radius: 5px; cursor: pointer; background: #edf2f7; font-size: 14px; }
    .monitor-legend { margin-top: 20px; padding: 15px; background: white; border-radius: 8px; }
    .legend-items { display: flex; gap: 20px; flex-wrap: wrap; }
    .legend-item { display: flex; align-items: center; gap: 8px; font-size: 13px; color: #718096; }
    .dot { width: 10px; height: 10px; border-radius: 50%; }
    .dot.good { background: #48bb78; }
    .dot.warning { background: #ed8936; }
    .dot.critical { background: #f56565; }
    .dot.online { background: #48bb78; box-shadow: 0 0 5px #48bb78; }
  `]
})
export class BehaviorMonitorComponent implements OnInit, OnDestroy {
  behaviors: Behavior[] = [];
  autoRefresh = true;
  criticalAlerts: { userId: number; message: string }[] = [];

  private destroy$ = new Subject<void>();
  private readonly refreshInterval = 5000;

  constructor(
    private personalityService: PersonalityService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.loadData();
    this.resetRefreshCycle();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  resetRefreshCycle(): void {
    this.destroy$.next();
    if (!this.autoRefresh) return;

    interval(this.refreshInterval)
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => this.loadData());
  }

  loadData(): void {
    this.personalityService.getAllBehaviors().subscribe({
      next: (data) => {
        this.behaviors = data;
        this.updateAlerts();
      },
      error: (err) => console.error('Failed to load behavior data', err)
    });
  }

  updateAlerts(): void {
    this.criticalAlerts = [];
    this.behaviors.forEach((behavior) => {
      if (behavior.agitationLevelPct > 80) {
        this.criticalAlerts.push({ userId: behavior.userId, message: 'High agitation detected.' });
      }
      if (behavior.fraudProbabilityScore > 70) {
        this.criticalAlerts.push({ userId: behavior.userId, message: 'Suspicious activity detected.' });
      }
    });
  }

  get onlineCount(): number {
    return this.behaviors.filter((behavior) => this.isOnline(behavior)).length;
  }

  get focusedCount(): number {
    return this.behaviors.filter((behavior) => behavior.focusScorePct > 70).length;
  }

  get atRiskCount(): number {
    return this.behaviors.filter((behavior) => this.isCritical(behavior)).length;
  }

  get fraudAlerts(): number {
    return this.behaviors.filter((behavior) => this.getFraudRisk(behavior) === 'HIGH').length;
  }

  isOnline(behavior: Behavior): boolean {
    if (!behavior.lastInteraction) return false;
    return Date.now() - new Date(behavior.lastInteraction).getTime() < 5 * 60 * 1000;
  }

  isCritical(behavior: Behavior): boolean {
    return this.getStatus(behavior) === 'CRITICAL';
  }

  isWarning(behavior: Behavior): boolean {
    return this.getStatus(behavior) === 'WARNING';
  }

  getStatus(behavior: Behavior): 'GOOD' | 'WARNING' | 'CRITICAL' {
    if (behavior.agitationLevelPct > 70 || behavior.fraudProbabilityScore > 50) return 'CRITICAL';
    if (behavior.agitationLevelPct > 50 || behavior.focusScorePct < 40) return 'WARNING';
    return 'GOOD';
  }

  getMetricValue(behavior: Behavior, metric: BehaviorMetric): number {
    return behavior[metric];
  }

  getFraudRisk(behavior: Behavior): 'LOW' | 'MEDIUM' | 'HIGH' {
    if (behavior.fraudProbabilityScore < 20) return 'LOW';
    if (behavior.fraudProbabilityScore < 50) return 'MEDIUM';
    return 'HIGH';
  }

  getLastInteraction(behavior: Behavior): Date {
    return behavior.lastInteraction ? new Date(behavior.lastInteraction) : new Date();
  }

  viewBehavior(id: string): void {
    this.router.navigate(['/dashboard/personality/behaviors/detail', id]);
  }

  editBehavior(id: string): void {
    this.router.navigate(['/dashboard/personality/behaviors/edit', id]);
  }

  viewBehaviorByUser(userId: number): void {
    const match = this.behaviors.find((behavior) => behavior.userId === userId);
    if (match) this.viewBehavior(match.behaviorId);
  }
}
