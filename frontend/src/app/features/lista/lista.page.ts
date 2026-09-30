import {
  ChangeDetectionStrategy, Component, ElementRef, HostListener, effect, inject, input, numberAttribute, signal, viewChild,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { catchError, of } from 'rxjs';
import { Api } from '../../core/api';
import { mensagemErro } from '../../core/avisos';
import { ItemLista, Pagina } from '../../core/modelos';
import { Faixa } from '../../layout/faixa';

/**
 * Lista de computadores com busca única (nome, serial, patrimônio, usuário, último login ou IP) e filtros de
 * status e local. Os filtros ficam no endereço (?q=&status=&local=&pagina=): o "voltar" do navegador e um link
 * colado no chat trazem a mesma lista.
 *
 * Atalhos: "/" vai para a busca; Enter com um único resultado abre a ficha.
 */
@Component({
  selector: 'inv-lista',
  imports: [RouterLink, Faixa],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <inv-faixa titulo="Computadores" subtitulo="Tudo o que o GLPI sabe de cada máquina, numa tela só. Busque por nome, serial, patrimônio, usuário ou IP." />
    <main class="container">
      <div class="inv-filtros">
        <label>Buscar
          <input #busca type="search" [value]="q() ?? ''" (input)="digitou($any($event.target).value)" (keydown.enter)="abrirUnico()"
            placeholder="RD-LJ014, 7XK2Q93, maria, 10.14.2.32…" aria-describedby="dica-busca" autofocus>
        </label>
        <label>Status
          <select [value]="status() ?? ''" (change)="filtrar({ status: $any($event.target).value || null })">
            <option value="">Todos</option>
            @for (s of listaStatus(); track s.id) { <option [value]="s.id" [selected]="s.id === status()">{{ s.nome }}</option> }
          </select>
        </label>
        <label>Local
          <select [value]="local() ?? ''" (change)="filtrar({ local: $any($event.target).value || null })">
            <option value="">Todos</option>
            @for (l of listaLocais(); track l.id) { <option [value]="l.id" [selected]="l.id === local()">{{ l.nome }}</option> }
          </select>
        </label>
      </div>
      <p class="inv-contagem" id="dica-busca">
        @if (resultado(); as r) {
          {{ r.total }} computador{{ r.total === 1 ? '' : 'es' }}
          @if (r.total > r.tamanho) { · página {{ r.pagina + 1 }} de {{ totalPaginas() }} }
        } @else if (!erro()) { <span class="girando"></span> buscando no GLPI… }
        · atalho <kbd>/</kbd> para buscar
      </p>

      @if (erro()) { <div class="status erro" role="alert">{{ erro() }}</div> }

      @if (resultado(); as r) {
        <div class="inv-tabela" [class.inv-carregando]="carregando()">
          <div class="inv-linha cabecalho" aria-hidden="true">
            <span>Computador</span><span>Responsável</span><span>Local</span><span>Status</span><span>Sistema</span><span>IP</span>
          </div>
          @for (c of r.itens; track c.id) {
            <a class="inv-linha" [routerLink]="['/computadores', c.id]">
              <span class="inv-nome">
                <b>{{ c.nome || '(sem nome)' }}</b>
                <small>{{ juntar(c.fabricante, c.modelo) }}{{ c.serial ? ' · ' + c.serial : '' }}</small>
              </span>
              <span [title]="c.ultimoLogin ? 'Último login: ' + c.ultimoLogin : ''">{{ c.responsavel || '—' }}</span>
              <span [title]="c.local || ''">{{ c.local || '—' }}</span>
              <span>@if (c.status) { <span class="selo-status">{{ c.status }}</span> } @else { — }</span>
              <span [title]="c.sistema || ''">{{ c.sistema || '—' }}</span>
              <span class="mono">{{ c.ip || '—' }}</span>
            </a>
          } @empty {
            <div class="inv-vazio">Nenhum computador encontrado. Confira a busca e os filtros.</div>
          }
        </div>
        @if (r.total > r.tamanho) {
          <div class="paginacao">
            <button type="button" [disabled]="r.pagina === 0" (click)="irPara(r.pagina - 1)">Anterior</button>
            <span>{{ r.pagina + 1 }} / {{ totalPaginas() }}</span>
            <button type="button" [disabled]="r.pagina + 1 >= totalPaginas()" (click)="irPara(r.pagina + 1)">Próxima</button>
          </div>
        }
      }
    </main>
  `,
})
export class ListaPage {
  private readonly api = inject(Api);
  private readonly router = inject(Router);
  private readonly campoBusca = viewChild<ElementRef<HTMLInputElement>>('busca');

  // filtros vindos do endereço (withComponentInputBinding)
  readonly q = input<string>();
  readonly status = input(null, { transform: (v: unknown) => (v == null || v === '' ? null : numberAttribute(v)) });
  readonly local = input(null, { transform: (v: unknown) => (v == null || v === '' ? null : numberAttribute(v)) });
  readonly pagina = input(0, { transform: (v: unknown) => (v == null || v === '' ? 0 : numberAttribute(v)) });

  protected readonly listaStatus = toSignal(this.api.status().pipe(catchError(() => of([]))), { initialValue: [] });
  protected readonly listaLocais = toSignal(this.api.locais().pipe(catchError(() => of([]))), { initialValue: [] });
  protected readonly resultado = signal<Pagina<ItemLista> | null>(null);
  protected readonly carregando = signal(false);
  protected readonly erro = signal<string | null>(null);
  private temporizador: ReturnType<typeof setTimeout> | undefined;
  private seq = 0;

  constructor() {
    // recarrega sempre que o endereço mudar; descarta respostas atrasadas de buscas anteriores
    effect(() => {
      const filtro = { q: this.q(), status: this.status(), local: this.local(), pagina: this.pagina() };
      const n = ++this.seq;
      this.carregando.set(true);
      this.api.computadores(filtro).subscribe({
        next: (r) => {
          if (n !== this.seq) return;
          this.resultado.set(r);
          this.erro.set(null);
          this.carregando.set(false);
        },
        error: (e) => {
          if (n !== this.seq) return;
          this.erro.set(mensagemErro(e, 'Não foi possível buscar no GLPI.'));
          this.carregando.set(false);
        },
      });
    });
  }

  @HostListener('document:keydown', ['$event'])
  protected atalho(ev: KeyboardEvent): void {
    const alvo = ev.target as HTMLElement | null;
    const digitando = alvo && ['INPUT', 'TEXTAREA', 'SELECT'].includes(alvo.tagName);
    if (ev.key === '/' && !digitando) {
      ev.preventDefault();
      this.campoBusca()?.nativeElement.focus();
    }
  }

  protected digitou(texto: string): void {
    clearTimeout(this.temporizador);
    this.temporizador = setTimeout(() => this.filtrar({ q: texto.trim() || null }), 300);
  }

  protected filtrar(mudanca: { q?: string | null; status?: string | null; local?: string | null }): void {
    this.router.navigate([], { queryParams: { ...mudanca, pagina: null }, queryParamsHandling: 'merge', replaceUrl: 'q' in mudanca });
  }

  protected irPara(p: number): void {
    this.router.navigate([], { queryParams: { pagina: p || null }, queryParamsHandling: 'merge' });
    window.scrollTo({ top: 0 });
  }

  protected abrirUnico(): void {
    const r = this.resultado();
    if (r && r.itens.length === 1) this.router.navigate(['/computadores', r.itens[0].id]);
  }

  protected totalPaginas(): number {
    const r = this.resultado();
    return r ? Math.max(1, Math.ceil(r.total / r.tamanho)) : 1;
  }

  protected juntar(a: string | null, b: string | null): string {
    return [a, b].filter(Boolean).join(' ') || 'modelo não informado';
  }
}
