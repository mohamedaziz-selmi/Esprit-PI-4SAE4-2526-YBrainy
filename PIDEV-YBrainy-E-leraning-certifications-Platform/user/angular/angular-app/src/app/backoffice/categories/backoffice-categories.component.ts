import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Subscription } from 'rxjs';

import { CategoryResponse } from '../../frontoffice/models/forum.models';
import { CategoryApiService } from '../../frontoffice/services/category-api.service';
import { BackofficeNavComponent } from '../backoffice-nav/backoffice-nav.component';

@Component({
  selector: 'app-backoffice-categories',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, BackofficeNavComponent],
  templateUrl: './backoffice-categories.component.html',
  styleUrl: './backoffice-categories.component.css',
  host: { style: 'display:block' },
})
export class BackofficeCategoriesComponent implements OnInit, OnDestroy {
  categories: CategoryResponse[] = [];
  loading = true;
  error: string | null = null;

  form: FormGroup;
  editingId: number | null = null;

  private readonly subs = new Subscription();

  constructor(private categoryApi: CategoryApiService, private fb: FormBuilder) {
    this.form = this.fb.group({
      name: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(100)]],
      description: ['', [Validators.maxLength(300)]],
    });
  }

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.error = null;

    this.subs.add(
      this.categoryApi.list().subscribe({
        next: (cats) => {
          this.categories = cats;
          this.loading = false;
        },
        error: (err) => {
          this.loading = false;
          this.error = err?.message ?? 'Failed to load categories';
        },
      })
    );
  }

  startCreate(): void {
    this.editingId = null;
    this.form.reset();
  }

  startEdit(c: CategoryResponse): void {
    this.editingId = c.id;
    this.form.setValue({
      name: c.name ?? '',
      description: c.description ?? '',
    });
  }

  cancel(): void {
    this.editingId = null;
    this.form.reset();
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const name = String(this.form.value.name ?? '');
    const descriptionRaw = this.form.value.description;
    const description = descriptionRaw === null || descriptionRaw === '' ? null : String(descriptionRaw);

    if (this.editingId === null) {
      this.subs.add(
        this.categoryApi.create({ name, description }).subscribe({
          next: () => {
            this.form.reset();
            this.load();
          },
          error: (err) => {
            this.error = err?.message ?? 'Failed to create category';
          },
        })
      );
      return;
    }

    const id = this.editingId;
    this.subs.add(
      this.categoryApi.update(id, { name, description }).subscribe({
        next: () => {
          this.editingId = null;
          this.form.reset();
          this.load();
        },
        error: (err) => {
          this.error = err?.message ?? 'Failed to update category';
        },
      })
    );
  }

  delete(c: CategoryResponse): void {
    if (!confirm(`Delete category #${c.id} (${c.name})?`)) return;

    this.subs.add(
      this.categoryApi.delete(c.id).subscribe({
        next: () => this.load(),
        error: (err) => {
          this.error = err?.message ?? 'Failed to delete category';
        },
      })
    );
  }

  ngOnDestroy(): void {
    this.subs.unsubscribe();
  }
}
