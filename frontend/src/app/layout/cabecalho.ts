import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { MARCAS, MarcaService } from '../core/marca';
import { Sessao } from '../core/sessao';
import { Icone } from './icone';

/** Cabeçalho fixo do DS: logo da marca, navegação, técnico logado e seletor de visual (Damásio × TD). */
@Component({
  selector: 'inv-cabecalho',
  imports: [RouterLink, RouterLinkActive, Icone],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="header">
      <a class="logo" routerLink="/" aria-label="Início">
        <img [src]="marca.info().logoBranca" [class.branca]="marca.info().filtrarParaBranco" [alt]="marca.info().nome">
        <span class="logo-sep"></span>
        <span class="logo-sub">Inventário · GLPI</span>
      </a>
      <nav class="nav">
        @if (sessao.usuario()) {
          <a routerLink="/" routerLinkActive="ativo" [routerLinkActiveOptions]="{ exact: false }" title="Computadores" aria-label="Computadores">
            <inv-icone nome="computador" [tamanho]="15" /><span>Computadores</span>
          </a>
        }
      </nav>
      @if (sessao.usuario(); as u) {
        <!-- login em espera: sem nome nem "Sair" (é sempre a conta de serviço) -->
        @if (u.loginHabilitado) {
        <div class="usuario-topo">
          <span>{{ u.nome }}</span>
          <button type="button" (click)="sessao.sair()" title="Sair">Sair</button>
        </div>
        }
      }
      <div class="seletor-marca" role="radiogroup" aria-label="Visual">
        @for (m of marcas; track m.id) {
          <button role="radio" [attr.aria-checked]="marca.marca() === m.id" [class.ativo]="marca.marca() === m.id"
            (click)="marca.definir(m.id)" [title]="'Visual ' + m.nome">
            <img [src]="m.logoBranca" [class.branca]="m.filtrarParaBranco" [alt]="m.nome">
          </button>
        }
      </div>
    </header>
  `,
})
export class Cabecalho {
  protected readonly marca = inject(MarcaService);
  protected readonly sessao = inject(Sessao);
  protected readonly marcas = Object.values(MARCAS);
}
