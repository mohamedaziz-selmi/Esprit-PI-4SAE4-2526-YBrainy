import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, ActivatedRoute, convertToParamMap } from '@angular/router';
import { ReactiveFormsModule } from '@angular/forms';
import { of, throwError } from 'rxjs';

import { EventFormComponent } from './event-form.component';
import { EventApiService } from '../../../frontoffice/services/event-api.service';
import { EventType, EventStatut } from '../../../models/event.model';

describe('EventFormComponent', () => {
  let component: EventFormComponent;
  let fixture: ComponentFixture<EventFormComponent>;
  let eventApiSpy: jasmine.SpyObj<EventApiService>;

  const mockEvent = {
    idEvent: 1,
    name: 'Spring Boot Conference',
    description: 'A great event',
    location: 'Tunis',
    capacite: 100,
    dateDebut: '2025-06-01T09:00:00',
    dateFin: '2025-06-01T18:00:00',
    type: EventType.FORMATION,
    statut: EventStatut.UPCOMING,
  };

  function createComponent(routeParams: Record<string, string> = {}) {
    return TestBed.configureTestingModule({
      imports: [EventFormComponent, ReactiveFormsModule],
      providers: [
        provideRouter([]),
        { provide: EventApiService, useValue: eventApiSpy },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: { paramMap: convertToParamMap(routeParams) }
          }
        }
      ],
    }).compileComponents();
  }

  beforeEach(() => {
    eventApiSpy = jasmine.createSpyObj('EventApiService', [
      'getById', 'create', 'update', 'generateDescription', 'generateImage', 'uploadImage'
    ]);
  });

  // ── Create mode ──────────────────────────────────────────────

  describe('create mode (no route id)', () => {
    beforeEach(async () => {
      await createComponent();
      fixture = TestBed.createComponent(EventFormComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
    });

    it('should create in create mode', () => {
      expect(component).toBeTruthy();
      expect(component.isEditMode).toBeFalse();
      expect(component.eventId).toBeNull();
    });

    it('should initialize form with default values', () => {
      expect(component.form.get('name')?.value).toBe('');
      expect(component.form.get('capacite')?.value).toBe(50);
      expect(component.form.get('type')?.value).toBe(EventType.FORMATION);
      expect(component.form.get('statut')?.value).toBe(EventStatut.UPCOMING);
    });

    // Form validation

    it('form should be invalid when required fields are empty', () => {
      expect(component.form.valid).toBeFalse();
    });

    it('name should be invalid when empty', () => {
      const ctrl = component.form.get('name')!;
      ctrl.setValue('');
      ctrl.markAsTouched();
      expect(ctrl.errors?.['required']).toBeTruthy();
    });

    it('name should be invalid when shorter than 3 characters', () => {
      component.form.get('name')!.setValue('AB');
      expect(component.form.get('name')?.errors?.['minlength']).toBeTruthy();
    });

    it('name should be valid with 3+ characters', () => {
      component.form.get('name')!.setValue('Tech Meetup');
      expect(component.form.get('name')?.valid).toBeTrue();
    });

    it('capacite should be invalid when 0', () => {
      component.form.get('capacite')!.setValue(0);
      expect(component.form.get('capacite')?.errors?.['min']).toBeTruthy();
    });

    it('capacite should be valid when positive', () => {
      component.form.get('capacite')!.setValue(50);
      expect(component.form.get('capacite')?.valid).toBeTrue();
    });

    it('description is required', () => {
      component.form.get('description')!.setValue('');
      component.form.get('description')!.markAsTouched();
      expect(component.form.get('description')?.errors?.['required']).toBeTruthy();
    });

    it('submit should not call create when form is invalid', () => {
      component.submit();
      expect(eventApiSpy.create).not.toHaveBeenCalled();
    });

    it('submit should call create when form is valid', () => {
      eventApiSpy.create.and.returnValue(of(mockEvent as any));
      fillFormValid(component);
      component.submit();
      expect(eventApiSpy.create).toHaveBeenCalled();
    });

    it('cancel should navigate away', () => {
      const routerSpy = spyOn(component['router'], 'navigate');
      component.cancel();
      expect(routerSpy).toHaveBeenCalled();
    });

    it('generateDescription should set generatingDesc flag while loading', () => {
      component.form.get('name')!.setValue('Tech Event');
      component.form.get('type')!.setValue(EventType.FORMATION);
      eventApiSpy.generateDescription.and.returnValue(of({ description: 'AI desc', generatedByAi: true }));
      component.generateDescription();
      expect(eventApiSpy.generateDescription).toHaveBeenCalledWith('Tech Event', EventType.FORMATION);
    });

    it('generateDescription should not call API when name or type is missing', () => {
      component.form.get('name')!.setValue('');
      component.generateDescription();
      expect(eventApiSpy.generateDescription).not.toHaveBeenCalled();
    });
  });

  // ── Edit mode ────────────────────────────────────────────────

  describe('edit mode (route id = 1)', () => {
    beforeEach(async () => {
      eventApiSpy.getById = jasmine.createSpy().and.returnValue(of(mockEvent as any));
      await createComponent({ id: '1' });
      fixture = TestBed.createComponent(EventFormComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
    });

    it('should enter edit mode when route has id', () => {
      expect(component.isEditMode).toBeTrue();
      expect(component.eventId).toBe(1);
    });

    it('should load event and patch form in edit mode', () => {
      expect(eventApiSpy.getById).toHaveBeenCalledWith(1);
      expect(component.form.get('name')?.value).toBe('Spring Boot Conference');
      expect(component.form.get('location')?.value).toBe('Tunis');
    });

    it('submit should call update in edit mode', () => {
      eventApiSpy.update.and.returnValue(of(mockEvent as any));
      fillFormValid(component);
      component.submit();
      expect(eventApiSpy.update).toHaveBeenCalled();
    });
  });

  // ── toInputDate helper ───────────────────────────────────────

  describe('toInputDate helper', () => {
    beforeEach(async () => {
      await createComponent();
      fixture = TestBed.createComponent(EventFormComponent);
      component = fixture.componentInstance;
    });

    it('converts ISO string to datetime-local format', () => {
      const result = component.toInputDate('2025-06-01T09:30:00');
      expect(result).toBe('2025-06-01T09:30');
    });

    it('returns empty string for falsy input', () => {
      expect(component.toInputDate('')).toBe('');
      expect(component.toInputDate(null as any)).toBe('');
    });
  });

  function fillFormValid(c: EventFormComponent): void {
    c.form.patchValue({
      name: 'Spring Conference',
      description: 'A technical conference about Spring',
      location: 'Tunis',
      capacite: 100,
      dateDebut: '2025-06-01T09:00',
      dateFin: '2025-06-01T18:00',
      type: EventType.FORMATION,
      statut: EventStatut.UPCOMING,
    });
  }
});
