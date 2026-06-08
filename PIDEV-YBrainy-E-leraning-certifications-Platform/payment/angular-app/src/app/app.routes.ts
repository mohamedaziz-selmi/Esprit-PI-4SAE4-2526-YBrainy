import { Routes } from '@angular/router';
import { LoginPageComponent } from './login/login-page.component';
import { BackofficeDashboardComponent } from './backoffice/backoffice-dashboard.component';
import { FinanceComponent } from './backoffice/finance/finance.component';
import { CategoryListComponent } from './backoffice/category-list/category-list.component';
import { CategoryFormComponent } from './backoffice/category-form/category-form.component';
import { PackListComponent } from './backoffice/pack-list/pack-list.component';
import { PackFormComponent } from './backoffice/pack-form/pack-form.component';
import { PacksOrderComponent } from './backoffice/packs-order/packs-order.component';
import { ProfileComponent } from './backoffice/profile/profile.component';
import { CvSubmissionsComponent } from './backoffice/cv-submissions/cv-submissions.component';
import { AuthGuard } from './guards/auth.guard';

export const routes: Routes = [
  {
    path: 'login',
    component: LoginPageComponent,
  },

  // ── Profile & CV Submissions (Angular components) ──────────────────────────
  {
    path: 'dashboard/profile',
    component: ProfileComponent,
    canActivate: [AuthGuard],
  },
  {
    path: 'dashboard/cv-submissions',
    component: CvSubmissionsComponent,
    canActivate: [AuthGuard],
  },

  // ── Pack Categories ────────────────────────────────────────────────────────
  {
    path: 'dashboard/categories',
    component: CategoryListComponent,
    canActivate: [AuthGuard],
  },
  {
    path: 'dashboard/categories/new',
    component: CategoryFormComponent,
    canActivate: [AuthGuard],
  },
  {
    path: 'dashboard/categories/edit/:id',
    component: CategoryFormComponent,
    canActivate: [AuthGuard],
  },

  // ── Packs ──────────────────────────────────────────────────────────────────
  {
    path: 'dashboard/packs',
    component: PackListComponent,
    canActivate: [AuthGuard],
  },
  {
    path: 'dashboard/packs/new',
    component: PackFormComponent,
    canActivate: [AuthGuard],
  },
  {
    path: 'dashboard/packs/edit/:id',
    component: PackFormComponent,
    canActivate: [AuthGuard],
  },
  {
    path: 'dashboard/packsorder',
    component: PacksOrderComponent,
    canActivate: [AuthGuard],
  },

  // ── Finance ────────────────────────────────────────────────────────────────
  {
    path: 'dashboard/finance',
    component: FinanceComponent,
    canActivate: [AuthGuard],
  },

  // ── Static iframe pages ────────────────────────────────────────────────────
  { path: 'dashboard/courses',         component: BackofficeDashboardComponent, canActivate: [AuthGuard], data: { page: 'courses.html' } },
  { path: 'dashboard/lessons',         component: BackofficeDashboardComponent, canActivate: [AuthGuard], data: { page: 'lessons.html' } },
  { path: 'dashboard/calendar',        component: BackofficeDashboardComponent, canActivate: [AuthGuard], data: { page: 'app-calender.html' } },
  { path: 'dashboard/e-learning-packs',component: BackofficeDashboardComponent, canActivate: [AuthGuard], data: { page: 'ecom-product-list.html' } },

  // Students
  { path: 'dashboard/students',        component: BackofficeDashboardComponent, canActivate: [AuthGuard], data: { page: 'student.html' } },
  { path: 'dashboard/student-detail',  component: BackofficeDashboardComponent, canActivate: [AuthGuard], data: { page: 'student-detail.html' } },
  { path: 'dashboard/add-student',     component: BackofficeDashboardComponent, canActivate: [AuthGuard], data: { page: 'add-student.html' } },

  // Teachers
  { path: 'dashboard/teachers',        component: BackofficeDashboardComponent, canActivate: [AuthGuard], data: { page: 'teacher.html' } },
  { path: 'dashboard/teacher-detail',  component: BackofficeDashboardComponent, canActivate: [AuthGuard], data: { page: 'teacher-detail.html' } },
  { path: 'dashboard/add-teacher',     component: BackofficeDashboardComponent, canActivate: [AuthGuard], data: { page: 'add-teacher.html' } },

  // Assessment & Certification
  { path: 'dashboard/certifications',  component: BackofficeDashboardComponent, canActivate: [AuthGuard], data: { page: 'certifications.html' } },
  { path: 'dashboard/quiz',            component: BackofficeDashboardComponent, canActivate: [AuthGuard], data: { page: 'quiz.html' } },

  // Reports
  { path: 'dashboard/student-reports', component: BackofficeDashboardComponent, canActivate: [AuthGuard], data: { page: 'student-report.html' } },
  { path: 'dashboard/teacher-reports', component: BackofficeDashboardComponent, canActivate: [AuthGuard], data: { page: 'teacher-report.html' } },

  // File Manager
  { path: 'dashboard/files',           component: BackofficeDashboardComponent, canActivate: [AuthGuard], data: { page: 'app-file-manager.html' } },

  // ── Dashboard home ─────────────────────────────────────────────────────────
  {
    path: 'dashboard',
    component: BackofficeDashboardComponent,
    canActivate: [AuthGuard],
    data: { page: 'index.html' },
  },

  // ── Frontoffice ────────────────────────────────────────────────────────────
  {
    path: '',
    loadChildren: () => import('./frontoffice/frontoffice.module').then(m => m.FrontofficeModule),
  },
  {
    path: '**',
    redirectTo: '',
  },
];
