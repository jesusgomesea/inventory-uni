import { ChangeDetectionStrategy, Component, inject, input, output, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { catchError, of } from 'rxjs';
import { Api } from '../../core/api';
import { Avisos, codigoErro, mensagemErro } from '../../core/avisos';
import { Alteracao, Ficha, Ref } from '../../core/modelos';
import { BuscaUsuario } from './busca-usuario';

/**
 * Edição dos campos administrativos (os únicos editáveis; hardware vem do agente). Manda ao backend só o que
 * mudou, junto com o date_mod lido: se outra pessoa mudou a máquina no GLPI no meio, o backend recusa (409) e
 * aqui oferecemos recarregar em vez de sobrescrever.
 */
@Component({
  selector: 'inv-edicao-ficha',
  imports: [FormsModule, BuscaUsuario],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="card edicao" aria-labelledby="t-edicao">
      <div class="card-header"><span class="card-num">1</span><h2 id="t-edicao">Editar dados do computador</h2>
        <span class="card-sub">grava direto no GLPI</span></div>
      <div class="card-body">
        @if (ficha().dinamico) {
          <div class="status alerta">Esta máquina vem do inventário (GLPI Agent). Ao salvar, o GLPI trava os campos alterados
            para o agente não desfazer a mudança. Para destravar, use a aba <b>Campos bloqueados</b> da máquina no GLPI.</div>
        }
        <form (ngSubmit)="salvar()">
          <div class="linha cols-2">
            <label>Nome <input name="nome" [(ngModel)]="nome" required maxlength="255"></label>
            <label>Status
              <select name="status" [(ngModel)]="statusId">
                <option [ngValue]="0">—</option>
                @for (s of listaStatus(); track s.id) { <option [ngValue]="s.id">{{ s.nome }}</option> }
              </select>
            </label>
          </div>
          <div class="linha cols-2">
            <inv-busca-usuario rotulo="Responsável (usuário)" [(valor)]="responsavel" />
            <label>Localização
              <select name="local" [(ngModel)]="localId">
                <option [ngValue]="0">—</option>
                @for (l of listaLocais(); track l.id) { <option [ngValue]="l.id">{{ l.nome }}</option> }
              </select>
            </label>
          </div>
          <div class="linha cols-3">
            <inv-busca-usuario rotulo="Técnico responsável" [(valor)]="tecnico" />
            <label>Grupo técnico
              <select name="grupo" [(ngModel)]="grupoId">
                <option [ngValue]="0">—</option>
                @for (g of listaGrupos(); track g.id) { <option [ngValue]="g.id">{{ g.nome }}</option> }
              </select>
            </label>
            <label>Patrimônio <input name="patrimonio" [(ngModel)]="patrimonio" maxlength="255"></label>
          </div>
          <label>Descrição <textarea name="descricao" rows="3" [(ngModel)]="descricao"></textarea></label>

          @if (erro()) {
            <div class="status erro" role="alert">{{ erro() }}
              @if (conflito()) { <button type="button" class="btn-link" (click)="recarregar()">Recarregar a ficha</button> }
            </div>
          }
          <div class="edicao-acoes">
            <button type="button" class="btn-secundario" (click)="cancelar.emit()">Cancelar</button>
            <button type="submit" class="btn-primario" [disabled]="salvando()">{{ salvando() ? 'Salvando no GLPI…' : 'Salvar' }}</button>
          </div>
        </form>
      </div>
    </section>
  `,
})
export class EdicaoFicha {
  private readonly api = inject(Api);
  private readonly avisos = inject(Avisos);

  readonly ficha = input.required<Ficha>();
  /** Salvou: ficha relida do GLPI (com o date_mod novo). */
  readonly salvo = output<Ficha>();
  readonly cancelar = output<void>();

  protected readonly listaStatus = toSignal(this.api.status().pipe(catchError(() => of([]))), { initialValue: [] });
  protected readonly listaLocais = toSignal(this.api.locais().pipe(catchError(() => of([]))), { initialValue: [] });
  protected readonly listaGrupos = toSignal(this.api.grupos().pipe(catchError(() => of([]))), { initialValue: [] });
  protected readonly salvando = signal(false);
  protected readonly erro = signal<string | null>(null);
  protected readonly conflito = signal(false);

  protected nome = '';
  protected descricao = '';
  protected patrimonio = '';
  protected statusId = 0;
  protected localId = 0;
  protected grupoId = 0;
  protected responsavel: Ref | null = null;
  protected tecnico: Ref | null = null;

  ngOnInit(): void {
    this.preencher(this.ficha());
  }

  private preencher(f: Ficha): void {
    this.nome = f.nome ?? '';
    this.descricao = f.descricao ?? '';
    this.patrimonio = f.patrimonio ?? '';
    this.statusId = f.status?.id ?? 0;
    this.localId = f.local?.id ?? 0;
    this.grupoId = f.grupoTecnico?.id ?? 0;
    this.responsavel = f.responsavel;
    this.tecnico = f.tecnico;
  }

  /** Só o que mudou em relação à ficha lida. */
  private alteracao(): Alteracao | null {
    const f = this.ficha();
    const a: Alteracao = { dataModificacao: f.dataModificacao };
    if (this.nome.trim() !== (f.nome ?? '')) a.nome = this.nome.trim();
    if (this.descricao.trim() !== (f.descricao ?? '')) a.descricao = this.descricao.trim();
    if (this.patrimonio.trim() !== (f.patrimonio ?? '')) a.patrimonio = this.patrimonio.trim();
    if (this.statusId !== (f.status?.id ?? 0)) a.statusId = this.statusId;
    if (this.localId !== (f.local?.id ?? 0)) a.localId = this.localId;
    if (this.grupoId !== (f.grupoTecnico?.id ?? 0)) a.grupoTecnicoId = this.grupoId;
    if ((this.responsavel?.id ?? 0) !== (f.responsavel?.id ?? 0)) a.responsavelId = this.responsavel?.id ?? 0;
    if ((this.tecnico?.id ?? 0) !== (f.tecnico?.id ?? 0)) a.tecnicoId = this.tecnico?.id ?? 0;
    return Object.keys(a).length > 1 ? a : null;
  }

  protected salvar(): void {
    this.erro.set(null);
    this.conflito.set(false);
    if (!this.nome.trim()) {
      this.erro.set('O nome não pode ficar vazio.');
      return;
    }
    const a = this.alteracao();
    if (!a) {
      this.cancelar.emit();
      return;
    }
    this.salvando.set(true);
    this.api.editar(this.ficha().id, a).subscribe({
      next: (f) => {
        this.salvando.set(false);
        this.avisos.toast('Salvo no GLPI');
        this.salvo.emit(f);
      },
      error: (e) => {
        this.salvando.set(false);
        this.conflito.set(codigoErro(e) === 'ALTERADO_POR_OUTRO');
        this.erro.set(mensagemErro(e, 'Não foi possível salvar no GLPI.'));
      },
    });
  }

  /** Depois de um conflito: relê do GLPI e reabre o formulário com os dados atuais (as mudanças se perdem). */
  protected recarregar(): void {
    this.api.ficha(this.ficha().id).subscribe({
      next: (f) => {
        this.salvo.emit(f);
        this.avisos.toast('Ficha recarregada. Refaça a alteração.', '↻');
      },
      error: (e) => this.erro.set(mensagemErro(e)),
    });
  }
}
