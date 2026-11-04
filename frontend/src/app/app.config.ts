import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { environment } from '../environments/environment';
import { routes } from './app.routes';
import { mockApiInterceptor } from './core/api/mocks/mock-api.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withComponentInputBinding()),
    // Interceptors (auth, error) are added in the Core task.
    // The mock must stay last: it acts as the backend, so the others run before it.
    provideHttpClient(
      withFetch(),
      withInterceptors(environment.useMocks ? [mockApiInterceptor] : []),
    ),
  ],
};
