import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { LayoutComponent } from './layout/layout.component';
import { HomeComponent } from './home/home.component';
import { ServicesComponent } from './pages/services/services.component';
import { ServiceDetailComponent } from './pages/service-detail/service-detail.component';
import { PaymentComponent } from './pages/payment/payment.component';
import { ResourcesComponent } from './pages/resources/resources.component';
import { AboutComponent } from './pages/about/about.component';
import { ForumComponent } from './pages/forum/forum.component';
import { CalendarComponent } from './pages/calendar/calendar.component';
import { CoursesComponent } from './pages/courses/courses.component';
import { LessonsComponent } from './pages/lessons/lessons.component';
import { TemplateMirrorComponent } from './pages/template-mirror/template-mirror.component';
import { FoPackListComponent } from './pages/packs/pack-list/pack-list.component';
import { FoPackDetailComponent } from './pages/packs/pack-detail/pack-detail.component';

const routes: Routes = [
  {
    path: '',
    component: LayoutComponent,
    children: [
      { path: '', component: HomeComponent },
      { path: 'services/:slug', component: ServiceDetailComponent },
      { path: 'services', component: ServicesComponent },
      { path: 'payment', component: PaymentComponent },
      { path: 'resources', component: ResourcesComponent },
      { path: 'about', component: AboutComponent },
      { path: 'forum', component: ForumComponent },
      { path: 'calendar', component: CalendarComponent },
      { path: 'courses', component: CoursesComponent },
      { path: 'courses/:courseId/lessons', component: LessonsComponent },
      { path: 'packs', component: FoPackListComponent },
      { path: 'packs/:id', component: FoPackDetailComponent },
      { path: 'template', component: TemplateMirrorComponent },
    ]
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class FrontofficeRoutingModule { }
