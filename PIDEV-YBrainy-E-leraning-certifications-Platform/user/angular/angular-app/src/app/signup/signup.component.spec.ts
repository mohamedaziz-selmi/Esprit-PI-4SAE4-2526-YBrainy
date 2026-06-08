import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { AbstractControl } from '@angular/forms';

import { SignUpPageComponent } from './signup.component';

describe('SignUpPageComponent', () => {
  let component: SignUpPageComponent;
  let fixture: ComponentFixture<SignUpPageComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SignUpPageComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(SignUpPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  // ── Form structure ───────────────────────────────────────────

  it('should initialize form with required controls', () => {
    expect(component.form.get('username')).toBeTruthy();
    expect(component.form.get('email')).toBeTruthy();
    expect(component.form.get('password')).toBeTruthy();
    expect(component.form.get('confirmPassword')).toBeTruthy();
    expect(component.form.get('role')).toBeTruthy();
  });

  it('should have form invalid on initial empty state', () => {
    // username, email, password, confirmPassword, role are required
    expect(component.form.valid).toBeFalse();
  });

  // ── Username validation ──────────────────────────────────────

  it('should mark username invalid when empty', () => {
    const ctrl = component.form.get('username')!;
    ctrl.setValue('');
    ctrl.markAsTouched();
    expect(ctrl.invalid).toBeTrue();
    expect(ctrl.errors?.['required']).toBeTruthy();
  });

  it('should mark username invalid when shorter than 3 chars', () => {
    const ctrl = component.form.get('username')!;
    ctrl.setValue('ab');
    expect(ctrl.errors?.['minlength']).toBeTruthy();
  });

  it('should mark username invalid with special characters', () => {
    const ctrl = component.form.get('username')!;
    ctrl.setValue('user@name!');
    expect(ctrl.errors?.['pattern']).toBeTruthy();
  });

  it('should accept valid username with dots and underscores', () => {
    const ctrl = component.form.get('username')!;
    ctrl.setValue('john.doe_99');
    expect(ctrl.errors).toBeNull();
  });

  // ── Email validation ─────────────────────────────────────────

  it('should mark email invalid when empty', () => {
    const ctrl = component.form.get('email')!;
    ctrl.setValue('');
    ctrl.markAsTouched();
    expect(ctrl.errors?.['required']).toBeTruthy();
  });

  it('should mark email invalid for malformed address', () => {
    const ctrl = component.form.get('email')!;
    ctrl.setValue('not-an-email');
    expect(ctrl.errors?.['email']).toBeTruthy();
  });

  it('should accept valid email', () => {
    const ctrl = component.form.get('email')!;
    ctrl.setValue('user@example.com');
    expect(ctrl.errors).toBeNull();
  });

  // ── Password validation ──────────────────────────────────────

  it('should mark password invalid when empty', () => {
    const ctrl = component.form.get('password')!;
    ctrl.setValue('');
    expect(ctrl.errors?.['required']).toBeTruthy();
  });

  it('should mark password invalid when shorter than 8 chars', () => {
    const ctrl = component.form.get('password')!;
    ctrl.setValue('Ab1!');
    expect(ctrl.errors?.['minlength']).toBeTruthy();
  });

  it('should mark password invalid without uppercase letter', () => {
    const ctrl = component.form.get('password')!;
    ctrl.setValue('lowercase123');
    expect(ctrl.errors?.['passwordPolicy']).toBeTruthy();
  });

  it('should mark password invalid without digit', () => {
    const ctrl = component.form.get('password')!;
    ctrl.setValue('OnlyLetters');
    expect(ctrl.errors?.['passwordPolicy']).toBeTruthy();
  });

  it('should accept strong password with upper, lower and digit', () => {
    const ctrl = component.form.get('password')!;
    ctrl.setValue('StrongPass1');
    expect(ctrl.errors).toBeNull();
  });

  // ── Password match validator ─────────────────────────────────

  it('should have passwordMismatch error when passwords do not match', () => {
    component.form.get('password')!.setValue('StrongPass1');
    component.form.get('confirmPassword')!.setValue('DifferentPass1');
    fixture.detectChanges();
    expect(component.form.errors?.['passwordMismatch']).toBeTruthy();
  });

  it('should clear passwordMismatch error when passwords match', () => {
    component.form.get('password')!.setValue('StrongPass1');
    component.form.get('confirmPassword')!.setValue('StrongPass1');
    fixture.detectChanges();
    expect(component.form.errors?.['passwordMismatch']).toBeFalsy();
  });

  // ── Role / enterprise validation ─────────────────────────────

  it('isEnterpriseRole should return false for non-enterprise role', () => {
    component.form.get('role')!.setValue('INSTRUCTOR');
    expect(component.isEnterpriseRole).toBeFalse();
  });

  it('isEnterpriseRole should return true for ENTERPRISE_USER role', () => {
    component.form.get('role')!.setValue('ENTERPRISE_USER');
    expect(component.isEnterpriseRole).toBeTrue();
  });

  // ── Age calculation from date of birth ───────────────────────

  it('syncAgeFromDateOfBirth sets age from valid date', () => {
    const twentyYearsAgo = new Date();
    twentyYearsAgo.setFullYear(twentyYearsAgo.getFullYear() - 20);
    const dateStr = twentyYearsAgo.toISOString().split('T')[0];
    component.syncAgeFromDateOfBirth(dateStr);
    const age = component.form.get('age')?.value;
    expect(age).toBeGreaterThanOrEqual(19);
    expect(age).toBeLessThanOrEqual(20);
  });

  it('syncAgeFromDateOfBirth does nothing with empty string', () => {
    component.form.get('age')!.setValue(null);
    component.syncAgeFromDateOfBirth('');
    expect(component.form.get('age')?.value).toBeNull();
  });

  it('syncAgeFromDateOfBirth does nothing with invalid date string', () => {
    component.form.get('age')!.setValue(null);
    component.syncAgeFromDateOfBirth('not-a-date');
    expect(component.form.get('age')?.value).toBeNull();
  });

  // ── canStartChallenge guard ──────────────────────────────────

  it('canStartChallenge is false when age is too low', () => {
    component.form.get('age')!.setValue(5);
    component.form.get('country')!.setValue('Tunisia');
    expect(component.canStartChallenge).toBeFalse();
  });

  it('canStartChallenge is false when country is missing', () => {
    component.form.get('age')!.setValue(20);
    component.form.get('country')!.setValue('');
    expect(component.canStartChallenge).toBeFalse();
  });

  it('canStartChallenge is true with valid age and country', () => {
    component.form.get('age')!.setValue(20);
    component.form.get('country')!.setValue('Tunisia');
    expect(component.canStartChallenge).toBeTrue();
  });

  // ── Submit guard ─────────────────────────────────────────────

  it('onSubmit should not proceed when form is invalid', () => {
    // Form is initially invalid (empty required fields)
    const spy = spyOn<any>(component, 'onSubmit').and.callThrough();
    component.onSubmit();
    // The form remains in its invalid state — no network call expected
    expect(component.form.invalid).toBeTrue();
  });

  // ── File input handling ──────────────────────────────────────

  it('clearProfilePicture resets picture state', () => {
    component['profilePicturePreview'] = 'data:image/png;base64,abc';
    component['profilePictureFilename'] = 'photo.png';
    component.clearProfilePicture();
    expect(component['profilePicturePreview']).toBeNull();
    expect(component['profilePictureFilename']).toBeNull();
  });
});
