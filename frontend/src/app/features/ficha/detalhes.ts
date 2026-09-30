import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { Api } from '../../core/api';
import { mensagemErro } from '../../core/avisos';
import { data, dataHora, slot, tamanho } from '../../core/formato';
import { Detalhes, Ficha } from '../../core/modelos';
import { Sessao } from '../../core/sessao';

/**
 * "Ver mais informações": só busca no GLPI quando o técnico abre (são ~10 consultas a mais). Cada seção mostra o
 * próprio erro sem derrubar as outras (ex.: perfil sem direito a ver chamados).
 */
@Component({
  selector: 'inv-detalhes-ficha',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <details class="ver-mais" (toggle)="abriu($any($event.target).open)">
      <summary><span>Ver mais informações</span>
        <span class="doc-meta">processador, softwares, garantia, chamados, histórico…</span><span class="seta"></span></summary>
      <div class="ver-mais-corpo">
        @if (erro()) { <div class="status erro">{{ erro() }}</div> }
        <div class="det-grade">
          <div class="det-secao">
            <h3>Identificação</h3>
            <ul class="det-lista">
              <li>Tipo: <b>{{ ficha().tipo || '—' }}</b></li>
              <li>UUID: <span class="mono">{{ ficha().uuid || '—' }}</span></li>
              <li>Técnico responsável: <b>{{ ficha().tecnico?.nome || '—' }}</b></li>
              <li>Grupo técnico: <b>{{ ficha().grupoTecnico?.nome || '—' }}</b></li>
              <li>Último boot: {{ dataHora(ficha().ultimoBoot) }}</li>
              <li>Cadastrado no GLPI em {{ dataHora(ficha().criadoEm) }} · alterado em {{ dataHora(ficha().dataModificacao) }}</li>
            </ul>
          </div>
          <div class="det-secao">
            <h3>Memória e discos em detalhe</h3>
            <ul class="det-lista">
              @for (m of ficha().memoria.modulos; track $index) {
                <li>{{ slot(m.slot) }}: <b>{{ tamanho(m.tamanhoMb) }}</b> {{ m.descricao || '' }}
                  @if (m.fabricante) { <small>· {{ m.fabricante }}</small> } @if (m.serial) { <small class="mono">· S/N {{ m.serial }}</small> }</li>
              }
              @for (d of ficha().armazenamento.discos; track $index) {
                <li>Disco: <b>{{ d.modelo || '—' }}</b> {{ tamanho(d.capacidadeMb) }} {{ d.tipo || 'tipo não identificado' }}
                  @if (d.serial) { <small class="mono">· S/N {{ d.serial }}</small> }</li>
              }
              @for (i of ficha().rede.interfaces; track $index) {
                <li>{{ i.nome || 'Interface' }}: <span class="mono">{{ i.ips.join(', ') || 'sem IP' }}</span></li>
              }
            </ul>
          </div>

          @if (detalhes(); as d) {
            <div class="det-secao">
              <h3>Componentes</h3>
              @if (d.componentes.erro) { <p class="det-erro">{{ d.componentes.erro }}</p> }
              <ul class="det-lista">
                @for (c of d.componentes.dados ?? []; track $index) {
                  <li><small>{{ c.tipo }}</small><br><b>{{ c.nome || '—' }}</b>
                    @for (p of c.detalhes; track p.rotulo) { <small> · {{ p.rotulo }}: {{ p.valor }}</small> }</li>
                } @empty { @if (!d.componentes.erro) { <li class="det-erro">Nenhum componente informado.</li> } }
              </ul>
            </div>
            <div class="det-secao">
              <h3>Compra e garantia</h3>
              @if (d.financeiro.erro) { <p class="det-erro">{{ d.financeiro.erro }}</p> }
              @if (d.financeiro.dados; as fin) {
                <ul class="det-lista">
                  <li>Compra: <b>{{ data(fin.compra) }}</b> @if (fin.notaFiscal) { · {{ fin.notaFiscal }} }</li>
                  <li>Garantia: <b>{{ fin.fimGarantia ? 'até ' + data(fin.fimGarantia) : '—' }}</b>
                    @if (fin.mesesGarantia) { <small>({{ fin.mesesGarantia }} meses)</small> }
                    @if (garantiaVencida()) { <small> · vencida</small> }</li>
                  <li>Fornecedor: {{ fin.fornecedor || '—' }} @if (fin.pedido) { · pedido {{ fin.pedido }} }</li>
                  <li>Valor: {{ valor(fin.valor) }}</li>
                </ul>
              } @else if (!d.financeiro.erro) { <p class="det-erro">Sem dados financeiros no GLPI.</p> }
            </div>
            <div class="det-secao">
              <h3>Antivírus</h3>
              @if (d.antivirus.erro) { <p class="det-erro">{{ d.antivirus.erro }}</p> }
              <ul class="det-lista">
                @for (a of d.antivirus.dados ?? []; track $index) {
                  <li><b>{{ a.nome }}</b> <small>{{ a.versao }}</small><br>
                    {{ a.ativo ? 'Ativo' : 'Inativo' }} · {{ a.atualizado ? 'atualizado' : 'desatualizado' }}
                    @if (a.assinatura) { <small class="mono">· assinatura {{ a.assinatura }}</small> }</li>
                } @empty { @if (!d.antivirus.erro) { <li class="det-erro">Nenhum antivírus informado.</li> } }
              </ul>
            </div>
            <div class="det-secao">
              <h3>Itens conectados</h3>
              @if (d.conectados.erro) { <p class="det-erro">{{ d.conectados.erro }}</p> }
              <ul class="det-lista">
                @for (c of d.conectados.dados ?? []; track $index) {
                  <li><small>{{ c.tipo }}</small> <b>{{ c.nome }}</b> {{ c.modelo || '' }} @if (c.serial) { <small class="mono">· S/N {{ c.serial }}</small> }</li>
                } @empty { @if (!d.conectados.erro) { <li class="det-erro">Nenhum monitor ou periférico vinculado.</li> } }
              </ul>
            </div>
            <div class="det-secao larga">
              <h3>Chamados</h3>
              @if (d.chamados.erro) { <p class="det-erro">{{ d.chamados.erro }}</p> }
              <ul class="det-lista">
                @for (c of d.chamados.dados ?? []; track c.id) {
                  <li><a [href]="urlChamado(c.id)" target="_blank" rel="noopener" class="mono">#{{ c.id }}</a>
                    <b> {{ c.titulo }}</b> <small>· {{ c.status }} · {{ dataHora(c.abertoEm) }}</small></li>
                } @empty { @if (!d.chamados.erro) { <li class="det-erro">Nenhum chamado vinculado a esta máquina.</li> } }
              </ul>
            </div>
            <div class="det-secao larga">
              <h3>Softwares ({{ d.softwares.dados?.length ?? 0 }})</h3>
              @if (d.softwares.erro) { <p class="det-erro">{{ d.softwares.erro }}</p> }
              @if ((d.softwares.dados?.length ?? 0) > 8) {
                <input class="det-filtro" type="search" placeholder="Filtrar softwares" (input)="filtroSoftware.set($any($event.target).value)">
              }
              <ul class="det-lista det-softwares">
                @for (s of softwares(); track $index) { <li><b>{{ s.nome }}</b> <small class="mono">{{ s.versao }}</small></li> }
                @empty { @if (!d.softwares.erro) { <li class="det-erro">Nenhum software.</li> } }
              </ul>
            </div>
            <div class="det-secao larga">
              <h3>Histórico no GLPI</h3>
              @if (d.historico.erro) { <p class="det-erro">{{ d.historico.erro }}</p> }
              <ul class="det-lista">
                @for (h of d.historico.dados ?? []; track $index) {
                  <li><small class="mono">{{ dataHora(h.data) }}</small> <b>{{ h.usuario }}</b> — {{ h.descricao }}</li>
                } @empty { @if (!d.historico.erro) { <li class="det-erro">Sem histórico.</li> } }
              </ul>
            </div>
          } @else if (!erro()) {
            <div class="det-secao larga"><span class="girando"></span> Buscando no GLPI…</div>
          }
        </div>
      </div>
    </details>
  `,
})
export class DetalhesFicha {
  private readonly api = inject(Api);
  private readonly sessao = inject(Sessao);

  readonly ficha = input.required<Ficha>();

  protected readonly detalhes = signal<Detalhes | null>(null);
  protected readonly erro = signal<string | null>(null);
  protected readonly filtroSoftware = signal('');
  private carregadoPara: number | null = null;

  protected readonly softwares = computed(() => {
    const t = this.filtroSoftware().trim().toLowerCase();
    const l = this.detalhes()?.softwares.dados ?? [];
    return t ? l.filter((s) => s.nome.toLowerCase().includes(t)) : l;
  });

  protected readonly garantiaVencida = computed(() => {
    const fim = this.detalhes()?.financeiro.dados?.fimGarantia;
    return !!fim && fim < new Date().toISOString().slice(0, 10);
  });

  protected readonly tamanho = tamanho;
  protected readonly slot = slot;
  protected readonly dataHora = dataHora;
  protected readonly data = data;

  protected abriu(aberto: boolean): void {
    const id = this.ficha().id;
    if (!aberto || this.carregadoPara === id) return;
    this.carregadoPara = id;
    this.detalhes.set(null);
    this.erro.set(null);
    this.api.detalhes(id).subscribe({
      next: (d) => this.detalhes.set(d),
      error: (e) => {
        this.carregadoPara = null;
        this.erro.set(mensagemErro(e, 'Não foi possível buscar os detalhes no GLPI.'));
      },
    });
  }

  protected urlChamado(id: number): string {
    return `${this.sessao.usuario()?.urlGlpi ?? ''}/front/ticket.form.php?id=${id}`;
  }

  protected valor(v: string | null): string {
    const n = v == null ? NaN : Number(v);
    return Number.isFinite(n) && n > 0 ? n.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' }) : '—';
  }
}
