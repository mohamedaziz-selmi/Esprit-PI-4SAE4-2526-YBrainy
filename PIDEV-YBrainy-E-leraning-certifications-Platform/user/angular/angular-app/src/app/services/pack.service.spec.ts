import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { PackService } from './pack.service';
import { Pack, PackStatus, PackLevel, PageResponse } from '../models/pack.model';

describe('PackService', () => {
  let service: PackService;
  let http: HttpTestingController;
  const adminBase = '/api/admin/packs';
  const publicBase = '/api/packs';

  const samplePack = (): Pack => ({
    id: 1,
    title: 'Full-Stack Pack',
    description: 'Learn full-stack dev',
    level: 'INTERMEDIATE' as PackLevel,
    status: 'ACTIVE' as PackStatus,
    price: 149,
    salePrice: 99,
    categoryId: 2,
    thumbnailUrl: null,
    courseIds: [],
    createdAt: '2026-01-01'
  } as unknown as Pack);

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [PackService]
    });
    service = TestBed.inject(PackService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  /* ─── Admin API ─── */

  it('getAll() sends GET to admin/packs', () => {
    const page: PageResponse<Pack> = { content: [samplePack()], totalElements: 1, totalPages: 1, size: 10, number: 0 } as PageResponse<Pack>;
    service.getAll().subscribe(res => expect(res.content.length).toBe(1));
    const req = http.expectOne(r => r.url === adminBase);
    expect(req.request.method).toBe('GET');
    req.flush(page);
  });

  it('getAll() forwards pagination params', () => {
    service.getAll({ page: 1, size: 5, sortBy: 'price', sortDir: 'asc' }).subscribe();
    const req = http.expectOne(r => r.url === adminBase && r.params.get('page') === '1');
    expect(req.request.params.get('size')).toBe('5');
    expect(req.request.params.get('sortBy')).toBe('price');
    req.flush({ content: [], totalElements: 0, totalPages: 0, size: 5, number: 1 });
  });

  it('getById() sends GET to admin/packs/:id', () => {
    service.getById(1).subscribe(res => expect(res.id).toBe(1));
    const req = http.expectOne(`${adminBase}/1`);
    expect(req.request.method).toBe('GET');
    req.flush(samplePack());
  });

  it('create() sends POST with FormData', () => {
    const dto = { title: 'New Pack' } as any;
    service.create(dto).subscribe(res => expect(res.id).toBe(1));
    const req = http.expectOne(adminBase);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toBeInstanceOf(FormData);
    req.flush(samplePack());
  });

  it('update() sends PUT with FormData to admin/packs/:id', () => {
    const dto = { title: 'Updated Pack' } as any;
    service.update(1, dto).subscribe(res => expect(res.title).toBe('Full-Stack Pack'));
    const req = http.expectOne(`${adminBase}/1`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toBeInstanceOf(FormData);
    req.flush(samplePack());
  });

  it('delete() sends DELETE to admin/packs/:id', () => {
    service.delete(1).subscribe();
    const req = http.expectOne(`${adminBase}/1`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });

  it('changeStatus() sends PATCH to admin/packs/:id/status', () => {
    service.changeStatus(1, 'ARCHIVED').subscribe(res => expect(res).toBeTruthy());
    const req = http.expectOne(`${adminBase}/1/status`);
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ status: 'ARCHIVED' });
    req.flush(samplePack());
  });

  /* ─── Public API ─── */

  it('getActivePacks() sends GET to packs/active', () => {
    service.getActivePacks().subscribe(res => expect(Array.isArray(res)).toBeTrue());
    const req = http.expectOne(`${publicBase}/active`);
    expect(req.request.method).toBe('GET');
    req.flush([samplePack()]);
  });

  it('getActivePacksByCategory() sends GET to packs/category/:id', () => {
    service.getActivePacksByCategory(2).subscribe(res => expect(res.length).toBe(1));
    const req = http.expectOne(`${publicBase}/category/2`);
    expect(req.request.method).toBe('GET');
    req.flush([samplePack()]);
  });

  it('getActivePackById() sends GET to packs/:id', () => {
    service.getActivePackById(1).subscribe(res => expect(res.id).toBe(1));
    const req = http.expectOne(`${publicBase}/1`);
    expect(req.request.method).toBe('GET');
    req.flush(samplePack());
  });
});
