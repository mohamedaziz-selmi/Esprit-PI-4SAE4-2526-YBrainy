import { Component, OnInit, AfterViewInit, ViewEncapsulation, HostListener } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { FinanceService } from '../../services/finance.service';
import { ExportService } from '../../services/export.service';
import { Income, Expense, ExpenseCategory, ExpenseStatus, TwelveDataQuote, TwelveDataTimeSeries } from '../../models/finance.model';

@Component({
    selector: 'app-finance',
    templateUrl: './finance.component.html',
    styleUrls: ['./finance.component.css'],
    encapsulation: ViewEncapsulation.None
})
export class FinanceComponent implements OnInit, AfterViewInit {

    incomes: Income[] = [];
    filteredIncomes: Income[] = [];
    paginatedIncomes: Income[] = [];
    incomeSearch: string = '';
    incomeSortField: string = 'createdAt';
    incomeSortDir: 'asc' | 'desc' = 'desc';
    incomeCurrentPage: number = 1;
    incomeItemsPerPage: number = 10;
    incomeTotalPages: number = 0;

    expenses: Expense[] = [];
    filteredExpenses: Expense[] = [];
    paginatedExpenses: Expense[] = [];
    expenseSearch: string = '';
    expenseSortField: string = 'expenseDate';
    expenseSortDir: 'asc' | 'desc' = 'desc';
    expenseCurrentPage: number = 1;
    expenseItemsPerPage: number = 10;
    expenseTotalPages: number = 0;

    // Static overview visuals (stats + charts)
    showStaticVisuals = false;

    incomeForm: FormGroup;
    expenseForm: FormGroup;

    expenseCategories = Object.values(ExpenseCategory);
    expenseStatuses = Object.values(ExpenseStatus);

    isIncomeEditMode = false;
    currentIncomeId: number | null = null;

    isExpenseEditMode = false;
    currentExpenseId: number | null = null;

    /** Which export dropdown is open (Angular-controlled to avoid Bootstrap conflicts) */
    openExportDropdown: 'income' | 'expense' | null = null;

    // Creative delete confirmation state
    showDeleteModal = false;
    deleteTargetType: 'income' | 'expense' | null = null;
    deleteTargetId: number | null = null;
    deleteTargetLabel = '';
    deleteInProgress = false;

    // Creative feedback toast state
    showFinanceToast = false;
    financeToastType: 'success' | 'error' = 'success';
    financeToastTitle = '';
    financeToastMessage = '';
    private financeToastTimer: ReturnType<typeof setTimeout> | null = null;

    // Twelve Data API properties
    showTwelveDataModal = false;
    twelveDataForm: FormGroup;
    twelveDataSearching = false;
    twelveDataResult: TwelveDataQuote | TwelveDataTimeSeries | null = null;
    twelveDataError: string | null = null;

    // Make Math available in template
    Math = Math;

    constructor(
        private financeService: FinanceService,
        private exportService: ExportService,
        private fb: FormBuilder
    ) {
        this.incomeForm = this.fb.group({
            sourceType: ['MANUAL', Validators.required],
            description: ['', [Validators.maxLength(500)]],
            amount: [0, [Validators.required, Validators.min(0.01), Validators.max(999999999.99)]],
            currency: ['USD', [Validators.required, Validators.minLength(3), Validators.maxLength(5)]],
            paymentMethod: ['LOCAL', Validators.required]
        });

        this.expenseForm = this.fb.group({
            title: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(200)]],
            description: ['', [Validators.maxLength(500)]],
            amount: [0, [Validators.required, Validators.min(0.01), Validators.max(999999999.99)]],
            currency: ['USD', [Validators.required, Validators.minLength(3), Validators.maxLength(5)]],
            category: [ExpenseCategory.OTHER, Validators.required],
            status: [ExpenseStatus.PENDING, Validators.required],
            expenseDate: [new Date().toISOString().slice(0, 16), Validators.required]
        });

        this.twelveDataForm = this.fb.group({
            symbol: ['AAPL', [Validators.required, Validators.minLength(1), Validators.maxLength(20)]],
            dataType: ['quote', Validators.required] // 'quote' or 'timeseries'
        });
    }

    ngOnInit(): void {
        this.loadIncomes();
        this.loadExpenses();
    }

    ngAfterViewInit(): void {
        setTimeout(() => {
            this.loadScripts();
        }, 100);
    }

    private loadScripts(): void {
        const scripts = [
            'assets/backoffice/vendor/global/global.min.js',
            // 'assets/backoffice/vendor/bootstrap-select/dist/js/bootstrap-select.min.js', // Conflict with Angular Forms
            'assets/backoffice/vendor/chart.js/Chart.bundle.min.js',
            'assets/backoffice/vendor/apexchart/apexchart.js',
            'assets/backoffice/js/dashboard/dashboard-2.js',
            // 'assets/backoffice/vendor/jquery-nice-select/js/jquery.nice-select.min.js', // Conflict with Angular Forms
            'assets/backoffice/js/custom.js',
            'assets/backoffice/js/dlabnav-init.js',
            'assets/backoffice/js/demo.js'
        ];

        this.loadScriptSequence(scripts);
    }

    private loadScriptSequence(scripts: string[], index = 0): void {
        if (index >= scripts.length) {
            // Only initialize static dashboard charts when the overview is visible.
            if (this.showStaticVisuals) {
                setTimeout(() => this.reinitializeStaticCharts(), 500);
            }
            return;
        }

        const script = document.createElement('script');
        script.src = scripts[index];
        script.async = false; // Ensure sequential execution
        script.onload = () => {
            // Mock plugins after jQuery (loaded by global.min.js) is available
            if (scripts[index].includes('global.min.js')) {
                this.mockLegacyPlugins();
            }
            this.loadScriptSequence(scripts, index + 1);
        };
        script.onerror = (err) => console.error(`Error loading script ${scripts[index]}`, err);
        document.body.appendChild(script);
    }

    toggleStaticVisuals(): void {
        this.showStaticVisuals = !this.showStaticVisuals;
        if (this.showStaticVisuals) {
            // Wait for *ngIf content to render before chart bootstrapping.
            setTimeout(() => this.reinitializeStaticCharts(), 150);
        }
    }

    private reinitializeStaticCharts(): void {
        const w = window as any;
        if (w.dlabChartlist && w.dlabChartlist.load) {
            w.dlabChartlist.load();
        }
    }

    private mockLegacyPlugins(): void {
        const win = window as any;
        if (win.jQuery && win.jQuery.fn) {
            // Mock selectpicker to prevent errors in custom.js
            if (!win.jQuery.fn.selectpicker) {
                win.jQuery.fn.selectpicker = function () {
                    console.log('Mock selectpicker called on', this);
                    return this;
                };
            }
            // Mock niceSelect to prevent errors
            if (!win.jQuery.fn.niceSelect) {
                win.jQuery.fn.niceSelect = function () {
                    console.log('Mock niceSelect called on', this);
                    return this;
                };
            }
        }
    }

    /* ─── Income Methods ─── */
    loadIncomes(): void {
        this.financeService.getAllIncomes().subscribe({
            next: (data) => {
                this.incomes = data;
                this.applyIncomeFilters();
            },
            error: (err) => console.error('Error loading incomes', err)
        });
    }

    onIncomeSearch(term: string): void {
        this.incomeSearch = term;
        this.applyIncomeFilters();
    }

    sortIncome(field: string): void {
        if (this.incomeSortField === field) {
            this.incomeSortDir = this.incomeSortDir === 'asc' ? 'desc' : 'asc';
        } else {
            this.incomeSortField = field;
            this.incomeSortDir = 'asc';
        }
        this.applyIncomeFilters();
    }

    applyIncomeFilters(): void {
        let result = this.incomes.filter(item => {
            const term = this.incomeSearch.toLowerCase();
            return !term ||
                item.sourceType.toLowerCase().includes(term) ||
                (item.description && item.description.toLowerCase().includes(term)) ||
                item.paymentMethod.toLowerCase().includes(term) ||
                item.amount.toString().includes(term);
        });

        this.filteredIncomes = this.sortData(result, this.incomeSortField, this.incomeSortDir);
        this.incomeCurrentPage = 1; // Reset to first page
        this.updateIncomePagination();
    }

    private updateIncomePagination(): void {
        const startIndex = (this.incomeCurrentPage - 1) * this.incomeItemsPerPage;
        const endIndex = startIndex + this.incomeItemsPerPage;
        this.paginatedIncomes = this.filteredIncomes.slice(startIndex, endIndex);
        this.incomeTotalPages = Math.ceil(this.filteredIncomes.length / this.incomeItemsPerPage);
    }

    goToIncomePage(page: number): void {
        if (page >= 1 && page <= this.incomeTotalPages) {
            this.incomeCurrentPage = page;
            this.updateIncomePagination();
        }
    }

    submitIncome(): void {
        if (this.incomeForm.invalid) {
            this.incomeForm.markAllAsTouched();
            return;
        }

        const income: Income = this.incomeForm.value;

        if (this.isIncomeEditMode && this.currentIncomeId) {
            this.financeService.updateIncome(this.currentIncomeId, income).subscribe({
                next: () => {
                    this.loadIncomes();
                    this.resetIncomeForm();
                },
                error: (err) => console.error('Error updating income', err)
            });
        } else {
            this.financeService.createIncome(income).subscribe({
                next: () => {
                    this.loadIncomes();
                    this.resetIncomeForm();
                    // Manually close collapse if needed or show success message
                },
                error: (err) => console.error('Error creating income', err)
            });
        }
    }

    editIncome(income: Income): void {
        this.isIncomeEditMode = true;
        this.currentIncomeId = income.id!;
        this.incomeForm.patchValue({
            sourceType: income.sourceType,
            description: income.description,
            amount: income.amount,
            currency: income.currency,
            paymentMethod: income.paymentMethod
        });

        // Ensure the form is visible
        const collapseElement = document.getElementById('incomeCollapse');
        if (collapseElement && !collapseElement.classList.contains('show')) {
            const btn = document.querySelector('[data-bs-target="#incomeCollapse"]') as HTMLElement;
            if (btn) btn.click();
        }
    }

    deleteIncome(income: Income): void {
        if (!income.id) {
            return;
        }
        const label = income.description?.trim()
            ? income.description.trim()
            : `${income.sourceType} - ${income.amount} ${income.currency}`;
        this.openDeleteModal('income', income.id, label);
    }

    resetIncomeForm(): void {
        this.isIncomeEditMode = false;
        this.currentIncomeId = null;
        this.incomeForm.reset({
            sourceType: 'MANUAL',
            amount: 0,
            currency: 'USD',
            paymentMethod: 'LOCAL'
        });
    }

    /* ─── Expense Methods ─── */
    loadExpenses(): void {
        this.financeService.getAllExpenses().subscribe({
            next: (data) => {
                this.expenses = data;
                this.applyExpenseFilters();
            },
            error: (err) => console.error('Error loading expenses', err)
        });
    }

    onExpenseSearch(term: string): void {
        this.expenseSearch = term;
        this.applyExpenseFilters();
    }

    sortExpense(field: string): void {
        if (this.expenseSortField === field) {
            this.expenseSortDir = this.expenseSortDir === 'asc' ? 'desc' : 'asc';
        } else {
            this.expenseSortField = field;
            this.expenseSortDir = 'asc';
        }
        this.applyExpenseFilters();
    }

    applyExpenseFilters(): void {
        let result = this.expenses.filter(item => {
            const term = this.expenseSearch.toLowerCase();
            return !term ||
                item.title.toLowerCase().includes(term) ||
                (item.description && item.description.toLowerCase().includes(term)) ||
                item.category.toLowerCase().includes(term) ||
                item.status.toLowerCase().includes(term) ||
                item.amount.toString().includes(term);
        });

        this.filteredExpenses = this.sortData(result, this.expenseSortField, this.expenseSortDir);
        this.expenseCurrentPage = 1; // Reset to first page
        this.updateExpensePagination();
    }

    private updateExpensePagination(): void {
        const startIndex = (this.expenseCurrentPage - 1) * this.expenseItemsPerPage;
        const endIndex = startIndex + this.expenseItemsPerPage;
        this.paginatedExpenses = this.filteredExpenses.slice(startIndex, endIndex);
        this.expenseTotalPages = Math.ceil(this.filteredExpenses.length / this.expenseItemsPerPage);
    }

    goToExpensePage(page: number): void {
        if (page >= 1 && page <= this.expenseTotalPages) {
            this.expenseCurrentPage = page;
            this.updateExpensePagination();
        }
    }

    private sortData(data: any[], field: string, dir: 'asc' | 'desc'): any[] {
        return [...data].sort((a, b) => {
            let valA = a[field];
            let valB = b[field];

            if (valA === undefined || valA === null) return 1;
            if (valB === undefined || valB === null) return -1;

            if (typeof valA === 'string') valA = valA.toLowerCase();
            if (typeof valB === 'string') valB = valB.toLowerCase();

            if (valA < valB) return dir === 'asc' ? -1 : 1;
            if (valA > valB) return dir === 'asc' ? 1 : -1;
            return 0;
        });
    }

    submitExpense(): void {
        if (this.expenseForm.invalid) {
            this.expenseForm.markAllAsTouched();
            return;
        }

        const expense: Expense = this.expenseForm.value;

        if (this.isExpenseEditMode && this.currentExpenseId) {
            this.financeService.updateExpense(this.currentExpenseId, expense).subscribe({
                next: () => {
                    this.loadExpenses();
                    this.resetExpenseForm();
                },
                error: (err) => console.error('Error updating expense', err)
            });
        } else {
            this.financeService.createExpense(expense).subscribe({
                next: () => {
                    this.loadExpenses();
                    this.resetExpenseForm();
                },
                error: (err) => console.error('Error creating expense', err)
            });
        }
    }

    toggleExportDropdown(type: 'income' | 'expense'): void {
        this.openExportDropdown = this.openExportDropdown === type ? null : type;
    }

    closeExportDropdown(): void {
        this.openExportDropdown = null;
    }

    @HostListener('document:click', ['$event'])
    onDocumentClick(event: Event): void {
        const target = event.target as HTMLElement;
        if (!target.closest('.finance-export-dropdown')) {
            this.closeExportDropdown();
        }
    }

    @HostListener('document:keydown.escape')
    onEscapePress(): void {
        if (this.showDeleteModal) {
            this.closeDeleteModal();
        }
        if (this.showTwelveDataModal) {
            this.closeTwelveDataModal();
        }
    }

    // ─── Export Methods ───

    exportIncomes(format: 'pdf' | 'excel'): void {
        this.closeExportDropdown();
        if (format === 'excel') {
            const dataToExport = this.filteredIncomes.map(inc => ({
                Source: inc.sourceType,
                Description: inc.description || '',
                Amount: inc.amount,
                Currency: inc.currency,
                Method: inc.paymentMethod,
                Date: inc.createdAt ? new Date(inc.createdAt).toLocaleDateString() : ''
            }));
            this.exportService.exportToExcel(dataToExport, 'Incomes_Report');
        } else {
            const headers = ['Source', 'Description', 'Amount', 'Currency', 'Method', 'Date'];
            const data = this.filteredIncomes.map(inc => [
                inc.sourceType,
                inc.description || '',
                `${inc.amount}`,
                inc.currency,
                inc.paymentMethod,
                inc.createdAt ? new Date(inc.createdAt).toLocaleDateString() : ''
            ]);
            this.exportService.exportToPDF(headers, data, 'Incomes_Report', 'Income Management Report');
        }
    }

    exportExpenses(format: 'pdf' | 'excel'): void {
        this.closeExportDropdown();
        if (format === 'excel') {
            const dataToExport = this.filteredExpenses.map(exp => ({
                Title: exp.title,
                Category: exp.category,
                Amount: exp.amount,
                Currency: exp.currency,
                Status: exp.status,
                Date: exp.expenseDate ? new Date(exp.expenseDate).toLocaleDateString() : ''
            }));
            this.exportService.exportToExcel(dataToExport, 'Expenses_Report');
        } else {
            const headers = ['Title', 'Category', 'Amount', 'Currency', 'Status', 'Date'];
            const data = this.filteredExpenses.map(exp => [
                exp.title,
                exp.category,
                `${exp.amount}`,
                exp.currency,
                exp.status,
                exp.expenseDate ? new Date(exp.expenseDate).toLocaleDateString() : ''
            ]);
            this.exportService.exportToPDF(headers, data, 'Expenses_Report', 'School Expenses Report');
        }
    }

    editExpense(expense: Expense): void {
        this.isExpenseEditMode = true;
        this.currentExpenseId = expense.id!;

        // Format date for datetime-local input (YYYY-MM-DDTHH:mm)
        let formattedDate = '';
        if (expense.expenseDate) {
            formattedDate = new Date(expense.expenseDate).toISOString().slice(0, 16);
        }

        this.expenseForm.patchValue({
            title: expense.title,
            description: expense.description,
            amount: expense.amount,
            currency: expense.currency,
            category: expense.category,
            status: expense.status,
            expenseDate: formattedDate
        });

        // Ensure the form is visible
        const collapseElement = document.getElementById('expenseCollapse');
        if (collapseElement && !collapseElement.classList.contains('show')) {
            const btn = document.querySelector('[data-bs-target="#expenseCollapse"]') as HTMLElement;
            if (btn) btn.click();
        }
    }

    deleteExpense(expense: Expense): void {
        if (!expense.id) {
            return;
        }
        const label = expense.title?.trim() || 'this expense';
        this.openDeleteModal('expense', expense.id, label);
    }

    private openDeleteModal(type: 'income' | 'expense', id: number, label: string): void {
        this.deleteTargetType = type;
        this.deleteTargetId = id;
        this.deleteTargetLabel = label;
        this.deleteInProgress = false;
        this.showDeleteModal = true;
    }

    closeDeleteModal(): void {
        if (this.deleteInProgress) {
            return;
        }
        this.showDeleteModal = false;
        this.deleteTargetType = null;
        this.deleteTargetId = null;
        this.deleteTargetLabel = '';
    }

    confirmDeleteRecord(): void {
        if (!this.deleteTargetType || this.deleteTargetId === null || this.deleteInProgress) {
            return;
        }

        this.deleteInProgress = true;
        const onSuccess = () => {
            const typeLabel = this.deleteTargetType === 'income' ? 'Income' : 'Expense';
            this.deleteInProgress = false;
            this.closeDeleteModal();
            this.showFinanceMessage('success', `${typeLabel} deleted`, 'The record was removed successfully.');
        };
        const onError = (err: any) => {
            console.error('Error deleting record', err);
            this.deleteInProgress = false;
            this.showFinanceMessage('error', 'Delete failed', 'Could not delete this record. Please try again.');
        };

        if (this.deleteTargetType === 'income') {
            this.financeService.deleteIncome(this.deleteTargetId).subscribe({
                next: () => {
                    this.loadIncomes();
                    onSuccess();
                },
                error: onError
            });
            return;
        }

        this.financeService.deleteExpense(this.deleteTargetId).subscribe({
            next: () => {
                this.loadExpenses();
                onSuccess();
            },
            error: onError
        });
    }

    private showFinanceMessage(type: 'success' | 'error', title: string, message: string): void {
        this.financeToastType = type;
        this.financeToastTitle = title;
        this.financeToastMessage = message;
        this.showFinanceToast = true;

        if (this.financeToastTimer) {
            clearTimeout(this.financeToastTimer);
        }
        this.financeToastTimer = setTimeout(() => this.closeFinanceToast(), 3500);
    }

    closeFinanceToast(): void {
        this.showFinanceToast = false;
        if (this.financeToastTimer) {
            clearTimeout(this.financeToastTimer);
            this.financeToastTimer = null;
        }
    }

    resetExpenseForm(): void {
        this.isExpenseEditMode = false;
        this.currentExpenseId = null;
        this.expenseForm.reset({
            amount: 0,
            currency: 'USD',
            category: ExpenseCategory.OTHER,
            status: ExpenseStatus.PENDING,
            expenseDate: new Date().toISOString().slice(0, 16)
        });
    }

    /* ─── Twelve Data API Methods ─── */
    openTwelveDataModal(): void {
        this.showTwelveDataModal = true;
        this.twelveDataError = null;
        this.twelveDataResult = null;
        this.twelveDataForm.reset({
            symbol: 'AAPL',
            dataType: 'quote'
        });
    }

    closeTwelveDataModal(): void {
        this.showTwelveDataModal = false;
        this.twelveDataError = null;
        this.twelveDataResult = null;
    }

    fetchTwelveDataQuote(): void {
        if (this.twelveDataForm.invalid) {
            this.twelveDataForm.markAllAsTouched();
            return;
        }

        const symbol = this.twelveDataForm.get('symbol')?.value;
        const dataType = this.twelveDataForm.get('dataType')?.value;

        this.twelveDataSearching = true;
        this.twelveDataError = null;
        this.twelveDataResult = null;

        // Fetch quote or time series data from Twelve Data API
        if (dataType === 'quote') {
            this.financeService.getStockQuote(symbol).subscribe({
                next: (data) => {
                    this.twelveDataResult = data;
                    this.twelveDataSearching = false;
                },
                error: (err) => {
                    this.twelveDataError = `Error fetching data: ${err.error?.message || err.message || 'Unknown error'}`;
                    this.twelveDataSearching = false;
                }
            });
        } else if (dataType === 'timeseries') {
            this.financeService.getTimeSeries(symbol, '1day', 30).subscribe({
                next: (data) => {
                    this.twelveDataResult = data;
                    this.twelveDataSearching = false;
                },
                error: (err) => {
                    this.twelveDataError = `Error fetching data: ${err.error?.message || err.message || 'Unknown error'}`;
                    this.twelveDataSearching = false;
                }
            });
        }
    }
}
