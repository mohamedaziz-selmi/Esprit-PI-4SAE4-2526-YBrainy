import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { ReactiveFormsModule, FormsModule } from '@angular/forms';
import { of, throwError } from 'rxjs';
import { FinanceComponent } from './finance.component';
import { FinanceService } from '../../services/finance.service';
import { RecommendationService } from '../../services/recommendation.service';
import { FinanceScenarioService } from '../../services/finance-scenario.service';
import { ExportService } from '../../services/export.service';
import { RuntimePageStyleService } from '../runtime-page-style.service';
import { Income, Expense, ExpenseCategory, ExpenseStatus } from '../../models/finance.model';

describe('FinanceComponent', () => {
  let component: FinanceComponent;
  let fixture: ComponentFixture<FinanceComponent>;
  let financeServiceSpy: jasmine.SpyObj<FinanceService>;

  const mockIncome = (id: number): Income => ({
    id,
    sourceType: 'PACK_PURCHASE',
    description: 'Test income',
    amount: 100,
    currency: 'USD',
    paymentMethod: 'STRIPE',
    receivedDate: '2026-01-01'
  } as unknown as Income);

  const mockExpense = (id: number): Expense => ({
    id,
    title: `Expense ${id}`,
    description: 'Server cost',
    amount: 50,
    currency: 'USD',
    category: ExpenseCategory.INFRASTRUCTURE,
    status: ExpenseStatus.PAID,
    expenseDate: '2026-01-01'
  } as unknown as Expense);

  beforeEach(async () => {
    financeServiceSpy = jasmine.createSpyObj('FinanceService', [
      'getAllIncomes', 'createIncome', 'updateIncome', 'deleteIncome',
      'getAllExpenses', 'createExpense', 'updateExpense', 'deleteExpense',
      'runScraper', 'getScraperStatus'
    ]);

    financeServiceSpy.getAllIncomes.and.returnValue(of([mockIncome(1), mockIncome(2)]));
    financeServiceSpy.getAllExpenses.and.returnValue(of([mockExpense(1)]));

    await TestBed.configureTestingModule({
      imports: [
        FinanceComponent,
        HttpClientTestingModule,
        ReactiveFormsModule,
        FormsModule
      ],
      providers: [
        { provide: FinanceService, useValue: financeServiceSpy },
        { provide: RecommendationService, useValue: jasmine.createSpyObj('RecommendationService', ['getSummary']) },
        { provide: FinanceScenarioService, useValue: jasmine.createSpyObj('FinanceScenarioService', ['getRanking', 'getSummary']) },
        { provide: ExportService, useValue: jasmine.createSpyObj('ExportService', ['exportToCsv', 'exportToPdf']) },
        {
          provide: RuntimePageStyleService,
          useValue: { attach: () => () => {} }
        }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(FinanceComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should load incomes on init', () => {
    expect(financeServiceSpy.getAllIncomes).toHaveBeenCalled();
    expect(component.incomes.length).toBe(2);
  });

  it('should load expenses on init', () => {
    expect(financeServiceSpy.getAllExpenses).toHaveBeenCalled();
    expect(component.expenses.length).toBe(1);
  });

  it('should populate filteredIncomes from loaded incomes', () => {
    expect(component.filteredIncomes.length).toBe(2);
  });

  it('onIncomeSearch() filters incomes by term', () => {
    component.incomes = [mockIncome(1), mockIncome(2)];
    component.onIncomeSearch('PACK');
    expect(component.filteredIncomes.length).toBe(2);
    component.onIncomeSearch('nonexistent-term-xyz');
    expect(component.filteredIncomes.length).toBe(0);
  });

  it('onIncomeSearch() with empty term shows all incomes', () => {
    component.incomes = [mockIncome(1), mockIncome(2)];
    component.onIncomeSearch('');
    expect(component.filteredIncomes.length).toBe(2);
  });

  it('sortIncome() toggles sort direction on same field', () => {
    component.incomeSortField = 'amount';
    component.incomeSortDir = 'asc';
    component.sortIncome('amount');
    expect(component.incomeSortDir).toBe('desc');
    component.sortIncome('amount');
    expect(component.incomeSortDir).toBe('asc');
  });

  it('sortIncome() resets to asc when switching fields', () => {
    component.incomeSortField = 'amount';
    component.incomeSortDir = 'desc';
    component.sortIncome('sourceType');
    expect(component.incomeSortField).toBe('sourceType');
    expect(component.incomeSortDir).toBe('asc');
  });

  it('income form should be invalid when amount is 0', () => {
    component.incomeForm.patchValue({ amount: 0 });
    expect(component.incomeForm.get('amount')?.valid).toBeFalse();
  });

  it('income form should be valid with correct values', () => {
    component.incomeForm.setValue({
      sourceType: 'PACK_PURCHASE',
      description: 'Sale',
      amount: 250,
      currency: 'USD',
      paymentMethod: 'STRIPE'
    });
    expect(component.incomeForm.valid).toBeTrue();
  });

  it('expense form should be invalid when title is missing', () => {
    component.expenseForm.patchValue({ title: '' });
    expect(component.expenseForm.get('title')?.valid).toBeFalse();
  });

  it('should initialize income pagination on load', () => {
    expect(component.incomeCurrentPage).toBe(1);
    expect(component.paginatedIncomes.length).toBeLessThanOrEqual(component.incomeItemsPerPage);
  });

  it('goToIncomePage() ignores out-of-range page numbers', () => {
    component.incomeTotalPages = 3;
    component.incomeCurrentPage = 1;
    component.goToIncomePage(0);
    expect(component.incomeCurrentPage).toBe(1);
    component.goToIncomePage(100);
    expect(component.incomeCurrentPage).toBe(1);
  });
});
