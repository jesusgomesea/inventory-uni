import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/** Ícones de traço (mesmo estilo do HELP-AGENT). Para um novo: um caminho SVG 24×24 nesta lista. */
const ICONES = {
  computador: 'M4 5h16v10H4zM9 19h6M12 15v4',
  memoria: 'M4 8h16v8H4zM8 8v8M12 8v8M16 8v8M6 16v3M18 16v3',
  disco: 'M4 6h16v12H4zM8 14h.01M12 14h5',
  rede: 'M12 20h.01M8.5 16.5a5 5 0 0 1 7 0M5 13a10 10 0 0 1 14 0M2 9.5a15 15 0 0 1 20 0',
  documento: 'M14 3H6a1 1 0 0 0-1 1v16a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1V8l-5-5ZM14 3v5h5M9 13h6M9 17h6',
  termo: 'M14 3H6a1 1 0 0 0-1 1v16a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1V8l-5-5ZM14 3v5h5M8 17c1.5-2 2.5-2 3.5 0s2 2 3.5 0',
  enviar: 'M12 20V9M7 14l5-5 5 5M5 4h14',
  baixar: 'M12 4v11M7 10l5 5 5-5M5 20h14',
  editar: 'M4 20h4L19 9l-4-4L4 16v4ZM13.5 6.5l4 4',
  externo: 'M14 4h6v6M20 4l-9 9M18 14v5a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1h5',
  lixo: 'M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13',
  busca: 'M11 4a7 7 0 1 0 0 14 7 7 0 0 0 0-14ZM20 20l-4-4',
  alerta: 'M12 3 2 20h20L12 3ZM12 10v4M12 17h.01',
  sistema: 'M4 4h7v7H4zM13 4h7v7h-7zM4 13h7v7H4zM13 13h7v7h-7z',
  fechar: 'M6 6l12 12M18 6 6 18',
  voltar: 'M19 12H5M11 6l-6 6 6 6',
} as const;

export type NomeIcone = keyof typeof ICONES;

@Component({
  selector: 'inv-icone',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'icone-svg', 'aria-hidden': 'true' },
  template: `
    <svg viewBox="0 0 24 24" [attr.width]="tamanho()" [attr.height]="tamanho()" fill="none" stroke="currentColor"
      stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path [attr.d]="d()" /></svg>
  `,
})
export class Icone {
  readonly nome = input.required<NomeIcone>();
  readonly tamanho = input(18);
  protected readonly d = computed(() => ICONES[this.nome()]);
}
