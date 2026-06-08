import { Component, OnInit } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';

interface Note {
  id: number;
  student_id: number;
  title: string;
  content: string | null;
  directory: string;
  color: string;
  is_pinned: boolean;
  is_archived: boolean;
  created_at: string;
  updated_at: string;
}

const NOTE_COLORS = ['#ffffff', '#fef9c3', '#dcfce7', '#dbeafe', '#fce7f3', '#f3e8ff', '#ffedd5'];
const API_BASE = '/api/notes';

@Component({
  selector: 'app-notes',
  standalone: false,
  templateUrl: './notes.component.html',
  styleUrl: './notes.component.css'
})
export class NotesComponent implements OnInit {
  notes: Note[] = [];
  directories: string[] = ['General'];
  selectedDirectory: string | null = null;
  searchQuery = '';
  isLoading = false;
  errorMessage = '';

  editingNoteId: number | null = null;
  editTitle = '';
  editContent = '';
  editDirectory = 'General';
  editColor = '#ffffff';

  newNoteMode = false;
  newTitle = '';
  newContent = '';
  newDirectory = 'General';
  newColor = '#ffffff';

  readonly colors = NOTE_COLORS;

  private studentId: number | null = null;

  constructor(private http: HttpClient) {}

  ngOnInit(): void {
    const raw = localStorage.getItem('bb_user_session_v1');
    if (raw) {
      try {
        const session = JSON.parse(raw);
        this.studentId = session.id ?? session.userId ?? null;
      } catch {
        this.studentId = null;
      }
    }
    this.loadDirectories();
    this.loadNotes();
  }

  private headers(): HttpHeaders {
    return new HttpHeaders({ 'X-User-Id': String(this.studentId ?? 0) });
  }

  loadNotes(): void {
    this.isLoading = true;
    this.errorMessage = '';
    let url = API_BASE;
    const params: string[] = [];
    if (this.selectedDirectory) params.push(`directory=${encodeURIComponent(this.selectedDirectory)}`);
    if (this.searchQuery.trim()) {
      url = `${API_BASE}/search?q=${encodeURIComponent(this.searchQuery.trim())}`;
      this.http.get<Note[]>(url, { headers: this.headers() }).subscribe({
        next: (notes) => { this.notes = notes; this.isLoading = false; },
        error: () => { this.errorMessage = 'Failed to load notes.'; this.isLoading = false; }
      });
      return;
    }
    if (params.length) url += '?' + params.join('&');
    this.http.get<Note[]>(url, { headers: this.headers() }).subscribe({
      next: (notes) => { this.notes = notes; this.isLoading = false; },
      error: () => { this.errorMessage = 'Failed to load notes.'; this.isLoading = false; }
    });
  }

  loadDirectories(): void {
    this.http.get<string[]>(`${API_BASE}/directories`, { headers: this.headers() }).subscribe({
      next: (dirs) => { this.directories = dirs; },
      error: () => {}
    });
  }

  selectDirectory(dir: string | null): void {
    this.selectedDirectory = dir;
    this.searchQuery = '';
    this.loadNotes();
  }

  onSearch(): void {
    this.selectedDirectory = null;
    this.loadNotes();
  }

  openNewNote(): void {
    this.newNoteMode = true;
    this.newTitle = '';
    this.newContent = '';
    this.newDirectory = this.selectedDirectory || 'General';
    this.newColor = '#ffffff';
    this.editingNoteId = null;
  }

  cancelNewNote(): void {
    this.newNoteMode = false;
  }

  saveNewNote(): void {
    if (!this.newTitle.trim()) return;
    const body = {
      title: this.newTitle.trim(),
      content: this.newContent,
      directory: this.newDirectory,
      color: this.newColor,
      is_pinned: false
    };
    this.http.post<Note>(API_BASE, body, { headers: this.headers() }).subscribe({
      next: () => {
        this.newNoteMode = false;
        this.loadDirectories();
        this.loadNotes();
      },
      error: () => { this.errorMessage = 'Failed to create note.'; }
    });
  }

  startEdit(note: Note): void {
    this.editingNoteId = note.id;
    this.editTitle = note.title;
    this.editContent = note.content ?? '';
    this.editDirectory = note.directory;
    this.editColor = note.color;
    this.newNoteMode = false;
  }

  cancelEdit(): void {
    this.editingNoteId = null;
  }

  saveEdit(note: Note): void {
    const body = {
      title: this.editTitle.trim() || note.title,
      content: this.editContent,
      directory: this.editDirectory,
      color: this.editColor
    };
    this.http.put<Note>(`${API_BASE}/${note.id}`, body, { headers: this.headers() }).subscribe({
      next: () => {
        this.editingNoteId = null;
        this.loadDirectories();
        this.loadNotes();
      },
      error: () => { this.errorMessage = 'Failed to update note.'; }
    });
  }

  deleteNote(note: Note, event: Event): void {
    event.stopPropagation();
    this.http.delete(`${API_BASE}/${note.id}`, { headers: this.headers() }).subscribe({
      next: () => {
        if (this.editingNoteId === note.id) this.editingNoteId = null;
        this.loadDirectories();
        this.loadNotes();
      },
      error: () => { this.errorMessage = 'Failed to delete note.'; }
    });
  }

  togglePin(note: Note, event: Event): void {
    event.stopPropagation();
    this.http.patch<Note>(`${API_BASE}/${note.id}/pin`, {}, { headers: this.headers() }).subscribe({
      next: () => this.loadNotes(),
      error: () => {}
    });
  }

  toggleArchive(note: Note, event: Event): void {
    event.stopPropagation();
    this.http.patch<Note>(`${API_BASE}/${note.id}/archive`, {}, { headers: this.headers() }).subscribe({
      next: () => { this.loadDirectories(); this.loadNotes(); },
      error: () => {}
    });
  }

  setNoteColor(note: Note, color: string, event: Event): void {
    event.stopPropagation();
    this.http.put<Note>(`${API_BASE}/${note.id}`, { color }, { headers: this.headers() }).subscribe({
      next: (updated) => {
        const idx = this.notes.findIndex(n => n.id === note.id);
        if (idx !== -1) this.notes[idx] = updated;
      },
      error: () => {}
    });
  }

  setNewColor(color: string): void {
    this.newColor = color;
  }

  setEditColor(color: string): void {
    this.editColor = color;
  }
}
