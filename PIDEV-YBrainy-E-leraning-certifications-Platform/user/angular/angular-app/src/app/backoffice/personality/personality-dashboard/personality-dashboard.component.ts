import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';

@Component({
  selector: 'app-personality-dashboard',
  standalone: true,
  imports: [CommonModule, RouterModule],
  template: `
    <div class="personality-dashboard">
      <nav class="dashboard-nav">
        <h2>Personality & Behavior Management</h2>
        <div class="nav-links">
          <a routerLink="/dashboard/personality/list" routerLinkActive="active">Profiles</a>
          <a routerLink="/dashboard/personality/create" routerLinkActive="active">New Profile</a>
          <a routerLink="/dashboard/personality/behaviors" routerLinkActive="active">Behaviors</a>
          <a routerLink="/dashboard/personality/behaviors/create" routerLinkActive="active">New Behavior</a>
          <a routerLink="/dashboard/personality/behavior-monitor" routerLinkActive="active">Live Monitor</a>
        </div>
      </nav>
      <div class="dashboard-content">
        <router-outlet></router-outlet>
      </div>
    </div>
  `,
  styles: [`
    .personality-dashboard {
      padding: 20px;
      background: #f5f7fa;
      min-height: 100vh;
    }
    .dashboard-nav {
      background: white;
      padding: 20px;
      border-radius: 12px;
      margin-bottom: 20px;
      box-shadow: 0 2px 8px rgba(0,0,0,0.1);
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: 16px;
      flex-wrap: wrap;
    }
    .dashboard-nav h2 {
      margin: 0;
      color: #2d3748;
      font-size: 1.5rem;
    }
    .nav-links {
      display: flex;
      gap: 12px;
      flex-wrap: wrap;
    }
    .nav-links a {
      padding: 10px 16px;
      text-decoration: none;
      color: #4a5568;
      border-radius: 8px;
      transition: all 0.3s;
    }
    .nav-links a:hover {
      background: #e2e8f0;
    }
    .nav-links a.active {
      background: #667eea;
      color: white;
    }
    .dashboard-content {
      background: white;
      border-radius: 12px;
      padding: 20px;
      box-shadow: 0 2px 8px rgba(0,0,0,0.1);
    }
  `]
})
export class PersonalityDashboardComponent {}
