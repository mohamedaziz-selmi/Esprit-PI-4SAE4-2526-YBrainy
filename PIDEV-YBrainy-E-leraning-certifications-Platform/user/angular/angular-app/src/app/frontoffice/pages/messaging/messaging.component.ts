import {
  Component, ElementRef, HostListener, OnDestroy, OnInit, ViewChild
} from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Subscription } from 'rxjs';
import {
  MessageApiService,
  MessageResponse,
  ConversationPreview,
  UserSummary,
  UploadResult,
} from '../../services/message-api.service';
import { AuthService } from '../../services/auth.service';
import { environment } from '@env/environment';

const LEVEL_TITLES = ['', 'Newcomer', 'Apprentice', 'Explorer', 'Contributor', 'Expert', 'Senior Expert', 'Master', 'Legend'];
const AVATAR_COLORS = ['#6366f1','#8b5cf6','#ec4899','#f59e0b','#10b981','#3b82f6','#ef4444','#14b8a6'];

export const EMOJIS = [
  '😀','😂','😍','😎','😭','😡','😱','🤔','😊','🥺',
  '👍','👎','👋','🤝','🙏','💪','❤️','💯','🔥','✨',
  '🎉','🎊','🏆','🎓','⭐','💡','🔧','💻','📱','📚',
  '✅','❌','⚠️','💬','📝','🔔','📎','🖼️','🌈','⚡',
  '😴','🤣','😇','🤩','😏','🙄','😤','😢','😃','🤗',
  '👀','💀','🫡','🫂','🙌','✌️','🤞','👁️','🫶','💅',
];

@Component({
  selector: 'app-messaging',
  standalone: false,
  templateUrl: './messaging.component.html',
  styleUrl: './messaging.component.css',
})
export class MessagingComponent implements OnInit, OnDestroy {

  // ── State ──────────────────────────────────────────────────────────────────
  conversations: ConversationPreview[] = [];
  messages: MessageResponse[] = [];
  activePartner: UserSummary | null = null;
  loading = false;
  msgLoading = false;

  inputText = '';
  pendingMedia: { url: string; type: string; name: string } | null = null;
  uploadingFile = false;

  showEmojiPicker = false;
  showNewMsgModal = false;
  showImageViewer = false;
  viewerImageUrl = '';

  convSearch = '';
  userSearch = '';
  allUsers: UserSummary[] = [];

  readonly EMOJIS = EMOJIS;

  @ViewChild('bubblesEl') bubblesEl!: ElementRef<HTMLDivElement>;
  @ViewChild('fileInputEl') fileInputEl!: ElementRef<HTMLInputElement>;
  @ViewChild('msgInputEl') msgInputEl!: ElementRef<HTMLTextAreaElement>;

  get currentUserId(): number {
    return this.auth.currentUserId ?? 0;
  }

  get filteredConversations(): ConversationPreview[] {
    if (!this.convSearch.trim()) return this.conversations;
    const q = this.convSearch.toLowerCase();
    return this.conversations.filter(c => (c.username ?? '').toLowerCase().includes(q));
  }

  get filteredUsers(): UserSummary[] {
    const q = this.userSearch.toLowerCase();
    return this.allUsers
      .filter(u => u.id !== this.currentUserId)
      .filter(u => !q || (u.username ?? '').toLowerCase().includes(q));
  }

  get canSend(): boolean {
    return (!!this.inputText.trim() || !!this.pendingMedia) && !this.msgLoading;
  }

  private subs = new Subscription();

  constructor(
    private route: ActivatedRoute,
    public router: Router,
    private msgService: MessageApiService,
    private auth: AuthService,
    private elRef: ElementRef
  ) {}

  // ── Lifecycle ──────────────────────────────────────────────────────────────

  ngOnInit(): void {
    this.loadInbox();

    // Connect WebSocket for real-time messages
    const uid = this.currentUserId;
    if (uid) {
      this.subs.add(
        this.msgService.connectWebSocket(uid).subscribe((msg) => {
          this.onIncomingMessage(msg);
        })
      );
    }

    // Open chat if userId is in route
    this.subs.add(
      this.route.paramMap.subscribe((params) => {
        const partnerId = params.get('userId');
        if (partnerId) {
          this.openChatById(+partnerId);
        } else {
          this.activePartner = null;
          this.messages = [];
        }
      })
    );
  }

  ngOnDestroy(): void {
    this.subs.unsubscribe();
    this.msgService.disconnectWebSocket();
  }

  // ── Inbox ──────────────────────────────────────────────────────────────────

  loadInbox(): void {
    const uid = this.currentUserId;
    if (!uid) return;
    this.loading = true;
    this.msgService.getInbox(uid).subscribe({
      next: (list) => { this.conversations = list; this.loading = false; },
      error: () => { this.loading = false; },
    });
  }

  openChatById(partnerId: number): void {
    // Try to find partner info from existing conversation list first
    const existing = this.conversations.find(c => c.userId === partnerId);
    if (existing) {
      this.activePartner = {
        id: existing.userId,
        username: existing.username,
        role: existing.role,
        level: existing.level,
        levelTitle: existing.levelTitle,
      };
      this.loadConversation(partnerId);
      return;
    }
    // Otherwise fetch from users list
    this.msgService.getAllUsers().subscribe({
      next: (users) => {
        const found = users.find(u => u.id === partnerId);
        if (found) {
          this.activePartner = found;
          this.loadConversation(partnerId);
        }
      },
    });
  }

  openChat(partnerId: number | null | undefined): void {
    if (!partnerId) return;
    this.router.navigate(['/messages', partnerId]);
  }

  loadConversation(partnerId: number): void {
    const uid = this.currentUserId;
    if (!uid) return;
    this.msgLoading = true;
    this.messages = [];
    this.msgService.getConversation(uid, partnerId).subscribe({
      next: (msgs) => {
        this.messages = msgs;
        this.msgLoading = false;
        this.scrollToBottom();
        // Mark conversation as read
        this.msgService.markConversationAsRead(partnerId, uid).subscribe({
          next: () => this.loadInbox(),
          error: () => {},
        });
      },
      error: () => { this.msgLoading = false; },
    });
  }

  // ── Incoming WebSocket message ─────────────────────────────────────────────

  private onIncomingMessage(msg: MessageResponse): void {
    const uid = this.currentUserId;
    const partnerId = msg.senderId === uid ? msg.receiverId : msg.senderId;

    if (this.activePartner && this.activePartner.id === partnerId) {
      this.messages = [...this.messages, msg];
      this.scrollToBottom();
      // Auto-mark as read since chat is open
      if (msg.senderId !== uid) {
        this.msgService.markConversationAsRead(msg.senderId, uid).subscribe({ error: () => {} });
      }
    }
    this.loadInbox();
  }

  // ── Send ───────────────────────────────────────────────────────────────────

  send(): void {
    if (!this.canSend || !this.activePartner) return;
    const uid = this.currentUserId;

    const request = {
      senderId: uid,
      receiverId: this.activePartner.id,
      content: this.inputText.trim() || undefined,
      mediaUrl: this.pendingMedia?.url,
      mediaType: this.pendingMedia?.type,
    };

    this.inputText = '';
    this.pendingMedia = null;
    this.showEmojiPicker = false;
    this.msgLoading = true;

    this.msgService.send(request).subscribe({
      next: (msg) => {
        this.messages = [...this.messages, msg];
        this.msgLoading = false;
        this.scrollToBottom();
        this.loadInbox();
      },
      error: () => { this.msgLoading = false; },
    });
  }

  onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.send();
    }
  }

  // ── File attachment ────────────────────────────────────────────────────────

  openFilePicker(): void {
    this.fileInputEl?.nativeElement.click();
  }

  onFilePicked(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (!input.files?.length) return;
    const file = input.files[0];
    input.value = '';
    this.uploadFile(file);
  }

  private uploadFile(file: File): void {
    this.uploadingFile = true;
    this.msgService.uploadMedia(file).subscribe({
      next: (res: UploadResult) => {
        this.pendingMedia = { url: res.url, type: res.type, name: res.name };
        this.uploadingFile = false;
      },
      error: () => { this.uploadingFile = false; },
    });
  }

  clearMedia(): void {
    this.pendingMedia = null;
  }

  // ── Emoji picker ───────────────────────────────────────────────────────────

  toggleEmojiPicker(): void {
    this.showEmojiPicker = !this.showEmojiPicker;
  }

  insertEmoji(emoji: string): void {
    this.inputText += emoji;
    this.showEmojiPicker = false;
    this.msgInputEl?.nativeElement.focus();
  }

  // ── New message modal ──────────────────────────────────────────────────────

  openNewMsg(): void {
    this.showNewMsgModal = true;
    this.userSearch = '';
    if (!this.allUsers.length) {
      this.msgService.getAllUsers().subscribe({
        next: (users) => { this.allUsers = users; },
        error: () => {},
      });
    }
  }

  closeNewMsg(): void {
    this.showNewMsgModal = false;
  }

  startChat(userId: number): void {
    this.closeNewMsg();
    this.openChat(userId);
  }

  // ── Image viewer ───────────────────────────────────────────────────────────

  viewImage(url: string): void {
    this.viewerImageUrl = url;
    this.showImageViewer = true;
  }

  closeImageViewer(): void {
    this.showImageViewer = false;
    this.viewerImageUrl = '';
  }

  // ── Helpers: display ──────────────────────────────────────────────────────

  getInitials(username: string | null | undefined): string {
    return ((username ?? '').slice(0, 2).toUpperCase()) || '?';
  }

  getAvatarBg(username: string | null | undefined): string {
    const s = username ?? '';
    if (!s) return AVATAR_COLORS[0];
    let hash = 0;
    for (let i = 0; i < s.length; i++) hash += s.charCodeAt(i);
    return AVATAR_COLORS[hash % AVATAR_COLORS.length];
  }

  getRoleEmoji(role: string | null | undefined): string {
    switch (role) {
      case 'INSTRUCTOR': return '👨‍🏫';
      case 'ADMIN': return '⭐';
      default: return '🎓';
    }
  }

  getLevelTitle(level: number): string {
    return LEVEL_TITLES[level] ?? `Level ${level}`;
  }

  formatTime(iso: string): string {
    if (!iso) return '';
    const d = new Date(iso);
    const now = new Date();
    const diff = Math.floor((now.getTime() - d.getTime()) / 1000);
    if (diff < 60) return 'now';
    if (diff < 3600) return Math.floor(diff / 60) + 'm';
    if (diff < 86400) return Math.floor(diff / 3600) + 'h';
    if (diff < 604800) return Math.floor(diff / 86400) + 'd';
    return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
  }

  formatTimeShort(iso: string): string {
    if (!iso) return '';
    const d = new Date(iso);
    return d.toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit', hour12: false });
  }

  /** Returns true if we should show a date separator before this message */
  showDateSep(msg: MessageResponse, index: number): boolean {
    if (index === 0) return true;
    const prev = this.messages[index - 1];
    return this.dayLabel(msg.createdAt) !== this.dayLabel(prev.createdAt);
  }

  getDateLabel(iso: string): string {
    if (!iso) return '';
    const d = new Date(iso);
    const now = new Date();
    if (this.dayLabel(iso) === this.dayLabel(now.toISOString())) return 'Today';
    const yesterday = new Date(now);
    yesterday.setDate(now.getDate() - 1);
    if (this.dayLabel(iso) === this.dayLabel(yesterday.toISOString())) return 'Yesterday';
    return d.toLocaleDateString('en-US', { weekday: 'long', month: 'short', day: 'numeric' });
  }

  private dayLabel(iso: string): string {
    return new Date(iso).toDateString();
  }

  /** Return image src — prepend backend host since /uploads is on port 8040 */
  mediaUrl(url: string | null): string {
    if (!url) return '';
    if (url.startsWith('http')) return url;
    return url;
  }

  // ── Close dropdowns on outside click ──────────────────────────────────────

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (this.showEmojiPicker) {
      const picker = this.elRef.nativeElement.querySelector('.msg-emoji-picker');
      const btn = this.elRef.nativeElement.querySelector('.msg-btn-emoji');
      if (picker && !picker.contains(event.target) && btn && !btn.contains(event.target)) {
        this.showEmojiPicker = false;
      }
    }
  }

  private scrollToBottom(): void {
    setTimeout(() => {
      if (this.bubblesEl?.nativeElement) {
        this.bubblesEl.nativeElement.scrollTop = this.bubblesEl.nativeElement.scrollHeight;
      }
    }, 30);
  }
}
