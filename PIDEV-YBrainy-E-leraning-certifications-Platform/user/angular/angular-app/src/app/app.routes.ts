import { Routes } from '@angular/router';
import { LoginPageComponent } from './login/login-page.component';
import { BackofficeDashboardComponent } from './backoffice/backoffice-dashboard.component';
import { CategoryListComponent } from './backoffice/category-list/category-list.component';
import { FinanceComponent } from './backoffice/finance/finance.component';
import { PackListComponent } from './backoffice/pack-list/pack-list.component';
import { PacksOrderComponent } from './backoffice/packs-order/packs-order.component';
import { SignUpPageComponent } from './signup/signup.component';
import { ForgotPasswordComponent } from './forgot-password/forgot-password.component';
import { UsersComponent } from './backoffice/users/users.component';
import { adminGuard } from './auth/admin.guard';

export const routes: Routes = [
  {
    path: 'login',
    component: LoginPageComponent,
  },
  {
    path: 'signup',
    component: SignUpPageComponent,
  },
  {
    path: 'forgot-password',
    component: ForgotPasswordComponent,
  },
  {
    path: 'dashboard/courses',
    component: BackofficeDashboardComponent,
    data: { page: 'courses.html' },
    canActivate: [adminGuard],
  },
  {
    path: 'dashboard/lessons',
    component: BackofficeDashboardComponent,
    data: { page: 'lessons.html' },
    canActivate: [adminGuard],
  },
  {
    path: 'dashboard/calendar',
    component: BackofficeDashboardComponent,
    data: { page: 'app-calender.html' },
    canActivate: [adminGuard],
  },
  {
    path: 'dashboard/celan',
    component: BackofficeDashboardComponent,
    data: { page: 'celandar.html' },
    canActivate: [adminGuard],
  },
  {
    path: 'dashboard/users',
    component: UsersComponent,
    canActivate: [adminGuard],
  },
  {
    path: 'dashboard/finance',
    component: FinanceComponent,
    canActivate: [adminGuard],
  },
  {
    path: 'dashboard/partners',
    canActivate: [adminGuard],
    loadComponent: () =>
      import('./backoffice/partnerships/backoffice-partnerships-list.component').then(
        (m) => m.BackofficePartnershipsListComponent
      ),
  },
  {
    path: 'dashboard/partners/new',
    canActivate: [adminGuard],
    loadComponent: () =>
      import('./backoffice/partnerships/backoffice-partnership-form.component').then(
        (m) => m.BackofficePartnershipFormComponent
      ),
  },
  {
    path: 'dashboard/partners/:id/edit',
    canActivate: [adminGuard],
    loadComponent: () =>
      import('./backoffice/partnerships/backoffice-partnership-form.component').then(
        (m) => m.BackofficePartnershipFormComponent
      ),
  },
  {
    path: 'dashboard/job-offers',
    canActivate: [adminGuard],
    loadComponent: () =>
      import('./backoffice/job-offers/backoffice-job-offers-list.component').then(
        (m) => m.BackofficeJobOffersListComponent
      ),
  },
  {
    path: 'dashboard/job-offers/new',
    canActivate: [adminGuard],
    loadComponent: () =>
      import('./backoffice/job-offers/backoffice-job-offer-form.component').then(
        (m) => m.BackofficeJobOfferFormComponent
      ),
  },
  {
    path: 'dashboard/job-offers/:id/edit',
    canActivate: [adminGuard],
    loadComponent: () =>
      import('./backoffice/job-offers/backoffice-job-offer-form.component').then(
        (m) => m.BackofficeJobOfferFormComponent
      ),
  },
  {
    path: 'dashboard/cv-submissions',
    component: BackofficeDashboardComponent,
    data: { page: 'cv-list.html' },
    canActivate: [adminGuard],
  },
  {
    path: 'dashboard/applications',
    canActivate: [adminGuard],
    loadComponent: () =>
      import('./backoffice/applications/backoffice-applications-list.component').then(
        (m) => m.BackofficeApplicationsListComponent
      ),
  },
  {
    path: 'dashboard/applications/evaluate',
    canActivate: [adminGuard],
    loadComponent: () =>
      import('./backoffice/applications/backoffice-candidate-evaluation.component').then(
        (m) => m.BackofficeCandidateEvaluationComponent
      ),
  },
  {
    path: 'dashboard/ai-application',
    canActivate: [adminGuard],
    loadComponent: () =>
      import('./backoffice/ai-application/backoffice-ai-application.component').then(
        (m) => m.BackofficeAiApplicationComponent
      ),
  },
  {
    path: 'dashboard/packs',
    component: PackListComponent,
    canActivate: [adminGuard],
  },
  {
    path: 'dashboard/packsorder',
    component: PacksOrderComponent,
    canActivate: [adminGuard],
  },
  {
    path: 'dashboard/categories',
    component: CategoryListComponent,
    canActivate: [adminGuard],
  },
  {
    path: 'dashboard/events',
    canActivate: [adminGuard],
    loadComponent: () =>
      import('./backoffice/event-management/event-list/event-list.component').then(
        (m) => m.EventListComponent
      ),
  },
  {
    path: 'dashboard/events/new',
    canActivate: [adminGuard],
    loadComponent: () =>
      import('./backoffice/event-management/event-form/event-form.component').then(
        (m) => m.EventFormComponent
      ),
  },
  {
    path: 'dashboard/events/:id/edit',
    canActivate: [adminGuard],
    loadComponent: () =>
      import('./backoffice/event-management/event-form/event-form.component').then(
        (m) => m.EventFormComponent
      ),
  },
  {
    path: 'dashboard/forum/categories',
    canActivate: [adminGuard],
    loadComponent: () =>
      import('./backoffice/categories/backoffice-categories.component').then(
        (m) => m.BackofficeCategoriesComponent
      ),
  },
  {
    path: 'dashboard/forum/threads',
    canActivate: [adminGuard],
    loadComponent: () =>
      import('./backoffice/forum/backoffice-forum-threads.component').then(
        (m) => m.BackofficeForumThreadsComponent
      ),
  },
  {
    path: 'dashboard/forum/threads/:threadId',
    canActivate: [adminGuard],
    loadComponent: () =>
      import('./backoffice/forum/backoffice-forum-thread-detail.component').then(
        (m) => m.BackofficeForumThreadDetailComponent
      ),
  },
  {
    path: 'dashboard/profile',
    canActivate: [adminGuard],
    loadComponent: () =>
      import('./frontoffice/pages/profile/profile.component').then((m) => m.ProfileComponent),
  },
  {
    path: 'dashboard/personality',
    canActivate: [adminGuard],
    loadChildren: () =>
      import('./backoffice/personality/personality.module').then((m) => m.PersonalityModule),
  },
  {
    path: 'dashboard',
    component: BackofficeDashboardComponent,
    data: { page: 'index.html' },
    canActivate: [adminGuard],
  },
  {
    path: '',
    loadChildren: () => import('./frontoffice/frontoffice.module').then(m => m.FrontofficeModule)
  },
  {
    path: '**',
    redirectTo: ''
  }
];
