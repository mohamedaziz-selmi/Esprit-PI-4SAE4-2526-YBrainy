import {
  Component, OnInit, OnDestroy, AfterViewInit,
  ViewChild, ElementRef, ChangeDetectorRef
} from '@angular/core';
import { Chart, registerables } from 'chart.js';
import { Subscription } from 'rxjs';
import { filter, take } from 'rxjs/operators';
import { AuthService } from '../../services/auth.service';
import { DashboardService } from '../../services/dashboard.service';
import {
  UserDashboard, PerformanceInsight, DayActivity, XpDataPoint
} from '../../models/forum.models';

Chart.register(...registerables);

@Component({
  selector: 'app-dashboard',
  standalone: false,
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.css',
})
export class DashboardComponent implements OnInit, OnDestroy, AfterViewInit {

  @ViewChild('xpCanvas')       xpCanvas!: ElementRef<HTMLCanvasElement>;
  @ViewChild('donutCanvas')    donutCanvas!: ElementRef<HTMLCanvasElement>;
  @ViewChild('barCanvas')      barCanvas!: ElementRef<HTMLCanvasElement>;
  @ViewChild('mlCanvas')       mlCanvas!: ElementRef<HTMLCanvasElement>;

  data: UserDashboard | null = null;
  loading = true;
  error = false;

  // Animated counter values
  animPosts    = 0;
  animUpvotes  = 0;
  animRatio    = 0;
  animRank     = 0;
  animHqRate   = 0;

  private charts: Chart[] = [];
  private subs = new Subscription();
  private animFrames: number[] = [];

  constructor(
    private auth: AuthService,
    private dashService: DashboardService,
    private cdr: ChangeDetectorRef
  ) {}

  // ── Lifecycle ──────────────────────────────────────────────────────────────

  ngOnInit(): void {
    const uid = this.auth.currentUserId;
    if (uid) {
      this.fetchDashboard(uid);
    } else {
      // Auth refresh is async — wait for it to resolve
      this.subs.add(
        this.auth.currentUser$.pipe(
          filter(user => user !== null),
          take(1)
        ).subscribe(user => {
          if (user?.id) {
            this.fetchDashboard(user.id);
          } else {
            this.error = true;
            this.loading = false;
          }
        })
      );
      // Timeout fallback if auth never resolves
      setTimeout(() => {
        if (this.loading && !this.data) {
          this.error = true;
          this.loading = false;
          this.cdr.detectChanges();
        }
      }, 8000);
    }
  }

  private fetchDashboard(uid: number): void {
    this.subs.add(
      this.dashService.getDashboard(uid).subscribe({
        next: (d) => {
          this.data = d;
          this.loading = false;
          this.cdr.detectChanges();
          this.startCounters();
          this.buildCharts();
        },
        error: () => { this.error = true; this.loading = false; }
      })
    );
  }

  ngAfterViewInit(): void {}

  ngOnDestroy(): void {
    this.subs.unsubscribe();
    this.charts.forEach(c => c.destroy());
    this.animFrames.forEach(id => cancelAnimationFrame(id));
  }

  // ── Counter animations ─────────────────────────────────────────────────────

  private startCounters(): void {
    if (!this.data) return;
    this.animateCount('animPosts',   this.data.totalPostsCreated,    800);
    this.animateCount('animUpvotes', this.data.totalUpvotesReceived, 1000);
    this.animateCount('animRatio',   Math.round(this.positiveRatioPct), 900);
    this.animateCount('animRank',    this.data.rankPosition,          700);
    this.animateCount('animHqRate',  Math.round(this.data.mlHqRate ?? 0), 1100);
  }

  private animateCount(prop: 'animPosts'|'animUpvotes'|'animRatio'|'animRank'|'animHqRate',
                        target: number, duration: number): void {
    const start = performance.now();
    const tick = (now: number) => {
      const progress = Math.min((now - start) / duration, 1);
      const eased = 1 - Math.pow(1 - progress, 3);
      (this as any)[prop] = Math.round(eased * target);
      this.cdr.detectChanges();
      if (progress < 1) {
        const id = requestAnimationFrame(tick);
        this.animFrames.push(id);
      } else {
        (this as any)[prop] = target;
        this.cdr.detectChanges();
      }
    };
    const id = requestAnimationFrame(tick);
    this.animFrames.push(id);
  }

  // ── Charts ─────────────────────────────────────────────────────────────────

  private buildCharts(): void {
    setTimeout(() => {
      this.buildXpChart();
      this.buildDonutChart();
      this.buildBarChart();
      this.buildMlChart();
    }, 50);
  }

  private buildXpChart(): void {
    if (!this.xpCanvas || !this.data?.xpTimeline?.length) return;
    const points: XpDataPoint[] = this.data.xpTimeline;
    const labels = points.map(p => {
      const d = new Date(p.date);
      return d.toLocaleDateString('fr-FR', { day: '2-digit', month: 'short' });
    });
    const values = points.map(p => p.newTotal);

    const ctx = this.xpCanvas.nativeElement.getContext('2d')!;
    const grad = ctx.createLinearGradient(0, 0, 0, 300);
    grad.addColorStop(0, 'rgba(102,126,234,0.45)');
    grad.addColorStop(1, 'rgba(118,75,162,0.02)');

    const chart = new Chart(ctx, {
      type: 'line',
      data: {
        labels,
        datasets: [{
          label: 'XP Total',
          data: values,
          borderColor: '#667eea',
          backgroundColor: grad,
          borderWidth: 2.5,
          pointBackgroundColor: '#764ba2',
          pointRadius: 4,
          pointHoverRadius: 6,
          fill: true,
          tension: 0.4,
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: { display: false },
          tooltip: {
            callbacks: {
              afterLabel: (ctx) => {
                const pt = points[ctx.dataIndex];
                return `+${pt.xpGained} XP  •  ${this.sourceLabel(pt.source)}`;
              }
            }
          }
        },
        scales: {
          x: { grid: { color: 'rgba(255,255,255,0.06)' }, ticks: { color: '#94a3b8', maxTicksLimit: 8 } },
          y: { grid: { color: 'rgba(255,255,255,0.06)' }, ticks: { color: '#94a3b8' } }
        },
        animation: { duration: 900, easing: 'easeInOutQuart' }
      }
    });
    this.charts.push(chart);
  }

  private buildDonutChart(): void {
    if (!this.donutCanvas || !this.data) return;
    const stats = this.data.reactionStats;
    const labels = Object.keys(stats);
    const values = Object.values(stats);
    const colors: Record<string, string> = {
      UPVOTE: '#22c55e', DOWNVOTE: '#ef4444',
      LIKE: '#3b82f6', DISLIKE: '#f97316'
    };
    const bgColors = labels.map(l => colors[l] ?? '#a78bfa');

    const chart = new Chart(this.donutCanvas.nativeElement, {
      type: 'doughnut',
      data: { labels, datasets: [{ data: values, backgroundColor: bgColors, borderWidth: 0, hoverOffset: 8 }] },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        cutout: '68%',
        plugins: {
          legend: { position: 'bottom', labels: { color: '#94a3b8', padding: 14, boxWidth: 12 } },
          tooltip: { callbacks: { label: (c) => ` ${c.label}: ${c.raw}` } }
        },
        animation: { duration: 900, easing: 'easeInOutQuart' }
      }
    });
    this.charts.push(chart);
  }

  private buildBarChart(): void {
    if (!this.barCanvas || !this.data?.weeklyActivity?.length) return;
    const weeks: DayActivity[] = this.data.weeklyActivity;
    const labels = weeks.map(w => w.weekLabel);

    const chart = new Chart(this.barCanvas.nativeElement, {
      type: 'bar',
      data: {
        labels,
        datasets: [
          { label: 'Posts',     data: weeks.map(w => w.postsCount),    backgroundColor: 'rgba(99,102,241,0.75)',  borderRadius: 4 },
          { label: 'Threads',   data: weeks.map(w => w.threadsCount),  backgroundColor: 'rgba(16,185,129,0.75)', borderRadius: 4 },
          { label: 'Commentaires', data: weeks.map(w => w.commentsCount), backgroundColor: 'rgba(249,115,22,0.75)', borderRadius: 4 },
        ]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: { position: 'bottom', labels: { color: '#94a3b8', padding: 14, boxWidth: 12 } }
        },
        scales: {
          x: { grid: { display: false }, ticks: { color: '#94a3b8', maxRotation: 45 } },
          y: { grid: { color: 'rgba(255,255,255,0.06)' }, ticks: { color: '#94a3b8', stepSize: 1 } }
        },
        animation: { duration: 900, easing: 'easeInOutQuart' }
      }
    });
    this.charts.push(chart);
  }

  private buildMlChart(): void {
    if (!this.mlCanvas || !this.data?.mlAvailable || (this.data.mlTotalAnalyzed ?? 0) === 0) return;

    const data = this.data;
    const chart = new Chart(this.mlCanvas.nativeElement, {
      type: 'doughnut',
      data: {
        labels: ['High Quality', 'Needs Editing', 'Likely to Struggle'],
        datasets: [
          {
            data: [data.mlHqCount ?? 0, data.mlLqEditCount ?? 0, data.mlLqCloseCount ?? 0],
            backgroundColor: ['#22c55e', '#f59e0b', '#ef4444'],
            borderWidth: 0,
            hoverOffset: 8,
          },
        ],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        cutout: '68%',
        plugins: {
          legend: {
            position: 'bottom',
            labels: { color: '#94a3b8', padding: 12, boxWidth: 12 },
          },
          tooltip: {
            callbacks: {
              label: (context) => ` ${context.label}: ${context.raw} threads`,
            },
          },
        },
        animation: { duration: 900, easing: 'easeInOutQuart' },
      },
    });
    this.charts.push(chart);
  }

  // ── Computed helpers ───────────────────────────────────────────────────────

  get positiveRatioPct(): number {
    if (!this.data) return 0;
    const total = this.data.totalPositiveReactions + this.data.totalNegativeReactions;
    return total === 0 ? 0 : Math.round(this.data.totalPositiveReactions / total * 100);
  }

  get currentUserId(): number | null {
    return this.auth.currentUserId;
  }

  get mlLabelColor(): string {
    const rate = this.data?.mlHqRate ?? 0;
    if (rate >= 70) return '#22c55e';
    if (rate >= 40) return '#f59e0b';
    return '#ef4444';
  }

  get mlLabel(): string {
    const rate = this.data?.mlHqRate ?? 0;
    if (rate >= 70) return 'Excellent';
    if (rate >= 40) return 'Average';
    return 'Needs work';
  }

  leaderboardInitials(username: string): string {
    return username ? username.substring(0, 2).toUpperCase() : '??';
  }

  leaderboardAvatarColor(rank: number): string {
    const colors = ['#667eea', '#10b981', '#f59e0b', '#ef4444', '#a78bfa'];
    return colors[(rank - 1) % colors.length];
  }

  statusBadge(userVal: number, commVal: number): 'above' | 'below' | 'avg' {
    if (userVal > commVal * 1.1) return 'above';
    if (userVal < commVal * 0.9) return 'below';
    return 'avg';
  }

  insightClass(insight: PerformanceInsight): string {
    return `insight--${insight.type.toLowerCase()}`;
  }

  sourceLabel(src: string): string {
    const map: Record<string, string> = {
      THREAD_CREATED: 'Thread créé', POST_CREATED: 'Post créé',
      POST_DETAILED: 'Post détaillé', COMMENT_CREATED: 'Commentaire',
      UPVOTE_RECEIVED: 'Upvote reçu', BEST_ANSWER: 'Meilleure réponse'
    };
    return map[src] ?? src;
  }
}
