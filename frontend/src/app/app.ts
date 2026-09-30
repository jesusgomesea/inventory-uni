import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { MarcaService } from './core/marca';
import { Cabecalho } from './layout/cabecalho';
import { Toasts } from './layout/toasts';

/**
 * Casca da aplicação: cabeçalho, página da rota, rodapé e toasts.
 * Cada página desenha a própria faixa de abertura (inv-faixa) e o seu <main class="container">.
 */
@Component({
  selector: 'inv-root',
  imports: [RouterOutlet, Cabecalho, Toasts],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <inv-cabecalho />
    <router-outlet />
    <footer class="rodape">
      <img [src]="marca.info().logoBranca" [class.branca]="marca.info().filtrarParaBranco" alt="">
      <span>Inventário · TI R Damásio · dados do GLPI</span>
    </footer>
    <inv-toasts />
  `,
})
export class App {
  protected readonly marca = inject(MarcaService);
}
