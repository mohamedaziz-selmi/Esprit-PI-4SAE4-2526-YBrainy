import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, OnChanges, OnInit, Output, SimpleChanges } from '@angular/core';
import { AbstractControl, FormBuilder, FormGroup, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { CategoryService } from '../../services/category.service';
import { PackCategory } from '../../models/pack-category.model';

@Component({
  selector: 'app-category-form',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './category-form.component.html',
  styleUrl: './category-form.component.css'
})
export class CategoryFormComponent implements OnInit, OnChanges {
  form!: FormGroup;
  isEdit = false;
  categoryId: number | null = null;
  loading = false;
  submitting = false;
  error = '';

  @Input() category: PackCategory | null = null;
  @Input() isModal = false;
  @Output() saved = new EventEmitter<PackCategory>();
  @Output() closed = new EventEmitter<void>();

  icons = [
    'bi-globe', 'bi-bar-chart-line', 'bi-briefcase', 'bi-megaphone',
    'bi-phone', 'bi-shield-lock', 'bi-code-slash', 'bi-palette',
    'bi-camera-video', 'bi-music-note', 'bi-book', 'bi-cpu',
    'bi-cloud', 'bi-gear', 'bi-heart', 'bi-star', 'bi-lightning',
    'bi-rocket', 'bi-trophy', 'bi-puzzle'
  ];

  constructor(
    private fb: FormBuilder,
    private categoryService: CategoryService,
    private router: Router,
    private route: ActivatedRoute
  ) {}

  ngOnInit(): void {
    this.form = this.fb.group({
      name: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(100)]],
      description: ['', [Validators.required, Validators.maxLength(1000), CategoryFormComponent.notBlankValidator]],
      icon: ['bi-folder', [Validators.required]]
    });

    if (this.isModal && this.category) {
      this.applyEditCategory(this.category);
    }

    // If used as routed component, read from route params
    if (!this.isModal) {
      const idParam = this.route.snapshot.paramMap.get('id');
      if (idParam) {
        this.isEdit = true;
        this.categoryId = +idParam;
        this.loadCategory();
      }
    }
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (!changes['category']) return;

    // React to category input changes (for modal mode)
    if (this.category) {
      this.applyEditCategory(this.category);
      return;
    }

    if (this.isModal) {
      this.resetCreateCategoryForm();
    }
  }

  private applyEditCategory(category: PackCategory): void {
    this.isEdit = true;
    this.categoryId = category.id;
    if (!this.form) return;

    this.form.patchValue({
      name: category.name,
      description: category.description,
      icon: category.icon || 'bi-folder'
    });
  }

  private resetCreateCategoryForm(): void {
    this.isEdit = false;
    this.categoryId = null;
    this.error = '';
    this.submitting = false;
    if (!this.form) return;

    this.form.reset({
      name: '',
      description: '',
      icon: 'bi-folder'
    });
    this.form.markAsPristine();
    this.form.markAsUntouched();
  }

  static notBlankValidator(control: AbstractControl): ValidationErrors | null {
    const v = control.value;
    if (v == null) return { blank: true };
    if (typeof v === 'string' && v.trim().length === 0) return { blank: true };
    return null;
  }

  loadCategory(): void {
    this.loading = true;
    this.categoryService.getById(this.categoryId!).subscribe({
      next: (cat) => {
        this.form.patchValue({
          name: cat.name,
          description: cat.description,
          icon: cat.icon || 'bi-folder'
        });
        this.loading = false;
      },
      error: () => {
        this.error = 'Failed to load category';
        this.loading = false;
      }
    });
  }

  onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.submitting = true;
    this.error = '';
    const data = this.form.value;

    const request$ = this.isEdit
      ? this.categoryService.update(this.categoryId!, data)
      : this.categoryService.create(data);

    request$.subscribe({
      next: (savedCategory) => {
        this.submitting = false;
        if (this.isModal) {
          this.saved.emit(savedCategory);
        } else {
          this.router.navigate(['/dashboard/categories']);
        }
      },
      error: (err) => {
        this.error = err.error?.message || 'Failed to save category';
        this.submitting = false;
      }
    });
  }

  selectIcon(icon: string): void {
    this.form.patchValue({ icon });
  }

  onCancel(): void {
    if (this.isModal) {
      this.closed.emit();
    } else {
      this.router.navigate(['/dashboard/categories']);
    }
  }
}

