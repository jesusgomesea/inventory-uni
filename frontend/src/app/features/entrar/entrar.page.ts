import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { Api } from '../../core/api';
import { mensagemErro } from '../../core/avisos';
import { Sessao } from '../../core/sessao';
import { Faixa } from '../../layout/faixa';

/**
 * Login com o usuário do GLPI. Alternativa: o token pessoal (GLPI → Preferências → Chaves de acesso remoto),
 * útil quando o GLPI entra por outro meio (LDAP/SSO) e o login por senha na API está desabilitado.
 * Em espera desde 30/09/2026 (login desligado no backend): a página só redireciona para a lista.
 */
@Component({
  selector: 'inv-entrar',
  imports: [FormsModule, Faixa],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <inv-faixa sobretitulo="Inventário · GLPI" titulo="Entrar" subtitulo="Use o mesmo usuário e senha do GLPI. O que você pode ver e alterar aqui é o que o seu perfil permite lá." />
    <main class="container">
      <div class="card entrar">
        <div class="card-body">
          <form (ngSubmit)="entrar()">
            @if (comToken()) {
              <label>Token pessoal do GLPI
                <input name="token" type="password" [(ngModel)]="token" autocomplete="off" required autofocus>
                <span class="ajuda">No GLPI: seu nome (canto superior) → Preferências → Chaves de acesso remoto → Token de API.</span>
              </label>
            } @else {
              <label>Usuário
                <input name="login" [(ngModel)]="login" autocomplete="username" required autofocus>
              </label>
              <label>Senha
                <input name="senha" type="password" [(ngModel)]="senha" autocomplete="current-password" required>
              </label>
            }
            @if (erro()) { <div class="status erro" role="alert">{{ erro() }}</div> }
            <button class="btn-primario" type="submit" [disabled]="enviando()">
              {{ enviando() ? 'Entrando…' : 'Entrar' }}
            </button>
          </form>
          <button type="button" class="btn-link" (click)="alternar()">
            {{ comToken() ? 'Entrar com usuário e senha' : 'Entrar com token pessoal' }}
          </button>
        </div>
      </div>
    </main>
  `,
})
export class EntrarPage {
  private readonly api = inject(Api);
  private readonly sessao = inject(Sessao);
  private readonly router = inject(Router);

  /** Para onde voltar depois do login (?volta=/computadores/101). */
  readonly volta = input<string>();

  protected login = '';
  protected senha = '';
  protected token = '';
  protected readonly comToken = signal(false);
  protected readonly enviando = signal(false);
  protected readonly erro = signal<string | null>(null);

  constructor() {
    // login em espera: quem cair aqui (favorito antigo) vai direto para a lista
    this.sessao.verificar().then((u) => {
      if (u && !u.loginHabilitado) this.router.navigateByUrl('/');
    });
  }

  protected alternar(): void {
    this.comToken.update((v) => !v);
    this.erro.set(null);
  }

  protected async entrar(): Promise<void> {
    this.erro.set(null);
    const dados = this.comToken() ? { tokenPessoal: this.token.trim() } : { login: this.login.trim(), senha: this.senha };
    if (this.comToken() ? !this.token.trim() : !this.login.trim() || !this.senha) {
      this.erro.set(this.comToken() ? 'Cole o token pessoal.' : 'Informe usuário e senha.');
      return;
    }
    this.enviando.set(true);
    try {
      const u = await firstValueFrom(this.api.entrar(dados));
      this.sessao.entrou(u);
      this.senha = '';
      this.token = '';
      const destino = this.volta();
      // só caminho interno: um ?volta=https://... não pode levar para fora da aplicação
      this.router.navigateByUrl(destino && destino.startsWith('/') && !destino.startsWith('//') ? destino : '/');
    } catch (e) {
      this.erro.set(mensagemErro(e, 'Não foi possível entrar.'));
    } finally {
      this.enviando.set(false);
    }
  }
}
