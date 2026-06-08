import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { AppComponent } from './app/app.component';
import { installGlobalRuntimeErrorHooks } from './app/runtime-error.handler';

if (typeof window !== 'undefined' && !('global' in window)) {
  (window as typeof window & { global: Window }).global = window;
}

installGlobalRuntimeErrorHooks();

bootstrapApplication(AppComponent, appConfig)
  .catch((err) => console.error(err));
