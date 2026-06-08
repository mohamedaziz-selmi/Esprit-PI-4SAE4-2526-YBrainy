import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { Course, Lesson } from '../models/course.models';

const STORAGE_KEY = 'ybrainy.staticCourses.v2';

function uuid(): string {
  // Good-enough stable id for UI-only CRUD
  return Math.random().toString(16).slice(2) + Date.now().toString(16);
}

function nowIso(): string {
  return new Date().toISOString();
}

function seedCourses(): Course[] {
  return [
    {
      id: uuid(),
      title: 'Cybersecurity Fundamentals',
      category: 'Cybersecurity',
      description: 'Understand threats, encryption, network security and ethical hacking basics.',
      imageUrl: 'assets/frontoffice/www.ciklum.com/wp-content/uploads/2025/10/all.png',
      rating: 4.9,
      studentsCount: 980,
      thumbnailVideoPath: 'assets/frontoffice/www.ciklum.com/wp-content/uploads/2025/10/industry-travel.mp4',
      createdAt: nowIso(),
      lessons: [
        {
          id: uuid(),
          title: 'Security Basics',
          description: 'CIA triad, common attack vectors, and threat modeling.',
          videoUrl: 'assets/frontoffice/www.ciklum.com/wp-content/uploads/2025/10/industry-travel.mp4',
          durationMinutes: 22,
        },
        {
          id: uuid(),
          title: 'Passwords & Hashing',
          description: 'Hashing vs encryption, salts, and best practices.',
          videoUrl: 'assets/frontoffice/www.ciklum.com/wp-content/uploads/2025/10/industry-travel.mp4',
          durationMinutes: 19,
        },
      ],
    },
    {
      id: uuid(),
      title: 'UI/UX Design Essentials',
      category: 'Design',
      description: 'Learn user-centered design principles, wireframing, prototyping and usability testing.',
      imageUrl: 'assets/frontoffice/www.ciklum.com/wp-content/uploads/2025/10/CUATOMER-EXP.png',
      rating: 4.5,
      studentsCount: 415,
      thumbnailVideoPath: 'assets/frontoffice/www.ciklum.com/wp-content/uploads/2025/10/industry-travel.mp4',
      createdAt: nowIso(),
      lessons: [
        {
          id: uuid(),
          title: 'Design Foundations',
          description: 'Layout, typography, color systems and accessibility.',
          videoUrl: 'assets/frontoffice/www.ciklum.com/wp-content/uploads/2025/10/industry-travel.mp4',
          durationMinutes: 17,
        },
      ],
    },
    {
      id: uuid(),
      title: 'Mobile App Development with Flutter',
      category: 'Mobile Development',
      description: 'Build beautiful, natively compiled mobile apps for iOS and Android from a single codebase.',
      imageUrl: 'assets/frontoffice/www.ciklum.com/wp-content/uploads/2025/10/agentic.png',
      rating: 4.7,
      studentsCount: 632,
      thumbnailVideoPath: 'assets/frontoffice/www.ciklum.com/wp-content/uploads/2025/10/industry-travel.mp4',
      createdAt: nowIso(),
      lessons: [
        {
          id: uuid(),
          title: 'Dart Crash Course',
          description: 'Variables, functions, classes and null-safety.',
          videoUrl: 'assets/frontoffice/www.ciklum.com/wp-content/uploads/2025/10/industry-travel.mp4',
          durationMinutes: 20,
        },
      ],
    },
    {
      id: uuid(),
      title: 'Data Science with Python',
      category: 'Data Science',
      description: 'From Pandas to Machine Learning — analyze data, build models and visualize insights.',
      imageUrl: 'assets/frontoffice/www.ciklum.com/wp-content/uploads/2025/10/all.png',
      rating: 4.6,
      studentsCount: 870,
      thumbnailVideoPath: 'assets/frontoffice/www.ciklum.com/wp-content/uploads/2025/10/industry-travel.mp4',
      createdAt: nowIso(),
      lessons: [
        {
          id: uuid(),
          title: 'Pandas Essentials',
          description: 'DataFrames, cleaning and transformations.',
          videoUrl: 'assets/frontoffice/www.ciklum.com/wp-content/uploads/2025/10/industry-travel.mp4',
          durationMinutes: 24,
        },
      ],
    },
    {
      id: uuid(),
      title: 'Calculus I (Foundations)',
      category: 'Mathematics',
      description: 'Limits, derivatives and integrals with practical problem solving.',
      imageUrl: 'assets/frontoffice/www.ciklum.com/wp-content/uploads/2025/10/CUATOMER-EXP.png',
      rating: 4.4,
      studentsCount: 290,
      thumbnailVideoPath: 'assets/frontoffice/www.ciklum.com/wp-content/uploads/2025/10/industry-travel.mp4',
      createdAt: nowIso(),
      lessons: [
        {
          id: uuid(),
          title: 'Limits & Continuity',
          description: 'Core concepts + worked examples.',
          videoUrl: 'assets/frontoffice/www.ciklum.com/wp-content/uploads/2025/10/industry-travel.mp4',
          durationMinutes: 28,
        },
      ],
    },
    {
      id: uuid(),
      title: 'Physics: Mechanics Basics',
      category: 'Science',
      description: 'Motion, forces, energy and momentum explained with real-world examples.',
      imageUrl: 'assets/frontoffice/www.ciklum.com/wp-content/uploads/2025/10/agentic.png',
      rating: 4.3,
      studentsCount: 310,
      thumbnailVideoPath: 'assets/frontoffice/www.ciklum.com/wp-content/uploads/2025/10/industry-travel.mp4',
      createdAt: nowIso(),
      lessons: [
        {
          id: uuid(),
          title: 'Newton’s Laws',
          description: 'Forces, mass, and acceleration with exercises.',
          videoUrl: 'assets/frontoffice/www.ciklum.com/wp-content/uploads/2025/10/industry-travel.mp4',
          durationMinutes: 21,
        },
      ],
    },
  ];
}

@Injectable({ providedIn: 'root' })
export class CourseStoreService {
  private readonly _courses$ = new BehaviorSubject<Course[]>(this.loadInitial());

  readonly courses$ = this._courses$.asObservable();

  get snapshot(): Course[] {
    return this._courses$.value;
  }

  getCourse(courseId: string): Course | undefined {
    return this.snapshot.find((c) => c.id === courseId);
  }

  addCourse(input: Omit<Course, 'id' | 'createdAt' | 'lessons'> & { lessons?: Lesson[] }): Course {
    const course: Course = {
      id: uuid(),
      createdAt: nowIso(),
      lessons: input.lessons ?? [],
      title: input.title,
      category: input.category,
      description: input.description,
      imageUrl: input.imageUrl,
      rating: input.rating,
      studentsCount: input.studentsCount,
      thumbnailVideoPath: input.thumbnailVideoPath ?? input.promoVideoUrl,
      promoVideoUrl: input.promoVideoUrl,
    };
    this.setCourses([course, ...this.snapshot]);
    return course;
  }

  updateCourse(courseId: string, patch: Partial<Omit<Course, 'id' | 'createdAt' | 'lessons'>>): void {
    const next = this.snapshot.map((c) => (c.id === courseId ? { ...c, ...patch } : c));
    this.setCourses(next);
  }

  deleteCourse(courseId: string): void {
    const next = this.snapshot.filter((c) => c.id !== courseId);
    this.setCourses(next);
  }

  addLesson(courseId: string, input: Omit<Lesson, 'id'>): Lesson {
    const lesson: Lesson = { id: uuid(), ...input };
    const next = this.snapshot.map((c) =>
      c.id === courseId ? { ...c, lessons: [lesson, ...c.lessons] } : c
    );
    this.setCourses(next);
    return lesson;
  }

  updateLesson(courseId: string, lessonId: string, patch: Partial<Omit<Lesson, 'id'>>): void {
    const next = this.snapshot.map((c) => {
      if (c.id !== courseId) return c;
      return {
        ...c,
        lessons: c.lessons.map((l) => (l.id === lessonId ? { ...l, ...patch } : l)),
      };
    });
    this.setCourses(next);
  }

  deleteLesson(courseId: string, lessonId: string): void {
    const next = this.snapshot.map((c) => {
      if (c.id !== courseId) return c;
      return { ...c, lessons: c.lessons.filter((l) => l.id !== lessonId) };
    });
    this.setCourses(next);
  }

  private setCourses(courses: Course[]): void {
    this._courses$.next(courses);
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(courses));
    } catch {
      /* ignore */
    }
  }

  private loadInitial(): Course[] {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (!raw) return seedCourses();
      const parsed = JSON.parse(raw) as Course[];
      if (!Array.isArray(parsed)) return seedCourses();
      return parsed;
    } catch {
      return seedCourses();
    }
  }

  /** Resets to the bundled seed data (useful for demos and when localStorage gets into a bad state). */
  resetToSeed(): void {
    this.setCourses(seedCourses());
  }
}


