import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';

import { UserService, UserProfile } from './user.service';
import { UserSessionService } from '../../tracking/user-session.service';

describe('UserService', () => {
  let service: UserService;
  let httpMock: HttpTestingController;
  let sessionSpy: jasmine.SpyObj<UserSessionService>;

  const mockProfile: UserProfile = {
    userId: 42,
    username: 'alice',
    email: 'alice@example.com',
    role: 'INSTRUCTOR',
  };

  beforeEach(() => {
    sessionSpy = jasmine.createSpyObj('UserSessionService', ['get', 'set']);
    sessionSpy.get.and.returnValue({ userId: 42, role: 'INSTRUCTOR', email: 'alice@example.com', username: 'alice' });

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: UserSessionService, useValue: sessionSpy },
      ],
    });

    service = TestBed.inject(UserService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  // ── updateUser ───────────────────────────────────────────────

  it('updateUser should send PUT request with DTO', () => {
    const dto = { firstName: 'Alice', lastName: 'Smith' };
    service.updateUser(42, dto).subscribe(user => {
      expect(user.username).toBe('alice');
    });

    const req = httpMock.expectOne(r => r.url.includes('/api/users/42') && !r.url.includes('/password'));
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(dto);
    req.flush(mockProfile);
  });

  it('updateUser should refresh session on success', () => {
    service.updateUser(42, { firstName: 'Alice' }).subscribe();
    const req = httpMock.expectOne(r => r.url.includes('/api/users/42') && !r.url.includes('/password'));
    req.flush(mockProfile);
    expect(sessionSpy.set).toHaveBeenCalledWith(jasmine.objectContaining({ userId: 42, role: 'INSTRUCTOR' }));
  });

  // ── changePassword ───────────────────────────────────────────

  it('changePassword should send PUT to password endpoint', () => {
    service.changePassword(42, 'NewPass123', 'NewPass123').subscribe();

    const req = httpMock.expectOne(r => r.url.includes('/api/users/42/password'));
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ newPassword: 'NewPass123', confirmPassword: 'NewPass123' });
    req.flush(null);
  });

  // ── deleteUser ───────────────────────────────────────────────

  it('deleteUser should send DELETE request', () => {
    service.deleteUser(42).subscribe();

    const req = httpMock.expectOne(r =>
      r.url.includes('/api/users/42') &&
      !r.url.includes('/password') &&
      !r.url.includes('/face-biometric')
    );
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });

  // ── registerFaceBiometric ────────────────────────────────────

  it('registerFaceBiometric should POST FormData with image blob', () => {
    const blob = new Blob(['fake-image'], { type: 'image/png' });
    service.registerFaceBiometric(42, blob).subscribe();

    const req = httpMock.expectOne(r => r.url.includes('/api/users/42/face-biometric') && r.method === 'POST');
    expect(req.request.body instanceof FormData).toBeTrue();
    req.flush(mockProfile);
  });

  it('registerFaceBiometric should refresh session on success', () => {
    const blob = new Blob(['fake'], { type: 'image/png' });
    service.registerFaceBiometric(42, blob).subscribe();

    const req = httpMock.expectOne(r => r.url.includes('/api/users/42/face-biometric') && r.method === 'POST');
    req.flush(mockProfile);
    expect(sessionSpy.set).toHaveBeenCalledWith(jasmine.objectContaining({ userId: 42 }));
  });

  // ── removeFaceBiometric ──────────────────────────────────────

  it('removeFaceBiometric should send DELETE to face-biometric endpoint', () => {
    service.removeFaceBiometric(42).subscribe();

    const req = httpMock.expectOne(r => r.url.includes('/api/users/42/face-biometric') && r.method === 'DELETE');
    expect(req.request.method).toBe('DELETE');
    req.flush(mockProfile);
  });

  // ── JWT decoding ─────────────────────────────────────────────

  it('should decode valid JWT claims', () => {
    // Build a minimal JWT: header.payload.signature
    const payload = btoa(JSON.stringify({ sub: '42', email: 'alice@example.com', preferred_username: 'alice' }));
    const jwt = `eyJhbGciOiJSUzI1NiJ9.${payload}.sig`;

    const claims = (service as any).decodeJwtClaims(jwt);
    expect(claims).not.toBeNull();
    expect(claims['email']).toBe('alice@example.com');
    expect(claims['preferred_username']).toBe('alice');
  });

  it('should return null for malformed JWT', () => {
    expect((service as any).decodeJwtClaims('not.a.jwt.token.extra')).toBeNull();
    // Actually this might work, let's test truly malformed
    expect((service as any).decodeJwtClaims('only-one-part')).toBeNull();
  });

  it('should return null for null/undefined token', () => {
    expect((service as any).decodeJwtClaims(null)).toBeNull();
    expect((service as any).decodeJwtClaims(undefined)).toBeNull();
    expect((service as any).decodeJwtClaims('')).toBeNull();
  });

  // ── base64url normalisation ──────────────────────────────────

  it('normalizeBase64Url converts URL-safe chars and adds padding', () => {
    // '-' → '+', '_' → '/'
    const input = 'abc-def_ghi';
    const result = (service as any).normalizeBase64Url(input);
    expect(result).not.toContain('-');
    expect(result).not.toContain('_');
    // length should be multiple of 4 after padding
    expect(result.length % 4).toBe(0);
  });

  // ── getCurrentUser error path ────────────────────────────────

  it('getCurrentUser should throw when no session or JWT identity available', (done) => {
    sessionSpy.get.and.returnValue(null as any);
    // Patch getToken to return null via spyOn on the module
    spyOn<any>(service, 'decodeJwtClaims').and.returnValue(null);

    service.getCurrentUser().subscribe({
      error: (err) => {
        expect(err.message).toContain('Unable to resolve');
        done();
      }
    });
  });
});
