/**
 * Formato das respostas do backend (espelho dos records Java). Tamanhos em MB, como o GLPI guarda;
 * datas do GLPI como texto "2026-09-30 08:12:03"; datas do banco próprio em ISO ("2026-09-30T20:17:42").
 */

export interface Usuario {
  id: number;
  login: string;
  nome: string;
  perfil: string | null;
  /** Endereço das telas do GLPI, para "Abrir no GLPI". */
  urlGlpi: string;
  /** false = login em espera: todos usam a conta de serviço; a tela esconde "Sair" e a página de login. */
  loginHabilitado: boolean;
}

export interface ItemLista {
  id: number;
  nome: string | null;
  status: string | null;
  responsavel: string | null;
  ultimoLogin: string | null;
  local: string | null;
  fabricante: string | null;
  modelo: string | null;
  serial: string | null;
  patrimonio: string | null;
  sistema: string | null;
  ip: string | null;
  modificadoEm: string | null;
}

export interface Pagina<T> {
  total: number;
  pagina: number;
  tamanho: number;
  itens: T[];
}

export interface Ref {
  id: number;
  nome: string;
}

export interface Modulo {
  slot: string | null;
  tamanhoMb: number;
  tipo: string | null;
  frequencia: string | null;
  fabricante: string | null;
  descricao: string | null;
  serial: string | null;
}

export interface Disco {
  modelo: string | null;
  capacidadeMb: number;
  /** "SSD", "HDD" ou null (não identificado). */
  tipo: 'SSD' | 'HDD' | null;
  /** O tipo foi deduzido pelo nome do modelo (regra no backend, RegraDisco). */
  inferido: boolean;
  interfaceTipo: string | null;
  fabricante: string | null;
  serial: string | null;
}

export interface Volume {
  nome: string | null;
  ponto: string | null;
  totalMb: number;
  livreMb: number;
  sistemaArquivos: string | null;
}

export interface DocumentoGlpi {
  id: number;
  nome: string | null;
  arquivo: string | null;
  mime: string | null;
  categoria: string | null;
  link: string | null;
  vinculadoEm: string | null;
}

export interface Ficha {
  id: number;
  nome: string | null;
  descricao: string | null;
  status: Ref | null;
  responsavel: Ref | null;
  ultimoLogin: string | null;
  local: Ref | null;
  tecnico: Ref | null;
  grupoTecnico: Ref | null;
  fabricante: string | null;
  modelo: string | null;
  tipo: string | null;
  serial: string | null;
  patrimonio: string | null;
  uuid: string | null;
  memoria: { totalMb: number; modulos: Modulo[] };
  armazenamento: { totalMb: number; discos: Disco[]; volumes: Volume[] };
  sistema: { nome: string | null; versao: string | null; edicao: string | null; arquitetura: string | null; kernel: string | null } | null;
  rede: { ipPrincipal: string | null; interfaces: { nome: string | null; mac: string | null; ips: string[] }[] };
  documentosGlpi: DocumentoGlpi[];
  ultimoInventario: string | null;
  ultimoBoot: string | null;
  criadoEm: string | null;
  /** date_mod do GLPI; volta na edição para detectar alteração feita por outra pessoa. */
  dataModificacao: string | null;
  /** Veio do inventário (GLPI Agent): hardware só leitura, e editar trava o campo no GLPI. */
  dinamico: boolean;
  urlGlpi: string | null;
  avisos: string[];
}

/** Campos alterados na edição. Ausente = não mexer; nos ids, 0 = nenhum. */
export interface Alteracao {
  dataModificacao: string | null;
  nome?: string;
  descricao?: string;
  patrimonio?: string;
  statusId?: number;
  responsavelId?: number;
  localId?: number;
  tecnicoId?: number;
  grupoTecnicoId?: number;
}

export interface Opcao {
  id: number;
  nome: string;
}

export interface UsuarioGlpi {
  id: number;
  nome: string;
  login: string;
}

export interface Secao<T> {
  dados: T | null;
  erro: string | null;
}

export interface Detalhes {
  componentes: Secao<{ tipo: string; nome: string | null; fabricante: string | null; detalhes: { rotulo: string; valor: string }[] }[]>;
  softwares: Secao<{ nome: string; versao: string | null }[]>;
  antivirus: Secao<{ nome: string | null; versao: string | null; assinatura: string | null; ativo: boolean; atualizado: boolean; validade: string | null }[]>;
  conectados: Secao<{ tipo: string; nome: string | null; modelo: string | null; serial: string | null }[]>;
  financeiro: Secao<{
    compra: string | null; inicioUso: string | null; inicioGarantia: string | null; mesesGarantia: number | null;
    fimGarantia: string | null; valor: string | null; fornecedor: string | null; pedido: string | null; notaFiscal: string | null;
  }>;
  chamados: Secao<{ id: number; titulo: string | null; status: string | null; abertoEm: string | null }[]>;
  historico: Secao<{ data: string | null; usuario: string | null; descricao: string }[]>;
}

/** Documento anexado por esta aplicação (banco próprio). */
export interface DocumentoLocal {
  id: number;
  categoria: string;
  categoriaRotulo: string;
  titulo: string;
  observacao: string | null;
  validade: string | null;
  nomeArquivo: string;
  tipoConteudo: string;
  tamanhoBytes: number;
  sha256: string;
  /** Responsável da máquina no momento do envio (num termo, quem assinou). */
  responsavelNome: string | null;
  enviadoPor: string;
  enviadoEm: string;
  removidoEm: string | null;
  removidoPor: string | null;
  motivoRemocao: string | null;
}

export interface Categoria {
  id: string;
  rotulo: string;
}

/** Erro do backend (ProblemDetail). {@code codigo} é estável; {@code idRequisicao} vai para o suporte. */
export interface Problema {
  status: number;
  detail: string;
  codigo?: string;
  idRequisicao?: string;
}
