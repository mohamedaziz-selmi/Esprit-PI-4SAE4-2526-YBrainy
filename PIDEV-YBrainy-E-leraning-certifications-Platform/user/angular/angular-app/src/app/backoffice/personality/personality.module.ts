import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Routes } from '@angular/router';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';

import { PersonalityDashboardComponent } from './personality-dashboard/personality-dashboard.component';
import { PersonalityListComponent } from './personality-list/personality-list.component';
import { PersonalityDetailComponent } from './personality-detail/personality-detail.component';
import { PersonalityFormComponent } from './personality-form/personality-form.component';
import { BehaviorMonitorComponent } from './behavior-monitor/behavior-monitor.component';
import { BehaviorListComponent } from './behavior-list/behavior-list.component';
import { BehaviorDetailComponent } from './behavior-detail/behavior-detail.component';
import { BehaviorFormComponent } from './behavior-form/behavior-form.component';

const routes: Routes = [
  {
    path: '',
    component: PersonalityDashboardComponent,
    children: [
      { path: '', component: PersonalityListComponent },
      { path: 'list', component: PersonalityListComponent },
      { path: 'create', component: PersonalityFormComponent },
      { path: 'edit/:id', component: PersonalityFormComponent },
      { path: 'detail/:id', component: PersonalityDetailComponent },
      { path: 'behaviors', component: BehaviorListComponent },
      { path: 'behaviors/create', component: BehaviorFormComponent },
      { path: 'behaviors/edit/:id', component: BehaviorFormComponent },
      { path: 'behaviors/detail/:id', component: BehaviorDetailComponent },
      { path: 'behavior', redirectTo: 'behaviors', pathMatch: 'full' },
      { path: 'behavior-monitor', component: BehaviorMonitorComponent }
    ]
  }
];

@NgModule({
  declarations: [],
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    RouterModule.forChild(routes),
    PersonalityDashboardComponent,
    PersonalityListComponent,
    PersonalityDetailComponent,
    PersonalityFormComponent,
    BehaviorMonitorComponent,
    BehaviorListComponent,
    BehaviorDetailComponent,
    BehaviorFormComponent
  ]
})
export class PersonalityModule {}
