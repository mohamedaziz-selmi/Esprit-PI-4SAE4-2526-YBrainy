import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Subscription } from 'rxjs';
import { Course, Lesson } from '../../models/course.models';
import { CourseStoreService } from '../../services/course-store.service';

@Component({
  selector: 'app-lessons',
  standalone: false,
  templateUrl: './lessons.component.html',
  styleUrls: ['./lessons.component.css'],
  host: { style: 'display:block' },
})
export class LessonsComponent implements OnInit, OnDestroy {
  course: Course | null = null;
  courseId = '';
  editingLessonId: string | null = null;
  showForm = false;

  private readonly sub = new Subscription();

  form!: FormGroup;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private fb: FormBuilder,
    private store: CourseStoreService
  ) {
    this.form = this.fb.group({
      title: ['', [Validators.required, Validators.minLength(3)]],
      description: ['', [Validators.required, Validators.minLength(5)]],
      videoUrl: ['', [Validators.required]],
      durationMinutes: [null as number | null],
    });
  }

  ngOnInit(): void {
    this.sub.add(
      this.route.paramMap.subscribe((pm) => {
        this.courseId = pm.get('courseId') ?? '';
        this.syncCourse();
      })
    );

    this.sub.add(
      this.store.courses$.subscribe(() => {
        this.syncCourse();
      })
    );
  }

  ngOnDestroy(): void {
    this.sub.unsubscribe();
  }

  backToCourses(): void {
    this.router.navigateByUrl('/courses');
  }

  openCreate(): void {
    this.editingLessonId = null;
    this.showForm = true;
    this.form.reset({
      title: '',
      description: '',
      videoUrl: 'assets/frontoffice/www.ciklum.com/wp-content/uploads/2025/10/industry-travel.mp4',
      durationMinutes: null,
    });
  }

  openEdit(lesson: Lesson): void {
    this.editingLessonId = lesson.id;
    this.showForm = true;
    this.form.reset({
      title: lesson.title,
      description: lesson.description,
      videoUrl: lesson.videoUrl,
      durationMinutes: lesson.durationMinutes ?? null,
    });
  }

  cancel(): void {
    this.showForm = false;
    this.editingLessonId = null;
  }

  save(): void {
    if (!this.courseId) return;
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const v = this.form.getRawValue();
    if (this.editingLessonId) {
      this.store.updateLesson(this.courseId, this.editingLessonId, {
        title: v.title!,
        description: v.description!,
        videoUrl: v.videoUrl!,
        durationMinutes: v.durationMinutes ?? undefined,
      });
    } else {
      this.store.addLesson(this.courseId, {
        title: v.title!,
        description: v.description!,
        videoUrl: v.videoUrl!,
        durationMinutes: v.durationMinutes ?? undefined,
      });
    }

    this.cancel();
  }

  remove(lesson: Lesson): void {
    if (!this.courseId) return;
    const ok = confirm(`Delete lesson "${lesson.title}"?`);
    if (!ok) return;
    this.store.deleteLesson(this.courseId, lesson.id);
  }

  private syncCourse(): void {
    if (!this.courseId) {
      this.course = null;
      return;
    }
    this.course = this.store.getCourse(this.courseId) ?? null;
  }
}


