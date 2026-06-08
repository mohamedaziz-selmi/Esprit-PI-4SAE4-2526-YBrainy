import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Subject, Subscription } from 'rxjs';
import { debounceTime } from 'rxjs/operators';

import { CategoryResponse } from '../../../models/forum.models';
import { CategoryApiService } from '../../../services/category-api.service';
import { ThreadApiService } from '../../../services/thread-api.service';
import { UserStateService } from '../../../services/user-state.service';
import { AiGenerationService } from '../../../services/ai-generation.service';
import { AuthService } from '../../../services/auth.service';
import { DraftService } from '../../../services/draft.service';
import { AiDraftFilesService } from '../../../services/ai-draft-files.service';

@Component({
  selector: 'app-create-thread',
  standalone: false,
  templateUrl: './create-thread.component.html',
  styleUrl: './create-thread.component.css',
  host: { style: 'display:block' },
})
export class CreateThreadComponent implements OnInit, OnDestroy {
  categories: CategoryResponse[] = [];

  submitting = false;
  submitAttempted = false;
  error: string | null = null;
  checkerVisible = false;

  generating = false;
  aiError: string | null = null;

  form: FormGroup;

  activeTab: 'TEXT' | 'IMAGE' = 'TEXT';

  imageFile: File | null = null;
  imagePreviewUrl: string | null = null;
  dropActive = false;

  attachedFile: File | null = null;
  attachedFileType: string | null = null;
  attachedFileObjectUrl: string | null = null;

  /** Draft state */
  currentDraftId: string | null = null;
  draftSaved = false;

  get attachedFileIsVideo(): boolean { return !!this.attachedFileType?.startsWith('video/'); }
  get attachedFileIsPdf(): boolean { return this.attachedFileType === 'application/pdf'; }

  private readonly subs = new Subscription();
  private readonly draftSave$ = new Subject<void>();

  constructor(
    private fb: FormBuilder,
    private threadApi: ThreadApiService,
    private categoryApi: CategoryApiService,
    private userState: UserStateService,
    private aiService: AiGenerationService,
    private router: Router,
    private route: ActivatedRoute,
    private auth: AuthService,
    private draftService: DraftService,
    private aiDraftFiles: AiDraftFilesService
  ) {
    this.form = this.fb.group({
      title: ['', [Validators.required, Validators.minLength(5), Validators.maxLength(300)]],
      body: [''],
      authorId: [this.auth.currentUserId ?? 0, [Validators.required]],
      categoryId: [null],
    });
  }

  ngOnInit(): void {
    // Keep authorId in sync: patch as soon as the authenticated user is known
    this.subs.add(
      this.auth.currentUser$.subscribe(user => {
        if (user?.id) {
          this.form.patchValue({ authorId: user.id }, { emitEvent: false });
        }
      })
    );

    this.subs.add(
      this.categoryApi.list().subscribe({
        next: (cats) => (this.categories = cats),
        error: () => (this.categories = []),
      })
    );

    // Load draft from query param if present
    this.subs.add(
      this.route.queryParamMap.subscribe((params) => {
        const draftId = params.get('draftId');
        if (draftId) {
          const draft = this.draftService.getById(draftId);
          if (draft) {
            this.currentDraftId = draft.id;
            this.form.patchValue({
              title: draft.title ?? '',
              body: draft.body ?? '',
              categoryId: draft.categoryId ?? null,
            });

            // Pre-fill files from AI draft (in-memory, clears after use)
            const aiFiles = this.aiDraftFiles.get(draftId);
            if (aiFiles.length) {
              const images = aiFiles.filter(f => f.type.startsWith('image/'));
              const nonImages = aiFiles.filter(f => !f.type.startsWith('image/'));
              if (images.length) this.onImagePicked(images[0]);
              if (nonImages.length) {
                this.attachedFile = nonImages[0];
                this.attachedFileType = nonImages[0].type;
                this.attachedFileObjectUrl = nonImages[0].type.startsWith('video/')
                  ? URL.createObjectURL(nonImages[0]) : null;
              }
              this.aiDraftFiles.clear(draftId);
            }
          }
        }
      })
    );

    // Auto-save draft with debounce
    this.subs.add(
      this.draftSave$.pipe(debounceTime(1500)).subscribe(() => this.saveDraft())
    );

    this.subs.add(
      this.form.valueChanges.subscribe(() => this.draftSave$.next())
    );
  }

  private saveDraft(): void {
    const { title, body, categoryId } = this.form.value;
    if (!title && !body) return;
    const saved = this.draftService.save(
      { title, body, categoryId: categoryId ?? null },
      this.currentDraftId ?? undefined
    );
    if (!this.currentDraftId) {
      this.currentDraftId = saved.id;
    }
    this.draftSaved = true;
    setTimeout(() => (this.draftSaved = false), 2000);
  }

  goToDrafts(): void {
    this.router.navigate(['/forum/drafts']);
  }

  setTab(t: 'TEXT' | 'IMAGE'): void {
    this.activeTab = t;
  }

  generateBody(): void {
    const title = (this.form.value.title ?? '').trim();
    if (!title || title.length < 5) {
      this.aiError = 'Please enter a title of at least 5 characters before generating.';
      return;
    }
    this.aiError = null;
    this.generating = true;
    this.subs.add(
      this.aiService.generateThreadBody(title).subscribe({
        next: (res) => {
          this.generating = false;
          this.form.get('body')?.setValue(res.body);
          this.activeTab = 'TEXT';
        },
        error: (err) => {
          this.generating = false;
          this.aiError = err?.error?.message ?? err?.message ?? 'AI generation failed. Check your API key.';
        },
      })
    );
  }

  onImagePicked(file: File | null): void {
    if (this.imagePreviewUrl) {
      URL.revokeObjectURL(this.imagePreviewUrl);
    }
    this.imageFile = file;
    this.imagePreviewUrl = file ? URL.createObjectURL(file) : null;
  }

  onImageInputChange(evt: Event): void {
    const input = evt.target as HTMLInputElement;
    const file = input.files && input.files.length ? input.files[0] : null;
    this.onImagePicked(file);
  }

  onDropOver(evt: DragEvent): void {
    evt.preventDefault();
    this.dropActive = true;
  }

  onDropLeave(evt: DragEvent): void {
    evt.preventDefault();
    this.dropActive = false;
  }

  onDrop(evt: DragEvent): void {
    evt.preventDefault();
    this.dropActive = false;
    const file = evt.dataTransfer?.files && evt.dataTransfer.files.length ? evt.dataTransfer.files[0] : null;
    if (file) this.onImagePicked(file);
  }

  clearImage(): void {
    this.onImagePicked(null);
  }

  onAttachmentChange(evt: Event): void {
    const input = evt.target as HTMLInputElement;
    const f = input.files?.[0] ?? null;
    if (!f) return;
    if (this.attachedFileObjectUrl) URL.revokeObjectURL(this.attachedFileObjectUrl);
    this.attachedFile = f;
    this.attachedFileType = f.type;
    this.attachedFileObjectUrl = f.type.startsWith('video/') ? URL.createObjectURL(f) : null;
  }

  clearAttachment(): void {
    if (this.attachedFileObjectUrl) URL.revokeObjectURL(this.attachedFileObjectUrl);
    this.attachedFile = null;
    this.attachedFileType = null;
    this.attachedFileObjectUrl = null;
  }

  formatFileSize(bytes: number): string {
    if (bytes < 1024 * 1024) return Math.round(bytes / 1024) + ' KB';
    return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
  }

  openChecker(): void {
    this.submitAttempted = true;
    this.error = null;

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const title = String(this.form.value.title ?? '');
    const body = String(this.form.value.body ?? '');
    const authorId = Number(this.form.value.authorId);
    const categoryIdRaw = this.form.value.categoryId;
    const categoryId = categoryIdRaw === null || categoryIdRaw === '' ? null : Number(categoryIdRaw);

    if (!Number.isFinite(authorId) || authorId <= 0) {
      this.form.get('authorId')?.setErrors({ invalid: true });
      return;
    }

    this.checkerVisible = true;
  }

  onCheckerConfirm(): void {
    this.checkerVisible = false;
    this.submit();
  }

  onCheckerClosed(): void {
    this.checkerVisible = false;
  }

  private submit(): void {
    const title = String(this.form.value.title ?? '');
    const body = String(this.form.value.body ?? '');
    const authorId = Number(this.form.value.authorId);
    const categoryIdRaw = this.form.value.categoryId;
    const categoryId = categoryIdRaw === null || categoryIdRaw === '' ? null : Number(categoryIdRaw);

    if (this.submitting) return;
    this.submitting = true;

    this.subs.add(
      this.threadApi
        .create(
          {
            title,
            body,
            authorId,
            categoryId: categoryId !== null && Number.isFinite(categoryId) ? categoryId : null,
          },
          this.imageFile,
          this.attachedFile
        )
        .subscribe({
          next: (created) => {
            this.submitting = false;
            if (this.currentDraftId) {
              this.draftService.delete(this.currentDraftId);
            }
            this.userState.refresh();
            this.router.navigate(['/forum', created.id]);
          },
          error: (err) => {
            this.submitting = false;
            this.error = err?.message ?? 'Failed to create thread';
          },
        })
    );
  }

  ngOnDestroy(): void {
    if (this.imagePreviewUrl) URL.revokeObjectURL(this.imagePreviewUrl);
    if (this.attachedFileObjectUrl) URL.revokeObjectURL(this.attachedFileObjectUrl);
    this.subs.unsubscribe();
  }
}
