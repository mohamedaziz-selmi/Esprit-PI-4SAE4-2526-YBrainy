import { CommonModule } from '@angular/common';
import { HttpClientModule } from '@angular/common/http';
import { NgModule } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';

import { BackofficeDashboardComponent } from './backoffice-dashboard.component';
import { CategoryFormComponent } from './category-form/category-form.component';
import { CategoryListComponent } from './category-list/category-list.component';
import { CvSubmissionsComponent } from './cv-submissions/cv-submissions.component';
import { FinanceComponent } from './finance/finance.component';
import { PackFormComponent } from './pack-form/pack-form.component';
import { PackListComponent } from './pack-list/pack-list.component';
import { PacksOrderComponent } from './packs-order/packs-order.component';
import { ProfileComponent } from './profile/profile.component';

@NgModule({
  declarations: [
    BackofficeDashboardComponent,
    CategoryFormComponent,
    CategoryListComponent,
    CvSubmissionsComponent,
    FinanceComponent,
    PackFormComponent,
    PackListComponent,
    PacksOrderComponent,
    ProfileComponent,
  ],
  imports: [
    CommonModule,
    FormsModule,
    HttpClientModule,
    ReactiveFormsModule,
    RouterModule,
  ],
})
export class BackofficeModule { }
