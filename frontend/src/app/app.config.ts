import { registerLocaleData } from '@angular/common';
import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import localePt from '@angular/common/locales/pt';
import { ApplicationConfig, LOCALE_ID, provideBrowserGlobalErrorListeners, provideZonelessChangeDetection } from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { routes } from './app.routes';
import { interceptadorApi } from './core/interceptador-api';

registerLocaleData(localePt);

/**
 * Configuração da aplicação (mesmo padrão do HELP-AGENT): sem zone.js (signals), locale pt-BR, HttpClient com
 * fetch e o interceptadorApi em toda chamada. Caminhos relativos (/api): o proxy do ng serve leva ao backend.
 */
export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideZonelessChangeDetection(),
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(withFetch(), withInterceptors([interceptadorApi])),
    { provide: LOCALE_ID, useValue: 'pt-BR' },
  ],
};
