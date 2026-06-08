import { APP_INITIALIZER, ApplicationConfig, ErrorHandler, provideZoneChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';

import { routes } from './app.routes';
import { apiPrefixInterceptor } from './api-prefix.interceptor';
import { initKeycloak } from './auth/keycloak.service';
import { authInterceptor } from './auth/auth.interceptor';
import { RuntimeErrorHandler } from './runtime-error.handler';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    provideHttpClient(withInterceptors([apiPrefixInterceptor, authInterceptor])),
    {
      provide: ErrorHandler,
      useClass: RuntimeErrorHandler,
    },
    {
      provide: APP_INITIALIZER,
      useFactory: () => () => initKeycloak(),
      multi: true,
    },
  ],
};
