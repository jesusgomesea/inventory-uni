import { ChangeDetectionStrategy, Component, computed, effect, inject, input, numberAttribute, signal } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { RouterLink } from '@angular/router';
import { Api } from '../../core/api';
import { mensagemErro } from '../../core/avisos';
import { dataHora, slot, tamanho, usoPercentual } from '../../core/formato';
import { Ficha } from '../../core/modelos';
import { Faixa } from '../../layout/faixa';
import { Icone } from '../../layout/icone';
import { DetalhesFicha } from './detalhes';
import { DocumentosFicha } from './documentos';
import { EdicaoFicha } from './edicao';

/**
 * Ficha única do computador (docs/PROJETO.md §6): cabeçalho, fatos principais, três blocos (memória,
 * armazenamento, sistema e rede), documentos e o "ver mais". O hardware é só leitura; a edição cobre os campos
 * administrativos (EdicaoFicha).
 */
@Component({
  selector: 'inv-ficha',
  imports: [RouterLink, Faixa, Icone, EdicaoFicha, DocumentosFicha, DetalhesFicha],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (ficha(); as f) {
      <inv-faixa [sobretitulo]="'Computador · #' + f.id" [titulo]="f.nome || '(sem nome)'" [subtitulo]="f.descricao">
        <div class="ficha-topo">
          @if (f.status) { <span class="selo-status">{{ f.status.nome }}</span> }
          <button type="button" class="btn-acao" (click)="editando.set(!editando())" [attr.aria-expanded]="editando()">
            <inv-icone nome="editar" [tamanho]="14" /> {{ editando() ? 'Fechar edição' : 'Editar' }}
          </button>
          @if (f.urlGlpi) {
            <a class="btn-acao" [href]="f.urlGlpi" target="_blank" rel="noopener"><inv-icone nome="externo" [tamanho]="14" /> Abrir no GLPI</a>
          }
          <a class="ficha-voltar" routerLink="/">← Voltar para a lista</a>
        </div>
      </inv-faixa>
    } @else {
      <inv-faixa sobretitulo="Computador" [titulo]="erro() ? 'Não foi possível abrir' : 'Carregando…'" />
    }

    <main class="container">
      @if (erro()) {
        <div class="status erro" role="alert">{{ erro() }}</div>
        <a class="btn-link" routerLink="/">Voltar para a lista</a>
      } @else if (ficha(); as f) {
        @for (a of f.avisos; track a) { <div class="status alerta">{{ a }}</div> }

        @if (editando()) {
          <inv-edicao-ficha [ficha]="f" (salvo)="salvou($event)" (cancelar)="editando.set(false)" />
        }

        <dl class="ficha-fatos">
          <div><dt>Responsável</dt><dd>{{ f.responsavel?.nome || '—' }}</dd></div>
          <div><dt>Último login</dt>
            <dd [class.fraco]="!f.ultimoLogin">{{ f.ultimoLogin || 'não informado' }}</dd></div>
          <div><dt>Localização</dt><dd>{{ f.local?.nome || '—' }}</dd></div>
          <div><dt>Modelo</dt><dd>{{ juntar(f.fabricante, f.modelo) || '—' }}</dd></div>
          <div><dt>Serial</dt><dd class="mono">{{ f.serial || '—' }}</dd></div>
          <div><dt>Patrimônio</dt><dd class="mono">{{ f.patrimonio || '—' }}</dd></div>
        </dl>

        <div class="ficha-blocos">
          <section class="bloco" aria-labelledby="b-mem">
            <div class="bloco-rotulo" id="b-mem"><inv-icone nome="memoria" [tamanho]="15" /> Memória</div>
            <div class="bloco-numero">{{ tamanho(f.memoria.totalMb) }}
              <small>{{ f.memoria.modulos.length }} módulo{{ f.memoria.modulos.length === 1 ? '' : 's' }}</small></div>
            <ul class="bloco-linhas">
              @for (m of f.memoria.modulos; track $index) {
                <li><span>{{ slot(m.slot) }}</span>
                  <span class="mono">{{ tamanho(m.tamanhoMb) }}{{ m.tipo ? ' ' + m.tipo : '' }}{{ m.frequencia ? ' · ' + m.frequencia : '' }}</span></li>
              } @empty { <li><span>Nenhum módulo informado pelo inventário.</span></li> }
            </ul>
          </section>

          <section class="bloco" aria-labelledby="b-disco">
            <div class="bloco-rotulo" id="b-disco"><inv-icone nome="disco" [tamanho]="15" /> Armazenamento</div>
            <div class="bloco-numero">{{ tamanho(f.armazenamento.totalMb) }}
              @for (t of tiposDisco(); track t) {
                <span class="selo-disco" [class.ssd]="t === 'SSD'" [class.hdd]="t === 'HDD'" [class.nd]="t === '?'"
                  [title]="t === '?' ? 'Tipo não identificado' : t">{{ t }}</span>
              }
            </div>
            <ul class="bloco-linhas">
              @for (d of f.armazenamento.discos; track $index) {
                <li><span>{{ d.modelo || 'Disco' }}{{ d.interfaceTipo ? ' · ' + d.interfaceTipo : '' }}
                    @if (d.inferido) { <span class="inferido" title="O GLPI não informa o tipo; deduzido pelo nome do modelo">(tipo inferido)</span> }
                    @if (!d.tipo) { <span class="inferido">(tipo não identificado)</span> }</span>
                  <span class="mono">{{ tamanho(d.capacidadeMb) }}</span></li>
              } @empty { <li><span>Nenhum disco informado pelo inventário.</span></li> }
              @for (v of f.armazenamento.volumes; track $index) {
                <li><span>{{ v.ponto || v.nome }} · {{ usoPercentual(v.totalMb, v.livreMb) }}% usado
                    <span class="uso" [class.cheio]="usoPercentual(v.totalMb, v.livreMb) >= 90"><i [style.width.%]="usoPercentual(v.totalMb, v.livreMb)"></i></span></span>
                  <span class="mono">{{ tamanho(v.livreMb) }} livres</span></li>
              }
            </ul>
          </section>

          <section class="bloco" aria-labelledby="b-so">
            <div class="bloco-rotulo" id="b-so"><inv-icone nome="sistema" [tamanho]="15" /> Sistema e rede</div>
            <div class="bloco-titulo">{{ f.sistema?.nome || 'Sistema não informado' }}</div>
            <div class="bloco-sub mono">
              {{ juntarCom(' · ', f.sistema?.versao, f.sistema?.kernel ? 'build ' + f.sistema.kernel : null, f.sistema?.arquitetura) }}
            </div>
            <div class="bloco-ip">{{ f.rede.ipPrincipal || 'IP não informado' }}</div>
            <ul class="bloco-linhas com-espaco">
              @for (i of f.rede.interfaces; track $index) {
                <li><span>{{ i.nome || 'Interface' }}</span><span class="mono">{{ i.mac || '' }}</span></li>
              }
            </ul>
          </section>
        </div>
        <p class="ficha-rodape">
          Último inventário do agente: {{ dataHora(f.ultimoInventario) }}
          @if (f.dinamico) { · Hardware, sistema e IP vêm do GLPI Agent e são só leitura }
        </p>

        <inv-documentos-ficha [ficha]="f" />
        <inv-detalhes-ficha [ficha]="f" />
      } @else {
        <div class="esqueleto"><span class="girando"></span> Juntando os dados do GLPI…</div>
      }
    </main>
  `,
})
export class FichaPage {
  private readonly api = inject(Api);
  private readonly titulo = inject(Title);

  readonly id = input.required({ transform: numberAttribute });

  protected readonly ficha = signal<Ficha | null>(null);
  protected readonly erro = signal<string | null>(null);
  protected readonly editando = signal(false);

  /** Selos do cabeçalho do bloco: um por tipo presente ("?" = não identificado). */
  protected readonly tiposDisco = computed(() => {
    const discos = this.ficha()?.armazenamento.discos ?? [];
    return [...new Set(discos.map((d) => d.tipo ?? '?'))];
  });

  protected readonly tamanho = tamanho;
  protected readonly slot = slot;
  protected readonly dataHora = dataHora;
  protected readonly usoPercentual = usoPercentual;

  constructor() {
    effect(() => {
      const id = this.id();
      this.ficha.set(null);
      this.erro.set(null);
      this.editando.set(false);
      this.api.ficha(id).subscribe({
        next: (f) => {
          this.ficha.set(f);
          this.titulo.setTitle(`${f.nome ?? 'Computador'} — Inventário`);
        },
        error: (e) => this.erro.set(mensagemErro(e, 'Não foi possível ler este computador no GLPI.')),
      });
    });
  }

  protected salvou(f: Ficha): void {
    this.ficha.set(f);
    this.editando.set(false);
  }

  protected juntar(a: string | null, b: string | null): string {
    return [a, b].filter(Boolean).join(' ');
  }

  protected juntarCom(sep: string, ...partes: (string | null | undefined)[]): string {
    return partes.filter(Boolean).join(sep);
  }
}
