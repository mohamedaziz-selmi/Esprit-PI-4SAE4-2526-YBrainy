import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { PackService } from '../../services/pack.service';
import { CategoryService } from '../../services/category.service';
import { PackCategory } from '../../models/pack-category.model';

@Component({
  selector: 'app-pack-form',
  templateUrl: './pack-form.component.html',
  styleUrl: './pack-form.component.css'
})
export class PackFormComponent implements OnInit {
  form!: FormGroup;
  isEdit = false;
  packId: number | null = null;
  loading = false;
  submitting = false;
  error = '';
  categories: PackCategory[] = [];

  constructor(
    private fb: FormBuilder,
    private packService: PackService,
    private categoryService: CategoryService,
    private router: Router,
    private route: ActivatedRoute
  ) { }

  ngOnInit(): void {
    this.form = this.fb.group({
      title: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(200)]],
      description: ['', [Validators.required, Validators.minLength(10), Validators.maxLength(2000)]],
      originalPrice: [null, [Validators.required, Validators.min(0.01), Validators.max(999999.99)]],
      salePrice: [null, [Validators.required, Validators.min(0), Validators.max(999999.99)]],
      level: ['', [Validators.required]],
      durationHours: [null, [Validators.required, Validators.min(1), Validators.max(9999)]],
      certificateName: ['', [Validators.maxLength(100)]],
      categoryId: [null, [Validators.required]]
    });

    this.loadCategories();

    const idParam = this.route.snapshot.paramMap.get('id');
    if (idParam) {
      this.isEdit = true;
      this.packId = +idParam;
      this.loadPack();
    }
  }

  loadCategories(): void {
    this.categoryService.getAll().subscribe({
      next: (data) => this.categories = data,
      error: () => console.error('Failed to load categories')
    });
  }

  loadPack(): void {
    this.loading = true;
    this.packService.getById(this.packId!).subscribe({
      next: (pack) => {
        this.form.patchValue({
          title: pack.title,
          description: pack.description,
          originalPrice: pack.originalPrice,
          salePrice: pack.salePrice,
          level: pack.level,
          durationHours: pack.durationHours,
          certificateName: pack.certificateName || '',
          categoryId: pack.categoryId
        });
        this.loading = false;
      },
      error: () => {
        this.error = 'Failed to load pack';
        this.loading = false;
      }
    });
  }

  selectedFile: File | null = null;

  onFileSelect(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      this.selectedFile = input.files[0];
    }
  }

  onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    // Custom validation: salePrice <= originalPrice
    const { originalPrice, salePrice } = this.form.value;
    if (salePrice > originalPrice) {
      this.error = 'Sale price cannot be greater than original price';
      return;
    }

    this.submitting = true;
    this.error = '';

    const data = { ...this.form.value };
    if (!data.certificateName) delete data.certificateName;

    const request$ = this.isEdit
      ? this.packService.update(this.packId!, data, this.selectedFile || undefined)
      : this.packService.create(data, this.selectedFile || undefined);

    request$.subscribe({
      next: () => {
        this.submitting = false;
        this.router.navigate(['/dashboard/packs']);
      },
      error: (err) => {
        this.error = 'Failed to save pack';
        this.submitting = false;
        console.error(err);
      }
    });
  }

  getDiscount(): number {
    const orig = this.form.get('originalPrice')?.value;
    const sale = this.form.get('salePrice')?.value;
    if (!orig || orig === 0 || !sale) return 0;
    return Math.round(((orig - sale) / orig) * 100);
  }
}

