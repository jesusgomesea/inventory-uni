import { Injectable, computed, signal } from '@angular/core';

export type Marca = 'damasio' | 'td';

export interface InfoMarca {
  id: Marca;
  nome: string;
  /** Versão branca, para fundo escuro (cabeçalho, rodapé). */
  logoBranca: string;
  /** A logo em branco da Damásio é gerada por filtro CSS; a da TD já vem em branco. */
  filtrarParaBranco: boolean;
}

export const MARCAS: Record<Marca, InfoMarca> = {
  damasio: { id: 'damasio', nome: 'Damásio Motopeças', logoBranca: 'logo-damasio.png', filtrarParaBranco: true },
  td: { id: 'td', nome: 'TD Motopeças', logoBranca: 'logo-td-branco.png', filtrarParaBranco: false },
};

const CHAVE = 'inventario-marca';

/**
 * Visual ativo (Damásio × TD), igual ao HELP-AGENT. É preferência de cada pessoa, por isso fica no navegador;
 * o script inline do index.html aplica a mesma chave antes da primeira pintura.
 */
@Injectable({ providedIn: 'root' })
export class MarcaService {
  readonly marca = signal<Marca>(lerSalva());
  readonly info = computed(() => MARCAS[this.marca()]);

  definir(m: Marca): void {
    this.marca.set(m);
    if (m === 'td') document.documentElement.dataset['marca'] = 'td';
    else delete document.documentElement.dataset['marca'];
    try {
      localStorage.setItem(CHAVE, m);
    } catch {
      // navegador sem storage (janela privada etc.): vale só para esta visita
    }
  }
}

function lerSalva(): Marca {
  try {
    return localStorage.getItem(CHAVE) === 'td' ? 'td' : 'damasio';
  } catch {
    return 'damasio';
  }
}
