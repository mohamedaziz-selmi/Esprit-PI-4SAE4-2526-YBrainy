import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { LearningPath } from '../../models/course.models';
import { CourseApiService } from '../../services/course-api.service';
import { UserSessionService } from '../../../tracking/user-session.service';

@Component({
  selector: 'app-ai-learning-path-page',
  standalone: false,
  templateUrl: './learning-paths.component.html',
  styleUrls: ['./learning-paths.component.css'],
})
export class AiLearningPathPageComponent implements OnInit {

  goal: string = '';
  generating: boolean = false;
  generatedPath: LearningPath | null = null;
  savedPaths: LearningPath[] = [];
  generateError: string = '';
  saving: boolean = false;
  enrolling: boolean = false;
  showSaved: boolean = false;
  showEnrollSuccess: boolean = false;

  constructor(
    private api: CourseApiService,
    private userSession: UserSessionService,
    private router: Router,
    private route: ActivatedRoute
  ) {}

  ngOnInit(): void {
    this.loadSavedPaths();
    const cached = sessionStorage.getItem('ybrainy_generated_path');
    if (cached) {
      try {
        this.generatedPath = JSON.parse(cached);
        this.goal = this.generatedPath?.goal || '';
      } catch {}
    }
    const enrolled = this.route.snapshot.queryParams['enrolled'];
    const pathId = this.route.snapshot.queryParams['pathId'];
    if (enrolled === 'true' && pathId) {
      this.showEnrollSuccess = true;
      this.loadSavedPaths();
    }
  }

  loadSavedPaths(): void {
    this.api.getSavedLearningPaths((this.userSession.get()?.userId ?? 0))
      .subscribe({ next: (paths) => this.savedPaths = paths, error: () => {} });
  }

  generatePath(): void {
    if (!this.goal.trim()) return;
    this.generating = true;
    this.generatedPath = null;
    this.generateError = '';
    this.api.generateLearningPath((this.userSession.get()?.userId ?? 0), this.goal).subscribe({
      next: (path) => {
        this.generatedPath = path;
        sessionStorage.setItem('ybrainy_generated_path', JSON.stringify(path));
        this.generating = false;
      },
      error: () => {
        this.generateError = 'Could not generate path. Try again.';
        this.generating = false;
      }
    });
  }

  savePath(): void {
    if (!this.generatedPath) return;
    this.saving = true;
    const whyMap: Record<string, string> = {};
    this.generatedPath.courses.forEach(c => {
      if (c.whyIncluded) whyMap[String(c.id)] = c.whyIncluded;
    });
    const whyIncludedJson = JSON.stringify(whyMap);
    this.api.saveLearningPath(this.generatedPath, whyIncludedJson).subscribe({
      next: (saved) => {
        this.generatedPath = saved;
        sessionStorage.setItem('ybrainy_generated_path', JSON.stringify(saved));
        this.savedPaths.unshift(saved);
        this.saving = false;
      },
      error: () => this.saving = false
    });
  }

  enrollInPath(): void {
    if (!this.generatedPath?.id) return;
    this.enrolling = true;
    this.api.enrollInPath(this.generatedPath.id, (this.userSession.get()?.userId ?? 0)).subscribe({
      next: (res) => {
        if (res.checkoutUrl) {
          window.location.href = res.checkoutUrl;
        } else {
          this.router.navigate(['/courses']);
        }
        this.enrolling = false;
      },
      error: () => this.enrolling = false
    });
  }

  clearGeneratedPath(): void {
    this.generatedPath = null;
    this.goal = '';
    sessionStorage.removeItem('ybrainy_generated_path');
  }

  getTotalFreeCount(): number {
    return this.generatedPath?.courses
      .filter(c => !c.price || c.price === 0).length || 0;
  }

  getTotalPaidCount(): number {
    return this.generatedPath?.courses
      .filter(c => c.price && c.price > 0).length || 0;
  }

  useExample(example: string): void {
    this.goal = example;
    // Scroll to top so user sees the textarea filled
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }
}
