import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of, throwError } from 'rxjs';

import { EventListComponent } from './event-list.component';
import { EventApiService, EventAnalytics, PendingInscription } from '../../../frontoffice/services/event-api.service';
import { Event, EventStatut, EventType } from '../../../models/event.model';

describe('EventListComponent', () => {
  let component: EventListComponent;
  let fixture: ComponentFixture<EventListComponent>;
  let eventApiSpy: jasmine.SpyObj<EventApiService>;
  let routerSpy: jasmine.SpyObj<Router>;

  const mockEvents: Event[] = [
    { idEvent: 1, name: 'Spring Conference', location: 'Tunis', capacite: 100,
      dateDebut: '2025-06-01T09:00', dateFin: '2025-06-01T18:00',
      type: EventType.FORMATION, statut: EventStatut.UPCOMING } as Event,
    { idEvent: 2, name: 'React Workshop', location: 'Sfax', capacite: 50,
      dateDebut: '2025-07-01T10:00', dateFin: '2025-07-01T17:00',
      type: EventType.WORKSHOP, statut: EventStatut.UPCOMING } as Event,
    { idEvent: 3, name: 'DevOps Webinar', location: 'Online', capacite: 200,
      dateDebut: '2025-08-01T14:00', dateFin: '2025-08-01T16:00',
      type: EventType.WEBINAR, statut: EventStatut.CANCELLED } as Event,
  ];

  const mockAnalytics: EventAnalytics = {
    trend: { currentLabel: 'This week', currentTotal: 10, previousLabel: 'Last week',
              previousTotal: 8, labels: [], currentData: [], previousData: [] },
    overview: { range: 'week', labels: [], eventCounts: [], registrations: [], activeEvents: [] }
  };

  const mockPending: PendingInscription[] = [
    { idInscription: 1, idStudent: 10, idEvent: 1, eventName: 'Spring Conference',
      dateInscription: '2025-05-01', statut: 'EN_ATTENTE' }
  ];

  beforeEach(async () => {
    eventApiSpy = jasmine.createSpyObj('EventApiService', [
      'getAll', 'getAnalytics', 'getPendingInscriptions', 'delete', 'updateInscriptionStatus'
    ]);
    eventApiSpy.getAll.and.returnValue(of(mockEvents));
    eventApiSpy.getAnalytics.and.returnValue(of(mockAnalytics));
    eventApiSpy.getPendingInscriptions.and.returnValue(of(mockPending));

    await TestBed.configureTestingModule({
      imports: [EventListComponent],
      providers: [
        provideRouter([]),
        { provide: EventApiService, useValue: eventApiSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(EventListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  // ── Initial load ─────────────────────────────────────────────

  it('should load events on init', () => {
    expect(eventApiSpy.getAll).toHaveBeenCalled();
    expect(component.events.length).toBe(3);
    expect(component.loading).toBeFalse();
  });

  it('should load analytics on init', () => {
    expect(eventApiSpy.getAnalytics).toHaveBeenCalledWith('week');
    expect(component.analytics).toEqual(mockAnalytics);
  });

  it('should load pending inscriptions on init', () => {
    expect(eventApiSpy.getPendingInscriptions).toHaveBeenCalled();
    expect(component.pendingInscriptions.length).toBe(1);
  });

  it('should set error message on load failure', () => {
    component.events = [];
    eventApiSpy.getAll.and.returnValue(throwError(() => new Error('Network error')));
    component.loadEvents();
    expect(component.error).toContain('Failed to load events');
    expect(component.loading).toBeFalse();
  });

  // ── Filter logic ─────────────────────────────────────────────

  it('applyFilters with no criteria shows all events', () => {
    component.searchTerm = '';
    component.filterType = '';
    component.filterStatus = '';
    component.applyFilters();
    expect(component.filteredEvents.length).toBe(3);
  });

  it('applyFilters by search term filters by name (case-insensitive)', () => {
    component.searchTerm = 'spring';
    component.filterType = '';
    component.filterStatus = '';
    component.applyFilters();
    expect(component.filteredEvents.length).toBe(1);
    expect(component.filteredEvents[0].name).toBe('Spring Conference');
  });

  it('applyFilters by type shows only matching events', () => {
    component.searchTerm = '';
    component.filterType = EventType.WORKSHOP;
    component.filterStatus = '';
    component.applyFilters();
    expect(component.filteredEvents.length).toBe(1);
    expect(component.filteredEvents[0].type).toBe(EventType.WORKSHOP);
  });

  it('applyFilters by status shows only matching events', () => {
    component.searchTerm = '';
    component.filterType = '';
    component.filterStatus = EventStatut.CANCELLED;
    component.applyFilters();
    expect(component.filteredEvents.length).toBe(1);
    expect(component.filteredEvents[0].statut).toBe(EventStatut.CANCELLED);
  });

  it('applyFilters combines search and type filters', () => {
    component.searchTerm = 'conference';
    component.filterType = EventType.FORMATION;
    component.filterStatus = '';
    component.applyFilters();
    expect(component.filteredEvents.length).toBe(1);
  });

  it('applyFilters returns empty when no match', () => {
    component.searchTerm = 'nonexistent-event-xyz';
    component.filterType = '';
    component.filterStatus = '';
    component.applyFilters();
    expect(component.filteredEvents.length).toBe(0);
  });

  // ── Analytics trendDelta ─────────────────────────────────────

  it('trendDelta calculates percentage change from analytics', () => {
    // currentTotal=10, previousTotal=8 → ((10-8)/8)*100 = 25
    expect(component.trendDelta).toBeCloseTo(25, 1);
  });

  it('trendDelta returns 0 when analytics is null', () => {
    component.analytics = null;
    expect(component.trendDelta).toBe(0);
  });

  it('trendDelta returns 0 when previousTotal is 0', () => {
    component.analytics = {
      ...mockAnalytics,
      trend: { ...mockAnalytics.trend, previousTotal: 0, currentTotal: 5 }
    };
    expect(component.trendDelta).toBe(0);
  });

  // ── Navigation ───────────────────────────────────────────────

  it('createEvent navigates to new event form', () => {
    const router = TestBed.inject(Router);
    const spy = spyOn(router, 'navigate');
    component.createEvent();
    expect(spy).toHaveBeenCalledWith(jasmine.arrayContaining([jasmine.stringContaining('new')]));
  });

  it('editEvent navigates to edit route with event id', () => {
    const router = TestBed.inject(Router);
    const spy = spyOn(router, 'navigate');
    component.editEvent(mockEvents[0]);
    expect(spy).toHaveBeenCalledWith(jasmine.arrayContaining([1, jasmine.any(String)]));
  });

  // ── Delete ───────────────────────────────────────────────────

  it('deleteEvent calls api.delete and removes from events list', () => {
    spyOn(window, 'confirm').and.returnValue(true);
    eventApiSpy.delete.and.returnValue(of(undefined));
    component.deleteEvent(mockEvents[0]);
    expect(eventApiSpy.delete).toHaveBeenCalledWith(1);
    expect(component.events.find(e => e.idEvent === 1)).toBeUndefined();
  });

  it('deleteEvent does nothing when confirm is cancelled', () => {
    spyOn(window, 'confirm').and.returnValue(false);
    component.deleteEvent(mockEvents[0]);
    expect(eventApiSpy.delete).not.toHaveBeenCalled();
  });

  // ── Inscription actions ──────────────────────────────────────

  it('confirmInscription calls updateInscriptionStatus with CONFIRMEE', () => {
    eventApiSpy.updateInscriptionStatus.and.returnValue(of(undefined));
    component.confirmInscription(mockPending[0]);
    expect(eventApiSpy.updateInscriptionStatus).toHaveBeenCalledWith(1, 'CONFIRMEE');
  });

  it('confirmInscription removes inscription from pending list on success', () => {
    eventApiSpy.updateInscriptionStatus.and.returnValue(of(undefined));
    component.confirmInscription(mockPending[0]);
    expect(component.pendingInscriptions.length).toBe(0);
  });

  it('rejectInscription calls updateInscriptionStatus with ANNULEE', () => {
    eventApiSpy.updateInscriptionStatus.and.returnValue(of(undefined));
    component.rejectInscription(mockPending[0]);
    expect(eventApiSpy.updateInscriptionStatus).toHaveBeenCalledWith(1, 'ANNULEE');
  });

  // ── CSS class helpers ────────────────────────────────────────

  it('getStatusClass returns correct class for each status', () => {
    expect(component.getStatusClass(EventStatut.UPCOMING)).toContain('upcoming');
    expect(component.getStatusClass(EventStatut.CANCELLED)).toContain('cancelled');
  });

  it('getTypeClass returns correct class for each type', () => {
    expect(component.getTypeClass(EventType.WEBINAR)).toContain('webinar');
    expect(component.getTypeClass(EventType.WORKSHOP)).toContain('workshop');
  });

  // ── Date formatting ──────────────────────────────────────────

  it('formatDate returns formatted string for valid ISO date', () => {
    const result = component.formatDate('2025-06-01T09:00:00');
    expect(result).toBeTruthy();
    expect(typeof result).toBe('string');
  });

  it('formatDate returns dash for empty string', () => {
    expect(component.formatDate('')).toBe('—');
  });
});
