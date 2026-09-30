import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * Abertura de cada página no padrão "hero" do DS: sobretítulo em caixa-alta com o traço vermelho, título grande
 * e texto de apoio. Conteúdo extra (botões da ficha) entra por projeção.
 */
@Component({
  selector: 'inv-faixa',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="faixa">
      <div class="sobretitulo">{{ sobretitulo() }}</div>
      <h1>{{ titulo() }}</h1>
      @if (subtitulo()) { <p>{{ subtitulo() }}</p> }
      <ng-content />
    </div>
  `,
})
export class Faixa {
  readonly titulo = input.required<string>();
  readonly subtitulo = input<string | null>();
  readonly sobretitulo = input('Inventário');
}
