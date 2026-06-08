import { Injectable, OnDestroy } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, Subject } from 'rxjs';
import { Client, IMessage } from '@stomp/stompjs';
import { environment } from '@env/environment';

import SockJS from 'sockjs-client';

// ── DTOs ─────────────────────────────────────────────────────────────────────

export interface MessageResponse {
  id: number;
  senderId: number;
  senderUsername: string;
  senderRole: string;
  senderLevel: number;
  senderLevelTitle: string;
  receiverId: number;
  receiverUsername: string;
  content: string | null;
  mediaUrl: string | null;
  mediaType: string | null;
  read: boolean;
  createdAt: string;
}

export interface ConversationPreview {
  userId: number;
  username: string;
  role: string;
  level: number;
  levelTitle: string;
  lastMessage: string;
  lastMessageAt: string;
  unreadCount: number;
  sentByMe: boolean;
}

export interface MessageRequest {
  senderId: number;
  receiverId: number;
  content?: string;
  mediaUrl?: string;
  mediaType?: string;
}

export interface UserSummary {
  id: number;
  username: string;
  role: string;
  level: number;
  levelTitle: string;
}

export interface UploadResult {
  url: string;
  type: string;
  name: string;
}

// ── Service ───────────────────────────────────────────────────────────────────

@Injectable({ providedIn: 'root' })
export class MessageApiService implements OnDestroy {
  private readonly base = '/api/messages';
  private stompClient: Client | null = null;
  private readonly incomingMsg$ = new Subject<MessageResponse>();

  constructor(private http: HttpClient) {}

  // ── REST ──────────────────────────────────────────────────────────────────

  send(request: MessageRequest): Observable<MessageResponse> {
    return this.http.post<MessageResponse>(this.base, request);
  }

  getConversation(userId1: number, userId2: number): Observable<MessageResponse[]> {
    return this.http.get<MessageResponse[]>(`${this.base}/conversation/${userId1}/${userId2}`);
  }

  getInbox(userId: number): Observable<ConversationPreview[]> {
    return this.http.get<ConversationPreview[]>(`${this.base}/inbox/${userId}`);
  }

  getUnreadCount(userId: number): Observable<{ count: number }> {
    return this.http.get<{ count: number }>(`${this.base}/unread/${userId}`);
  }

  markAsRead(messageId: number): Observable<MessageResponse> {
    return this.http.put<MessageResponse>(`${this.base}/read/${messageId}`, {});
  }

  markConversationAsRead(senderId: number, receiverId: number): Observable<void> {
    return this.http.put<void>(`${this.base}/conversation/read/${senderId}/${receiverId}`, {});
  }

  getAllUsers(): Observable<UserSummary[]> {
    return this.http.get<UserSummary[]>(`${this.base}/users`);
  }

  uploadMedia(file: File): Observable<UploadResult> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<UploadResult>(`${this.base}/upload`, form);
  }

  // ── WebSocket ─────────────────────────────────────────────────────────────

  /** Connect to WebSocket and return an Observable of incoming messages for this user */
  connectWebSocket(userId: number): Observable<MessageResponse> {
    if (this.stompClient?.active) {
      return this.incomingMsg$.asObservable();
    }

    this.stompClient = new Client({
      // Connect directly to backend port to bypass Angular proxy (SockJS needs full URL)
      webSocketFactory: () => new SockJS(`${environment.forumWsUrl}/ws`) as WebSocket,
      reconnectDelay: 5000,
      onConnect: () => {
        this.stompClient!.subscribe(
          `/topic/messages/user-${userId}`,
          (frame: IMessage) => {
            try {
              const msg: MessageResponse = JSON.parse(frame.body);
              this.incomingMsg$.next(msg);
            } catch (_) {}
          }
        );
      },
    });

    this.stompClient.activate();
    return this.incomingMsg$.asObservable();
  }

  disconnectWebSocket(): void {
    if (this.stompClient?.active) {
      this.stompClient.deactivate();
    }
    this.stompClient = null;
  }

  ngOnDestroy(): void {
    this.disconnectWebSocket();
  }
}
