import { ChangeDetectionStrategy, Component, inject, input, model, signal } from '@angular/core';
import { catchError, of } from 'rxjs';
import { Api } from '../../core/api';
import { Ref, UsuarioGlpi } from '../../core/modelos';

/**
 * Campo de usuário do GLPI com busca enquanto digita (a partir de 2 letras). Os usuários podem ser milhares, por
 * isso não há lista fixa. Setas ↑↓ escolhem, Enter confirma, Esc fecha. "Nenhum" limpa o campo (id 0 no GLPI).
 */
@Component({
  selector: 'inv-busca-usuario',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <label class="combo">{{ rotulo() }}
      <input [value]="texto()" (input)="digitou($any($event.target).value)" (keydown)="tecla($event)" (blur)="fechar()"
        placeholder="Digite nome ou login" autocomplete="off" role="combobox" [attr.aria-expanded]="aberta()">
      @if (aberta()) {
        <ul class="combo-lista" role="listbox">
          <li><button type="button" (mousedown)="escolher(null)" [class.ativo]="indice() === -1">Nenhum</button></li>
          @for (u of resultados(); track u.id; let i = $index) {
            <li><button type="button" role="option" (mousedown)="escolher(u)" [class.ativo]="indice() === i">
              {{ u.nome }}<small>{{ u.login }}</small></button></li>
          } @empty {
            <li><button type="button" disabled>{{ buscando() ? 'Buscando…' : 'Nenhum usuário ativo encontrado' }}</button></li>
          }
        </ul>
      }
    </label>
  `,
})
export class BuscaUsuario {
  private readonly api = inject(Api);

  readonly rotulo = input.required<string>();
  /** Usuário escolhido; null = nenhum. */
  readonly valor = model<Ref | null>(null);

  protected readonly texto = signal('');
  protected readonly resultados = signal<UsuarioGlpi[]>([]);
  protected readonly aberta = signal(false);
  protected readonly buscando = signal(false);
  protected readonly indice = signal(0);
  private temporizador: ReturnType<typeof setTimeout> | undefined;

  ngOnInit(): void {
    this.texto.set(this.valor()?.nome ?? '');
  }

  protected digitou(t: string): void {
    this.texto.set(t);
    clearTimeout(this.temporizador);
    if (t.trim().length < 2) {
      this.resultados.set([]);
      this.aberta.set(t.trim().length > 0);
      return;
    }
    this.aberta.set(true);
    this.buscando.set(true);
    this.temporizador = setTimeout(() => {
      this.api.usuarios(t.trim()).pipe(catchError(() => of([]))).subscribe((r) => {
        this.resultados.set(r);
        this.indice.set(0);
        this.buscando.set(false);
      });
    }, 250);
  }

  protected tecla(ev: KeyboardEvent): void {
    if (!this.aberta()) return;
    const n = this.resultados().length;
    if (ev.key === 'ArrowDown') { ev.preventDefault(); this.indice.update((i) => Math.min(n - 1, i + 1)); }
    if (ev.key === 'ArrowUp') { ev.preventDefault(); this.indice.update((i) => Math.max(-1, i - 1)); }
    if (ev.key === 'Escape') this.fechar();
    if (ev.key === 'Enter') {
      ev.preventDefault();
      const i = this.indice();
      this.escolher(i === -1 ? null : (this.resultados()[i] ?? null));
    }
  }

  protected escolher(u: UsuarioGlpi | null): void {
    this.valor.set(u ? { id: u.id, nome: u.nome } : null);
    this.texto.set(u?.nome ?? '');
    this.aberta.set(false);
  }

  /** Sair do campo sem escolher: volta a mostrar o valor atual. */
  protected fechar(): void {
    this.aberta.set(false);
    this.texto.set(this.valor()?.nome ?? '');
  }
}
