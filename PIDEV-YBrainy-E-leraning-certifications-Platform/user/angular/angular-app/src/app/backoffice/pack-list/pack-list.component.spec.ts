import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { ReactiveFormsModule, FormsModule } from '@angular/forms';
import { RouterTestingModule } from '@angular/router/testing';
import { of, throwError } from 'rxjs';
import { PackListComponent } from './pack-list.component';
import { PackService } from '../../services/pack.service';
import { CategoryService } from '../../services/category.service';
import { FinanceService } from '../../services/finance.service';
import { RecommendationService } from '../../services/recommendation.service';
import { PackConversionService } from '../../services/pack-conversion.service';
import { PackPricingService } from '../../services/pack-pricing.service';
import { RuntimePageStyleService } from '../runtime-page-style.service';
import { Pack, PackLevel, PackStatus, PageResponse } from '../../models/pack.model';
import { PackCategory } from '../../models/pack-category.model';

describe('PackListComponent', () => {
  let component: PackListComponent;
  let fixture: ComponentFixture<PackListComponent>;
  let packServiceSpy: jasmine.SpyObj<PackService>;
  let categoryServiceSpy: jasmine.SpyObj<CategoryService>;
  let financeServiceSpy: jasmine.SpyObj<FinanceService>;

  const mockPack = (id: number, status: PackStatus = 'ACTIVE'): Pack => ({
    id,
    title: `Pack ${id}`,
    description: 'A description',
    level: 'BEGINNER' as PackLevel,
    status,
    price: 100,
    salePrice: 80,
    categoryId: 1,
    categoryName: 'Web',
    courseIds: [],
    thumbnailUrl: null,
    createdAt: '2026-01-01'
  } as unknown as Pack);

  const mockPage = (packs: Pack[]): PageResponse<Pack> => ({
    content: packs,
    totalElements: packs.length,
    totalPages: 1,
    size: 10,
    number: 0
  } as PageResponse<Pack>);

  const mockCategory: PackCategory = {
    id: 1,
    name: 'Web Development',
    active: true
  } as unknown as PackCategory;

  beforeEach(async () => {
    packServiceSpy = jasmine.createSpyObj('PackService', ['getAll', 'create', 'update', 'delete', 'changeStatus', 'generateContent']);
    categoryServiceSpy = jasmine.createSpyObj('CategoryService', ['getAll', 'getById']);
    financeServiceSpy = jasmine.createSpyObj('FinanceService', ['runScraper', 'getScraperStatus']);

    packServiceSpy.getAll.and.returnValue(of(mockPage([mockPack(1), mockPack(2)])));
    categoryServiceSpy.getAll.and.returnValue(of([mockCategory]));

    await TestBed.configureTestingModule({
      imports: [
        PackListComponent,
        HttpClientTestingModule,
        ReactiveFormsModule,
        FormsModule,
        RouterTestingModule
      ],
      providers: [
        { provide: PackService, useValue: packServiceSpy },
        { provide: CategoryService, useValue: categoryServiceSpy },
        { provide: FinanceService, useValue: financeServiceSpy },
        { provide: RecommendationService, useValue: jasmine.createSpyObj('RecommendationService', ['getSummary']) },
        { provide: PackConversionService, useValue: jasmine.createSpyObj('PackConversionService', ['getSummary']) },
        { provide: PackPricingService, useValue: jasmine.createSpyObj('PackPricingService', ['getSummary']) },
        {
          provide: RuntimePageStyleService,
          useValue: { attach: () => () => {} }
        }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(PackListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should load packs on init and populate filteredPacks', () => {
    expect(packServiceSpy.getAll).toHaveBeenCalled();
    expect(component.packs.length).toBe(2);
    expect(component.filteredPacks.length).toBe(2);
  });

  it('should load categories on init', () => {
    expect(categoryServiceSpy.getAll).toHaveBeenCalled();
    expect(component.categories.length).toBe(1);
    expect(component.categories[0].name).toBe('Web Development');
  });

  it('should filter packs by search term', () => {
    component.packs = [mockPack(1), mockPack(2)];
    component.searchTerm = 'Pack 1';
    component.onSearchChange();
    expect(component.filteredPacks.length).toBe(1);
    expect(component.filteredPacks[0].title).toBe('Pack 1');
  });

  it('should show all packs when searchTerm is cleared', () => {
    component.packs = [mockPack(1), mockPack(2)];
    component.searchTerm = '';
    component.onSearchChange();
    expect(component.filteredPacks.length).toBe(2);
  });

  it('should calculate totalPacks from totalElements', () => {
    expect(component.totalPacks).toBe(2);
  });

  it('should set error message when loadPacks fails', () => {
    packServiceSpy.getAll.and.returnValue(throwError(() => new Error('Network error')));
    component.loadPacks();
    expect(component.error).toBe('Failed to load packs');
    expect(component.loading).toBeFalse();
  });

  it('should reset to page 0 on filter change', () => {
    component.currentPage = 3;
    component.onFilterChange();
    expect(component.currentPage).toBe(0);
    expect(packServiceSpy.getAll).toHaveBeenCalled();
  });

  it('should initialize the form with required controls', () => {
    expect(component.form.get('title')).toBeTruthy();
    expect(component.form.get('description')).toBeTruthy();
    expect(component.form.get('originalPrice')).toBeTruthy();
    expect(component.form.get('salePrice')).toBeTruthy();
    expect(component.form.get('level')).toBeTruthy();
    expect(component.form.get('categoryId')).toBeTruthy();
  });

  it('form should be invalid when empty', () => {
    component.form.reset();
    expect(component.form.valid).toBeFalse();
  });

  it('form should be valid when required fields are populated', () => {
    component.form.setValue({
      title: 'Valid Pack',
      description: 'A valid description for this pack',
      originalPrice: 120,
      salePrice: 99,
      level: 'BEGINNER',
      durationHours: 10,
      certificateName: '',
      categoryId: 1
    });
    expect(component.form.valid).toBeTrue();
  });
});
