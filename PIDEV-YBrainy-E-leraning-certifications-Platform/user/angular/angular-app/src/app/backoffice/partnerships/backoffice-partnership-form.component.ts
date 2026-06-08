import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { AbstractControl, FormControl, FormGroup, ReactiveFormsModule, ValidatorFn, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { Subscription } from 'rxjs';
import { environment } from '@env/environment';
import { Partnership } from '../../shared/models/partnership.models';
import { PartnershipStoreService } from '../../shared/stores/partnership-store.service';
import { fileToDataUrl } from '../../shared/utils/file-to-data-url';
import { applyBackofficeBodyClass } from '../utils/backoffice-body-class';

const INDUSTRY_OPTIONS = [
  'Éducation & Formation',
  "Technologies de l’information (IT)",
  'Commerce & Marketing',
  'Gestion & Administration',
  'Industrie & Génie',
  'Tourisme & Hôtellerie',
] as const;

function oneOfValidator(values: readonly string[]): ValidatorFn {
  return (control: AbstractControl<string | null>) => {
    const value = (control.value ?? '').trim();
    return values.includes(value) ? null : { oneOf: true };
  };
}

type PartnershipForm = FormGroup<{
  name: FormControl<string>;
  email: FormControl<string>;
  phone: FormControl<string>;
  website: FormControl<string>;
  industry: FormControl<string>;
  description: FormControl<string>;
  logoDataUrl: FormControl<string>;
  isActive: FormControl<boolean>;
}>;

@Component({
  selector: 'app-backoffice-partnership-form',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterModule],
  templateUrl: './backoffice-partnership-form.component.html',
  styleUrl: './backoffice-partnership-form.component.css',
  host: { style: 'display:block' },
})
export class BackofficePartnershipFormComponent implements OnInit, OnDestroy {
  readonly industryOptions = INDUSTRY_OPTIONS;
  mode: 'create' | 'edit' = 'create';
  partnershipId: string | null = null;
  previewLogo: string | null = null;
  saving = false;
  saveError: string | null = null;

  form: PartnershipForm = new FormGroup({
    name: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.minLength(2)] }),
    email: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.email] }),
    phone: new FormControl('', { nonNullable: true }),
    website: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(/^https?:\/\/.+/i)],
    }),
    industry: new FormControl('', { nonNullable: true, validators: [Validators.required, oneOfValidator(INDUSTRY_OPTIONS)] }),
    description: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.minLength(10)] }),
    logoDataUrl: new FormControl('', { nonNullable: true }),
    isActive: new FormControl(true, { nonNullable: true }),
  });

  private readonly sub = new Subscription();
  private restoreBodyClass: (() => void) | null = null;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private store: PartnershipStoreService
  ) {}

  ngOnInit(): void {
    this.restoreBodyClass = applyBackofficeBodyClass();

    this.sub.add(
      this.route.paramMap.subscribe((pm) => {
        const id = pm.get('id');
        this.partnershipId = id;
        this.saveError = null;

        if (!id) {
          this.mode = 'create';
          this.previewLogo = null;
          this.form.reset({
            name: '',
            email: '',
            phone: '',
            website: '',
            industry: '',
            description: '',
            logoDataUrl: '',
            isActive: true,
          });
          return;
        }

        this.mode = 'edit';
        this.loadPartnershipForEdit(id);
      })
    );
  }

  ngOnDestroy(): void {
    this.sub.unsubscribe();
    this.restoreBodyClass?.();
    this.restoreBodyClass = null;
  }

  get title(): string {
    return this.mode === 'create' ? 'Nouveau partenaire' : 'Modifier partenaire';
  }

  async onLogoSelected(e: Event): Promise<void> {
    const input = e.target as HTMLInputElement | null;
    const file = input?.files?.[0] ?? null;
    if (!file) return;
    const dataUrl = await fileToDataUrl(file);
    this.form.controls.logoDataUrl.setValue(dataUrl);
    this.previewLogo = dataUrl;
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const v = this.form.getRawValue();
    const input: Omit<Partnership, 'id' | 'createdAt'> = {
      name: v.name.trim(),
      email: v.email.trim(),
      phone: v.phone.trim() || undefined,
      website: v.website.trim() || undefined,
      industry: v.industry.trim() || undefined,
      description: v.description.trim() || undefined,
      logoDataUrl: v.logoDataUrl || undefined,
      isActive: v.isActive,
    };

    this.saving = true;
    this.saveError = null;

    const request$ = this.mode === 'create' ? this.store.add(input) : this.partnershipId ? this.store.update(this.partnershipId, input) : null;
    if (!request$) {
      this.saving = false;
      this.saveError = 'Identifiant partenaire manquant pour la mise a jour.';
      return;
    }

    this.sub.add(
      request$.subscribe({
        next: () => {
          this.saving = false;
          void this.router.navigateByUrl('/dashboard/partners');
        },
        error: (err: unknown) => {
          this.saving = false;
          this.saveError = this.extractApiError(err);
        },
      })
    );
  }

  cancel(): void {
    void this.router.navigateByUrl('/dashboard/partners');
  }

  private loadPartnershipForEdit(id: string): void {
    const existing = this.store.getById(id);
    if (existing) {
      this.applyPartnershipToForm(existing);
      return;
    }

    this.sub.add(
      this.store.reload().subscribe({
        next: () => {
          const loaded = this.store.getById(id);
          if (!loaded) {
            void this.router.navigateByUrl('/dashboard/partners');
            return;
          }
          this.applyPartnershipToForm(loaded);
        },
        error: () => {
          void this.router.navigateByUrl('/dashboard/partners');
        },
      })
    );
  }

  private applyPartnershipToForm(partnership: Partnership): void {
    this.previewLogo = partnership.logoDataUrl ?? null;
    this.form.reset({
      name: partnership.name ?? '',
      email: partnership.email ?? '',
      phone: partnership.phone ?? '',
      website: partnership.website ?? '',
      industry: partnership.industry ?? '',
      description: partnership.description ?? '',
      logoDataUrl: partnership.logoDataUrl ?? '',
      isActive: partnership.isActive ?? true,
    });
  }

  private extractApiError(err: unknown): string {
    if (err instanceof HttpErrorResponse) {
      const api = err.error as { message?: string; details?: string[] } | null;
      if (api?.message) {
        if (api.details?.length) return `${api.message}: ${api.details.join(', ')}`;
        return api.message;
      }
      if (err.status === 0) return `Backend inaccessible. Verifie que le service tourne sur ${environment.partnerApiBaseUrl}.`;
    }
    return 'Echec lors de la sauvegarde du partenaire.';
  }
}
