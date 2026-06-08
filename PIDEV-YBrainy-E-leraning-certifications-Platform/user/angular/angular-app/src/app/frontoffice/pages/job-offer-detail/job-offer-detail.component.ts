import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { environment } from '@env/environment';
import { JobOffer } from '../../../shared/models/job-offer.models';
import { AtsScoreResult } from '../../../shared/models/application-generator.models';
import { Partnership } from '../../../shared/models/partnership.models';
import { ApplicationGeneratorStoreService } from '../../../shared/stores/application-generator-store.service';
import { JobApplicationStoreService } from '../../../shared/stores/job-application-store.service';
import { JobOfferStoreService } from '../../../shared/stores/job-offer-store.service';
import { PartnershipStoreService } from '../../../shared/stores/partnership-store.service';
import { fileToDataUrl } from '../../../shared/utils/file-to-data-url';
import { extractBase64FromDataUrl } from '../../../shared/utils/profile-image-data-url';
import { computeAtsScore, downloadThemedCvPdf } from '../../../backoffice/ai-application/ai-application.utils';
import {
  CV_SKELETON_OPTIONS,
  CvSkeletonId,
  CvSkeletonOption,
} from '../../../shared/data/cv-skeleton.options';

type ApplyForm = FormGroup<{
  applicantName: FormControl<string>;
  applicantEmail: FormControl<string>;
  message: FormControl<string>;
  cvDataUrl: FormControl<string>;
}>;

type CvMode = 'upload' | 'optimizer';
type ResultTab = 'optimizedCV' | 'coverLetter';

@Component({
  selector: 'app-job-offer-detail',
  standalone: false,
  templateUrl: './job-offer-detail.component.html',
  styleUrls: ['./job-offer-detail.component.css'],
  host: { style: 'display:block' },
})
export class JobOfferDetailComponent implements OnInit, OnDestroy {
  private static readonly MAX_INPUT_CHARS = 1_000_000;
  private static readonly MAX_PROFILE_PHOTO_BYTES = 4_500_000;

  offerId: string | null = null;
  offer: JobOffer | null = null;
  partner: Partnership | null = null;
  offersLoadError: string | null = null;
  partnersLoadError: string | null = null;
  referencesReady = false;

  submitted = false;
  submitError: string | null = null;
  submitSuccess = false;

  cvMode: CvMode = 'upload';
  attachedCvLabel = '';

  optimizerCvText = '';
  optimizerFileName: string | null = null;
  optimizerJobDescription = '';
  optimizedCv = '';
  generatedCoverLetter = '';
  optimizerLoading = false;
  optimizerError: string | null = null;
  optimizerSuccess = false;
  activeTab: ResultTab = 'optimizedCV';
  atsResult: AtsScoreResult | null = null;

  readonly cvSkeletonOptions = CV_SKELETON_OPTIONS;
  selectedCvSkeletonId: CvSkeletonId = 'classic';

  profilePhotoDataUrl: string | null = null;
  profilePhotoLabel = '';
  enhancedProfilePhotoDataUrl: string | null = null;
  profilePhotoAssistantMessage: string | null = null;

  form: ApplyForm = new FormGroup({
    applicantName: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.minLength(2)] }),
    applicantEmail: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.email] }),
    message: new FormControl('', { nonNullable: true }),
    cvDataUrl: new FormControl('', { nonNullable: true }),
  });

  private readonly sub = new Subscription();

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private offersStore: JobOfferStoreService,
    private partnersStore: PartnershipStoreService,
    private generatorStore: ApplicationGeneratorStoreService,
    private applications: JobApplicationStoreService
  ) {}

  ngOnInit(): void {
    this.sub.add(
      this.route.paramMap.subscribe((pm) => {
        this.offerId = pm.get('offerId');
        this.refresh();
      })
    );
    this.sub.add(this.offersStore.items$.subscribe(() => this.refresh()));
    this.sub.add(this.partnersStore.items$.subscribe(() => this.refresh()));
    this.loadReferences();
  }

  ngOnDestroy(): void {
    this.sub.unsubscribe();
  }

  private refresh(): void {
    const id = this.offerId;
    if (!id) {
      this.offer = null;
      this.partner = null;
      return;
    }
    const o = this.offersStore.getById(id) ?? null;
    this.offer = o;
    this.partner = o ? this.partnersStore.getById(o.partnershipId) ?? null : null;

    if (this.offer && !this.optimizerJobDescription.trim()) {
      this.optimizerJobDescription = this.buildDefaultJobDescription(this.offer, this.partner);
    }
  }

  image(): string {
    return (
      this.offer?.imageDataUrl ||
      this.partner?.logoDataUrl ||
      'assets/frontoffice/www.ciklum.com/wp-content/uploads/2025/10/fi_6582502.png'
    );
  }

  get loadError(): string | null {
    return this.offersLoadError ?? this.partnersLoadError;
  }

  get isLoadingOffer(): boolean {
    return !this.offer && !this.referencesReady && !this.loadError;
  }

  get showNotFound(): boolean {
    return !this.offer && this.referencesReady && !this.loadError;
  }

  get hasResults(): boolean {
    return !!this.optimizedCv.trim() || !!this.generatedCoverLetter.trim();
  }

  get activeContent(): string {
    return this.activeTab === 'optimizedCV' ? this.optimizedCv : this.generatedCoverLetter;
  }

  get activeTitle(): string {
    return this.activeTab === 'optimizedCV' ? 'Optimized CV' : 'Cover Letter';
  }

  get selectedCvSkeleton(): CvSkeletonOption | undefined {
    return this.cvSkeletonOptions.find((s) => s.id === this.selectedCvSkeletonId);
  }

  selectCvSkeleton(id: CvSkeletonId): void {
    this.selectedCvSkeletonId = id;
    this.optimizerError = null;
  }

  /** Classes pour les miniatures / apercus colores type Canva */
  canvaClasses(size: 'sm' | 'lg', theme: 'classic' | 'tech' | 'compact' | 'academic'): string[] {
    return ['cv-canva', size === 'sm' ? 'cv-canva--sm' : 'cv-canva--lg', `cv-canva--${theme}`];
  }

  async onCvSelected(e: Event): Promise<void> {
    const input = e.target as HTMLInputElement | null;
    const file = input?.files?.[0] ?? null;
    if (!file) return;

    try {
      const dataUrl = await fileToDataUrl(file);
      this.form.controls.cvDataUrl.setValue(dataUrl);
      this.attachedCvLabel = file.name;
      this.cvMode = 'upload';
      this.optimizerSuccess = false;
    } catch {
      this.submitError = 'Impossible de lire ce fichier. Essaie un autre format.';
    }
  }

  setCvMode(mode: CvMode): void {
    this.cvMode = mode;
    this.optimizerError = null;
  }

  async onProfilePhotoSelected(e: Event): Promise<void> {
    const input = e.target as HTMLInputElement | null;
    const file = input?.files?.[0] ?? null;
    if (!file) return;
    if (!file.type.startsWith('image/')) {
      this.optimizerError = 'Choisis une image (JPG, PNG, WebP).';
      return;
    }
    if (file.size > JobOfferDetailComponent.MAX_PROFILE_PHOTO_BYTES) {
      this.optimizerError = 'Image trop volumineuse (max environ 4,5 Mo).';
      return;
    }
    try {
      const dataUrl = await fileToDataUrl(file);
      this.profilePhotoDataUrl = dataUrl;
      this.profilePhotoLabel = file.name;
      this.enhancedProfilePhotoDataUrl = null;
      this.profilePhotoAssistantMessage = null;
      this.optimizerError = null;
    } catch {
      this.optimizerError = 'Impossible de lire cette image.';
    }
    if (input) input.value = '';
  }

  clearProfilePhoto(): void {
    this.profilePhotoDataUrl = null;
    this.profilePhotoLabel = '';
    this.enhancedProfilePhotoDataUrl = null;
    this.profilePhotoAssistantMessage = null;
  }

  onOptimizerCvChange(payload: { text: string; fileName: string | null }): void {
    this.optimizerCvText = payload.text;
    this.optimizerFileName = payload.fileName;
    this.optimizerError = null;
  }

  onOptimizerJobDescriptionChange(value: string): void {
    this.optimizerJobDescription = value;
    this.optimizerError = null;
  }

  onResultChanged(content: string): void {
    if (this.activeTab === 'optimizedCV') {
      this.optimizedCv = content;
      this.atsResult = computeAtsScore(this.optimizerJobDescription, this.optimizedCv);
    } else {
      this.generatedCoverLetter = content;
    }

    if (this.optimizedCv.trim()) {
      this.attachOptimizedCvToApplication();
    }
  }

  setActiveTab(tab: ResultTab): void {
    this.activeTab = tab;
  }

  downloadPdf(): void {
    const filename = this.activeTab === 'optimizedCV' ? 'optimized-cv.pdf' : 'cover-letter.pdf';
    downloadThemedCvPdf(filename, this.activeTitle, this.activeContent, this.selectedCvSkeletonId);
  }

  generateOptimizedCv(): void {
    if (!this.optimizerCvText.trim() || !this.optimizerJobDescription.trim()) {
      this.optimizerError = 'Ajoute le texte du CV et la description du poste avant generation.';
      return;
    }

    if (
      this.optimizerCvText.length > JobOfferDetailComponent.MAX_INPUT_CHARS ||
      this.optimizerJobDescription.length > JobOfferDetailComponent.MAX_INPUT_CHARS
    ) {
      this.optimizerError = `Texte trop long. Garde chaque champ sous ${JobOfferDetailComponent.MAX_INPUT_CHARS} caracteres.`;
      return;
    }

    this.optimizerLoading = true;
    this.optimizerError = null;
    this.optimizerSuccess = false;

    const skeleton = this.cvSkeletonOptions.find((s) => s.id === this.selectedCvSkeletonId);
    const photoParts = this.profilePhotoDataUrl ? extractBase64FromDataUrl(this.profilePhotoDataUrl) : null;
    if (this.profilePhotoDataUrl && !photoParts) {
      this.optimizerLoading = false;
      this.optimizerError = 'Format de photo invalide. Reimporte une image.';
      return;
    }

    this.sub.add(
      this.generatorStore
        .generate({
          cv: this.optimizerCvText.trim(),
          jobDescription: this.optimizerJobDescription.trim(),
          cvSkeleton: skeleton?.promptStructure ?? null,
          profileImageBase64: photoParts?.base64 ?? undefined,
          profileImageMimeType: photoParts?.mime ?? undefined,
        })
        .subscribe({
          next: (response) => {
            this.optimizerLoading = false;
            this.optimizedCv = response.optimizedCV || '';
            this.generatedCoverLetter = response.coverLetter || '';
            this.enhancedProfilePhotoDataUrl = response.professionalProfilePhotoDataUrl ?? null;
            this.profilePhotoAssistantMessage = response.profilePhotoAssistantMessage ?? null;
            this.activeTab = 'optimizedCV';
            this.atsResult = computeAtsScore(this.optimizerJobDescription, this.optimizedCv);
            this.attachOptimizedCvToApplication();
            this.optimizerSuccess = true;
          },
          error: (err: unknown) => {
            this.optimizerLoading = false;
            this.optimizerError = this.extractGeneratorError(err);
            this.optimizerSuccess = false;
          },
        })
    );
  }

  apply(): void {
    this.submitted = true;
    this.submitError = null;
    this.submitSuccess = false;

    if (this.cvMode === 'optimizer' && !this.form.controls.cvDataUrl.value && this.optimizedCv.trim()) {
      this.attachOptimizedCvToApplication();
    }

    if (!this.offer) {
      this.submitError = 'Offre introuvable.';
      return;
    }

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const v = this.form.getRawValue();
    this.sub.add(
      this.applications
        .apply({
          offerId: this.offer.id,
          applicantName: v.applicantName.trim(),
          applicantEmail: v.applicantEmail.trim(),
          message: v.message.trim() || undefined,
          cvDataUrl: v.cvDataUrl || undefined,
        })
        .subscribe({
          next: () => {
            this.submitSuccess = true;
            this.form.reset({
              applicantName: '',
              applicantEmail: '',
              message: '',
              cvDataUrl: '',
            });
            this.attachedCvLabel = '';
            this.optimizerSuccess = false;
            this.optimizerError = null;
            this.submitted = false;
            this.clearProfilePhoto();
          },
          error: (err: unknown) => {
            this.submitSuccess = false;
            this.submitError = this.extractApplyError(err);
          },
        })
    );
  }

  back(): void {
    void this.router.navigateByUrl('/jobs');
  }

  private extractApplyError(err: unknown): string {
    if (err instanceof HttpErrorResponse) {
      const api = err.error as { message?: string; details?: string[] } | null;
      if (api?.message) {
        if (api.details?.length) return `${api.message}: ${api.details.join(', ')}`;
        return api.message;
      }
      if (err.status === 0) return 'Serveur inaccessible. Verifie que les microservices tournent.';
    }
    return 'Echec de l envoi de candidature.';
  }

  private buildDefaultJobDescription(offer: JobOffer, partner: Partnership | null): string {
    const skills = offer.skills?.length ? offer.skills.join(', ') : 'Non precisees';
    return [
      `Poste: ${offer.title}`,
      `Entreprise: ${partner?.name || 'Entreprise partenaire'}`,
      `Contrat: ${offer.contractType}`,
      `Localisation: ${offer.location || '-'}`,
      `Salaire: ${offer.salaryRange || '-'}`,
      `Competences: ${skills}`,
      '',
      offer.description || '',
    ]
      .join('\n')
      .trim();
  }

  private extractGeneratorError(err: unknown): string {
    const fallback = 'Generation IA impossible pour le moment.';
    if (!(err instanceof HttpErrorResponse)) return fallback;

    const api = err.error as { message?: string; details?: string[] } | null;
    if (api?.message) {
      if (api.details?.length) return `${api.message}: ${api.details.join(', ')}`;
      return api.message;
    }

    if (err.status === 0) return 'Service IA inaccessible. Verifie le backend.';
    return fallback;
  }

  private attachOptimizedCvToApplication(): void {
    const content = this.optimizedCv.trim();
    if (!content) return;

    const cover = this.generatedCoverLetter.trim();
    const merged = [
      'Optimized CV',
      '',
      content,
      '',
      '----------------------------------------',
      'Generated Cover Letter',
      '',
      cover || 'N/A',
    ].join('\n');

    this.form.controls.cvDataUrl.setValue(this.textToDataUrl(merged));
    this.attachedCvLabel = 'cv-optimise-ai.txt';
  }

  private textToDataUrl(text: string): string {
    const bytes = new TextEncoder().encode(text);
    const chunkSize = 0x8000;
    let binary = '';

    for (let i = 0; i < bytes.length; i += chunkSize) {
      const chunk = bytes.slice(i, i + chunkSize);
      binary += String.fromCharCode(...chunk);
    }

    return `data:text/plain;charset=utf-8;base64,${btoa(binary)}`;
  }

  private loadReferences(): void {
    this.referencesReady = false;
    let pendingLoads = 2;

    const markSettled = (): void => {
      pendingLoads -= 1;
      if (pendingLoads <= 0) {
        this.referencesReady = true;
      }
    };

    this.sub.add(
      this.offersStore.reload().subscribe({
        next: () => {
          this.offersLoadError = null;
          markSettled();
        },
        error: (error: unknown) => {
          this.offersLoadError = this.buildReferenceErrorMessage(error, 'Unable to load job offers right now.');
          markSettled();
        },
      })
    );

    this.sub.add(
      this.partnersStore.reload().subscribe({
        next: () => {
          this.partnersLoadError = null;
          markSettled();
        },
        error: (error: unknown) => {
          this.partnersLoadError = this.buildReferenceErrorMessage(
            error,
            'Unable to load partner information right now.'
          );
          markSettled();
        },
      })
    );
  }

  private buildReferenceErrorMessage(error: unknown, fallback: string): string {
    if (!(error instanceof HttpErrorResponse)) {
      return fallback;
    }

    const apiMessage =
      typeof error.error === 'object' && error.error && typeof (error.error as { message?: unknown }).message === 'string'
        ? (error.error as { message: string }).message.trim()
        : '';

    if (apiMessage) {
      return apiMessage;
    }

    if (error.status === 0) {
      return `Job data is unavailable right now. Check that the partner backend is running on ${environment.partnerApiBaseUrl}.`;
    }

    return fallback;
  }

}
