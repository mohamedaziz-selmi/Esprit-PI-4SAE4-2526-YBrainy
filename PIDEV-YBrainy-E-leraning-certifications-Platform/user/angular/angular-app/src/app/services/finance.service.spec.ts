import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { FinanceService, ScraperRunStatus } from './finance.service';
import { Income, Expense } from '../models/finance.model';

describe('FinanceService', () => {
  let service: FinanceService;
  let http: HttpTestingController;
  const base = '/api/finance';

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [FinanceService]
    });
    service = TestBed.inject(FinanceService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  /* ─── Income ─── */

  it('getAllIncomes() sends GET to /incomes', () => {
    const mock: Income[] = [{ id: 1, amount: 99, currency: 'USD', sourceType: 'PACK_PURCHASE', paymentMethod: 'STRIPE', receivedDate: '2026-01-01' } as Income];
    service.getAllIncomes().subscribe(res => expect(res).toEqual(mock));
    const req = http.expectOne(`${base}/incomes`);
    expect(req.request.method).toBe('GET');
    req.flush(mock);
  });

  it('createIncome() sends POST to /incomes with body', () => {
    const income = { amount: 99, currency: 'USD', sourceType: 'PACK_PURCHASE' } as Income;
    const response = { id: 1, ...income } as Income;
    service.createIncome(income).subscribe(res => expect(res.id).toBe(1));
    const req = http.expectOne(`${base}/incomes`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(income);
    req.flush(response);
  });

  it('updateIncome() sends PUT to /incomes/:id', () => {
    const income = { id: 1, amount: 150 } as Income;
    service.updateIncome(1, income).subscribe(res => expect(res.amount).toBe(150));
    const req = http.expectOne(`${base}/incomes/1`);
    expect(req.request.method).toBe('PUT');
    req.flush(income);
  });

  it('deleteIncome() sends DELETE to /incomes/:id', () => {
    service.deleteIncome(1).subscribe();
    const req = http.expectOne(`${base}/incomes/1`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });

  /* ─── Expense ─── */

  it('getAllExpenses() sends GET to /expenses', () => {
    const mock: Expense[] = [{ id: 1, amount: 50, category: 'INFRASTRUCTURE', status: 'PAID' } as Expense];
    service.getAllExpenses().subscribe(res => expect(res).toEqual(mock));
    const req = http.expectOne(`${base}/expenses`);
    expect(req.request.method).toBe('GET');
    req.flush(mock);
  });

  it('createExpense() sends POST to /expenses with body', () => {
    const expense = { amount: 50, category: 'INFRASTRUCTURE' } as Expense;
    const response = { id: 1, ...expense } as Expense;
    service.createExpense(expense).subscribe(res => expect(res.id).toBe(1));
    const req = http.expectOne(`${base}/expenses`);
    expect(req.request.method).toBe('POST');
    req.flush(response);
  });

  it('updateExpense() sends PUT to /expenses/:id', () => {
    const expense = { id: 1, amount: 75 } as Expense;
    service.updateExpense(1, expense).subscribe(res => expect(res.amount).toBe(75));
    const req = http.expectOne(`${base}/expenses/1`);
    expect(req.request.method).toBe('PUT');
    req.flush(expense);
  });

  it('deleteExpense() sends DELETE to /expenses/:id', () => {
    service.deleteExpense(1).subscribe();
    const req = http.expectOne(`${base}/expenses/1`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });

  /* ─── Scraper ─── */

  it('runScraper() sends POST to /scraper/run', () => {
    const status: ScraperRunStatus = {
      state: 'started', message: 'Started', running: true,
      lastStartedAt: null, lastFinishedAt: null, lastExitCode: null, lastError: null
    };
    service.runScraper().subscribe(res => expect(res.state).toBe('started'));
    const req = http.expectOne(`${base}/scraper/run`);
    expect(req.request.method).toBe('POST');
    req.flush(status);
  });

  it('getScraperStatus() sends GET to /scraper/status', () => {
    const status: ScraperRunStatus = {
      state: 'ok', message: 'Status fetched', running: false,
      lastStartedAt: null, lastFinishedAt: null, lastExitCode: 0, lastError: null
    };
    service.getScraperStatus().subscribe(res => expect(res.running).toBeFalse());
    const req = http.expectOne(`${base}/scraper/status`);
    expect(req.request.method).toBe('GET');
    req.flush(status);
  });
});
