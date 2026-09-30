import { Routes } from '@angular/router';
import { exigeSessao } from './core/sessao';

/** Rotas. Páginas carregadas sob demanda; todas, menos o login, exigem sessão do GLPI. */
export const routes: Routes = [
  {
    path: 'entrar',
    title: 'Inventário — Entrar',
    loadComponent: () => import('./features/entrar/entrar.page').then((m) => m.EntrarPage),
  },
  {
    path: '',
    title: 'Inventário — Computadores',
    canActivate: [exigeSessao],
    loadComponent: () => import('./features/lista/lista.page').then((m) => m.ListaPage),
  },
  {
    path: 'computadores/:id',
    title: 'Inventário — Ficha',
    canActivate: [exigeSessao],
    loadComponent: () => import('./features/ficha/ficha.page').then((m) => m.FichaPage),
  },
  { path: '**', redirectTo: '' },
];
