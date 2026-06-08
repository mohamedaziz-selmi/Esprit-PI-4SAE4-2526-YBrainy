import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ThreadInteractionStatus, ThreadResponse, VoteType, ReactionType } from '../models/forum.models';

@Injectable({ providedIn: 'root' })
export class ThreadInteractionService {
  constructor(private http: HttpClient) {}

  vote(threadId: number, userId: number, voteType: VoteType): Observable<ThreadInteractionStatus> {
    return this.http.post<ThreadInteractionStatus>(
      `/api/threads/${threadId}/interactions/vote`,
      { voteType },
      { params: new HttpParams().set('userId', String(userId)) }
    );
  }

  react(threadId: number, userId: number, reactionType: ReactionType): Observable<ThreadInteractionStatus> {
    return this.http.post<ThreadInteractionStatus>(
      `/api/threads/${threadId}/interactions/react`,
      { reactionType },
      { params: new HttpParams().set('userId', String(userId)) }
    );
  }

  toggleWishlist(threadId: number, userId: number): Observable<ThreadInteractionStatus> {
    return this.http.post<ThreadInteractionStatus>(
      `/api/threads/${threadId}/interactions/wishlist`,
      {},
      { params: new HttpParams().set('userId', String(userId)) }
    );
  }

  getStatus(threadId: number, userId: number): Observable<ThreadInteractionStatus> {
    return this.http.get<ThreadInteractionStatus>(
      `/api/threads/${threadId}/interactions/status`,
      { params: new HttpParams().set('userId', String(userId)) }
    );
  }

  getWishlist(userId: number): Observable<ThreadResponse[]> {
    return this.http.get<ThreadResponse[]>(
      `/api/wishlist`,
      { params: new HttpParams().set('userId', String(userId)) }
    );
  }
}
