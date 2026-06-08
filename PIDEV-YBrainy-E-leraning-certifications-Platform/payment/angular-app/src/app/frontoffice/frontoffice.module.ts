import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { ReactiveFormsModule, FormsModule } from '@angular/forms';

import { FrontofficeRoutingModule } from './frontoffice-routing.module';

// Layout components
import { LayoutComponent } from './layout/layout.component';
import { HeaderComponent } from './header/header.component';
import { FooterComponent } from './footer/footer.component';

// Page components
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

// Pack components
import { FoPackListComponent } from './pages/packs/pack-list/pack-list.component';
import { FoPackDetailComponent } from './pages/packs/pack-detail/pack-detail.component';

// Section components
import { HeroSectionComponent } from './home/hero-section/hero-section.component';
import { TrustLogosComponent } from './home/trust-logos/trust-logos.component';
import { WhyYbrainyComponent } from './home/why-ybrainy/why-ybrainy.component';
import { LearningPathsComponent } from './home/learning-paths/learning-paths.component';
import { LearningDomainsComponent } from './home/learning-domains/learning-domains.component';
import { DesignedForLearnersComponent } from './home/designed-for-learners/designed-for-learners.component';
import { SuccessStoriesComponent } from './home/success-stories/success-stories.component';
import { PartnersComponent } from './home/partners/partners.component';
import { CareerCtaComponent } from './home/career-cta/career-cta.component';
import { BlogInsightsComponent } from './home/blog-insights/blog-insights.component';
import { PrefooterCtaComponent } from './home/prefooter-cta/prefooter-cta.component';

@NgModule({
  declarations: [
    LayoutComponent,
    HeaderComponent,
    FooterComponent,
    HomeComponent,
    ServicesComponent,
    ServiceDetailComponent,
    PaymentComponent,
    ResourcesComponent,
    AboutComponent,
    ForumComponent,
    CalendarComponent,
    CoursesComponent,
    LessonsComponent,
    TemplateMirrorComponent,
    FoPackListComponent,
    FoPackDetailComponent,
    HeroSectionComponent,
    TrustLogosComponent,
    WhyYbrainyComponent,
    LearningPathsComponent,
    LearningDomainsComponent,
    DesignedForLearnersComponent,
    SuccessStoriesComponent,
    PartnersComponent,
    CareerCtaComponent,
    BlogInsightsComponent,
    PrefooterCtaComponent
  ],
  imports: [
    CommonModule,
    ReactiveFormsModule,
    FormsModule,
    RouterModule,
    FrontofficeRoutingModule
  ]
})
export class FrontofficeModule { }
