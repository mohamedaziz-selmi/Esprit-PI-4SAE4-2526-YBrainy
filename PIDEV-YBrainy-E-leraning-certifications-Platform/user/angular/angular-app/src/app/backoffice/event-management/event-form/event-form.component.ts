import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Event, EventType, EventStatut } from '../../../models/event.model';
import { EventApiService } from '../../../frontoffice/services/event-api.service';

@Component({
  selector: 'app-event-form',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './event-form.component.html',
  styleUrl: './event-form.component.css'
})
export class EventFormComponent implements OnInit {
  form!: FormGroup;
  isEditMode = false;
  eventId: number | null = null;
  loading = false;
  saving = false;
  error = '';
  success = '';

  generatingDesc = false;
  generatingImage = false;
  imageUrl = '';

  readonly eventTypes = Object.values(EventType);
  readonly eventStatuses = Object.values(EventStatut);

  constructor(
    private fb: FormBuilder,
    private route: ActivatedRoute,
    private router: Router,
    private eventApi: EventApiService
  ) {}

  ngOnInit(): void {
    this.buildForm();

    const idParam = this.route.snapshot.paramMap.get('id');
    if (idParam) {
      this.isEditMode = true;
      this.eventId = +idParam;
      this.loadEvent(this.eventId);
    }
  }

  buildForm(): void {
    this.form = this.fb.group({
      name: ['', [Validators.required, Validators.minLength(3)]],
      description: ['', Validators.required],
      location: ['', Validators.required],
      capacite: [50, [Validators.required, Validators.min(1)]],
      dateDebut: ['', Validators.required],
      dateFin: ['', Validators.required],
      type: [EventType.FORMATION, Validators.required],
      statut: [EventStatut.UPCOMING, Validators.required],
    });
  }

  loadEvent(id: number): void {
    this.loading = true;
    this.eventApi.getById(id).subscribe({
      next: (event) => {
        this.form.patchValue({
          name: event.name,
          description: event.description,
          location: event.location,
          capacite: event.capacite,
          dateDebut: this.toInputDate(event.dateDebut),
          dateFin: this.toInputDate(event.dateFin),
          type: event.type,
          statut: event.statut,
        });
        this.loading = false;
      },
      error: () => {
        this.error = 'Failed to load event data.';
        this.loading = false;
      }
    });
  }

  toInputDate(dateStr: string): string {
    if (!dateStr) return '';
    const d = new Date(dateStr);
    if (isNaN(d.getTime())) return '';
    return d.toISOString().slice(0, 16);
  }

  generateDescription(): void {
    const name = this.form.get('name')?.value;
    const type = this.form.get('type')?.value;
    if (!name || !type) {
      this.error = 'Enter event name and type before generating a description.';
      return;
    }
    this.generatingDesc = true;
    this.error = '';
    this.eventApi.generateDescription(name, type).subscribe({
      next: (res) => {
        this.form.patchValue({ description: res.description });
        this.generatingDesc = false;
      },
      error: () => {
        this.error = 'AI description generation failed. Please write one manually.';
        this.generatingDesc = false;
      }
    });
  }

  generateImage(): void {
    const name = this.form.get('name')?.value;
    const description = this.form.get('description')?.value;
    const type = this.form.get('type')?.value;
    if (!name || !description || !type) {
      this.error = 'Fill in the name, description, and type before generating an image.';
      return;
    }
    this.generatingImage = true;
    this.error = '';
    this.eventApi.generateImage(name, description, type).subscribe({
      next: (res) => {
        this.imageUrl = res.imageUrl;
        this.generatingImage = false;
      },
      error: () => {
        this.error = 'AI image generation failed.';
        this.generatingImage = false;
      }
    });
  }

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  onImageFileChange(fileInputEvent: any): void {
    const input = fileInputEvent.target as HTMLInputElement;
    if (!input.files?.length) return;
    const file = input.files[0];
    this.generatingImage = true;
    this.error = '';
    this.eventApi.uploadImage(file).subscribe({
      next: (res) => {
        this.imageUrl = res.imageUrl;
        this.generatingImage = false;
      },
      error: () => {
        this.error = 'Image upload failed.';
        this.generatingImage = false;
      }
    });
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving = true;
    this.error = '';
    this.success = '';

    const raw = this.form.value;
    const payload: Partial<Event> = {
      name: raw.name,
      description: raw.description,
      location: raw.location,
      capacite: Number(raw.capacite),
      dateDebut: new Date(raw.dateDebut).toISOString(),
      dateFin: new Date(raw.dateFin).toISOString(),
      type: raw.type,
      statut: raw.statut,
    };

    if (this.isEditMode && this.eventId) {
      const updatePayload: Event = { ...payload, idEvent: this.eventId! } as Event;
      this.eventApi.update(updatePayload).subscribe({
        next: () => {
          this.success = 'Event updated successfully!';
          this.saving = false;
          setTimeout(() => this.router.navigate(['/dashboard/events']), 1200);
        },
        error: () => {
          this.error = 'Failed to update event. Please try again.';
          this.saving = false;
        }
      });
    } else {
      this.eventApi.create(payload).subscribe({
        next: () => {
          this.success = 'Event created successfully!';
          this.saving = false;
          setTimeout(() => this.router.navigate(['/dashboard/events']), 1200);
        },
        error: () => {
          this.error = 'Failed to create event. Please try again.';
          this.saving = false;
        }
      });
    }
  }

  cancel(): void {
    this.router.navigate(['/dashboard/events']);
  }

  get f() { return this.form.controls; }
}
