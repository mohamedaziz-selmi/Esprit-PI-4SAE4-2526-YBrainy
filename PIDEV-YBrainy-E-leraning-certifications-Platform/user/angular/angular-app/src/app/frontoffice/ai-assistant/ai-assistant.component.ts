import { Component, ElementRef, OnDestroy, ViewChild } from '@angular/core';
import { Router } from '@angular/router';
import { Subscription } from 'rxjs';

import { AiGenerationService, AiChatHistoryItem, AiChatThreadSummary } from '../services/ai-generation.service';
import { DraftService } from '../services/draft.service';
import { AiDraftFilesService } from '../services/ai-draft-files.service';
import { AuthService } from '../services/auth.service';

export interface ChatMessage {
  role: 'user' | 'assistant';
  content: string;
  attachedFileNames?: string[];
  relatedThreads?: AiChatThreadSummary[];
  hasDraft?: boolean;
  draftTitle?: string;
  draftBody?: string;
  draftSaved?: boolean;
}

@Component({
  selector: 'app-ai-assistant',
  standalone: false,
  templateUrl: './ai-assistant.component.html',
  styleUrl: './ai-assistant.component.css',
})
export class AiAssistantComponent implements OnDestroy {
  isOpen = false;
  loading = false;
  inputText = '';
  messages: ChatMessage[] = [];

  /** Files staged for the next send */
  pendingFiles: File[] = [];
  /** All files accumulated across the conversation (for the final draft) */
  conversationFiles: File[] = [];

  @ViewChild('messagesEl') messagesEl!: ElementRef<HTMLDivElement>;
  @ViewChild('fileInput') fileInputEl!: ElementRef<HTMLInputElement>;

  private subs = new Subscription();

  constructor(
    private aiService: AiGenerationService,
    private draftService: DraftService,
    private aiDraftFiles: AiDraftFilesService,
    private router: Router,
    public auth: AuthService
  ) {}

  get isLoggedIn(): boolean {
    return this.auth.isLoggedIn();
  }

  toggle(): void {
    this.isOpen = !this.isOpen;
    if (this.isOpen && this.messages.length === 0) {
      this.addWelcome();
    }
  }

  close(): void {
    this.isOpen = false;
  }

  private addWelcome(): void {
    this.messages.push({
      role: 'assistant',
      content: this.isLoggedIn
        ? 'Hi! I\'m yForumy 🤖 your forum AI assistant.\n\nI can:\n• Find existing threads related to your problem\n• Ask targeted questions and generate a precise draft post\n• Attach screenshots or files to your draft via 📎\n\nWhat\'s your problem or question?'
        : 'Hi! I\'m yForumy 🤖 Please sign in to use the AI assistant.',
    });
  }

  // ── File handling ─────────────────────────────────────────

  openFilePicker(): void {
    this.fileInputEl?.nativeElement.click();
  }

  onFilesPicked(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (!input.files?.length) return;
    this.pendingFiles = [...this.pendingFiles, ...Array.from(input.files)];
    input.value = '';
  }

  removePendingFile(index: number): void {
    this.pendingFiles = this.pendingFiles.filter((_, i) => i !== index);
  }

  fileIcon(type: string): string {
    if (type.startsWith('image/')) return '🖼️';
    if (type === 'application/pdf') return '📄';
    if (type.startsWith('video/')) return '🎥';
    return '📎';
  }

  formatSize(bytes: number): string {
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return Math.round(bytes / 1024) + ' KB';
    return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
  }

  // ── Sending ───────────────────────────────────────────────

  send(): void {
    const text = this.inputText.trim();
    if ((!text && this.pendingFiles.length === 0) || this.loading) return;
    if (!this.isLoggedIn) return;

    const filesToSend = [...this.pendingFiles];
    this.pendingFiles = [];
    this.inputText = '';

    // Accumulate files for the final draft
    this.conversationFiles = [...this.conversationFiles, ...filesToSend];

    // Append file names to the message content so AI knows about attachments
    let content = text;
    if (filesToSend.length) {
      content += (text ? '\n' : '') + '[Attached files: ' + filesToSend.map(f => f.name).join(', ') + ']';
    }

    this.messages.push({
      role: 'user',
      content,
      attachedFileNames: filesToSend.length ? filesToSend.map(f => f.name) : undefined,
    });
    this.loading = true;
    this.scrollToBottom();

    // Build history (exclude welcome + current user message)
    const history: AiChatHistoryItem[] = this.messages
      .slice(1, -1)
      .map((m) => ({ role: m.role, content: m.content }));

    this.subs.add(
      this.aiService.chat(content, history).subscribe({
        next: (res) => {
          this.loading = false;
          this.messages.push({
            role: 'assistant',
            content: res.reply,
            relatedThreads: res.relatedThreads?.length ? res.relatedThreads : undefined,
            hasDraft: res.hasDraft,
            draftTitle: res.draftTitle ?? undefined,
            draftBody: res.draftBody ?? undefined,
          });
          this.scrollToBottom();
        },
        error: () => {
          this.loading = false;
          this.messages.push({
            role: 'assistant',
            content: 'Sorry, something went wrong. Please try again.',
          });
          this.scrollToBottom();
        },
      })
    );
  }

  onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.send();
    }
  }

  // ── Draft saving ─────────────────────────────────────────

  saveDraft(msg: ChatMessage): void {
    if (!msg.draftTitle && !msg.draftBody) return;

    const attachments = this.conversationFiles.map(f => ({
      name: f.name,
      type: f.type,
      size: f.size,
    }));

    const saved = this.draftService.save({
      title: msg.draftTitle ?? '',
      body: msg.draftBody ?? '',
      categoryId: null,
      attachments: attachments.length ? attachments : undefined,
    });

    if (this.conversationFiles.length) {
      this.aiDraftFiles.set(saved.id, this.conversationFiles);
    }

    msg.draftSaved = true;

    // Navigate to create-thread to let user review + publish
    setTimeout(() => {
      this.isOpen = false;
      this.router.navigate(['/forum/create'], { queryParams: { draftId: saved.id } });
    }, 700);
  }

  // ── Navigation ────────────────────────────────────────────

  goToThread(threadId: number): void {
    this.router.navigate(['/forum', threadId]);
    this.isOpen = false;
  }

  goToDrafts(): void {
    this.router.navigate(['/forum/drafts']);
    this.isOpen = false;
  }

  clearChat(): void {
    this.messages = [];
    this.pendingFiles = [];
    this.conversationFiles = [];
    this.addWelcome();
  }

  private scrollToBottom(): void {
    setTimeout(() => {
      if (this.messagesEl?.nativeElement) {
        this.messagesEl.nativeElement.scrollTop = this.messagesEl.nativeElement.scrollHeight;
      }
    }, 50);
  }

  ngOnDestroy(): void {
    this.subs.unsubscribe();
  }
}
