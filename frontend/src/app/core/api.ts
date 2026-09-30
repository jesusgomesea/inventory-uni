import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  Alteracao, Categoria, Detalhes, DocumentoLocal, Ficha, ItemLista, Opcao, Pagina, Usuario, UsuarioGlpi,
} from './modelos';

/** Filtros da lista de computadores (os mesmos que ficam no endereço da página). */
export interface FiltroLista {
  q?: string;
  status?: number | null;
  local?: number | null;
  pagina?: number;
}

/**
 * Todas as chamadas ao backend. Caminhos relativos (/api): o proxy do ng serve encaminha para o backend,
 * e o interceptador-api.ts põe os cabeçalhos de toda chamada.
 */
@Injectable({ providedIn: 'root' })
export class Api {
  private readonly http = inject(HttpClient);

  // ---- sessão
  entrar(dados: { login?: string; senha?: string; tokenPessoal?: string }): Observable<Usuario> {
    return this.http.post<Usuario>('/api/sessao', dados);
  }

  sessao(): Observable<Usuario> {
    return this.http.get<Usuario>('/api/sessao');
  }

  sair(): Observable<void> {
    return this.http.delete<void>('/api/sessao');
  }

  // ---- computadores
  computadores(f: FiltroLista): Observable<Pagina<ItemLista>> {
    let p = new HttpParams().set('pagina', f.pagina ?? 0);
    if (f.q) p = p.set('q', f.q);
    if (f.status) p = p.set('status', f.status);
    if (f.local) p = p.set('local', f.local);
    return this.http.get<Pagina<ItemLista>>('/api/computadores', { params: p });
  }

  ficha(id: number): Observable<Ficha> {
    return this.http.get<Ficha>(`/api/computadores/${id}`);
  }

  editar(id: number, a: Alteracao): Observable<Ficha> {
    return this.http.put<Ficha>(`/api/computadores/${id}`, a);
  }

  detalhes(id: number): Observable<Detalhes> {
    return this.http.get<Detalhes>(`/api/computadores/${id}/detalhes`);
  }

  /** Endereço para abrir/baixar um documento do GLPI (link direto: o navegador manda o cookie da sessão). */
  urlDocumentoGlpi(computadorId: number, documentoId: number): string {
    return `/api/computadores/${computadorId}/documentos-glpi/${documentoId}`;
  }

  // ---- opções dos campos
  status(): Observable<Opcao[]> {
    return this.http.get<Opcao[]>('/api/opcoes/status');
  }

  locais(): Observable<Opcao[]> {
    return this.http.get<Opcao[]>('/api/opcoes/locais');
  }

  grupos(): Observable<Opcao[]> {
    return this.http.get<Opcao[]>('/api/opcoes/grupos');
  }

  usuarios(q: string): Observable<UsuarioGlpi[]> {
    return this.http.get<UsuarioGlpi[]>('/api/opcoes/usuarios', { params: { q } });
  }

  // ---- documentos do banco próprio
  categorias(): Observable<Categoria[]> {
    return this.http.get<Categoria[]>('/api/documentos/categorias');
  }

  documentos(tipo: string, itemId: number, removidos: boolean): Observable<DocumentoLocal[]> {
    return this.http.get<DocumentoLocal[]>(`/api/equipamentos/${tipo}/${itemId}/documentos`, { params: { removidos } });
  }

  anexar(tipo: string, itemId: number, dados: FormData): Observable<DocumentoLocal> {
    return this.http.post<DocumentoLocal>(`/api/equipamentos/${tipo}/${itemId}/documentos`, dados);
  }

  urlDocumento(id: number): string {
    return `/api/documentos/${id}/arquivo`;
  }

  removerDocumento(id: number, motivo: string): Observable<DocumentoLocal> {
    return this.http.delete<DocumentoLocal>(`/api/documentos/${id}`, { params: { motivo } });
  }
}
