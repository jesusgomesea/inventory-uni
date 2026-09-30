import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, signal } from '@angular/core';
import { Problema } from './modelos';

export interface Toast {
  id: number;
  mensagem: string;
  icone: string;
}

/** Toasts no canto da tela (salvo, anexado...). */
@Injectable({ providedIn: 'root' })
export class Avisos {
  readonly toasts = signal<Toast[]>([]);
  private seq = 0;

  toast(mensagem: string, icone = '✓'): void {
    const t = { id: ++this.seq, mensagem, icone };
    this.toasts.update((l) => [...l, t]);
    setTimeout(() => this.toasts.update((l) => l.filter((x) => x.id !== t.id)), 2600);
  }
}

/**
 * Mensagem de erro para mostrar ao técnico. Usa o texto do backend (já em português) e acrescenta o código da
 * requisição, que liga a reclamação à linha de log.
 */
export function mensagemErro(e: unknown, padrao = 'Não foi possível concluir.'): string {
  if (e instanceof HttpErrorResponse) {
    if (e.status === 0) return 'Sem conexão com o servidor do inventário.';
    const p = e.error as Partial<Problema> | null;
    const texto = p?.detail || padrao;
    return p?.idRequisicao ? `${texto} (código ${p.idRequisicao.slice(0, 8)})` : texto;
  }
  return padrao;
}

/** Código estável do erro (ex.: ALTERADO_POR_OUTRO), para a tela decidir o que fazer. */
export function codigoErro(e: unknown): string | undefined {
  return e instanceof HttpErrorResponse ? (e.error as Partial<Problema> | null)?.codigo : undefined;
}
