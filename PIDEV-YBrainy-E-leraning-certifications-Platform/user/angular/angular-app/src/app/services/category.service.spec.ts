import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { CategoryService } from './category.service';
import { PackCategory, CreatePackCategory, UpdatePackCategory } from '../models/pack-category.model';

describe('CategoryService', () => {
  let service: CategoryService;
  let http: HttpTestingController;
  const adminBase = '/api/admin/categories';
  const publicBase = '/api/categories';

  const sampleCategory = (): PackCategory => ({
    id: 1,
    name: 'Web Development',
    description: 'Front and back-end courses',
    active: true,
    iconUrl: null
  } as unknown as PackCategory);

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [CategoryService]
    });
    service = TestBed.inject(CategoryService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  /* ─── Admin API ─── */

  it('getAll() sends GET to admin/categories', () => {
    service.getAll().subscribe(res => expect(res.length).toBe(1));
    const req = http.expectOne(adminBase);
    expect(req.request.method).toBe('GET');
    req.flush([sampleCategory()]);
  });

  it('getById() sends GET to admin/categories/:id', () => {
    service.getById(1).subscribe(res => expect(res.id).toBe(1));
    const req = http.expectOne(`${adminBase}/1`);
    expect(req.request.method).toBe('GET');
    req.flush(sampleCategory());
  });

  it('create() sends POST to admin/categories with body', () => {
    const dto: CreatePackCategory = { name: 'DevOps', description: 'CI/CD pipelines' } as CreatePackCategory;
    service.create(dto).subscribe(res => expect(res.name).toBe('Web Development'));
    const req = http.expectOne(adminBase);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(dto);
    req.flush(sampleCategory());
  });

  it('update() sends PUT to admin/categories/:id', () => {
    const dto: UpdatePackCategory = { name: 'Web Dev Updated' } as UpdatePackCategory;
    service.update(1, dto).subscribe(res => expect(res.id).toBe(1));
    const req = http.expectOne(`${adminBase}/1`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(dto);
    req.flush(sampleCategory());
  });

  it('delete() sends DELETE to admin/categories/:id', () => {
    service.delete(1).subscribe();
    const req = http.expectOne(`${adminBase}/1`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });

  it('toggleStatus() sends PATCH to admin/categories/:id/status', () => {
    service.toggleStatus(1).subscribe(res => expect(res).toBeTruthy());
    const req = http.expectOne(`${adminBase}/1/status`);
    expect(req.request.method).toBe('PATCH');
    req.flush(sampleCategory());
  });

  /* ─── Public API ─── */

  it('getActiveCategories() sends GET to categories/active', () => {
    service.getActiveCategories().subscribe(res => expect(res.length).toBe(1));
    const req = http.expectOne(`${publicBase}/active`);
    expect(req.request.method).toBe('GET');
    req.flush([sampleCategory()]);
  });
});
