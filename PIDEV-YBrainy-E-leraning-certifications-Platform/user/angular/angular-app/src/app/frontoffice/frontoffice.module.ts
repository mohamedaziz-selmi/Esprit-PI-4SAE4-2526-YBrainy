import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';

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
import { CreateThreadComponent } from './pages/forum/create-thread/create-thread.component';
import { AiThreadCheckerModalComponent } from './pages/forum/ai-thread-checker-modal/ai-thread-checker-modal.component';
import { DraftsComponent } from './pages/forum/drafts/drafts.component';
import { ThreadDetailComponent } from './pages/forum/thread-detail/thread-detail.component';
import { CalendarComponent } from './pages/calendar/calendar.component';
import { CoursesComponent } from './pages/courses/courses.component';
import { LessonsComponent } from './pages/lessons/lessons.component';
import { TemplateMirrorComponent } from './pages/template-mirror/template-mirror.component';
import { CodeLabComponent } from './pages/codelab/codelab.component';
import { CourseDetailComponent } from './pages/course-detail/course-detail.component';
import { LessonDetailComponent } from './pages/lesson-detail/lesson-detail.component';
import { QuizPageComponent } from './pages/quiz-page/quiz-page.component';
import { CourseProgressComponent } from './pages/course-progress/course-progress.component';
import { CourseReviewsComponent } from './pages/course-reviews/course-reviews.component';
import { AiLearningPathPageComponent } from './pages/learning-paths/learning-paths.component';
import { StudentDashboardComponent } from './pages/student-dashboard/student-dashboard.component';
import { FoPackListComponent } from './pages/packs/pack-list/pack-list.component';
import { FoPackDetailComponent } from './pages/packs/pack-detail/pack-detail.component';
import { AiAssistantComponent } from './ai-assistant/ai-assistant.component';
import { YforumyTextPipe } from './ai-assistant/yforumy-text.pipe';
import { LevelBarComponent } from './level-bar/level-bar.component';
import { DashboardComponent } from './pages/dashboard/dashboard.component';
import { MessagingComponent } from './pages/messaging/messaging.component';
import { UserProfileComponent } from './pages/user-profile/user-profile.component';
import { WishlistComponent } from './pages/wishlist/wishlist.component';
import { JobsComponent } from './pages/jobs/jobs.component';
import { JobOfferDetailComponent } from './pages/job-offer-detail/job-offer-detail.component';
import { NotesComponent } from './pages/notes/notes.component';
import { AnimatedButtonComponent } from '../backoffice/ai-application/components/animated-button.component';
import { CvUploadCardComponent } from '../backoffice/ai-application/components/cv-upload-card.component';
import { JobDescriptionCardComponent } from '../backoffice/ai-application/components/job-description-card.component';
import { LoaderComponent } from '../backoffice/ai-application/components/loader.component';
import { ResultCardComponent } from '../backoffice/ai-application/components/result-card.component';
import { ResultTabsComponent } from '../backoffice/ai-application/components/result-tabs.component';

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
    CreateThreadComponent,
    AiThreadCheckerModalComponent,
    DraftsComponent,
    ThreadDetailComponent,
    CalendarComponent,
    CoursesComponent,
    LessonsComponent,
    TemplateMirrorComponent,
    CodeLabComponent,
    CourseDetailComponent,
    LessonDetailComponent,
    QuizPageComponent,
    CourseProgressComponent,
    CourseReviewsComponent,
    AiLearningPathPageComponent,
    StudentDashboardComponent,
    FoPackListComponent,
    FoPackDetailComponent,
    AiAssistantComponent,
    YforumyTextPipe,
    DashboardComponent,
    MessagingComponent,
    UserProfileComponent,
    WishlistComponent,
    JobsComponent,
    JobOfferDetailComponent,
    NotesComponent,
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
    FormsModule,
    ReactiveFormsModule,
    AnimatedButtonComponent,
    CvUploadCardComponent,
    JobDescriptionCardComponent,
    LoaderComponent,
    ResultTabsComponent,
    ResultCardComponent,
    LevelBarComponent,
    RouterModule,
    FrontofficeRoutingModule
  ]
})
export class FrontofficeModule { }
