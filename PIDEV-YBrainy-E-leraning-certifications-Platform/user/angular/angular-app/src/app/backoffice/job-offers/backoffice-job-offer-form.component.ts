import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { AbstractControl, FormControl, FormGroup, ReactiveFormsModule, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { Subscription } from 'rxjs';
import { environment } from '@env/environment';
import { JobContractType, JobOffer } from '../../shared/models/job-offer.models';
import { Partnership } from '../../shared/models/partnership.models';
import { JobOfferStoreService } from '../../shared/stores/job-offer-store.service';
import { PartnershipStoreService } from '../../shared/stores/partnership-store.service';
import { fileToDataUrl } from '../../shared/utils/file-to-data-url';
import { applyBackofficeBodyClass } from '../utils/backoffice-body-class';

type OfferForm = FormGroup<{
  partnershipId: FormControl<string>;
  title: FormControl<string>;
  location: FormControl<string>;
  contractType: FormControl<JobContractType>;
  salaryRange: FormControl<string>;
  skillsCsv: FormControl<string>;
  description: FormControl<string>;
  imageDataUrl: FormControl<string>;
  deadline: FormControl<string>;
  isActive: FormControl<boolean>;
}>;

type LocationOption = {
  flag: string;
  city: string;
  country: string;
};

const CONTRACT_TYPES: JobContractType[] = ['CDI', 'CDD', 'Stage', 'Alternance', 'Freelance'];
const SALARY_PATTERN = /^(\d+([.,]\d+)?)\s*(-|to|a)\s*(\d+([.,]\d+)?)\s*[A-Za-z]{0,6}$|^\d+([.,]\d+)?\s*[A-Za-z]{0,6}$/i;
const LOCATION_OPTIONS: LocationOption[] = [
  { flag: '🇹🇳', city: 'Tunis', country: 'Tunisie' },
  { flag: '🇹🇳', city: 'Sfax', country: 'Tunisie' },
  { flag: '🇹🇳', city: 'Sousse', country: 'Tunisie' },
  { flag: '🇹🇳', city: 'Nabeul', country: 'Tunisie' },
  { flag: '🇹🇳', city: 'Monastir', country: 'Tunisie' },
  { flag: '🇲🇦', city: 'Casablanca', country: 'Maroc' },
  { flag: '🇲🇦', city: 'Rabat', country: 'Maroc' },
  { flag: '🇩🇿', city: 'Alger', country: 'Algerie' },
  { flag: '🇫🇷', city: 'Paris', country: 'France' },
  { flag: '🇩🇪', city: 'Berlin', country: 'Allemagne' },
  { flag: '🇮🇹', city: 'Milan', country: 'Italie' },
  { flag: '🇪🇸', city: 'Madrid', country: 'Espagne' },
  { flag: '🇬🇧', city: 'London', country: 'Royaume-Uni' },
  { flag: '🇨🇦', city: 'Montreal', country: 'Canada' },
  { flag: '🇺🇸', city: 'New York', country: 'Etats-Unis' },
  { flag: '🇦🇪', city: 'Dubai', country: 'EAU' },
];

function futureDateValidator(): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const value = (control.value as string | null | undefined)?.trim();
    if (!value) return null;
    const selected = new Date(value);
    if (Number.isNaN(selected.getTime())) return { invalidDate: true };
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    selected.setHours(0, 0, 0, 0);
    return selected <= today ? { notFutureDate: true } : null;
  };
}

@Component({
  selector: 'app-backoffice-job-offer-form',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterModule],
  templateUrl: './backoffice-job-offer-form.component.html',
  styleUrl: './backoffice-job-offer-form.component.css',
  host: { style: 'display:block' },
})
export class BackofficeJobOfferFormComponent implements OnInit, OnDestroy {
  mode: 'create' | 'edit' = 'create';
  offerId: string | null = null;
  partners: Partnership[] = [];
  previewImage: string | null = null;
  saveError: string | null = null;
  saving = false;
  showLocationDropdown = false;
  readonly locationSearchControl = new FormControl('', { nonNullable: true });
  readonly allLocations = LOCATION_OPTIONS;

  readonly contractTypes = CONTRACT_TYPES;

  form: OfferForm = new FormGroup({
    partnershipId: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    title: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.minLength(2)] }),
    location: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.minLength(2)] }),
    contractType: new FormControl<JobContractType>('Stage', { nonNullable: true, validators: [Validators.required] }),
    salaryRange: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.pattern(SALARY_PATTERN)] }),
    skillsCsv: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.minLength(2)] }),
    description: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.minLength(10)] }),
    imageDataUrl: new FormControl('', { nonNullable: true }),
    deadline: new FormControl('', { nonNullable: true, validators: [Validators.required, futureDateValidator()] }),
    isActive: new FormControl(true, { nonNullable: true }),
  });

  private readonly sub = new Subscription();
  private restoreBodyClass: (() => void) | null = null;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private offersStore: JobOfferStoreService,
    private partnersStore: PartnershipStoreService
  ) {}

  ngOnInit(): void {
    this.restoreBodyClass = applyBackofficeBodyClass();

    this.sub.add(
      this.partnersStore.items$.subscribe({
        next: (partners) => {
          this.partners = partners;
          if (this.mode === 'create' && !this.form.controls.partnershipId.value && partners.length > 0) {
            this.form.controls.partnershipId.setValue(partners[0].id);
          }
        },
        error: () => {
          this.partners = [];
          this.saveError = 'Impossible de charger les partenaires depuis le backend.';
        },
      })
    );
    this.sub.add(
      this.partnersStore.reload().subscribe({
        error: (err: unknown) => {
          this.saveError = this.extractApiError(err);
        },
      })
    );

    this.sub.add(
      this.route.paramMap.subscribe((pm) => {
        const id = pm.get('id');
        this.offerId = id;
        this.saveError = null;

        if (!id) {
          this.mode = 'create';
          this.previewImage = null;
          this.form.reset({
            partnershipId: this.partners[0]?.id ?? '',
            title: '',
            location: '',
            contractType: 'Stage',
            salaryRange: '',
            skillsCsv: '',
            description: '',
            imageDataUrl: '',
            deadline: '',
            isActive: true,
          });
          this.locationSearchControl.setValue('');
          return;
        }

        this.mode = 'edit';
        this.loadOfferForEdit(id);
      })
    );
  }

  ngOnDestroy(): void {
    this.sub.unsubscribe();
    this.restoreBodyClass?.();
    this.restoreBodyClass = null;
  }

  get title(): string {
    return this.mode === 'create' ? 'Nouvelle offre' : 'Modifier offre';
  }

  get minDeadline(): string {
    const d = new Date();
    d.setDate(d.getDate() + 1);
    return d.toISOString().slice(0, 10);
  }

  async onImageSelected(e: Event): Promise<void> {
    const input = e.target as HTMLInputElement | null;
    const file = input?.files?.[0] ?? null;
    if (!file) return;
    const dataUrl = await fileToDataUrl(file);
    this.form.controls.imageDataUrl.setValue(dataUrl);
    this.previewImage = dataUrl;
  }

  openDeadlinePicker(event: Event): void {
    const target = event.target as HTMLInputElement | null;
    if (!target || target.type !== 'date') return;
    const input = target as HTMLInputElement & { showPicker?: () => void };
    if (typeof input.showPicker !== 'function') return;
    try {
      input.showPicker();
    } catch {
      // Ignore browsers that block or do not support programmatic picker opening.
    }
  }

  get filteredLocations(): LocationOption[] {
    const query = this.locationSearchControl.value.trim().toLowerCase();
    if (!query) return this.allLocations;
    return this.allLocations.filter(
      (opt) =>
        opt.city.toLowerCase().includes(query) ||
        opt.country.toLowerCase().includes(query)
    );
  }

  onLocationFocus(): void {
    this.showLocationDropdown = true;
  }

  onLocationBlur(): void {
    setTimeout(() => {
      this.showLocationDropdown = false;
    }, 120);
  }

  selectLocation(option: LocationOption): void {
    const display = `${option.flag} ${option.city}, ${option.country}`;
    const raw = `${option.city}, ${option.country}`;
    this.locationSearchControl.setValue(display);
    this.form.controls.location.setValue(raw);
    this.form.controls.location.markAsTouched();
    this.showLocationDropdown = false;
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const v = this.form.getRawValue();
    const input: Omit<JobOffer, 'id' | 'createdAt'> = {
      partnershipId: v.partnershipId,
      title: v.title.trim(),
      location: v.location.trim() || undefined,
      contractType: v.contractType,
      salaryRange: v.salaryRange.trim() || undefined,
      skills: this.normalizeSkills(v.skillsCsv),
      description: v.description.trim() || undefined,
      imageDataUrl: v.imageDataUrl || undefined,
      deadline: v.deadline ? new Date(v.deadline).toISOString() : undefined,
      isActive: v.isActive,
    };

    this.saving = true;
    this.saveError = null;

    const request$ = this.mode === 'create' ? this.offersStore.add(input) : this.offerId ? this.offersStore.update(this.offerId, input) : null;
    if (!request$) {
      this.saving = false;
      this.saveError = 'Identifiant offre manquant pour la mise a jour.';
      return;
    }

    this.sub.add(
      request$.subscribe({
        next: () => {
          this.saving = false;
          void this.router.navigateByUrl('/dashboard/job-offers');
        },
        error: (err: unknown) => {
          this.saving = false;
          this.saveError = this.extractApiError(err);
        },
      })
    );
  }

  cancel(): void {
    void this.router.navigateByUrl('/dashboard/job-offers');
  }

  private normalizeSkills(csv: string): string[] | undefined {
    const arr = csv
      .split(',')
      .map((s) => s.trim())
      .filter(Boolean);
    return arr.length ? arr : undefined;
  }

  private loadOfferForEdit(id: string): void {
    const existing = this.offersStore.getById(id);
    if (existing) {
      this.applyOfferToForm(existing);
      return;
    }

    this.sub.add(
      this.offersStore.reload().subscribe({
        next: () => {
          const loaded = this.offersStore.getById(id);
          if (!loaded) {
            void this.router.navigateByUrl('/dashboard/job-offers');
            return;
          }
          this.applyOfferToForm(loaded);
        },
        error: () => {
          void this.router.navigateByUrl('/dashboard/job-offers');
        },
      })
    );
  }

  private applyOfferToForm(offer: JobOffer): void {
    this.previewImage = offer.imageDataUrl ?? null;
    this.form.reset({
      partnershipId: offer.partnershipId,
      title: offer.title ?? '',
      location: offer.location ?? '',
      contractType: offer.contractType,
      salaryRange: offer.salaryRange ?? '',
      skillsCsv: (offer.skills ?? []).join(', '),
      description: offer.description ?? '',
      imageDataUrl: offer.imageDataUrl ?? '',
      deadline: offer.deadline ? offer.deadline.slice(0, 10) : '',
      isActive: offer.isActive ?? true,
    });
    this.locationSearchControl.setValue(offer.location ?? '');
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
    return 'Echec lors de la sauvegarde de l\'offre.';
  }
}
