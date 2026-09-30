import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { Sessao } from './sessao';

/**
 * Toda chamada /api passa por aqui (registrado em app.config.ts):
 * - {@code X-Inventario: 1}: o backend recusa alteração sem ele (proteção contra CSRF, sessao/FiltroOrigem.java);
 * - {@code X-Request-Id} novo: o backend usa o mesmo id no log e o devolve no erro (código para o suporte);
 * - 401 com SESSAO_EXPIRADA → volta para o login, lembrando onde estava.
 */
export const interceptadorApi: HttpInterceptorFn = (req, next) => {
  if (!req.url.startsWith('/api')) return next(req);
  const sessao = inject(Sessao);
  const comCabecalhos = req.clone({ setHeaders: { 'X-Inventario': '1', 'X-Request-Id': novoId() } });
  return next(comCabecalhos).pipe(
    catchError((e: unknown) => {
      const ehLogin = req.url === '/api/sessao';
      if (e instanceof HttpErrorResponse && e.status === 401 && !ehLogin) sessao.expirou();
      return throwError(() => e);
    }),
  );
};

function novoId(): string {
  return typeof crypto !== 'undefined' && 'randomUUID' in crypto
    ? crypto.randomUUID()
    : Date.now().toString(36) + Math.random().toString(36).slice(2, 10);
}
