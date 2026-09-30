import { ChangeDetectionStrategy, Component, effect, inject, input, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { catchError, of } from 'rxjs';
import { Api } from '../../core/api';
import { Avisos, mensagemErro } from '../../core/avisos';
import { bytes, data, dataHora } from '../../core/formato';
import { DocumentoLocal, Ficha } from '../../core/modelos';
import { Icone } from '../../layout/icone';

/** Mesmo limite do backend (spring.servlet.multipart.max-file-size). */
const LIMITE_BYTES = 50 * 1024 * 1024;

/**
 * Documentos do computador, em duas partes:
 * - "Documentos e termos": guardados no banco desta aplicação (fora do GLPI). Anexar, abrir e remover
 *   (remoção lógica com motivo: termo é registro e continua guardado).
 * - "No GLPI": os que já estão vinculados à máquina no GLPI, só para abrir.
 */
@Component({
  selector: 'inv-documentos-ficha',
  imports: [FormsModule, Icone],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="card" aria-labelledby="t-docs">
      <div class="card-header"><span class="card-num">{{ ativos() }}</span><h2 id="t-docs">Documentos e termos</h2>
        <span class="card-sub">guardados nesta aplicação</span></div>
      <div class="card-body">
        <div class="termos-gerar">
          <span class="rotulo-secao">Gerar termo para imprimir</span>
          <a class="btn-acao" [href]="api.urlTermo(ficha().id, 'movimentacao')" target="_blank" rel="noopener"
            title="Recebimento, devolução, empréstimo ou transferência"><inv-icone nome="termo" [tamanho]="14" /> Movimentação</a>
          <a class="btn-acao" [href]="api.urlTermo(ficha().id, 'substituicao')" target="_blank" rel="noopener"
            title="Esta máquina entra como o equipamento antigo"><inv-icone nome="termo" [tamanho]="14" /> Substituição</a>
          <small>Abre o modelo da TI já preenchido com esta máquina. Depois de assinado, anexe aqui embaixo.</small>
        </div>
        @if (!arquivo()) {
          <label class="zona-envio" [class.arrastando]="arrastando()"
            (dragover)="$event.preventDefault(); arrastando.set(true)" (dragleave)="arrastando.set(false)" (drop)="soltou($event)">
            <inv-icone nome="enviar" [tamanho]="22" />
            <span><b>Escolha um arquivo</b> ou arraste aqui</span>
            <small>Termo, nota fiscal, laudo, garantia… PDF, imagem, Word, Excel ou ZIP até 50 MB</small>
            <input type="file" (change)="escolheu($any($event.target).files)">
          </label>
        } @else {
          <form class="envio-form" (ngSubmit)="enviar()">
            <div class="envio-arquivo"><inv-icone nome="documento" /> {{ arquivo()!.name }} <span>{{ bytes(arquivo()!.size) }}</span></div>
            <div class="linha cols-2">
              <label>Tipo do documento
                <select name="categoria" [(ngModel)]="categoria" required>
                  @for (c of categorias(); track c.id) { <option [value]="c.id">{{ c.rotulo }}</option> }
                </select>
              </label>
              <label>Título <input name="titulo" [(ngModel)]="titulo" maxlength="255" [placeholder]="rotuloCategoria()"></label>
            </div>
            <div class="linha cols-2">
              <label>Validade (opcional) <input name="validade" type="date" [(ngModel)]="validade"></label>
              <label>Observação (opcional) <input name="observacao" [(ngModel)]="observacao" maxlength="2000"></label>
            </div>
            @if (ficha().responsavel && ehTermo()) {
              <div class="status alerta">O termo fica registrado com o responsável atual: <b>{{ ficha().responsavel!.nome }}</b>.</div>
            }
            @if (erroEnvio()) { <div class="status erro" role="alert">{{ erroEnvio() }}</div> }
            <div class="edicao-acoes">
              <button type="button" class="btn-secundario" (click)="limparEnvio()">Cancelar</button>
              <button type="submit" class="btn-primario" [disabled]="enviando()">{{ enviando() ? 'Enviando…' : 'Anexar' }}</button>
            </div>
          </form>
        }

        @if (erro()) { <div class="status erro" role="alert">{{ erro() }}</div> }

        <div class="subtitulo-secao">
          <p class="rotulo-secao">Anexados aqui</p>
          <label class="switch"><input type="checkbox" [checked]="removidos()" (change)="removidos.set($any($event.target).checked)">
            <span class="switch-ui"></span><span>Mostrar removidos</span></label>
        </div>
        <div class="doc-lista">
          @for (d of docs(); track d.id) {
            <div class="doc-item" [class.removido]="d.removidoEm">
              <span class="doc-icone"><inv-icone [nome]="d.categoria.startsWith('TERMO') ? 'termo' : 'documento'" /></span>
              <div>
                <div class="doc-titulo"><span class="selo-categoria">{{ d.categoriaRotulo }}</span>{{ d.titulo }}</div>
                <div class="doc-meta">
                  {{ d.nomeArquivo }} · {{ bytes(d.tamanhoBytes) }} · enviado por <b>{{ d.enviadoPor }}</b> em {{ dataHora(d.enviadoEm) }}
                  @if (d.responsavelNome) { · responsável na época: <b>{{ d.responsavelNome }}</b> }
                  @if (d.validade) { · válido até {{ data(d.validade) }} }
                  @if (d.observacao) { <br>{{ d.observacao }} }
                  @if (d.removidoEm) { <br>Removido por {{ d.removidoPor }} em {{ dataHora(d.removidoEm) }}: {{ d.motivoRemocao }} }
                </div>
                @if (removendo() === d.id) {
                  <form class="linha cols-2" (ngSubmit)="remover(d)">
                    <label>Motivo da remoção <input name="motivo" [(ngModel)]="motivo" required maxlength="500" autofocus></label>
                    <div class="edicao-acoes">
                      <button type="button" class="btn-secundario" (click)="removendo.set(null)">Cancelar</button>
                      <button type="submit" class="btn-primario">Remover</button>
                    </div>
                  </form>
                }
              </div>
              <div class="doc-acoes">
                <a [href]="api.urlDocumento(d.id)" target="_blank" rel="noopener" title="Abrir" aria-label="Abrir"><inv-icone nome="baixar" [tamanho]="16" /></a>
                @if (!d.removidoEm) {
                  <button type="button" class="perigo" title="Remover" aria-label="Remover" (click)="pedirRemocao(d.id)"><inv-icone nome="lixo" [tamanho]="16" /></button>
                }
              </div>
            </div>
          } @empty {
            <p class="doc-meta">Nenhum documento anexado aqui ainda.</p>
          }
        </div>

        <div class="subtitulo-secao"><p class="rotulo-secao">Já no GLPI</p></div>
        <div class="doc-lista">
          @for (d of ficha().documentosGlpi; track d.id) {
            <div class="doc-item">
              <span class="doc-icone"><inv-icone nome="documento" /></span>
              <div>
                <div class="doc-titulo">@if (d.categoria) { <span class="selo-categoria glpi">{{ d.categoria }}</span> }{{ d.nome || d.arquivo }}</div>
                <div class="doc-meta">{{ d.arquivo || d.link }} · vinculado em {{ dataHora(d.vinculadoEm) }}</div>
              </div>
              <div class="doc-acoes">
                @if (d.arquivo) {
                  <a [href]="api.urlDocumentoGlpi(ficha().id, d.id)" target="_blank" rel="noopener" title="Abrir" aria-label="Abrir"><inv-icone nome="baixar" [tamanho]="16" /></a>
                } @else if (d.link) {
                  <a [href]="d.link" target="_blank" rel="noopener noreferrer" title="Abrir link" aria-label="Abrir link"><inv-icone nome="externo" [tamanho]="16" /></a>
                }
              </div>
            </div>
          } @empty {
            <p class="doc-meta">Nenhum documento vinculado no GLPI.</p>
          }
        </div>
      </div>
    </section>
  `,
})
export class DocumentosFicha {
  protected readonly api = inject(Api);
  private readonly avisos = inject(Avisos);

  readonly ficha = input.required<Ficha>();

  protected readonly categorias = toSignal(this.api.categorias().pipe(catchError(() => of([]))), { initialValue: [] });
  protected readonly docs = signal<DocumentoLocal[]>([]);
  protected readonly removidos = signal(false);
  protected readonly erro = signal<string | null>(null);
  protected readonly arquivo = signal<File | null>(null);
  protected readonly arrastando = signal(false);
  protected readonly enviando = signal(false);
  protected readonly erroEnvio = signal<string | null>(null);
  protected readonly removendo = signal<number | null>(null);

  protected categoria = 'TERMO_RESPONSABILIDADE';
  protected titulo = '';
  protected validade = '';
  protected observacao = '';
  protected motivo = '';

  protected readonly bytes = bytes;
  protected readonly dataHora = dataHora;
  protected readonly data = data;

  constructor() {
    effect(() => this.carregar(this.ficha().id, this.removidos()));
  }

  protected ativos(): number {
    return this.docs().filter((d) => !d.removidoEm).length;
  }

  protected rotuloCategoria(): string {
    return this.categorias().find((c) => c.id === this.categoria)?.rotulo ?? '';
  }

  protected ehTermo(): boolean {
    return this.categoria.startsWith('TERMO');
  }

  private carregar(id: number, removidos: boolean): void {
    this.api.documentos('Computer', id, removidos).subscribe({
      next: (l) => { this.docs.set(l); this.erro.set(null); },
      error: (e) => this.erro.set(mensagemErro(e, 'Não foi possível listar os documentos.')),
    });
  }

  protected escolheu(lista: FileList | null): void {
    const f = lista?.[0];
    if (!f) return;
    this.erroEnvio.set(null);
    if (f.size > LIMITE_BYTES) {
      this.erro.set(`"${f.name}" passa do limite de 50 MB.`);
      return;
    }
    this.erro.set(null);
    this.arquivo.set(f);
  }

  protected soltou(ev: DragEvent): void {
    ev.preventDefault();
    this.arrastando.set(false);
    this.escolheu(ev.dataTransfer?.files ?? null);
  }

  protected limparEnvio(): void {
    this.arquivo.set(null);
    this.titulo = '';
    this.validade = '';
    this.observacao = '';
    this.erroEnvio.set(null);
  }

  protected enviar(): void {
    const f = this.arquivo();
    if (!f) return;
    const dados = new FormData();
    dados.append('arquivo', f, f.name);
    dados.append('categoria', this.categoria);
    if (this.titulo.trim()) dados.append('titulo', this.titulo.trim());
    if (this.validade) dados.append('validade', this.validade);
    if (this.observacao.trim()) dados.append('observacao', this.observacao.trim());
    this.enviando.set(true);
    this.api.anexar('Computer', this.ficha().id, dados).subscribe({
      next: () => {
        this.enviando.set(false);
        this.limparEnvio();
        this.avisos.toast('Documento anexado');
        this.carregar(this.ficha().id, this.removidos());
      },
      error: (e) => {
        this.enviando.set(false);
        this.erroEnvio.set(mensagemErro(e, 'Não foi possível anexar.'));
      },
    });
  }

  protected pedirRemocao(id: number): void {
    this.motivo = '';
    this.removendo.set(id);
  }

  protected remover(d: DocumentoLocal): void {
    if (!this.motivo.trim()) return;
    this.api.removerDocumento(d.id, this.motivo.trim()).subscribe({
      next: () => {
        this.removendo.set(null);
        this.avisos.toast('Documento removido (continua guardado)');
        this.carregar(this.ficha().id, this.removidos());
      },
      error: (e) => this.erro.set(mensagemErro(e, 'Não foi possível remover.')),
    });
  }
}
