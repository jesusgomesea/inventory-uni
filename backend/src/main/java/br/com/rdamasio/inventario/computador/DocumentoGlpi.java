package br.com.rdamasio.inventario.computador;

/**
 * Documento vinculado à máquina dentro do GLPI (aba Documentos). Só leitura nesta aplicação: dá para ver e baixar.
 * Documentos e termos novos vão para o banco próprio (pacote documento).
 *
 * @param categoria rubrica do GLPI ("headings" na API)
 * @param link      endereço externo, quando o documento do GLPI é só um link
 */
public record DocumentoGlpi(long id, String nome, String arquivo, String mime, String categoria, String link,
        String vinculadoEm) {
}
