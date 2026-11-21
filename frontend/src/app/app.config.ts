import {
  ApplicationConfig,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { routes } from './app.routes';
import { AuthStore } from './core/auth/auth.store';
import { SessionTimer } from './core/auth/session-timer';
import { authInterceptor } from './core/http/auth.interceptor';
import { errorInterceptor } from './core/http/error.interceptor';
import { ThemeService } from './core/theme/theme.service';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(withFetch(), withInterceptors([authInterceptor, errorInterceptor])),
    // After a page reload: load the user before the guards check the first route.
    provideAppInitializer(() => inject(AuthStore).restore()),
    // Creating it is enough: from then on it follows login and logout by itself.
    provideAppInitializer(() => {
      inject(SessionTimer);
    }),
    // Applies the saved light/dark choice before the first page renders.
    provideAppInitializer(() => {
      inject(ThemeService);
    }),
  ],
};
