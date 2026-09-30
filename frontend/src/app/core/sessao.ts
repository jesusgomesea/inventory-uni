import { Injectable, inject, signal } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { Api } from './api';
import { Usuario } from './modelos';

/**
 * Técnico logado. O login é o do GLPI; a sessão vive num cookie do backend (o token do GLPI nunca chega ao
 * navegador). Aqui só guardamos quem é, para o cabeçalho e para o botão "Abrir no GLPI".
 */
@Injectable({ providedIn: 'root' })
export class Sessao {
  private readonly api = inject(Api);
  private readonly router = inject(Router);
  readonly usuario = signal<Usuario | null>(null);
  private verificada = false;

  /** Pergunta ao backend uma vez por carga da página se já existe sessão. */
  async verificar(): Promise<Usuario | null> {
    if (this.verificada) return this.usuario();
    try {
      this.usuario.set(await firstValueFrom(this.api.sessao()));
    } catch {
      this.usuario.set(null);
    }
    this.verificada = true;
    return this.usuario();
  }

  entrou(u: Usuario): void {
    this.usuario.set(u);
    this.verificada = true;
  }

  /** Chamado pelo interceptador quando o backend responde 401: manda para o login e volta aqui depois. */
  expirou(): void {
    if (!this.usuario() && this.router.url.startsWith('/entrar')) return;
    this.usuario.set(null);
    const volta = this.router.url.startsWith('/entrar') ? '/' : this.router.url;
    this.router.navigate(['/entrar'], { queryParams: { volta } });
  }

  async sair(): Promise<void> {
    try {
      await firstValueFrom(this.api.sair());
    } finally {
      this.usuario.set(null);
      this.router.navigate(['/entrar']);
    }
  }
}

/** Guarda das rotas: sem sessão, vai para o login. */
export const exigeSessao: CanActivateFn = async (_rota, estado) => {
  // inject() só vale antes do primeiro await (fora dele não há contexto de injeção)
  const sessao = inject(Sessao);
  const router = inject(Router);
  if (await sessao.verificar()) return true;
  return router.createUrlTree(['/entrar'], { queryParams: { volta: estado.url } });
};
