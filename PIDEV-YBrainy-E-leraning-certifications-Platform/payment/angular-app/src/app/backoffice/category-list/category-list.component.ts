import { Component, OnInit, AfterViewInit, ViewEncapsulation, HostListener } from '@angular/core';
import { CategoryService } from '../../services/category.service';
import { PackCategory } from '../../models/pack-category.model';

@Component({
  selector: 'app-category-list',
  templateUrl: './category-list.component.html',
  styleUrl: './category-list.component.css',
  encapsulation: ViewEncapsulation.None
})
export class CategoryListComponent implements OnInit, AfterViewInit {
  categories: PackCategory[] = [];
  loading = false;
  error = '';

  // Modal state
  showModal = false;
  selectedCategory: PackCategory | null = null;
  isEditMode = false;
  isDeleteModalOpen = false;
  deletingCategory = false;
  deleteError = '';
  categoryToDelete: PackCategory | null = null;

  constructor(private categoryService: CategoryService) { }

  ngOnInit(): void {
    this.loadCategories();
  }

  ngAfterViewInit(): void {
    // Delay slightly to ensure DOM is ready
    setTimeout(() => {
      this.loadScripts();
    }, 100);
  }

  private loadScripts(): void {
    const scripts = [
      'assets/backoffice/vendor/global/global.min.js',
      // 'assets/backoffice/vendor/bootstrap-select/dist/js/bootstrap-select.min.js',
      'assets/backoffice/js/custom.js',
      'assets/backoffice/js/dlabnav-init.js'
    ];
    this.loadScriptSequence(scripts);
  }

  private loadScriptSequence(scripts: string[], index = 0): void {
    if (index >= scripts.length) return;
    const script = document.createElement('script');
    script.src = scripts[index];
    script.async = false;
    script.onload = () => {
      // Mock plugins if needed, similar to Finance component
      if (scripts[index].includes('global.min.js')) {
        this.mockLegacyPlugins();
      }
      this.loadScriptSequence(scripts, index + 1);
    };
    document.body.appendChild(script);
  }

  private mockLegacyPlugins(): void {
    const win = window as any;
    if (win.jQuery && win.jQuery.fn) {
      if (!win.jQuery.fn.selectpicker) {
        win.jQuery.fn.selectpicker = function () { return this; };
      }
      if (!win.jQuery.fn.niceSelect) {
        win.jQuery.fn.niceSelect = function () { return this; };
      }
    }
  }

  loadCategories(): void {
    this.loading = true;
    this.error = '';
    this.categoryService.getAll().subscribe({
      next: (data) => {
        this.categories = data;
        this.loading = false;
      },
      error: (err) => {
        this.error = 'Failed to load categories';
        this.loading = false;
        console.error(err);
      }
    });
  }

  toggleStatus(category: PackCategory): void {
    this.categoryService.toggleStatus(category.id).subscribe({
      next: (updated) => {
        const idx = this.categories.findIndex(c => c.id === updated.id);
        if (idx >= 0) this.categories[idx] = updated;
      },
      error: (err) => {
        alert(err.error?.message || 'Failed to toggle status');
      }
    });
  }

  deleteCategory(category: PackCategory): void {
    this.categoryToDelete = category;
    this.isDeleteModalOpen = true;
    this.deletingCategory = false;
    this.deleteError = '';
  }

  closeDeleteModal(): void {
    if (this.deletingCategory) return;
    this.isDeleteModalOpen = false;
    this.categoryToDelete = null;
    this.deleteError = '';
  }

  confirmDeleteCategory(): void {
    if (!this.categoryToDelete || this.deletingCategory) return;

    this.deletingCategory = true;
    this.deleteError = '';

    const categoryId = this.categoryToDelete.id;
    this.categoryService.delete(categoryId).subscribe({
      next: () => {
        this.categories = this.categories.filter(c => c.id !== categoryId);
        this.deletingCategory = false;
        this.isDeleteModalOpen = false;
        this.categoryToDelete = null;
      },
      error: (err) => {
        this.deletingCategory = false;
        this.deleteError = err.error?.message || 'Failed to delete category';
      }
    });
  }

  @HostListener('document:keydown.escape')
  onEscapeKey(): void {
    if (this.isDeleteModalOpen) {
      this.closeDeleteModal();
    }
  }

  openNewCategoryModal(): void {
    this.selectedCategory = null;
    this.isEditMode = false;
    this.showModal = true;
  }

  openEditCategoryModal(category: PackCategory): void {
    this.selectedCategory = category;
    this.isEditMode = true;
    this.showModal = true;
  }

  onCategoryFormClosed(): void {
    this.showModal = false;
    this.selectedCategory = null;
  }

  onCategorySaved(savedCategory: PackCategory): void {
    if (this.isEditMode && this.selectedCategory) {
      // Update existing category
      const idx = this.categories.findIndex(c => c.id === savedCategory.id);
      if (idx >= 0) {
        this.categories[idx] = savedCategory;
      }
    } else {
      // Add new category
      this.categories.push(savedCategory);
    }
    this.showModal = false;
    this.selectedCategory = null;
  }
}

