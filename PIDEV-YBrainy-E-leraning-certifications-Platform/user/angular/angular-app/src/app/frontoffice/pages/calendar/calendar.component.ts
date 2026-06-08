import { AfterViewInit, Component, OnDestroy } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { SafeHtml } from '@angular/platform-browser';
import { Router } from '@angular/router';
import { Subscription, firstValueFrom } from 'rxjs';
import { redirectToAppLogin } from '../../../auth/keycloak.service';
import { FrontofficeStaticPageService } from '../../services/frontoffice-static-page.service';
import { FrontofficeUiInitService } from '../../services/frontoffice-ui-init.service';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-calendar',
  standalone: false,
  templateUrl: './calendar.component.html',
  styleUrls: ['./calendar.component.css'],
  host: { 'style': 'display:block' }
})
export class CalendarComponent implements AfterViewInit, OnDestroy {
  private static readonly statusSnapshotStorageKey = 'frontoffice-calendar-student-statuses';
  private static readonly heroRecommendationLoaderMs = 5000;
  private static readonly feedbackSentimentDebounceMs = 900;
  mainHtml: SafeHtml | null = null;
  pageVisible = false;
  confirmRegisterOpen = false;
  confirmRegisterBusy = false;
  confirmRegisterEventName = '';
  confirmCancelOpen = false;
  confirmCancelBusy = false;
  confirmCancelEventName = '';
  confirmationToastTitle = '';
  confirmationToastMessage = '';
  confirmationToastTone: StatusFxTone = 'confirmed';
  confirmationToastVisible = false;
  feedbackModalOpen = false;
  feedbackSubmitting = false;
  feedbackRecording = false;
  feedbackTranscribing = false;
  feedbackRating = 5;
  feedbackComment = '';
  feedbackError = '';
  feedbackSentimentLabel = '';
  feedbackSentimentSummary = '';
  feedbackSentimentScores: Record<string, number> = {};
  feedbackSentimentLoading = false;
  feedbackTargetEventName = '';
  feedbackSpeechStatus = '';
  private feedbackTargetEventId: number | null = null;
  private previousBodyClass: string | null = null;
  // Use same-origin relative endpoints so the dev proxy can forward to local microservices
  // without CORS issues (and so prod deployments can route via a gateway if needed).
  private readonly eventsApiUrl = '/Event/all';
  private readonly eventBaseUrl = '/Event';
  private readonly inscriptionBaseUrl = '/Inscription';
  private readonly feedbackBaseUrl = '/Feedback';
  private readonly recommendationBaseUrl = '/api/recommendations';
  private readonly pythonRecommendationBaseUrl = '/api/recommendations/python';
  private readonly pythonSentimentBaseUrl = '/api/sentiment/python';
  private allEvents: FrontofficeEvent[] = [];
  private selectedStudentId: number | null = null;
  private studentEventStatuses = new Map<number, string>();
  private studentFeedbacks = new Map<number, StudentFeedback>();
  private recommendedEvents: RecommendedEvent[] = [];
  private aiRecommendedEvents: RecommendedEvent[] = [];
  private loadingRecommendations = false;
  private heroRecommendationOpen = false;
  private heroRecommendationLoading = false;
  private heroRecommendationSource: 'classic' | 'python' = 'classic';
  private activeFilter: FrontofficeFilter = 'ALL';
  private statusFilter: FrontofficeStatusFilter = 'ALL';
  private searchQuery = '';
  private sortOrder: FrontofficeSortOrder = 'DATE_ASC';
  private currentPage = 1;
  private miniCalendarMonth = new Date(new Date().getFullYear(), new Date().getMonth(), 1);
  private cleanupFns: Array<() => void> = [];
  private pendingRegisterAction: { idEvent: number; button: HTMLAnchorElement } | null = null;
  private pendingCancelAction: { idEvent: number; button: HTMLAnchorElement } | null = null;
  private statusPollTimer: ReturnType<typeof setInterval> | null = null;
  private statusHydrated = false;
  private eventStatusEffects = new Map<number, StatusFxTone>();
  private confirmationCleanupTimers = new Map<number, ReturnType<typeof setTimeout>>();
  private confirmationToastTimer: ReturnType<typeof setTimeout> | null = null;
  private pendingFocusEventId: number | null = null;
  private heroRecommendationTimer: ReturnType<typeof setTimeout> | null = null;
  private authStateSub: Subscription | null = null;
  private feedbackRecorder: MediaRecorder | null = null;
  private feedbackRecorderStream: MediaStream | null = null;
  private feedbackAudioChunks: Blob[] = [];
  private feedbackRecorderMimeType = 'audio/webm';
  private shouldTranscribeFeedbackRecording = true;
  private feedbackSentimentDebounceTimer: ReturnType<typeof setTimeout> | null = null;
  private feedbackSentimentRequestSeq = 0;
  private feedbackSentimentLastAnalyzedComment = '';

  constructor(
    private staticPage: FrontofficeStaticPageService,
    private uiInit: FrontofficeUiInitService,
    private http: HttpClient,
    private router: Router,
    private auth: AuthService
  ) {}

  async ngAfterViewInit(): Promise<void> {
    this.pageVisible = false;
    const { bodyClass, mainHtml } = await this.staticPage.load(
      'assets/frontoffice/www.ciklum.com/videos/index.html',
      'main.main-container'
    );

    this.previousBodyClass = document.body.getAttribute('class');
    document.body.setAttribute('class', bodyClass || '');

    this.mainHtml = mainHtml;
    this.uiInit.initAfterDomPaint();
    this.authStateSub = this.auth.currentUser$.subscribe(() => {
      void this.handleAuthStateChange();
    });
    setTimeout(() => {
      void this.renderBackofficeEventsIntoFrontoffice();
    }, 0);
  }

  ngOnDestroy(): void {
    this.authStateSub?.unsubscribe();
    this.authStateSub = null;
    this.cleanupFns.forEach((fn) => fn());
    this.cleanupFns = [];

    if (this.previousBodyClass !== null) {
      document.body.setAttribute('class', this.previousBodyClass);
    }

    this.stopStatusPolling();
    this.clearConfirmationTimers();
    this.closeRegisterConfirmation();
    this.closeCancelConfirmation();
    this.abortFeedbackSpeech();
    this.closeFeedbackModal();
    this.clearHeroRecommendationTimer();
    this.clearFeedbackSentimentDebounce();
  }

  private async renderBackofficeEventsIntoFrontoffice(): Promise<void> {
    const pageRoot = document.querySelector('.fo-static-page') as HTMLElement | null;
    if (!pageRoot) {
      this.pageVisible = true;
      return;
    }

    this.prepareCalendarShell(pageRoot);

    // Use the currently logged-in user's ID — no dropdown needed
    this.selectedStudentId = this.auth.currentUserId;
    if (!this.selectedStudentId) {
      console.warn('Calendar: no authenticated user — events will be shown without registration status');
    }

    let events: FrontofficeEvent[] = [];
    try {
      const response = await firstValueFrom(this.http.get<FrontofficeEvent[]>(this.eventsApiUrl));
      events = Array.isArray(response) ? response : [];
    } catch (error) {
      console.error('Failed to load backoffice events for frontoffice calendar', error);
      this.pageVisible = true;
      return;
    }

    this.allEvents = events
      .filter((ev) => !!ev && !!ev.name)
      .sort((a, b) => {
        const aEnded = String(a.statut || '').toUpperCase() === 'TERMINE';
        const bEnded = String(b.statut || '').toUpperCase() === 'TERMINE';
        if (aEnded !== bEnded) return aEnded ? 1 : -1;
        return this.toDate(a.dateDebut).getTime() - this.toDate(b.dateDebut).getTime();
      });

    if (this.allEvents.length) {
      const firstDate = this.toDate(this.allEvents[0].dateDebut);
      this.miniCalendarMonth = new Date(firstDate.getFullYear(), firstDate.getMonth(), 1);
    }

    await this.loadRegisteredEventIdsForSelectedStudent();
    await this.loadRecommendedEventForSelectedStudent();
    this.bindCalendarInteractions(pageRoot);
    this.renderCalendarData(pageRoot);
    this.pageVisible = true;
    this.replayPendingEntryAnimation(pageRoot);
    this.startStatusPolling();
  }

  private async handleAuthStateChange(): Promise<void> {
    const nextStudentId = this.auth.currentUserId;
    if (this.selectedStudentId === nextStudentId) return;

    this.selectedStudentId = nextStudentId;
    this.studentEventStatuses = new Map<number, string>();
    this.studentFeedbacks = new Map<number, StudentFeedback>();
    this.recommendedEvents = [];
    this.aiRecommendedEvents = [];
    this.statusHydrated = false;
    this.clearConfirmationTimers();
    this.closeRegisterConfirmation();
    this.closeCancelConfirmation();
    this.closeFeedbackModal();

    const pageRoot = document.querySelector('.fo-static-page') as HTMLElement | null;
    if (!pageRoot || !this.pageVisible) return;

    await this.loadRegisteredEventIdsForSelectedStudent();
    await this.loadRecommendedEventForSelectedStudent();
    this.renderCalendarData(pageRoot);
  }

  private prepareCalendarShell(pageRoot: HTMLElement): void {
    const eventsColumn = pageRoot.querySelector('.cal-events-col') as HTMLElement | null;
    if (eventsColumn) {
      eventsColumn.id = 'cal-events';
    }

    const heroUpcomingButton = pageRoot.querySelector(
      '[data-upcoming-focus-trigger="true"], .services-ban-text .button, a[href="#cal-events"]'
    ) as HTMLAnchorElement | null;
    if (heroUpcomingButton) {
      heroUpcomingButton.setAttribute('href', 'javascript:void(0)');
      heroUpcomingButton.setAttribute('role', 'button');
      heroUpcomingButton.setAttribute('data-upcoming-focus-trigger', 'true');
    }

    let heroRecommendationButton = pageRoot.querySelector(
      '[data-show-recommendation-trigger="true"]'
    ) as HTMLElement | null;
    if (heroRecommendationButton) {
      const replacementButton = this.createHeroRecommendationButton();
      heroRecommendationButton.replaceWith(replacementButton);
      heroRecommendationButton = replacementButton;
    } else if (heroUpcomingButton?.parentElement) {
      const injectedRecommendationButton = this.createHeroRecommendationButton();
      heroUpcomingButton.parentElement.classList.add('hero-events-actions');
      heroUpcomingButton.parentElement.appendChild(injectedRecommendationButton);
      heroRecommendationButton = injectedRecommendationButton;
    }

    let heroCodelabButton = pageRoot.querySelector(
      '[data-open-codelab-trigger="true"]'
    ) as HTMLElement | null;
    if (heroCodelabButton) {
      const replacementCodeLabButton = this.createHeroCodeLabButton();
      heroCodelabButton.replaceWith(replacementCodeLabButton);
      heroCodelabButton = replacementCodeLabButton;
    } else if (heroUpcomingButton?.parentElement) {
      const injectedCodeLabButton = this.createHeroCodeLabButton();
      heroUpcomingButton.parentElement.classList.add('hero-events-actions');
      heroUpcomingButton.parentElement.appendChild(injectedCodeLabButton);
      heroCodelabButton = injectedCodeLabButton;
    }

    if (heroUpcomingButton?.parentElement) {
      heroUpcomingButton.parentElement.classList.add('hero-events-actions');
    }

    let heroPythonRecommendationButton = pageRoot.querySelector(
      '[data-show-python-recommendation-trigger="true"]'
    ) as HTMLElement | null;
    if (!heroPythonRecommendationButton && heroUpcomingButton?.parentElement) {
      const injectedPythonRecommendationButton = this.createHeroPythonRecommendationButton();
      heroUpcomingButton.parentElement.classList.add('hero-events-actions');
      heroUpcomingButton.parentElement.appendChild(injectedPythonRecommendationButton);
      heroPythonRecommendationButton = injectedPythonRecommendationButton;
    }

    this.normalizeHeroActionButtons(pageRoot);

    let heroRecommendationSlot = pageRoot.querySelector('.hero-recommendation-slot') as HTMLElement | null;
    const heroActions =
      (pageRoot.querySelector('.hero-events-actions') as HTMLElement | null) ||
      (heroRecommendationButton?.parentElement as HTMLElement | null) ||
      (heroUpcomingButton?.parentElement as HTMLElement | null);
    if (!heroRecommendationSlot && heroActions) {
      heroRecommendationSlot = document.createElement('div');
      heroRecommendationSlot.className = 'hero-recommendation-slot';
      heroActions.insertAdjacentElement('afterend', heroRecommendationSlot);
    }

    let recommendationSlot = pageRoot.querySelector('.cal-recommendation-slot') as HTMLElement | null;
    const sectionLabel = pageRoot.querySelector('.cal-section-label') as HTMLElement | null;
    if (!recommendationSlot && eventsColumn && sectionLabel) {
      recommendationSlot = document.createElement('div');
      recommendationSlot.className = 'cal-recommendation-slot';
      eventsColumn.insertBefore(recommendationSlot, sectionLabel);
    }

    const listEl = pageRoot.querySelector('.cal-events-list') as HTMLElement | null;
    if (listEl) {
      listEl.innerHTML = '';
    }

    let paginationEl = pageRoot.querySelector('.cal-events-pagination') as HTMLElement | null;
    if (!paginationEl && listEl?.parentElement) {
      paginationEl = document.createElement('div');
      paginationEl.className = 'cal-events-pagination';
      listEl.parentElement.appendChild(paginationEl);
    }

    if (sectionLabel) {
      sectionLabel.textContent = 'Events Schedule';
    }

    let controlsBar = pageRoot.querySelector('.cal-events-controls') as HTMLElement | null;
    if (!controlsBar && eventsColumn && sectionLabel) {
      controlsBar = document.createElement('div');
      controlsBar.className = 'cal-events-controls';
      controlsBar.innerHTML = `
        <label class="cal-search-shell" aria-label="Search events">
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path d="M10.5 4a6.5 6.5 0 1 1 0 13a6.5 6.5 0 0 1 0-13Zm0 1.8a4.7 4.7 0 1 0 0 9.4a4.7 4.7 0 0 0 0-9.4Zm8.37 11.3l1.93 1.92a.9.9 0 1 1-1.27 1.28l-1.93-1.93a.9.9 0 0 1 1.27-1.27Z"></path>
          </svg>
          <input type="search" class="cal-search-input" placeholder="Search by event, location, or keyword" />
        </label>
        <div class="cal-events-selects">
          <label class="cal-select-shell">
            <span>Status</span>
            <select class="cal-status-select">
              <option value="ALL">All statuses</option>
              <option value="PUBLIE">Published</option>
              <option value="TERMINE">Finished</option>
            </select>
          </label>
          <label class="cal-select-shell">
            <span>Sort by</span>
            <select class="cal-sort-select">
              <option value="DATE_ASC">Date: Soonest</option>
              <option value="DATE_DESC">Date: Latest</option>
              <option value="NAME_ASC">Name: A to Z</option>
              <option value="NAME_DESC">Name: Z to A</option>
            </select>
          </label>
        </div>
      `;
      sectionLabel.insertAdjacentElement('beforebegin', controlsBar);
    }

    const searchInput = pageRoot.querySelector('.cal-search-input') as HTMLInputElement | null;
    const statusSelect = pageRoot.querySelector('.cal-status-select') as HTMLSelectElement | null;
    const sortSelect = pageRoot.querySelector('.cal-sort-select') as HTMLSelectElement | null;
    if (searchInput) searchInput.value = this.searchQuery;
    if (statusSelect) statusSelect.value = this.statusFilter;
    if (sortSelect) sortSelect.value = this.sortOrder;
  }

  private createHeroRecommendationButton(): HTMLButtonElement {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'btn hero-recommend-button';
    button.setAttribute('data-show-recommendation-trigger', 'true');
    button.innerHTML = `
      <svg class="btn-svg" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" aria-hidden="true">
        <path
          stroke-linecap="round"
          stroke-linejoin="round"
          d="M9.813 15.904 9 18.75l-.813-2.846a4.5 4.5 0 0 0-3.09-3.09L2.25 12l2.846-.813a4.5 4.5 0 0 0 3.09-3.09L9 5.25l.813 2.846a4.5 4.5 0 0 0 3.09 3.09L15.75 12l-2.846.813a4.5 4.5 0 0 0-3.09 3.09ZM18.259 8.715 18 9.75l-.259-1.035a3.375 3.375 0 0 0-2.455-2.456L14.25 6l1.036-.259a3.375 3.375 0 0 0 2.455-2.456L18 2.25l.259 1.035a3.375 3.375 0 0 0 2.456 2.456L21.75 6l-1.035.259a3.375 3.375 0 0 0-2.456 2.456ZM16.894 20.567 16.5 21.75l-.394-1.183a2.25 2.25 0 0 0-1.423-1.423L13.5 18.75l1.183-.394a2.25 2.25 0 0 0 1.423-1.423l.394-1.183.394 1.183a2.25 2.25 0 0 0 1.423 1.423l1.183.394-1.183.394a2.25 2.25 0 0 0-1.423 1.423Z"
        ></path>
      </svg>
      <div class="txt-wrapper">
        <div class="txt-1">
        <span class="btn-letter">I</span>
        <span class="btn-letter">A</span>
        <span class="btn-space" aria-hidden="true"></span>
          <span class="btn-letter">S</span>
          <span class="btn-letter">u</span>
          <span class="btn-letter">g</span>
          <span class="btn-letter">g</span>
          <span class="btn-letter">e</span>
          <span class="btn-letter">s</span>
          <span class="btn-letter">t</span>
          <span class="btn-space" aria-hidden="true"></span>
          <span class="btn-letter">E</span>
          <span class="btn-letter">v</span>
          <span class="btn-letter">e</span>
          <span class="btn-letter">n</span>
          <span class="btn-letter">t</span>
        </div>
        <div class="txt-2">
          
          <span class="btn-letter">S</span>
          <span class="btn-letter">u</span>
          <span class="btn-letter">g</span>
          <span class="btn-letter">g</span>
          <span class="btn-letter">e</span>
          <span class="btn-letter">s</span>
          <span class="btn-letter">t</span>
          <span class="btn-space" aria-hidden="true"></span>
          <span class="btn-letter">E</span>
          <span class="btn-letter">v</span>
          <span class="btn-letter">e</span>
          <span class="btn-letter">n</span>
          <span class="btn-letter">t</span>
        </div>
      </div>
    `;
    return button;
  }

  private createHeroPythonRecommendationButton(): HTMLButtonElement {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'btn hero-recommend-button hero-python-recommend-button';
    button.setAttribute('data-show-python-recommendation-trigger', 'true');
    button.innerHTML = `
      <svg class="btn-svg" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" aria-hidden="true">
        <path
          stroke-linecap="round"
          stroke-linejoin="round"
          d="M12 3L14.25 8.25L19.5 10.5L14.25 12.75L12 18L9.75 12.75L4.5 10.5L9.75 8.25L12 3ZM18 14.25L19.125 16.875L21.75 18L19.125 19.125L18 21.75L16.875 19.125L14.25 18L16.875 16.875L18 14.25ZM6 14.25L6.75 16.5L9 17.25L6.75 18L6 20.25L5.25 18L3 17.25L5.25 16.5L6 14.25Z"
        ></path>
      </svg>
      <div class="txt-wrapper">
        <div class="txt-1">
          <span class="btn-letter">P</span>
          <span class="btn-letter">y</span>
          <span class="btn-letter">t</span>
          <span class="btn-letter">h</span>
          <span class="btn-letter">o</span>
          <span class="btn-letter">n</span>
          <span class="btn-space" aria-hidden="true"></span>
          <span class="btn-letter">M</span>
          <span class="btn-letter">L</span>
          <span class="btn-space" aria-hidden="true"></span>
          <span class="btn-letter">S</span>
          <span class="btn-letter">u</span>
          <span class="btn-letter">g</span>
          <span class="btn-letter">g</span>
          <span class="btn-letter">e</span>
          <span class="btn-letter">s</span>
          <span class="btn-letter">t</span>
        </div>
        <div class="txt-2">
          <span class="btn-letter">M</span>
          <span class="btn-letter">L</span>
          <span class="btn-space" aria-hidden="true"></span>
          <span class="btn-letter">R</span>
          <span class="btn-letter">e</span>
          <span class="btn-letter">c</span>
          <span class="btn-letter">o</span>
          <span class="btn-letter">m</span>
          <span class="btn-letter">m</span>
          <span class="btn-letter">e</span>
          <span class="btn-letter">n</span>
          <span class="btn-letter">d</span>
        </div>
      </div>
    `;
    return button;
  }

  private createHeroCodeLabButton(): HTMLAnchorElement {
    const button = document.createElement('a');
    button.className = 'hero-recommend-button hero-codelab-button';
    button.setAttribute('href', 'javascript:void(0)');
    button.setAttribute('role', 'button');
    button.setAttribute('data-open-codelab-trigger', 'true');
    button.innerHTML = `
      <span class="hero-codelab-icon" aria-hidden="true">&lt;/&gt;</span>
      <span class="hero-codelab-label">Open CodeStudio</span>
    `;
    return button;
  }

  private normalizeHeroActionButtons(pageRoot: HTMLElement): void {
    const actionButtons = Array.from(
      pageRoot.querySelectorAll<HTMLElement>('.hero-events-actions a, .hero-events-actions button')
    );

    actionButtons.forEach((element) => {
      const label = (element.textContent || '').replace(/\s+/g, ' ').trim().toLowerCase();

      if (label.includes('code') && label.includes('studio') && !element.querySelector('.hero-codelab-icon')) {
        const replacement = this.createHeroCodeLabButton();
        element.replaceWith(replacement);
        return;
      }

      const isRecommendationLabel =
        label.includes('recommended event') ||
        label.includes('suggest event') ||
        label.includes('recommend event');

      if (isRecommendationLabel && !element.querySelector('.btn-svg')) {
        const replacement = this.createHeroRecommendationButton();
        element.replaceWith(replacement);
      }
    });
  }

  private bindCalendarInteractions(pageRoot: HTMLElement): void {
    this.cleanupFns.forEach((fn) => fn());
    this.cleanupFns = [];

    this.normalizeFilterChips(pageRoot);

    const filterButtons = Array.from(pageRoot.querySelectorAll('.cal-filter-chip')) as HTMLButtonElement[];
    filterButtons.forEach((button) => {
      const onClick = () => {
        const filter = this.filterFromLabel(button.textContent || '');
        this.activeFilter = filter;
        this.currentPage = 1;
        filterButtons.forEach((b) => b.classList.remove('active'));
        button.classList.add('active');
        this.renderCalendarData(pageRoot);
      };

      button.addEventListener('click', onClick);
      this.cleanupFns.push(() => button.removeEventListener('click', onClick));
    });

    const searchInput = pageRoot.querySelector('.cal-search-input') as HTMLInputElement | null;
    if (searchInput) {
      const onSearch = () => {
        this.searchQuery = searchInput.value.trim();
        this.currentPage = 1;
        this.renderCalendarData(pageRoot);
      };
      searchInput.addEventListener('input', onSearch);
      this.cleanupFns.push(() => searchInput.removeEventListener('input', onSearch));
    }

    const statusSelect = pageRoot.querySelector('.cal-status-select') as HTMLSelectElement | null;
    if (statusSelect) {
      const onStatusChange = () => {
        const nextValue = String(statusSelect.value || 'ALL').toUpperCase() as FrontofficeStatusFilter;
        this.statusFilter = nextValue;
        this.currentPage = 1;
        this.renderCalendarData(pageRoot);
      };
      statusSelect.addEventListener('change', onStatusChange);
      this.cleanupFns.push(() => statusSelect.removeEventListener('change', onStatusChange));
    }

    const sortSelect = pageRoot.querySelector('.cal-sort-select') as HTMLSelectElement | null;
    if (sortSelect) {
      const onSortChange = () => {
        this.sortOrder = String(sortSelect.value || 'DATE_ASC').toUpperCase() as FrontofficeSortOrder;
        this.currentPage = 1;
        this.renderCalendarData(pageRoot);
      };
      sortSelect.addEventListener('change', onSortChange);
      this.cleanupFns.push(() => sortSelect.removeEventListener('change', onSortChange));
    }

    const navButtons = Array.from(pageRoot.querySelectorAll('.cal-mini .cal-nav button')) as HTMLButtonElement[];
    if (navButtons.length >= 2) {
      const onPrev = () => {
        this.miniCalendarMonth = new Date(this.miniCalendarMonth.getFullYear(), this.miniCalendarMonth.getMonth() - 1, 1);
        const events = this.getFilteredEvents();
        this.renderMiniCalendar(pageRoot, events);
        this.renderComingIn(pageRoot, events);
      };
      const onNext = () => {
        this.miniCalendarMonth = new Date(this.miniCalendarMonth.getFullYear(), this.miniCalendarMonth.getMonth() + 1, 1);
        const events = this.getFilteredEvents();
        this.renderMiniCalendar(pageRoot, events);
        this.renderComingIn(pageRoot, events);
      };

      navButtons[0].addEventListener('click', onPrev);
      navButtons[1].addEventListener('click', onNext);
      this.cleanupFns.push(() => navButtons[0].removeEventListener('click', onPrev));
      this.cleanupFns.push(() => navButtons[1].removeEventListener('click', onNext));
    }

    const heroUpcomingButton = pageRoot.querySelector(
      '[data-upcoming-focus-trigger="true"], .services-ban-text .button, a[href="#cal-events"]'
    ) as HTMLAnchorElement | null;
    if (heroUpcomingButton) {
      const onHeroUpcoming = (event: Event) => {
        event.preventDefault();
        this.openUpcomingFocus(pageRoot);
      };
      heroUpcomingButton.addEventListener('click', onHeroUpcoming);
      this.cleanupFns.push(() => heroUpcomingButton.removeEventListener('click', onHeroUpcoming));
    }

    const heroRecommendationButton = pageRoot.querySelector(
      '[data-show-recommendation-trigger="true"]'
    ) as HTMLAnchorElement | null;
    if (heroRecommendationButton) {
      const onHeroRecommendation = (event: Event) => {
        event.preventDefault();
        void this.openInlineRecommendationCard(pageRoot);
      };
      heroRecommendationButton.addEventListener('click', onHeroRecommendation);
      this.cleanupFns.push(() => heroRecommendationButton.removeEventListener('click', onHeroRecommendation));
    }

    const heroPythonRecommendationButton = pageRoot.querySelector(
      '[data-show-python-recommendation-trigger="true"]'
    ) as HTMLAnchorElement | null;
    if (heroPythonRecommendationButton) {
      const onHeroPythonRecommendation = (event: Event) => {
        event.preventDefault();
        void this.openInlinePythonRecommendationCard(pageRoot);
      };
      heroPythonRecommendationButton.addEventListener('click', onHeroPythonRecommendation);
      this.cleanupFns.push(() =>
        heroPythonRecommendationButton.removeEventListener('click', onHeroPythonRecommendation)
      );
    }

    const heroCodelabButton = pageRoot.querySelector(
      '[data-open-codelab-trigger="true"]'
    ) as HTMLAnchorElement | null;
    if (heroCodelabButton) {
      const onHeroCodelab = (event: Event) => {
        event.preventDefault();
        void this.router.navigate(['/codelab']);
      };
      heroCodelabButton.addEventListener('click', onHeroCodelab);
      this.cleanupFns.push(() => heroCodelabButton.removeEventListener('click', onHeroCodelab));
    }

    const heroRecommendationSlot = pageRoot.querySelector('.hero-recommendation-slot') as HTMLElement | null;
    if (heroRecommendationSlot) {
      const onHeroRecommendationAction = (event: Event) => {
        const rawTarget = event.target;
        const button = rawTarget instanceof Element ? rawTarget.closest('[data-recommend-action]') as HTMLElement | null : null;
        if (!button) return;
        event.preventDefault();
        const tid = Number(button.getAttribute('data-target-id'));
        this.openUpcomingFocus(pageRoot, tid > 0 ? tid : undefined);
      };
      heroRecommendationSlot.addEventListener('click', onHeroRecommendationAction);
      this.cleanupFns.push(() => heroRecommendationSlot.removeEventListener('click', onHeroRecommendationAction));
    }

    const listEl = pageRoot.querySelector('.cal-events-list') as HTMLElement | null;
    if (listEl) {
      const onRegisterClick = (event: Event) => {
        const rawTarget = event.target;
        const target =
          rawTarget instanceof Element
            ? rawTarget
            : rawTarget instanceof Node
              ? rawTarget.parentElement
              : null;
        const button = target?.closest('.cal-join-btn, .cal-cancel-btn, .cal-feedback-btn') as HTMLAnchorElement | null;
        if (!button) return;

        event.preventDefault();
        if (button.classList.contains('is-loading')) return;
        if (!this.selectedStudentId) {
          if (button.classList.contains('cal-requires-login')) {
            redirectToAppLogin(window.location.href);
          }
          return;
        }

        const idRaw = button.getAttribute('data-event-id');
        const idEvent = Number(idRaw);
        if (!Number.isFinite(idEvent) || idEvent <= 0) return;

        if (button.classList.contains('cal-cancel-btn')) {
          this.openCancelConfirmation(idEvent, button);
          return;
        }

        if (button.classList.contains('cal-feedback-btn')) {
          this.openFeedbackModal(idEvent);
          return;
        }

        this.openRegisterConfirmation(idEvent, button);
      };

      listEl.addEventListener('click', onRegisterClick);
      this.cleanupFns.push(() => listEl.removeEventListener('click', onRegisterClick));
    }

    const recommendationSlot = pageRoot.querySelector('.cal-recommendation-slot') as HTMLElement | null;
    if (recommendationSlot) {
      const onRecommendationClick = (event: Event) => {
        const rawTarget = event.target;
        const button = rawTarget instanceof Element ? rawTarget.closest('[data-recommend-action]') as HTMLElement | null : null;
        if (!button) return;
        event.preventDefault();
        const tid = Number(button.getAttribute('data-target-id'));
        this.openUpcomingFocus(pageRoot, tid > 0 ? tid : undefined);
      };
      recommendationSlot.addEventListener('click', onRecommendationClick);
      this.cleanupFns.push(() => recommendationSlot.removeEventListener('click', onRecommendationClick));
    }

    const paginationEl = pageRoot.querySelector('.cal-events-pagination') as HTMLElement | null;
    if (paginationEl) {
      const onPaginationClick = (event: Event) => {
        const rawTarget = event.target;
        const target = rawTarget instanceof Element ? rawTarget.closest('[data-page-action]') as HTMLElement | null : null;
        if (!target) return;

        event.preventDefault();
        const action = target.getAttribute('data-page-action');
        if (action === 'prev') {
          this.currentPage = Math.max(1, this.currentPage - 1);
        } else if (action === 'next') {
          this.currentPage += 1;
        } else if (action === 'page') {
          const page = Number(target.getAttribute('data-page'));
          if (Number.isFinite(page) && page > 0) {
            this.currentPage = page;
          }
        }
        this.renderCalendarData(pageRoot);
      };

      paginationEl.addEventListener('click', onPaginationClick);
      this.cleanupFns.push(() => paginationEl.removeEventListener('click', onPaginationClick));
    }
  }

  private renderCalendarData(pageRoot: HTMLElement): void {
    const listEl = pageRoot.querySelector('.cal-events-list') as HTMLElement | null;
    const paginationEl = pageRoot.querySelector('.cal-events-pagination') as HTMLElement | null;
    if (!listEl) return;

    const events = this.getFilteredEvents();
    this.renderHeroRecommendation(pageRoot);
    this.renderRecommendation(pageRoot);
    const eventsPerPage = this.getEventsPerPage();
    const totalPages = Math.max(1, Math.ceil(events.length / eventsPerPage));

    if (this.pendingFocusEventId) {
      const focusIndex = events.findIndex((ev) => Number(ev.idEvent) === Number(this.pendingFocusEventId));
      if (focusIndex >= 0) {
        this.currentPage = Math.floor(focusIndex / eventsPerPage) + 1;
      }
    }
    this.currentPage = Math.min(Math.max(this.currentPage, 1), totalPages);
    const pageStart = (this.currentPage - 1) * eventsPerPage;
    const pageEvents = events.slice(pageStart, pageStart + eventsPerPage);

    if (!events.length) {
      listEl.innerHTML =
        '<div class="cal-event-card"><div class="cal-event-info"><h4>No upcoming events</h4><p class="cal-event-desc">Events created in backoffice will appear here.</p></div></div>';
      if (paginationEl) {
        paginationEl.innerHTML = '';
      }
      this.updateSectionLabel(pageRoot, null);
    } else {
      listEl.innerHTML = pageEvents.map((ev) => this.toFrontofficeCardHtml(ev)).join('');
      this.renderPagination(pageRoot, events.length, totalPages);
      this.updateSectionLabel(pageRoot, pageEvents[0]?.dateDebut || events[0].dateDebut);
    }

    this.renderEventTypes(pageRoot, events);
    this.renderThisMonthStats(pageRoot, events);
    this.renderMiniCalendar(pageRoot, events);
    this.renderComingIn(pageRoot, events);
  }

  private getFilteredEvents(): FrontofficeEvent[] {
    return [...this.allEvents]
      .filter((ev) => this.activeFilter === 'ALL' || this.getTypeKey(ev) === this.activeFilter)
      .filter((ev) => this.statusFilter === 'ALL' || String(ev.statut || '').toUpperCase() === this.statusFilter)
      .filter((ev) => {
        if (!this.searchQuery) return true;
        const haystack = [
          ev.name,
          ev.location,
          ev.description,
          ev.type,
          ev.statut
        ]
          .map((value) => String(value || '').toLowerCase())
          .join(' ');
        return haystack.includes(this.searchQuery.toLowerCase());
      })
      .sort((a, b) => {
        switch (this.sortOrder) {
          case 'DATE_DESC':
            return this.toDate(b.dateDebut).getTime() - this.toDate(a.dateDebut).getTime();
          case 'NAME_ASC':
            return String(a.name || '').localeCompare(String(b.name || ''));
          case 'NAME_DESC':
            return String(b.name || '').localeCompare(String(a.name || ''));
          case 'DATE_ASC':
          default:
            return this.toDate(a.dateDebut).getTime() - this.toDate(b.dateDebut).getTime();
        }
      });
  }

  private async loadRecommendedEventForSelectedStudent(): Promise<void> {
    if (!this.selectedStudentId) {
      this.recommendedEvents = [];
      const pageRoot = document.querySelector('.fo-static-page') as HTMLElement | null;
      if (pageRoot) {
        this.renderRecommendation(pageRoot);
        this.renderHeroRecommendation(pageRoot);
      }
      return;
    }
    
    this.loadingRecommendations = true;
    const pageRoot = document.querySelector('.fo-static-page') as HTMLElement | null;
    if (pageRoot) {
      this.renderRecommendation(pageRoot);
      this.renderHeroRecommendation(pageRoot);
    }
    
    try {
      const response = await firstValueFrom(
        this.http.get<RecommendationApiEvent[]>(
          `${this.recommendationBaseUrl}/${this.selectedStudentId}`
        )
      );

      const recommendationList = Array.isArray(response) ? response : [];
      this.recommendedEvents = [];

      for (const rec of recommendationList) {
          const matchingEvent = this.allEvents.find((event) => Number(event.idEvent) === Number(rec.idEvent));
          this.recommendedEvents.push({
            ...(matchingEvent || rec),
            location: rec.location || matchingEvent?.location || '',
            dateDebut: rec.dateDebut || matchingEvent?.dateDebut || '',
            dateFin: rec.dateFin || matchingEvent?.dateFin || '',
            name: rec.name || matchingEvent?.name || '',
            type: rec.type || matchingEvent?.type || '',
            description: rec.description || matchingEvent?.description || '',
            recommendationScore: Number(rec.recommendationScore ?? (rec as any).hybridScore ?? 0),
            recommendationReason: rec.recommendationReason || (rec as any).reason || ''
          } as RecommendedEvent);
      }

      if (!this.recommendedEvents.length) {
        this.recommendedEvents = this.buildLocalFallbackRecommendations(2);
      }
    } catch (error) {
      console.error('Failed to load recommendations for student', error);
      this.recommendedEvents = this.buildLocalFallbackRecommendations(2);
    } finally {
      this.loadingRecommendations = false;
      if (pageRoot) {
        this.renderRecommendation(pageRoot);
        this.renderHeroRecommendation(pageRoot);
      }
    }
  }

  private async loadPythonRecommendedEventForSelectedStudent(): Promise<void> {
    if (!this.selectedStudentId) {
      this.aiRecommendedEvents = [];
      return;
    }

    try {
      const response = await firstValueFrom(
        this.http.get<RecommendationApiEvent[]>(
          `${this.pythonRecommendationBaseUrl}/${this.selectedStudentId}`
        )
      );

      const recommendationList = Array.isArray(response) ? response : [];
      this.aiRecommendedEvents = [];

      for (const rec of recommendationList) {
        const matchingEvent = this.allEvents.find((event) => Number(event.idEvent) === Number(rec.idEvent));
        this.aiRecommendedEvents.push({
          ...(matchingEvent || rec),
          location: rec.location || matchingEvent?.location || '',
          dateDebut: rec.dateDebut || matchingEvent?.dateDebut || '',
          dateFin: rec.dateFin || matchingEvent?.dateFin || '',
          name: rec.name || matchingEvent?.name || '',
          type: rec.type || matchingEvent?.type || '',
          description: rec.description || matchingEvent?.description || '',
          recommendationScore: Number(rec.recommendationScore ?? (rec as any).hybridScore ?? 0),
          recommendationReason: rec.recommendationReason || (rec as any).reason || ''
        } as RecommendedEvent);
      }
    } catch (error) {
      console.error('Failed to load Python recommendations for student', error);
      this.aiRecommendedEvents = [];
    }
  }

  private renderRecommendation(pageRoot: HTMLElement): void {
    const recommendationSlot = pageRoot.querySelector('.cal-recommendation-slot') as HTMLElement | null;
    if (!recommendationSlot) return;

    if (this.loadingRecommendations) {
      recommendationSlot.innerHTML = `
        <style>
          .ai-loader-container { display: flex; justify-content: center; align-items: center; width: 100%; padding: 20px 0; }
          .ai-loader { width: 100%; max-width: 600px; }
          .trace-bg { stroke: #e0e0e0; stroke-width: 1.8; fill: none; }
          .trace-flow { stroke-width: 1.8; fill: none; stroke-dasharray: 40 400; stroke-dashoffset: 438; filter: drop-shadow(0 0 6px currentColor); animation: flow 3s cubic-bezier(0.5, 0, 0.9, 1) infinite; }
          .blue2 { stroke: #624bfa; color: #624bfa; }
          .blue { stroke: #8b5cf6; color: #8b5cf6; }
          @keyframes flow { to { stroke-dashoffset: 0; } }
        </style>
        <div style="margin-bottom: 24px;">
          ${this.getRecommendationHeadingHtml()}
          <div class="ai-loader-container">
            <div class="ai-loader">
              <svg viewBox="0 0 800 500" xmlns="http://www.w3.org/2000/svg">
                <defs>
                  <linearGradient id="chipGradient" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stop-color="#2d2d2d"></stop>
                    <stop offset="100%" stop-color="#0f0f0f"></stop>
                  </linearGradient>
                  <linearGradient id="textGradient" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stop-color="#eeeeee"></stop>
                    <stop offset="100%" stop-color="#888888"></stop>
                  </linearGradient>
                  <linearGradient id="pinGradient" x1="1" y1="0" x2="0" y2="0">
                    <stop offset="0%" stop-color="#bbbbbb"></stop>
                    <stop offset="50%" stop-color="#888888"></stop>
                    <stop offset="100%" stop-color="#555555"></stop>
                  </linearGradient>
                </defs>
                <g id="traces">
                  <path d="M100 100 H200 V210 H326" class="trace-bg"></path>
                  <path d="M100 100 H200 V210 H326" class="trace-flow blue2"></path>
                  <path d="M80 180 H180 V230 H326" class="trace-bg"></path>
                  <path d="M80 180 H180 V230 H326" class="trace-flow blue"></path>
                  <path d="M60 260 H150 V250 H326" class="trace-bg"></path>
                  <path d="M60 260 H150 V250 H326" class="trace-flow blue2"></path>
                  <path d="M100 350 H200 V270 H326" class="trace-bg"></path>
                  <path d="M100 350 H200 V270 H326" class="trace-flow blue"></path>
                  <path d="M700 90 H560 V210 H474" class="trace-bg"></path>
                  <path d="M700 90 H560 V210 H474" class="trace-flow blue"></path>
                  <path d="M740 160 H580 V230 H474" class="trace-bg"></path>
                  <path d="M740 160 H580 V230 H474" class="trace-flow blue2"></path>
                  <path d="M720 250 H590 V250 H474" class="trace-bg"></path>
                  <path d="M720 250 H590 V250 H474" class="trace-flow blue"></path>
                  <path d="M680 340 H570 V270 H474" class="trace-bg"></path>
                  <path d="M680 340 H570 V270 H474" class="trace-flow blue2"></path>
                </g>
                <rect x="330" y="190" width="140" height="100" rx="20" ry="20" fill="url(#chipGradient)" stroke="#222" stroke-width="3" filter="drop-shadow(0 0 6px rgba(0,0,0,0.8))"></rect>
                <g>
                  <rect x="322" y="205" width="8" height="10" fill="url(#pinGradient)" rx="2"></rect>
                  <rect x="322" y="225" width="8" height="10" fill="url(#pinGradient)" rx="2"></rect>
                  <rect x="322" y="245" width="8" height="10" fill="url(#pinGradient)" rx="2"></rect>
                  <rect x="322" y="265" width="8" height="10" fill="url(#pinGradient)" rx="2"></rect>
                </g>
                <g>
                  <rect x="470" y="205" width="8" height="10" fill="url(#pinGradient)" rx="2"></rect>
                  <rect x="470" y="225" width="8" height="10" fill="url(#pinGradient)" rx="2"></rect>
                  <rect x="470" y="245" width="8" height="10" fill="url(#pinGradient)" rx="2"></rect>
                  <rect x="470" y="265" width="8" height="10" fill="url(#pinGradient)" rx="2"></rect>
                </g>
                <text x="400" y="240" font-family="Arial, sans-serif" font-size="22" fill="url(#textGradient)" text-anchor="middle" alignment-baseline="middle">Analyzing</text>
                <circle cx="100" cy="100" r="5" fill="#444"></circle>
                <circle cx="80" cy="180" r="5" fill="#444"></circle>
                <circle cx="60" cy="260" r="5" fill="#444"></circle>
                <circle cx="100" cy="350" r="5" fill="#444"></circle>
                <circle cx="700" cy="90" r="5" fill="#444"></circle>
                <circle cx="740" cy="160" r="5" fill="#444"></circle>
                <circle cx="720" cy="250" r="5" fill="#444"></circle>
                <circle cx="680" cy="340" r="5" fill="#444"></circle>
              </svg>
            </div>
          </div>
        </div>
      `;
      return;
    }

    if (!this.recommendedEvents || this.recommendedEvents.length === 0) {
      recommendationSlot.innerHTML = `
        <div style="margin-bottom: 24px;">
          ${this.getRecommendationHeadingHtml()}
          <div style="background: #f8f8fc; border-radius: 12px; padding: 24px; text-align: center; color: #555;">
             <i>No recommendations yet — attend more events to personalize your feed!</i>
          </div>
        </div>
      `;
      return;
    }

    const typeLabelMap: Record<EventTypeKey, string> = {
      WEBINAIRE: 'Live Webinar',
      FORMATION: 'Formation',
      ATELIER: 'Atelier',
      HACKATHON: 'Hackathon'
    };

    const cardsHtml = this.recommendedEvents.map(recommended => {
      const score = Math.max(0, Math.min(100, Math.round((recommended.recommendationScore || 0) * 100)));
      const type = this.getTypeKey(recommended as unknown as FrontofficeEvent);
      const timeRange = `${this.toTime(recommended.dateDebut)} – ${this.toTime(recommended.dateFin)}`;
      const eventDate = this.toDate(recommended.dateDebut).toLocaleDateString('en-US', {
        month: 'long', day: 'numeric', year: 'numeric'
      });
      const eventImageUrl = String(recommended.imageUrl || '').trim();
      const recommendationImageHtml = eventImageUrl
        ? `<div class="cal-recommendation-media has-image"><img src="${this.escapeHtml(eventImageUrl)}" alt="${this.escapeHtml(recommended.name)}" loading="lazy"></div>`
        : '';

      const barFilled = `<div style="height: 6px; background: #624bfa; width: ${score}%; border-radius: 3px;"></div>`;
      const barEmpty = `<div style="height: 6px; background: #e0e0e0; width: 100%; border-radius: 3px; overflow: hidden; margin-top: 4px;">${barFilled}</div>`;

      return `
        <section class="cal-recommendation-card type-${type.toLowerCase()}" style="flex: 1; min-width: 320px; max-width: 480px; display: flex; flex-direction: column;">
          ${recommendationImageHtml}
          <div class="cal-recommendation-copy" style="flex: 1;">
            <h3>${this.escapeHtml(recommended.name)}</h3>
            <p style="font-style: italic; font-size: 0.9em; margin-bottom: 12px; color: #444;">${this.escapeHtml(recommended.recommendationReason || '')}</p>
            <div class="cal-recommendation-meta">
              <span>${this.escapeHtml(typeLabelMap[type] || type)}</span>
              <span>${this.escapeHtml(eventDate)}</span>
              <span>${this.escapeHtml(recommended.location || 'Location TBD')}</span>
            </div>
          </div>
          <div class="cal-recommendation-side" style="margin-top: auto; border-left: none; border-top: 1px solid #eee; padding-top: 16px; display: flex; justify-content: space-between; align-items: center;">
            <div style="flex: 1; padding-right: 16px;">
              <small style="display: block; font-size: 0.75rem; color: #777;">Match Score: <strong>${score}%</strong></small>
              ${barEmpty}
            </div>
            <button type="button" class="cal-recommendation-btn" data-recommend-action="focus" data-target-id="${recommended.idEvent}" style="margin: 0; padding: 8px 16px; font-size: 0.85rem;">
              View
            </button>
          </div>
        </section>
      `;
    }).join('');

    recommendationSlot.innerHTML = `
      <div style="margin-bottom: 32px;">
        ${this.getRecommendationHeadingHtml()}
        <div style="display: flex; gap: 16px; overflow-x: auto; padding-bottom: 12px;">
          ${cardsHtml}
        </div>
      </div>
    `;
  }

  private buildLocalFallbackRecommendations(limit: number): RecommendedEvent[] {
    const now = new Date();
    const preferredTypes = new Set<string>();

    this.studentEventStatuses.forEach((status, eventId) => {
      const normalizedStatus = String(status || '').toUpperCase();
      if (!['EN_ATTENTE', 'LISTE_ATTENTE', 'CONFIRMEE'].includes(normalizedStatus)) {
        return;
      }

      const event = this.allEvents.find((item) => Number(item.idEvent) === Number(eventId));
      if (event?.type) {
        preferredTypes.add(String(event.type).toUpperCase());
      }
    });

    this.studentFeedbacks.forEach((feedback, eventId) => {
      if (Number(feedback?.rating) < 4) return;
      const event = this.allEvents.find((item) => Number(item.idEvent) === Number(eventId));
      if (event?.type) {
        preferredTypes.add(String(event.type).toUpperCase());
      }
    });

    const activeStudentEventIds = new Set(
      Array.from(this.studentEventStatuses.entries())
        .filter(([, status]) => {
          const normalizedStatus = String(status || '').toUpperCase();
          return ['EN_ATTENTE', 'LISTE_ATTENTE', 'CONFIRMEE'].includes(normalizedStatus);
        })
        .map(([eventId]) => Number(eventId))
    );

    const candidates = this.allEvents
      .filter((event) => {
        const status = String(event.statut || '').toUpperCase();
        if (status === 'ANNULE' || status === 'TERMINE') return false;
        if (activeStudentEventIds.has(Number(event.idEvent))) return false;

        const endDate = this.toDate(event.dateFin || event.dateDebut);
        return endDate.getTime() >= now.getTime();
      })
      .sort((a, b) => this.toDate(a.dateDebut).getTime() - this.toDate(b.dateDebut).getTime());

    const sameTypeCandidates = candidates.filter((event) =>
      preferredTypes.has(String(event.type || '').toUpperCase())
    );

    const orderedCandidates = [...sameTypeCandidates];
    candidates.forEach((event) => {
      if (!orderedCandidates.some((item) => Number(item.idEvent) === Number(event.idEvent))) {
        orderedCandidates.push(event);
      }
    });

    return orderedCandidates.slice(0, Math.max(0, limit)).map((event, index) => {
      const matchesType = preferredTypes.has(String(event.type || '').toUpperCase());
      const reason = matchesType
        ? `Because you already joined similar ${String(event.type || 'event').toLowerCase()} events.`
        : preferredTypes.size
          ? 'A nearby upcoming event that complements your recent activity.'
          : 'An upcoming event you can discover right now.';

      return {
        ...event,
        recommendationScore: matchesType ? 0.78 - index * 0.06 : 0.58 - index * 0.04,
        recommendationReason: reason
      };
    });
  }

  private getRecommendationHeadingHtml(): string {
    return `
      <h2 style="display: inline-flex; align-items: center; gap: 10px; font-size: 1.5rem; font-weight: 700; margin-bottom: 16px; color: #17203f;">
        <span style="display: inline-flex; align-items: center; justify-content: center; width: 34px; height: 34px; border-radius: 12px; background: linear-gradient(135deg, rgba(35, 40, 160, 0.12), rgba(57, 159, 255, 0.18)); color: #2328a0;">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <path d="M12 3L14.6 9.4L21 12L14.6 14.6L12 21L9.4 14.6L3 12L9.4 9.4L12 3Z" fill="currentColor"></path>
          </svg>
        </span>
        <span>Recommended for You</span>
      </h2>
    `;
  }
  private getAiRecommendationHeadingHtml(): string {
    return `
      <h2 style="display: inline-flex; align-items: center; gap: 10px; font-size: 1.5rem; font-weight: 700; margin-bottom: 16px; color: #17203f;">
        <span style="display: inline-flex; align-items: center; justify-content: center; width: 34px; height: 34px; border-radius: 12px; background: linear-gradient(135deg, rgba(35, 40, 160, 0.12), rgba(57, 159, 255, 0.18)); color: #2328a0;">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <path d="M12 3L14.6 9.4L21 12L14.6 14.6L12 21L9.4 14.6L3 12L9.4 9.4L12 3Z" fill="currentColor"></path>
          </svg>
        </span>
        <span>AI Suggested Event</span>
      </h2>
    `;
  }

  private getActiveHeroRecommendations(): RecommendedEvent[] {
    return this.heroRecommendationSource === 'python' ? this.aiRecommendedEvents : this.recommendedEvents;
  }

  private getHeroRecommendationKicker(): string {
    return this.heroRecommendationSource === 'python' ? 'Python ML Suggested Event' : 'AI Suggested Event';
  }

  private waitForHeroRecommendationLoader(): Promise<void> {
    return new Promise((resolve) => {
      this.heroRecommendationTimer = window.setTimeout(() => {
        this.heroRecommendationTimer = null;
        resolve();
      }, CalendarComponent.heroRecommendationLoaderMs);
    });
  }

  private renderHeroRecommendation(pageRoot: HTMLElement): void {
    const heroRecommendationSlot = pageRoot.querySelector('.hero-recommendation-slot') as HTMLElement | null;
    if (!heroRecommendationSlot) return;

    if (!this.heroRecommendationOpen) {
      heroRecommendationSlot.innerHTML = '';
      heroRecommendationSlot.classList.remove('is-visible');
      return;
    }

    if (this.heroRecommendationLoading) {
      heroRecommendationSlot.innerHTML = `
        <section class="hero-recommendation-loader-card">
          <div class="main-container">
            <div class="loader">
              <svg viewBox="0 0 800 500" xmlns="http://www.w3.org/2000/svg">
                <defs>
                  <linearGradient id="heroChipGradient" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stop-color="#2d2d2d"></stop>
                    <stop offset="100%" stop-color="#0f0f0f"></stop>
                  </linearGradient>
                  <linearGradient id="heroTextGradient" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stop-color="#eeeeee"></stop>
                    <stop offset="100%" stop-color="#888888"></stop>
                  </linearGradient>
                  <linearGradient id="heroPinGradient" x1="1" y1="0" x2="0" y2="0">
                    <stop offset="0%" stop-color="#bbbbbb"></stop>
                    <stop offset="50%" stop-color="#888888"></stop>
                    <stop offset="100%" stop-color="#555555"></stop>
                  </linearGradient>
                </defs>

                <g id="traces">
                  <path d="M100 100 H200 V210 H326" class="trace-bg"></path>
                  <path d="M100 100 H200 V210 H326" class="trace-flow blue2"></path>

                  <path d="M80 180 H180 V230 H326" class="trace-bg"></path>
                  <path d="M80 180 H180 V230 H326" class="trace-flow blue"></path>

                  <path d="M60 260 H150 V250 H326" class="trace-bg"></path>
                  <path d="M60 260 H150 V250 H326" class="trace-flow blue2"></path>

                  <path d="M100 350 H200 V270 H326" class="trace-bg"></path>
                  <path d="M100 350 H200 V270 H326" class="trace-flow blue"></path>

                  <path d="M700 90 H560 V210 H474" class="trace-bg"></path>
                  <path d="M700 90 H560 V210 H474" class="trace-flow blue"></path>

                  <path d="M740 160 H580 V230 H474" class="trace-bg"></path>
                  <path d="M740 160 H580 V230 H474" class="trace-flow blue2"></path>

                  <path d="M720 250 H590 V250 H474" class="trace-bg"></path>
                  <path d="M720 250 H590 V250 H474" class="trace-flow blue"></path>

                  <path d="M680 340 H570 V270 H474" class="trace-bg"></path>
                  <path d="M680 340 H570 V270 H474" class="trace-flow blue2"></path>
                </g>

                <rect
                  x="330"
                  y="190"
                  width="140"
                  height="100"
                  rx="20"
                  ry="20"
                  fill="url(#heroChipGradient)"
                  stroke="#222"
                  stroke-width="3"
                  filter="drop-shadow(0 0 6px rgba(0,0,0,0.8))"
                ></rect>

                <g>
                  <rect x="322" y="205" width="8" height="10" fill="url(#heroPinGradient)" rx="2"></rect>
                  <rect x="322" y="225" width="8" height="10" fill="url(#heroPinGradient)" rx="2"></rect>
                  <rect x="322" y="245" width="8" height="10" fill="url(#heroPinGradient)" rx="2"></rect>
                  <rect x="322" y="265" width="8" height="10" fill="url(#heroPinGradient)" rx="2"></rect>
                </g>

                <g>
                  <rect x="470" y="205" width="8" height="10" fill="url(#heroPinGradient)" rx="2"></rect>
                  <rect x="470" y="225" width="8" height="10" fill="url(#heroPinGradient)" rx="2"></rect>
                  <rect x="470" y="245" width="8" height="10" fill="url(#heroPinGradient)" rx="2"></rect>
                  <rect x="470" y="265" width="8" height="10" fill="url(#heroPinGradient)" rx="2"></rect>
                </g>

                <text
                  x="400"
                  y="240"
                  font-family="Arial, sans-serif"
                  font-size="22"
                  fill="url(#heroTextGradient)"
                  text-anchor="middle"
                  alignment-baseline="middle"
                >
                  Loading
                </text>

                <circle cx="100" cy="100" r="5" fill="black"></circle>
                <circle cx="80" cy="180" r="5" fill="black"></circle>
                <circle cx="60" cy="260" r="5" fill="black"></circle>
                <circle cx="100" cy="350" r="5" fill="black"></circle>

                <circle cx="700" cy="90" r="5" fill="black"></circle>
                <circle cx="740" cy="160" r="5" fill="black"></circle>
                <circle cx="720" cy="250" r="5" fill="black"></circle>
                <circle cx="680" cy="340" r="5" fill="black"></circle>
              </svg>
            </div>
          </div>
        </section>
      `;
      heroRecommendationSlot.classList.add('is-visible');
      return;
    }

    const activeHeroRecommendations = this.getActiveHeroRecommendations();
    if (!activeHeroRecommendations || activeHeroRecommendations.length === 0) {
      heroRecommendationSlot.innerHTML = `
        <section class="hero-recommendation-card is-empty">
          <span class="hero-recommendation-kicker">${this.escapeHtml(this.getHeroRecommendationKicker())}</span>
          <h3>No suggestion yet</h3>
          <p>Try again after joining or reviewing more events.</p>
        </section>
      `;
      heroRecommendationSlot.classList.add('is-visible');
      return;
    }

    const recommended = activeHeroRecommendations[0];
    const score = Math.max(0, Math.min(100, Math.round((recommended.recommendationScore || 0) * 100)));
    const type = this.getTypeKey(recommended as unknown as FrontofficeEvent);
    const eventDate = this.toDate(recommended.dateDebut).toLocaleDateString('en-US', { month: 'long', day: 'numeric', year: 'numeric' });
    const timeRange = `${this.toTime(recommended.dateDebut)} \u2013 ${this.toTime(recommended.dateFin)}`;
    const eventImageUrl = String(recommended.imageUrl || '').trim();
    const heroRecommendationImageHtml = eventImageUrl
      ? `<div class="hero-recommendation-media has-image"><img src="${this.escapeHtml(eventImageUrl)}" alt="${this.escapeHtml(recommended.name)}" loading="lazy"></div>`
      : '';

    heroRecommendationSlot.innerHTML = `
      <section class="hero-recommendation-card type-${type.toLowerCase()} is-visible-card">
        ${heroRecommendationImageHtml}
        <div class="hero-recommendation-copy">
          <span class="hero-recommendation-kicker">${this.escapeHtml(this.getHeroRecommendationKicker())}</span>
          <h3>${this.escapeHtml(recommended.name)}</h3>
          <p><i>${this.escapeHtml(recommended.recommendationReason || '')}</i></p>
          <div class="hero-recommendation-meta">
            <span>${this.escapeHtml(type)}</span>
            <span>${this.escapeHtml(eventDate)}</span>
            <span>${this.escapeHtml(timeRange)}</span>
            <span>${this.escapeHtml(recommended.location || 'Location TBD')}</span>
          </div>
        </div>
        <div class="hero-recommendation-side">
          <div class="hero-recommendation-score">
            <small>Match Score</small>
            <strong>${score}%</strong>
          </div>
          <button type="button" class="hero-recommendation-open" data-recommend-action="focus" data-target-id="${recommended.idEvent}">
            Open In Events List
          </button>
        </div>
      </section>
    `;
    heroRecommendationSlot.classList.add('is-visible');
  }

  private getEventsPerPage(): number {
    if (typeof window === 'undefined') return 4;
    if (window.innerWidth >= 1440) return 6;
    if (window.innerWidth >= 1080) return 5;
    if (window.innerWidth >= 768) return 4;
    return 3;
  }

  private renderPagination(pageRoot: HTMLElement, totalItems: number, totalPages: number): void {
    const paginationEl = pageRoot.querySelector('.cal-events-pagination') as HTMLElement | null;
    if (!paginationEl) return;

    if (totalItems <= this.getEventsPerPage()) {
      paginationEl.innerHTML = '';
      return;
    }

    const visiblePages = this.buildVisiblePages(totalPages);
    const startItem = (this.currentPage - 1) * this.getEventsPerPage() + 1;
    const endItem = Math.min(totalItems, this.currentPage * this.getEventsPerPage());
    paginationEl.innerHTML = `
      <div class="cal-pagination-copy">
        Showing <strong>${startItem}-${endItem}</strong> of <strong>${totalItems}</strong> events
      </div>
      <div class="cal-pagination-controls">
        <button type="button" class="cal-page-arrow" data-page-action="prev" ${this.currentPage <= 1 ? 'disabled' : ''}>Previous</button>
        <div class="cal-page-pills">
          ${visiblePages
            .map((page) =>
              page === '…'
                ? '<span class="cal-page-ellipsis">…</span>'
                : `<button type="button" class="cal-page-pill ${Number(page) === this.currentPage ? 'active' : ''}" data-page-action="page" data-page="${page}">${page}</button>`
            )
            .join('')}
        </div>
        <button type="button" class="cal-page-arrow" data-page-action="next" ${this.currentPage >= totalPages ? 'disabled' : ''}>Next</button>
      </div>
    `;
  }

  private buildVisiblePages(totalPages: number): Array<number | '…'> {
    if (totalPages <= 5) {
      return Array.from({ length: totalPages }, (_, index) => index + 1);
    }

    const pages: Array<number | '…'> = [1];
    const start = Math.max(2, this.currentPage - 1);
    const end = Math.min(totalPages - 1, this.currentPage + 1);

    if (start > 2) pages.push('…');
    for (let page = start; page <= end; page += 1) {
      pages.push(page);
    }
    if (end < totalPages - 1) pages.push('…');
    pages.push(totalPages);
    return pages;
  }

  private filterFromLabel(label: string): FrontofficeFilter {
    const value = label.trim().toUpperCase();
    if (value.includes('WEBINAR')) return 'WEBINAIRE';
    if (value.includes('WORKSHOP') || value.includes('FORMATION')) return 'FORMATION';
    if (value.includes('ATELIER')) return 'ATELIER';
    //if (value.includes('MEETUP')) return 'MEETUP';
    if (value.includes('HACKATHON')) return 'HACKATHON';
    return 'ALL';
  }

  private normalizeFilterChips(pageRoot: HTMLElement): void {
    const chips = Array.from(pageRoot.querySelectorAll('.cal-filter-chip')) as HTMLButtonElement[];
    chips.forEach((chip) => {
      const label = (chip.textContent || '').trim().toUpperCase();

      if (label === 'WORKSHOPS') {
        chip.textContent = 'Formation';
      } else if (label === 'CERTIFICATION EXAMS') {
        chip.textContent = 'Atelier';
      } else if (label === 'MEETUPS') {
        chip.remove();
      }
    });
  }

  // ensureStudentSelect and loadStudentIds removed — selectedStudentId is now
  // set directly from the logged-in user (AuthService.currentUserId) at init time.

  private async loadRegisteredEventIdsForSelectedStudent(): Promise<void> {
    if (!this.selectedStudentId) {
      this.studentEventStatuses = new Map<number, string>();
      this.studentFeedbacks = new Map<number, StudentFeedback>();
      this.statusHydrated = false;
      this.writePersistedStatusSnapshot();
      return;
    }

    try {
      const previousStatuses = this.statusHydrated
        ? new Map(this.studentEventStatuses)
        : this.readPersistedStatusSnapshot(this.selectedStudentId);
      const response = await firstValueFrom(
        this.http.get<Array<{ idEvent: number; statut: string }>>(
          `${this.inscriptionBaseUrl}/student/${this.selectedStudentId}/event-statuses`
        )
      );
      const statuses = Array.isArray(response)
        ? response
            .map((item) => ({
              idEvent: Number(item?.idEvent),
              statut: String(item?.statut || '').toUpperCase()
            }))
            .filter((item) => Number.isFinite(item.idEvent) && item.idEvent > 0)
        : [];
      this.studentEventStatuses = new Map<number, string>();
      statuses.forEach((item) => {
        if (!this.studentEventStatuses.has(item.idEvent)) {
          this.studentEventStatuses.set(item.idEvent, item.statut);
        }
      });

      if (this.statusHydrated || previousStatuses.size) {
        this.detectAnimatedTransitions(previousStatuses, this.studentEventStatuses);
      }
      this.statusHydrated = true;
      this.writePersistedStatusSnapshot();
      await this.loadFeedbacksForSelectedStudent();
    } catch (error) {
      console.error('Failed to load registered events for student', error);
      this.studentEventStatuses = new Map<number, string>();
      this.studentFeedbacks = new Map<number, StudentFeedback>();
    }
  }

  private async loadFeedbacksForSelectedStudent(): Promise<void> {
    if (!this.selectedStudentId) {
      this.studentFeedbacks = new Map<number, StudentFeedback>();
      return;
    }

    try {
      const response = await firstValueFrom(
        this.http.get<StudentFeedback[]>(`${this.feedbackBaseUrl}/student/${this.selectedStudentId}`)
      );
      this.studentFeedbacks = new Map<number, StudentFeedback>();
      (Array.isArray(response) ? response : []).forEach((item) => {
        const eventId = Number(item?.eventId);
        if (Number.isFinite(eventId) && eventId > 0) {
          this.studentFeedbacks.set(eventId, item);
        }
      });
    } catch (error) {
      console.error('Failed to load student feedbacks', error);
      this.studentFeedbacks = new Map<number, StudentFeedback>();
    }
  }

  private renderEventTypes(pageRoot: HTMLElement, events: FrontofficeEvent[]): void {
    const cards = Array.from(pageRoot.querySelectorAll('.cal-sidebar-card'));
    const eventTypeCard = cards.find((card) =>
      (card.querySelector('h3')?.textContent || '').trim().toLowerCase() === 'event types'
    ) as HTMLElement | undefined;

    if (!eventTypeCard) return;

    const legend = eventTypeCard.querySelector('.cal-legend') as HTMLElement | null;
    if (!legend) return;

    const counters = {
      WEBINAIRE: 0,
      FORMATION: 0,
      ATELIER: 0,
      HACKATHON: 0
    };

    events.forEach((ev) => {
      const key = this.getTypeKey(ev);
      if (key in counters) counters[key as keyof typeof counters] += 1;
    });

    legend.innerHTML = `
      <div class="cal-legend-item"><span class="cal-legend-dot webinar"></span>Webinars (${counters['WEBINAIRE']})</div>
      <div class="cal-legend-item"><span class="cal-legend-dot workshop"></span>Fomations (${counters['FORMATION']})</div>
      <div class="cal-legend-item"><span class="cal-legend-dot certification"></span>Ateliers (${counters['ATELIER']})</div>
      <div class="cal-legend-item"><span class="cal-legend-dot hackathon"></span>Hackathons (${counters['HACKATHON']})</div>
    `;
  }

  private renderThisMonthStats(pageRoot: HTMLElement, events: FrontofficeEvent[]): void {
    const cards = Array.from(pageRoot.querySelectorAll('.cal-sidebar-card'));
    const thisMonthCard = cards.find((card) =>
      (card.querySelector('h3')?.textContent || '').trim().toLowerCase() === 'this month'
    ) as HTMLElement | undefined;

    if (!thisMonthCard) return;

    const now = new Date();
    const monthEvents = events.filter((ev) => {
      const d = this.toDate(ev.dateDebut);
      return d.getMonth() === now.getMonth() && d.getFullYear() === now.getFullYear();
    });

    const workshops = monthEvents.filter((ev) => this.getTypeKey(ev) === 'FORMATION').length;
    const ateliers = monthEvents.filter((ev) => this.getTypeKey(ev) === 'ATELIER').length;
    const hackathons = monthEvents.filter((ev) => this.getTypeKey(ev) === 'HACKATHON').length;

    thisMonthCard.innerHTML = `
      <h3>This Month</h3>
      <div style="display:grid; grid-template-columns:1fr 1fr; gap:12px;">
        <div style="text-align:center; padding:14px; background:#f8f8fc; border-radius:12px;">
          <div style="font-size:1.5rem; font-weight:800; color:#1a1a2e;">${monthEvents.length}</div>
          <div style="font-size:0.75rem; color:#888;">Events</div>
        </div>
        <div style="text-align:center; padding:14px; background:#f8f8fc; border-radius:12px;">
          <div style="font-size:1.5rem; font-weight:800; color:#1a1a2e;">${workshops}</div>
          <div style="font-size:0.75rem; color:#888;">Fomation</div>
        </div>
        <div style="text-align:center; padding:14px; background:#f8f8fc; border-radius:12px;">
          <div style="font-size:1.5rem; font-weight:800; color:#1a1a2e;">${ateliers}</div>
          <div style="font-size:0.75rem; color:#888;">Atelier</div>
        </div>
        <div style="text-align:center; padding:14px; background:#f8f8fc; border-radius:12px;">
          <div style="font-size:1.5rem; font-weight:800; color:#1a1a2e;">${hackathons}</div>
          <div style="font-size:0.75rem; color:#888;">Hackathon</div>
        </div>
      </div>
    `;
  }

  private renderMiniCalendar(pageRoot: HTMLElement, events: FrontofficeEvent[]): void {
    const mini = pageRoot.querySelector('.cal-mini') as HTMLElement | null;
    if (!mini) return;

    const headerTitle = mini.querySelector('.cal-mini-header h3') as HTMLElement | null;
    const grid = mini.querySelector('.cal-grid') as HTMLElement | null;
    if (!headerTitle || !grid) return;

    const year = this.miniCalendarMonth.getFullYear();
    const month = this.miniCalendarMonth.getMonth();
    headerTitle.textContent = this.miniCalendarMonth.toLocaleDateString('en-US', { month: 'long', year: 'numeric' });

    const firstDayOfMonth = new Date(year, month, 1);
    const daysInMonth = new Date(year, month + 1, 0).getDate();
    const daysInPrevMonth = new Date(year, month, 0).getDate();
    const mondayIndex = (firstDayOfMonth.getDay() + 6) % 7;

    const days: Array<{ day: number; monthOffset: -1 | 0 | 1; date: Date }> = [];

    for (let i = mondayIndex; i > 0; i -= 1) {
      const day = daysInPrevMonth - i + 1;
      days.push({ day, monthOffset: -1, date: new Date(year, month - 1, day) });
    }

    for (let day = 1; day <= daysInMonth; day += 1) {
      days.push({ day, monthOffset: 0, date: new Date(year, month, day) });
    }

    let nextMonthDay = 1;
    while (days.length % 7 !== 0 || days.length < 35) {
      days.push({ day: nextMonthDay, monthOffset: 1, date: new Date(year, month + 1, nextMonthDay) });
      nextMonthDay += 1;
    }

    const eventDates = new Set(
      events.map((ev) => {
        const d = this.toDate(ev.dateDebut);
        return `${d.getFullYear()}-${d.getMonth()}-${d.getDate()}`;
      })
    );

    const today = new Date();
    const dayNames = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];

    grid.innerHTML = `
      ${dayNames.map((d) => `<div class="cal-day-name">${d}</div>`).join('')}
      ${days
        .map((item) => {
          const key = `${item.date.getFullYear()}-${item.date.getMonth()}-${item.date.getDate()}`;
          const classes = ['cal-day'];
          if (item.monthOffset !== 0) classes.push('other-month');
          if (
            item.date.getFullYear() === today.getFullYear() &&
            item.date.getMonth() === today.getMonth() &&
            item.date.getDate() === today.getDate()
          ) {
            classes.push('today');
          }
          if (eventDates.has(key)) classes.push('has-event');
          return `<div class="${classes.join(' ')}">${item.day}</div>`;
        })
        .join('')}
    `;
  }

  private renderComingIn(pageRoot: HTMLElement, events: FrontofficeEvent[]): void {
    const cards = Array.from(pageRoot.querySelectorAll('.cal-sidebar-card'));
    const comingCard = cards.find((card) =>
      (card.querySelector('h3')?.textContent || '').trim().toLowerCase().startsWith('coming in')
    ) as HTMLElement | undefined;

    if (!comingCard) return;

    const monthName = this.miniCalendarMonth.toLocaleDateString('en-US', { month: 'long' });
    const month = this.miniCalendarMonth.getMonth();
    const year = this.miniCalendarMonth.getFullYear();

    const monthEvents = events
      .filter((ev) => {
        const d = this.toDate(ev.dateDebut);
        return d.getMonth() === month && d.getFullYear() === year;
      })
      .sort((a, b) => this.toDate(a.dateDebut).getTime() - this.toDate(b.dateDebut).getTime())
      .slice(0, 4);

    const itemsHtml = monthEvents.length
      ? monthEvents
          .map((ev) => {
            const d = this.toDate(ev.dateDebut);
            const sbMonth = d.toLocaleDateString('en-US', { month: 'short' }).toUpperCase();
            const sbDay = d.toLocaleDateString('en-US', { day: 'numeric' });
            const subtitle = `${this.toTime(ev.dateDebut)} • ${this.escapeHtml(ev.location || 'Location TBD')}`;
            return `
              <div class="cal-sidebar-event">
                <div class="cal-sb-date">
                  <span class="sb-month">${sbMonth}</span>
                  <span class="sb-day">${sbDay}</span>
                </div>
                <div class="cal-sb-info">
                  <h5>${this.escapeHtml(ev.name || 'Event')}</h5>
                  <p>${subtitle}</p>
                </div>
              </div>
            `;
          })
          .join('')
      : `<div class="cal-sidebar-event"><div class="cal-sb-info"><h5>No events this month</h5><p>Stay tuned!</p></div></div>`;

    comingCard.innerHTML = `
      <h3>Coming in ${this.escapeHtml(monthName)}</h3>
      ${itemsHtml}
    `;
  }

  private getTypeKey(ev: FrontofficeEvent): EventTypeKey {
    const type = String(ev.type || 'WEBINAIRE').toUpperCase();
    if (type === 'FORMATION') return 'FORMATION';
    if (type === 'ATELIER') return 'ATELIER';
    //if (type === 'MEETUP') return 'MEETUP';
    if (type === 'HACKATHON') return 'HACKATHON';
    return 'WEBINAIRE';
  }

  private updateSectionLabel(pageRoot: HTMLElement, firstDate: string | null): void {
    const sectionLabel = pageRoot.querySelector('.cal-section-label') as HTMLElement | null;
    if (!sectionLabel) return;

    if (!firstDate) {
      sectionLabel.textContent = 'Events Schedule';
      return;
    }

    const d = this.toDate(firstDate);
    const monthYear = d.toLocaleDateString('en-US', { month: 'long', year: 'numeric' }).toUpperCase();
    sectionLabel.innerHTML = `Events Schedule &mdash; ${monthYear}`;
  }

  private openUpcomingFocus(pageRoot: HTMLElement, targetId?: number): void {
    const targetSection = pageRoot.querySelector('#cal-events, .cal-events-col') as HTMLElement | null;
    if (typeof window !== 'undefined' && window.location.hash === '#cal-events') {
      window.history.replaceState(null, '', window.location.pathname + window.location.search);
    }
    this.activeFilter = 'ALL';
    const chips = Array.from(pageRoot.querySelectorAll('.cal-filter-chip')) as HTMLElement[];
    chips.forEach((chip, index) => {
      chip.classList.toggle('active', index === 0);
    });
    if (targetId) {
      this.pendingFocusEventId = Number(targetId);
      this.currentPage = 1;
      this.renderCalendarData(pageRoot);
    }
    targetSection?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    this.replayPendingEntryAnimation(pageRoot);
  }

  private openRecommendationCard(pageRoot: HTMLElement): void {
    if (typeof window !== 'undefined' && window.location.hash === '#cal-events') {
      window.history.replaceState(null, '', window.location.pathname + window.location.search);
    }

    if (this.recommendedEvents && this.recommendedEvents.length > 0) {
      this.activeFilter = 'ALL';
      this.pendingFocusEventId = Number(this.recommendedEvents[0].idEvent);
      this.currentPage = 1;
      this.renderCalendarData(pageRoot);
    }

    const recommendationSlot = pageRoot.querySelector('.cal-recommendation-slot') as HTMLElement | null;
    const recommendationCard = recommendationSlot?.querySelector('.cal-recommendation-card') as HTMLElement | null;
    recommendationSlot?.scrollIntoView({ behavior: 'smooth', block: 'center' });

    if (recommendationCard) {
      recommendationCard.classList.remove('is-recommendation-spotlight');
      void recommendationCard.offsetWidth;
      recommendationCard.classList.add('is-recommendation-spotlight');
      window.setTimeout(() => {
        recommendationCard.classList.remove('is-recommendation-spotlight');
      }, 2200);
    } else {
      this.openUpcomingFocus(pageRoot);
    }
  }

  private async openInlineRecommendationCard(pageRoot: HTMLElement): Promise<void> {
    const isSameSourceOpen = this.heroRecommendationOpen && this.heroRecommendationSource === 'classic';
    if (isSameSourceOpen) {
      this.heroRecommendationOpen = false;
      this.clearHeroRecommendationTimer();
      this.renderHeroRecommendation(pageRoot);
      return;
    }

    this.heroRecommendationSource = 'classic';
    this.heroRecommendationOpen = true;
    this.clearHeroRecommendationTimer();
    this.heroRecommendationLoading = true;
    this.renderHeroRecommendation(pageRoot);

    const heroRecommendationSlot = pageRoot.querySelector('.hero-recommendation-slot') as HTMLElement | null;
    heroRecommendationSlot?.scrollIntoView({ behavior: 'smooth', block: 'nearest' });

    await this.waitForHeroRecommendationLoader();
    this.heroRecommendationLoading = false;
    this.renderHeroRecommendation(pageRoot);

    const heroRecommendationCard = heroRecommendationSlot?.querySelector('.hero-recommendation-card') as HTMLElement | null;
    if (this.heroRecommendationOpen && heroRecommendationCard) {
      heroRecommendationCard.classList.remove('is-recommendation-spotlight');
      void heroRecommendationCard.offsetWidth;
      heroRecommendationCard.classList.add('is-recommendation-spotlight');
      window.setTimeout(() => {
        heroRecommendationCard.classList.remove('is-recommendation-spotlight');
      }, 2200);
    }
  }

  private async openInlinePythonRecommendationCard(pageRoot: HTMLElement): Promise<void> {
    const isSameSourceOpen = this.heroRecommendationOpen && this.heroRecommendationSource === 'python';
    if (isSameSourceOpen) {
      this.heroRecommendationOpen = false;
      this.clearHeroRecommendationTimer();
      this.renderHeroRecommendation(pageRoot);
      return;
    }

    this.heroRecommendationSource = 'python';
    this.heroRecommendationOpen = true;
    this.clearHeroRecommendationTimer();
    this.heroRecommendationLoading = true;
    this.renderHeroRecommendation(pageRoot);

    const heroRecommendationSlot = pageRoot.querySelector('.hero-recommendation-slot') as HTMLElement | null;
    heroRecommendationSlot?.scrollIntoView({ behavior: 'smooth', block: 'nearest' });

    await Promise.all([
      this.waitForHeroRecommendationLoader(),
      this.loadPythonRecommendedEventForSelectedStudent()
    ]);
    this.heroRecommendationLoading = false;
    this.renderHeroRecommendation(pageRoot);

    const heroRecommendationCard = heroRecommendationSlot?.querySelector('.hero-recommendation-card') as HTMLElement | null;
    if (this.heroRecommendationOpen && heroRecommendationCard) {
      heroRecommendationCard.classList.remove('is-recommendation-spotlight');
      void heroRecommendationCard.offsetWidth;
      heroRecommendationCard.classList.add('is-recommendation-spotlight');
      window.setTimeout(() => {
        heroRecommendationCard.classList.remove('is-recommendation-spotlight');
      }, 2200);
    }
  }

  private clearHeroRecommendationTimer(): void {
    if (this.heroRecommendationTimer) {
      clearTimeout(this.heroRecommendationTimer);
      this.heroRecommendationTimer = null;
    }
    this.heroRecommendationLoading = false;
  }

  private toFrontofficeCardHtml(ev: FrontofficeEvent): string {
    const d = this.toDate(ev.dateDebut);
    const month = d.toLocaleDateString('en-US', { month: 'short' });
    const day = d.toLocaleDateString('en-US', { day: '2-digit' });
    const weekday = d.toLocaleDateString('en-US', { weekday: 'short' });
    const timeRange = `${this.toTime(ev.dateDebut)} \u2013 ${this.toTime(ev.dateFin)}`;

    const type = this.getTypeKey(ev);
    const typeMap: Record<EventTypeKey, { css: string; label: string; card: string }> = {
      WEBINAIRE: { css: 'webinar', label: 'Webinar', card: 'type-webinar' },
      FORMATION: { css: 'workshop', label: 'Fomation', card: 'type-workshop' },
      ATELIER: { css: 'certification', label: 'Atelier', card: 'type-certification' },
      // MEETUP: { css: 'meetup', label: 'Meetup', card: 'type-meetup' },
      HACKATHON: { css: 'hackathon', label: 'Hackathon', card: 'type-hackathon' }
    };
    const mappedType = typeMap[type];

    const status = String(ev.statut || 'PUBLIE').toUpperCase();
    const statusClass = status === 'PUBLIE' ? 'open' : status === 'ANNULE' ? 'full' : 'filling';
    const statusText = status === 'PUBLIE' ? 'Publie' : status === 'ANNULE' ? 'Annule' : 'Termine';
    const totalSeats = Number(ev.capacite ?? 0);
    const registeredSeats = Number(ev.inscriptionsCount ?? 0);
    const seatsLabel = `${Math.max(registeredSeats, 0)} / ${Math.max(totalSeats, 0)} seats`;
    const studentInscriptionStatus = String(this.studentEventStatuses.get(Number(ev.idEvent)) || '').toUpperCase();
    const existingFeedback = this.studentFeedbacks.get(Number(ev.idEvent));
    const canLeaveFeedback = status === 'TERMINE' && studentInscriptionStatus === 'CONFIRMEE';
    const transitionFx = this.eventStatusEffects.get(Number(ev.idEvent));
    const registeredClasses = transitionFx === 'confirmed' ? ' is-morph-confirmed' : '';
    const refusedClasses = transitionFx === 'refused' ? ' is-morph-refused' : '';
    const eventImageUrl = String(ev.imageUrl || '').trim();
    const eventImageHtml = eventImageUrl
      ? `<div class="cal-event-media has-image"><img src="${this.escapeHtml(eventImageUrl)}" alt="${this.escapeHtml(ev.name || 'Event image')}" loading="lazy"></div>`
      : `<div class="cal-event-media"><div class="cal-event-media-fallback">${this.escapeHtml(mappedType.label)}</div></div>`;
    const registerButtonHtml =
      (canLeaveFeedback || existingFeedback)
        ? `<div class="cal-action-stack"><a href="#" class="cal-feedback-btn" data-event-id="${ev.idEvent}">${existingFeedback ? 'Edit Feedback' : 'Leave Feedback'}</a>${existingFeedback ? `<span class="cal-feedback-rating">${'★'.repeat(Math.max(1, Math.min(5, Number(existingFeedback.rating || 0))))}</span>` : ''}</div>`
        : status === 'TERMINE'
          ? '<a href="#" class="cal-join-btn is-disabled" style="pointer-events:none; opacity:.65;">Completed</a>'
        : status === 'ANNULE'
        ? ''
        : studentInscriptionStatus === 'CONFIRMEE'
          ? `<div class="cal-action-stack"><a href="#" class="cal-join-btn is-registered${registeredClasses}" style="pointer-events:none; opacity:.85;">Registered</a><a href="#" class="cal-cancel-btn" data-event-id="${ev.idEvent}">Cancel</a></div>`
          : studentInscriptionStatus === 'EN_ATTENTE'
            ? `<div class="cal-action-stack"><a href="#" class="cal-join-btn is-hold" style="pointer-events:none; opacity:.85;">On Hold</a><a href="#" class="cal-cancel-btn" data-event-id="${ev.idEvent}">Cancel</a></div>`
            : studentInscriptionStatus === 'LISTE_ATTENTE'
              ? `<div class="cal-action-stack"><a href="#" class="cal-join-btn is-hold" style="pointer-events:none; opacity:.85;">Waitlist</a><a href="#" class="cal-cancel-btn" data-event-id="${ev.idEvent}">Leave Waitlist</a></div>`
            : studentInscriptionStatus === 'ANNULEE'
                ? `<a href="#" class="cal-join-btn is-refused${refusedClasses}" style="pointer-events:none; opacity:.75;">Refused</a>`
            : !this.selectedStudentId
              ? '<a href="#" class="cal-join-btn cal-requires-login">Sign in to register</a>'
              : `<a href="#" class="cal-join-btn" data-event-id="${ev.idEvent}">Register</a>`;

    const confirmationFxClass =
      transitionFx === 'confirmed'
        ? ' is-confirmed-celebration'
        : transitionFx === 'refused'
          ? ' is-refused-celebration'
          : '';

    return `
      <div class="cal-event-card ${mappedType.card}${confirmationFxClass}" data-event-card-id="${ev.idEvent}">
        <div class="cal-event-date">
          <div class="cal-month">${this.escapeHtml(month)}</div>
          <div class="cal-num">${this.escapeHtml(day)}</div>
          <div class="cal-weekday">${this.escapeHtml(weekday)}</div>
        </div>
        ${eventImageHtml}
        <div class="cal-event-info">
          <span class="cal-event-type ${mappedType.css}">${this.escapeHtml(mappedType.label)}</span>
          <h4>${this.escapeHtml(ev.name || 'Event')}</h4>
          <p class="cal-event-desc">${this.escapeHtml(ev.description || 'No description available.')}</p>
          <div class="cal-event-meta">
            <span>${this.escapeHtml(timeRange)}</span>
            <span>${this.escapeHtml(ev.location || 'Location not specified')}</span>
            <span>${this.escapeHtml(seatsLabel)}</span>
          </div>
        </div>
        <div class="cal-event-actions">
          ${registerButtonHtml}
          <span class="cal-status ${statusClass}">${this.escapeHtml(statusText)}</span>
        </div>
      </div>
    `;
  }

  private async registerStaticStudent(idEvent: number, button: HTMLAnchorElement): Promise<void> {
    if (!this.selectedStudentId) return;

    const previousText = button.textContent || 'Register';
    button.classList.add('is-loading');
    button.textContent = 'Registering...';

    try {
      const response = await firstValueFrom(
        this.http.post<EventAssignmentResponse>(`${this.eventBaseUrl}/${idEvent}/assign/${this.selectedStudentId}`, {})
      );
      this.studentEventStatuses.set(
        Number(idEvent),
        String(response?.inscriptionStatus || 'EN_ATTENTE').toUpperCase()
      );
      this.writePersistedStatusSnapshot();
      await this.renderBackofficeEventsIntoFrontoffice();
    } catch (error) {
      console.error('Failed to register student to event', error);
      button.textContent = 'Failed';
      setTimeout(() => {
        button.textContent = previousText;
      }, 1200);
    } finally {
      button.classList.remove('is-loading');
    }
  }

  private async cancelStudentRegistration(idEvent: number, button: HTMLAnchorElement): Promise<void> {
    if (!this.selectedStudentId) return;

    const previousText = button.textContent || 'Cancel';
    button.classList.add('is-loading');
    button.textContent = 'Cancelling...';

    try {
      await firstValueFrom(
        this.http.put(`${this.inscriptionBaseUrl}/student/${this.selectedStudentId}/event/${idEvent}/cancel`, {})
      );
      this.studentEventStatuses.delete(Number(idEvent));
      this.writePersistedStatusSnapshot();
      await this.renderBackofficeEventsIntoFrontoffice();
    } catch (error) {
      console.error('Failed to cancel student registration', error);
      button.textContent = 'Failed';
      setTimeout(() => {
        button.textContent = previousText;
      }, 1200);
    } finally {
      button.classList.remove('is-loading');
    }
  }

  openFeedbackModal(idEvent: number): void {
    const targetEvent = this.allEvents.find((event) => Number(event.idEvent) === Number(idEvent));
    const existingFeedback = this.studentFeedbacks.get(Number(idEvent));
    this.abortFeedbackSpeech();
    this.feedbackTargetEventId = Number(idEvent);
    this.feedbackTargetEventName = targetEvent?.name || 'this event';
    this.feedbackRating = existingFeedback?.rating ?? 5;
    this.feedbackComment = existingFeedback?.comment ?? '';
    this.feedbackError = '';
    this.feedbackSubmitting = false;
    this.feedbackSpeechStatus = '';
    this.clearFeedbackSentimentDebounce();
    this.resetFeedbackSentiment();
    this.feedbackSentimentLabel = existingFeedback?.sentimentLabel ?? '';
    this.feedbackSentimentSummary = this.buildFeedbackSentimentSummary(this.feedbackSentimentLabel);
    this.feedbackModalOpen = true;
  }

  closeFeedbackModal(): void {
    if (this.feedbackSubmitting || this.feedbackTranscribing || this.feedbackSentimentLoading) return;
    this.abortFeedbackSpeech();
    this.clearFeedbackSentimentDebounce();
    this.feedbackModalOpen = false;
    this.feedbackTargetEventId = null;
    this.feedbackTargetEventName = '';
    this.feedbackRating = 5;
    this.feedbackComment = '';
    this.feedbackError = '';
    this.feedbackSpeechStatus = '';
    this.resetFeedbackSentiment();
  }

  setFeedbackRating(rating: number): void {
    this.feedbackRating = rating;
    this.clearFeedbackSentimentDebounce();
    this.resetFeedbackSentiment();
  }

  onFeedbackCommentInput(value: string): void {
    this.feedbackComment = value;
    this.resetFeedbackSentiment();
    this.scheduleFeedbackSentimentAnalysis();
  }

  canUseFeedbackSpeech(): boolean {
    return typeof navigator !== 'undefined'
      && typeof MediaRecorder !== 'undefined'
      && !!navigator.mediaDevices?.getUserMedia;
  }

  async toggleFeedbackSpeechRecording(): Promise<void> {
    if (this.feedbackSubmitting || this.feedbackTranscribing) return;

    if (!this.canUseFeedbackSpeech()) {
      this.feedbackError = 'Speech recording is not supported in this browser.';
      return;
    }

    if (this.feedbackRecording) {
      this.stopFeedbackSpeechRecording(true);
      return;
    }

    this.feedbackError = '';
    this.feedbackSpeechStatus = 'Requesting microphone access...';

    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      const mimeType = this.getSupportedFeedbackMimeType();
      const recorder = mimeType ? new MediaRecorder(stream, { mimeType }) : new MediaRecorder(stream);

      this.feedbackRecorderStream = stream;
      this.feedbackRecorder = recorder;
      this.feedbackRecorderMimeType = mimeType || recorder.mimeType || 'audio/webm';
      this.feedbackAudioChunks = [];
      this.shouldTranscribeFeedbackRecording = true;

      recorder.ondataavailable = (event: BlobEvent) => {
        if (event.data.size > 0) {
          this.feedbackAudioChunks.push(event.data);
        }
      };

      recorder.onerror = () => {
        this.feedbackError = 'Audio recording failed. Please try again.';
        this.feedbackSpeechStatus = '';
        this.cleanupFeedbackRecorder();
      };

      recorder.onstop = () => {
        const shouldTranscribe = this.shouldTranscribeFeedbackRecording;
        const audioBlob = this.feedbackAudioChunks.length
          ? new Blob(this.feedbackAudioChunks, { type: this.feedbackRecorderMimeType })
          : null;

        this.cleanupFeedbackRecorder();
        if (shouldTranscribe && audioBlob && audioBlob.size > 0) {
          void this.transcribeFeedbackAudio(audioBlob);
        }
      };

      recorder.start();
      this.feedbackRecording = true;
      this.feedbackSpeechStatus = 'Recording... tap the mic again to stop.';
    } catch (error) {
      console.error('Failed to start feedback speech recording', error);
      this.feedbackError = 'Microphone access was denied or unavailable.';
      this.feedbackSpeechStatus = '';
      this.cleanupFeedbackRecorder();
    }
  }

  private stopFeedbackSpeechRecording(shouldTranscribe: boolean): void {
    this.shouldTranscribeFeedbackRecording = shouldTranscribe;

    if (this.feedbackRecorder && this.feedbackRecorder.state !== 'inactive') {
      this.feedbackSpeechStatus = shouldTranscribe ? 'Transcribing your comment...' : '';
      this.feedbackRecorder.stop();
      return;
    }

    this.cleanupFeedbackRecorder();
  }

  private abortFeedbackSpeech(): void {
    if (this.feedbackRecorder) {
      this.stopFeedbackSpeechRecording(false);
      return;
    }

    this.cleanupFeedbackRecorder();
  }

  private cleanupFeedbackRecorder(): void {
    this.feedbackRecording = false;
    this.feedbackRecorder = null;
    this.feedbackAudioChunks = [];
    this.shouldTranscribeFeedbackRecording = false;

    if (this.feedbackRecorderStream) {
      this.feedbackRecorderStream.getTracks().forEach((track) => track.stop());
      this.feedbackRecorderStream = null;
    }
  }

  private getSupportedFeedbackMimeType(): string {
    const candidates = [
      'audio/webm;codecs=opus',
      'audio/webm',
      'audio/mp4',
      'audio/ogg;codecs=opus'
    ];

    for (const candidate of candidates) {
      if (typeof MediaRecorder !== 'undefined' && MediaRecorder.isTypeSupported(candidate)) {
        return candidate;
      }
    }

    return '';
  }

  private async transcribeFeedbackAudio(audioBlob: Blob): Promise<void> {
    this.feedbackTranscribing = true;
    this.feedbackError = '';
    this.feedbackSpeechStatus = 'Transcribing your comment...';

    const extension = audioBlob.type.includes('ogg')
      ? 'ogg'
      : audioBlob.type.includes('mp4')
        ? 'mp4'
        : 'webm';
    const audioFile = new File([audioBlob], `feedback-comment.${extension}`, {
      type: audioBlob.type || 'audio/webm'
    });
    const formData = new FormData();
    formData.append('audio', audioFile);

    try {
      const response = await firstValueFrom(
        this.http.post<SpeechTranscriptionResponse>(`${this.feedbackBaseUrl}/transcribe`, formData)
      );
      const transcript = String(response?.text || '').trim();
      if (!transcript) {
        this.feedbackError = 'No speech was detected. Please try again.';
        this.feedbackSpeechStatus = '';
        return;
      }

      const spacer = this.feedbackComment.trim().length ? ' ' : '';
      const mergedComment = `${this.feedbackComment.trimEnd()}${spacer}${transcript}`.trim();
      this.feedbackComment = mergedComment.slice(0, 1000);
      this.feedbackSpeechStatus = 'Transcript added to your comment.';
      this.resetFeedbackSentiment();

      if (mergedComment.length > 1000) {
        this.feedbackError = 'Part of the transcript was trimmed to keep the comment under 1000 characters.';
      }
    } catch (error: any) {
      console.error('Failed to transcribe feedback speech', error);
      this.feedbackError = typeof error?.error === 'string'
        ? error.error
        : error?.error?.message || error?.message || 'Speech transcription failed.';
      this.feedbackSpeechStatus = '';
    } finally {
      this.feedbackTranscribing = false;
    }
  }

  async submitFeedback(): Promise<void> {
    if (this.feedbackSubmitting || this.feedbackRecording || this.feedbackTranscribing || this.feedbackSentimentLoading || !this.selectedStudentId || !this.feedbackTargetEventId) return;

    const trimmedComment = this.feedbackComment.trim();
    if (this.feedbackRating < 1 || this.feedbackRating > 5) {
      this.feedbackError = 'Please choose a rating between 1 and 5.';
      return;
    }
    if (trimmedComment.length > 1000) {
      this.feedbackError = 'Your feedback comment must stay under 1000 characters.';
      return;
    }

    this.feedbackSubmitting = true;
    this.feedbackError = '';
    const existingFeedback = this.studentFeedbacks.get(this.feedbackTargetEventId);
    const payload = {
      studentId: this.selectedStudentId,
      eventId: this.feedbackTargetEventId,
      rating: this.feedbackRating,
      comment: trimmedComment,
      sentimentLabel: this.feedbackSentimentLabel || null
    };

    try {
      const response = await firstValueFrom(
        existingFeedback
          ? this.http.put<StudentFeedback>(`${this.feedbackBaseUrl}/${existingFeedback.idFeedback}`, payload)
          : this.http.post<StudentFeedback>(this.feedbackBaseUrl, payload)
      );
      this.studentFeedbacks.set(Number(response.eventId), response);
      this.confirmationToastTone = 'confirmed';
      this.confirmationToastTitle = existingFeedback ? 'Feedback Updated' : 'Feedback Submitted';
      this.confirmationToastMessage = `${this.feedbackTargetEventName} now includes your review.`;
      this.confirmationToastVisible = true;
      if (this.confirmationToastTimer) {
        clearTimeout(this.confirmationToastTimer);
      }
      this.confirmationToastTimer = setTimeout(() => {
        this.confirmationToastVisible = false;
      }, 3200);

      const pageRoot = document.querySelector('.fo-static-page') as HTMLElement | null;
      if (pageRoot) {
        this.renderCalendarData(pageRoot);
      }
      this.closeFeedbackModal();
    } catch (error: any) {
      console.error('Failed to submit feedback', error);
      this.feedbackError = typeof error?.error === 'string'
        ? error.error
        : error?.error?.message || error?.message || 'Feedback could not be saved.';
    } finally {
      this.feedbackSubmitting = false;
    }
  }

  async analyzeFeedbackSentiment(): Promise<void> {
    if (this.feedbackSubmitting || this.feedbackRecording || this.feedbackTranscribing || this.feedbackSentimentLoading) return;

    const trimmedComment = this.feedbackComment.trim();
    if (!trimmedComment) {
      this.feedbackError = 'Please write a comment before analyzing sentiment.';
      this.resetFeedbackSentiment();
      return;
    }

    this.feedbackError = '';
    this.feedbackSentimentLoading = true;
    const requestSeq = ++this.feedbackSentimentRequestSeq;

    try {
      const response = await firstValueFrom(
        this.http.post<SentimentPredictionResponse>(`${this.pythonSentimentBaseUrl}/predict`, {
          comment: trimmedComment
        })
      );
      if (requestSeq !== this.feedbackSentimentRequestSeq) {
        return;
      }

      this.feedbackSentimentLabel = String(response?.label || '').toLowerCase();
      this.feedbackSentimentScores = response?.scores || {};
      this.feedbackSentimentSummary = this.buildFeedbackSentimentSummary(this.feedbackSentimentLabel);
      this.feedbackSentimentLastAnalyzedComment = trimmedComment;
    } catch (error: any) {
      console.error('Failed to analyze feedback sentiment', error);
      this.feedbackError = typeof error?.error === 'string'
        ? error.error
        : error?.error?.message || error?.message || 'Sentiment analysis could not be completed.';
      this.resetFeedbackSentiment();
    } finally {
      if (requestSeq === this.feedbackSentimentRequestSeq) {
        this.feedbackSentimentLoading = false;
      }
    }
  }

  getFeedbackSentimentEntries(): Array<{ key: string; value: number }> {
    return Object.entries(this.feedbackSentimentScores)
      .sort((left, right) => right[1] - left[1])
      .map(([key, value]) => ({ key, value }));
  }

  private resetFeedbackSentiment(): void {
    this.feedbackSentimentLabel = '';
    this.feedbackSentimentSummary = '';
    this.feedbackSentimentScores = {};
    this.feedbackSentimentLoading = false;
    this.feedbackSentimentLastAnalyzedComment = '';
  }

  private scheduleFeedbackSentimentAnalysis(): void {
    this.clearFeedbackSentimentDebounce();

    const trimmedComment = this.feedbackComment.trim();
    if (!trimmedComment || this.feedbackSubmitting || this.feedbackRecording || this.feedbackTranscribing) {
      return;
    }

    this.feedbackSentimentDebounceTimer = window.setTimeout(() => {
      this.feedbackSentimentDebounceTimer = null;
      if (this.feedbackComment.trim() === trimmedComment && trimmedComment !== this.feedbackSentimentLastAnalyzedComment) {
        void this.analyzeFeedbackSentiment();
      }
    }, CalendarComponent.feedbackSentimentDebounceMs);
  }

  private clearFeedbackSentimentDebounce(): void {
    if (this.feedbackSentimentDebounceTimer) {
      clearTimeout(this.feedbackSentimentDebounceTimer);
      this.feedbackSentimentDebounceTimer = null;
    }
  }

  private buildFeedbackSentimentSummary(label: string): string {
    switch (label) {
      case 'positive':
        return 'Your comment sounds positive overall.';
      case 'negative':
        return 'Your comment sounds negative overall.';
      case 'neutral':
        return 'Your comment sounds neutral or mixed.';
      default:
        return '';
    }
  }

  openRegisterConfirmation(idEvent: number, button: HTMLAnchorElement): void {
    const targetEvent = this.allEvents.find((event) => Number(event.idEvent) === Number(idEvent));
    this.pendingRegisterAction = { idEvent, button };
    this.confirmRegisterEventName = targetEvent?.name || 'this event';
    this.confirmRegisterBusy = false;
    this.confirmRegisterOpen = true;
  }

  closeRegisterConfirmation(): void {
    if (this.confirmRegisterBusy) return;
    this.confirmRegisterOpen = false;
    this.confirmRegisterEventName = '';
    this.pendingRegisterAction = null;
  }

  openCancelConfirmation(idEvent: number, button: HTMLAnchorElement): void {
    const targetEvent = this.allEvents.find((event) => Number(event.idEvent) === Number(idEvent));
    this.pendingCancelAction = { idEvent, button };
    this.confirmCancelEventName = targetEvent?.name || 'this event';
    this.confirmCancelBusy = false;
    this.confirmCancelOpen = true;
  }

  closeCancelConfirmation(): void {
    if (this.confirmCancelBusy) return;
    this.confirmCancelOpen = false;
    this.confirmCancelEventName = '';
    this.pendingCancelAction = null;
  }

  async confirmRegister(): Promise<void> {
    if (!this.pendingRegisterAction || this.confirmRegisterBusy) return;

    this.confirmRegisterBusy = true;
    const { idEvent, button } = this.pendingRegisterAction;

    try {
      await this.registerStaticStudent(idEvent, button);
      this.confirmRegisterOpen = false;
      this.confirmRegisterEventName = '';
      this.pendingRegisterAction = null;
    } finally {
      this.confirmRegisterBusy = false;
    }
  }

  async confirmCancel(): Promise<void> {
    if (!this.pendingCancelAction || this.confirmCancelBusy) return;

    this.confirmCancelBusy = true;
    const { idEvent, button } = this.pendingCancelAction;

    try {
      await this.cancelStudentRegistration(idEvent, button);
      this.confirmCancelOpen = false;
      this.confirmCancelEventName = '';
      this.pendingCancelAction = null;
    } finally {
      this.confirmCancelBusy = false;
    }
  }

  private startStatusPolling(): void {
    this.stopStatusPolling();
    this.statusPollTimer = setInterval(() => {
      void this.refreshStatusesWithAnimation();
    }, 7000);
  }

  private stopStatusPolling(): void {
    if (this.statusPollTimer) {
      clearInterval(this.statusPollTimer);
      this.statusPollTimer = null;
    }
  }

  private async refreshStatusesWithAnimation(): Promise<void> {
    const pageRoot = document.querySelector('.fo-static-page') as HTMLElement | null;
    if (!pageRoot || !this.pageVisible || !this.selectedStudentId) return;

    await this.loadRegisteredEventIdsForSelectedStudent();
    this.renderCalendarData(pageRoot);
  }

  private detectAnimatedTransitions(previousStatuses: Map<number, string>, nextStatuses: Map<number, string>): void {
    nextStatuses.forEach((nextStatus, eventId) => {
      const previousStatus = String(previousStatuses.get(eventId) || '').toUpperCase();
      if ((previousStatus === 'EN_ATTENTE' || previousStatus === 'LISTE_ATTENTE') && nextStatus === 'CONFIRMEE') {
        this.triggerStatusAnimation(eventId, 'confirmed');
      }
      if ((previousStatus === 'EN_ATTENTE' || previousStatus === 'LISTE_ATTENTE') && nextStatus === 'ANNULEE') {
        this.triggerStatusAnimation(eventId, 'refused');
      }
    });
  }

  private triggerStatusAnimation(eventId: number, tone: StatusFxTone): void {
    const targetEvent = this.allEvents.find((event) => Number(event.idEvent) === Number(eventId));
    this.eventStatusEffects.set(eventId, tone);
    this.pendingFocusEventId = eventId;
    this.confirmationToastTone = tone;
    this.confirmationToastTitle = tone === 'confirmed' ? 'Registration Accepted' : 'Registration Refused';
    this.confirmationToastMessage =
      tone === 'confirmed'
        ? `${targetEvent?.name || 'Your event'} was approved`
        : `${targetEvent?.name || 'Your event'} was refused`;
    this.confirmationToastVisible = true;

    const pageRoot = document.querySelector('.fo-static-page') as HTMLElement | null;
    if (pageRoot) {
      this.renderCalendarData(pageRoot);
    }

    const existingCleanup = this.confirmationCleanupTimers.get(eventId);
    if (existingCleanup) {
      clearTimeout(existingCleanup);
    }

    this.confirmationCleanupTimers.set(
      eventId,
      setTimeout(() => {
        this.eventStatusEffects.delete(eventId);
        this.confirmationCleanupTimers.delete(eventId);
        if (this.pendingFocusEventId === eventId) {
          this.pendingFocusEventId = null;
        }
        const freshRoot = document.querySelector('.fo-static-page') as HTMLElement | null;
        if (freshRoot) {
          this.renderCalendarData(freshRoot);
        }
      }, 3200)
    );

    if (this.confirmationToastTimer) {
      clearTimeout(this.confirmationToastTimer);
    }
    this.confirmationToastTimer = setTimeout(() => {
      this.confirmationToastVisible = false;
    }, 3600);
  }

  private clearConfirmationTimers(): void {
    this.confirmationCleanupTimers.forEach((timer) => clearTimeout(timer));
    this.confirmationCleanupTimers.clear();
    if (this.confirmationToastTimer) {
      clearTimeout(this.confirmationToastTimer);
      this.confirmationToastTimer = null;
    }
    this.eventStatusEffects.clear();
    this.pendingFocusEventId = null;
  }

  private replayPendingEntryAnimation(pageRoot: HTMLElement): void {
    if (!this.pendingFocusEventId) return;

    const eventId = this.pendingFocusEventId;
    setTimeout(() => {
      const targetCard = pageRoot.querySelector(`[data-event-card-id="${eventId}"]`) as HTMLElement | null;
      if (!targetCard) return;

      targetCard.classList.add('is-focused-target');
      targetCard.scrollIntoView({ behavior: 'smooth', block: 'center' });

      setTimeout(() => {
        targetCard.classList.remove('is-focused-target');
      }, 2600);
    }, 320);
  }

  private readPersistedStatusSnapshot(studentId: number | null): Map<number, string> {
    if (!studentId) return new Map<number, string>();

    try {
      const raw = localStorage.getItem(CalendarComponent.statusSnapshotStorageKey);
      if (!raw) return new Map<number, string>();

      const parsed = JSON.parse(raw) as Record<string, Record<string, string>>;
      const studentSnapshot = parsed?.[String(studentId)];
      if (!studentSnapshot || typeof studentSnapshot !== 'object') {
        return new Map<number, string>();
      }

      return new Map(
        Object.entries(studentSnapshot)
          .map(([eventId, status]) => [Number(eventId), String(status || '').toUpperCase()] as const)
          .filter(([eventId]) => Number.isFinite(eventId) && eventId > 0)
      );
    } catch {
      return new Map<number, string>();
    }
  }

  private writePersistedStatusSnapshot(): void {
    if (!this.selectedStudentId) return;

    try {
      const raw = localStorage.getItem(CalendarComponent.statusSnapshotStorageKey);
      const parsed = raw ? (JSON.parse(raw) as Record<string, Record<string, string>>) : {};
      parsed[String(this.selectedStudentId)] = Object.fromEntries(this.studentEventStatuses.entries());
      localStorage.setItem(CalendarComponent.statusSnapshotStorageKey, JSON.stringify(parsed));
    } catch {
      // Ignore storage failures and keep the live UI responsive.
    }
  }

  private toDate(value?: string | null): Date {
    if (!value) return new Date(0);
    const date = new Date(String(value).replace(' ', 'T'));
    return Number.isNaN(date.getTime()) ? new Date(0) : date;
  }

  private toTime(value?: string | null): string {
    const date = this.toDate(value);
    if (date.getTime() === 0) return '--:--';
    return date.toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit' });
  }

  private escapeHtml(value: string): string {
    return value
      .replaceAll('&', '&amp;')
      .replaceAll('<', '&lt;')
      .replaceAll('>', '&gt;')
      .replaceAll('"', '&quot;')
      .replaceAll("'", '&#039;');
  }
}

interface FrontofficeEvent {
  idEvent: number;
  name: string;
  description: string;
  imageUrl?: string;
  location: string;
  capacite: number;
  inscriptionsCount?: number;
  dateDebut: string;
  dateFin: string;
  type: string;
  statut: string;
}

interface RecommendationApiEvent {
  idEvent: number;
  name: string;
  imageUrl?: string;
  type: string;
  location?: string;
  statut?: string;
  capacite?: number;
  inscriptionsCount?: number;
  recommendationScore?: number;
  recommendationReason?: string;
  dateDebut?: string;
  dateFin?: string;
  description?: string;
}

interface RecommendedEvent extends FrontofficeEvent {
  recommendationScore: number;
  recommendationReason: string;
}

interface EventAssignmentResponse {
  eventId: number;
  studentId: number;
  inscriptionStatus: string;
}

interface StudentFeedback {
  idFeedback: number;
  studentId: number;
  eventId: number;
  rating: number;
  comment: string;
  sentimentLabel?: string;
  dateCreation: string;
  statut: string;
}

interface SpeechTranscriptionResponse {
  text: string;
}

interface SentimentPredictionResponse {
  comment: string;
  label: string;
  scores: Record<string, number>;
}

type EventTypeKey = 'WEBINAIRE' | 'FORMATION' | 'ATELIER' | 'HACKATHON';
type FrontofficeFilter = 'ALL' | EventTypeKey;
type FrontofficeStatusFilter = 'ALL' | 'PUBLIE' | 'TERMINE';
type FrontofficeSortOrder = 'DATE_ASC' | 'DATE_DESC' | 'NAME_ASC' | 'NAME_DESC';
type StatusFxTone = 'confirmed' | 'refused';
