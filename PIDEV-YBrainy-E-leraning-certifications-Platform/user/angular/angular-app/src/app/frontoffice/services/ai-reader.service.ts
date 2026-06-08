import { Injectable } from '@angular/core';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { AiReaderContext } from '../models/course.models';
import { CourseApiService } from './course-api.service';

export interface AiReaderFrame {
  context: AiReaderContext;
  url: SafeResourceUrl;
}

@Injectable({ providedIn: 'root' })
export class AiReaderService {
  private readonly maxReaderTextLength = 560;

  constructor(
    private api: CourseApiService,
    private sanitizer: DomSanitizer
  ) {}

  getCourseReaderFrame(courseId: number): Observable<AiReaderFrame> {
    return this.api.getCourseReaderContext(courseId).pipe(
      map((context) => this.toReaderFrame(context))
    );
  }

  getLessonReaderFrame(courseId: number, lessonId: number): Observable<AiReaderFrame> {
    return this.api.getLessonReaderContext(courseId, lessonId).pipe(
      map((context) => this.toReaderFrame(context))
    );
  }

  private toReaderFrame(context: AiReaderContext): AiReaderFrame {
    const baseUrl = this.normalizeBaseUrl(context.talkingHeadBaseUrl);
    const params = new URLSearchParams();
    params.set('title', context.title || 'Avatar reader');
    params.set('text', this.clipText(context.text || context.title || ''));
    if (context.speakerWavPath) {
      params.set('speaker_wav_path', context.speakerWavPath);
    }

    return {
      context,
      url: this.sanitizer.bypassSecurityTrustResourceUrl(`${baseUrl}/?${params.toString()}`),
    };
  }

  private normalizeBaseUrl(value: string | null | undefined): string {
    const raw = String(value || '').trim() || 'http://localhost:8765';
    return raw.replace(/\/+$/, '');
  }

  private clipText(value: string): string {
    const normalized = String(value || '').replace(/\s+/g, ' ').trim();
    if (normalized.length <= this.maxReaderTextLength) {
      return normalized;
    }
    return `${normalized.slice(0, this.maxReaderTextLength - 1).trim()}...`;
  }
}
