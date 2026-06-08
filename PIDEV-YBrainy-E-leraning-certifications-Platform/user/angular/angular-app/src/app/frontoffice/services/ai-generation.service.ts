import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface AiGenerateResponse {
  body: string;
}

export interface AiChatHistoryItem {
  role: 'user' | 'assistant';
  content: string;
}

export interface AiChatThreadSummary {
  id: number;
  title: string;
}

export interface AiChatApiResponse {
  reply: string;
  relatedThreads: AiChatThreadSummary[];
  hasDraft: boolean;
  draftTitle: string | null;
  draftBody: string | null;
}

export interface ThreadQualityScore {
  clarity: number;
  detail: number;
  usefulness: number;
  overall: number;
  label: string;
  labelColor: string;
  summary: string;
  strengths: string[];
  improvements: string[];
  improvedVersion: string;
}

export interface AiAnalysisResponse {
  score: ThreadQualityScore | null;
  analysisAvailable: boolean;
}

export interface MlPredictResponse {
  label: 'HQ' | 'LQ_CLOSE' | 'LQ_EDIT' | 'UNKNOWN';
  confidence: number;
  color: string;
  message: string;
  probabilities: {
    HQ: number;
    LQ_CLOSE: number;
    LQ_EDIT: number;
  } | null;
  available: boolean;
}

@Injectable({ providedIn: 'root' })
export class AiGenerationService {
  private readonly base = '/api/ai';

  constructor(private http: HttpClient) {}

  /** Generate a well-structured question body for a new thread. */
  generateThreadBody(title: string): Observable<AiGenerateResponse> {
    return this.http.post<AiGenerateResponse>(`${this.base}/generate-thread-body`, { title });
  }

  /** Generate a helpful answer for a post, based on the thread title and body. */
  generatePostBody(threadTitle: string, threadBody: string): Observable<AiGenerateResponse> {
    return this.http.post<AiGenerateResponse>(`${this.base}/generate-post-body`, {
      threadTitle,
      threadBody,
    });
  }

  /** Chat with yForumy AI assistant. */
  chat(message: string, history: AiChatHistoryItem[]): Observable<AiChatApiResponse> {
    return this.http.post<AiChatApiResponse>(`${this.base}/chat`, { message, history });
  }

  analyzeThread(title: string, threadBody: string): Observable<AiAnalysisResponse> {
    return this.http.post<AiAnalysisResponse>(`${this.base}/analyze`, { title, threadBody });
  }

  getThreadScore(threadId: number): Observable<AiAnalysisResponse> {
    return this.http.get<AiAnalysisResponse>(`${this.base}/score/thread/${threadId}`);
  }

  mlPredict(title: string, body: string, tags: string = ''): Observable<MlPredictResponse> {
    return this.http.post<MlPredictResponse>(`${this.base}/ml/predict`, { title, body, tags });
  }

  summarizeThread(threadId: number): Observable<{ summary: string; postCount: string }> {
    return this.http.get<{ summary: string; postCount: string }>(`${this.base}/summarize/${threadId}`);
  }
}
  
