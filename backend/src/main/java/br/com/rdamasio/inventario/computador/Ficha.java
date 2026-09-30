package br.com.rdamasio.inventario.computador;

import java.util.List;

/**
 * Ficha única de um computador: o que o GLPI espalha por várias abas, já montado para a tela (docs/PROJETO.md §2).
 * Tamanhos em MB, como o GLPI guarda; a tela converte para GB. Datas no formato do GLPI ("2026-09-30 08:12:03").
 *
 * @param dataModificacao {@code date_mod} lido junto com a ficha; volta na edição para detectar alteração
 *                        concorrente (EdicaoServico)
 * @param dinamico        veio do inventário (GLPI Agent): hardware é somente leitura e editar trava campos
 * @param documentosGlpi documentos já vinculados à máquina no GLPI (só leitura; os anexados por aqui ficam
 *                        no banco próprio, DocumentoController)
 * @param avisos          partes que não puderam ser lidas (ex.: sem permissão para ver componentes)
 */
public record Ficha(
        long id,
        String nome,
        String descricao,
        Ref status,
        Ref responsavel,
        String ultimoLogin,
        Ref local,
        Ref tecnico,
        Ref grupoTecnico,
        String fabricante,
        String modelo,
        String tipo,
        String serial,
        String patrimonio,
        String uuid,
        Memoria memoria,
        Armazenamento armazenamento,
        SistemaOperacional sistema,
        Rede rede,
        List<DocumentoGlpi> documentosGlpi,
        String ultimoInventario,
        String ultimoBoot,
        String criadoEm,
        String dataModificacao,
        boolean dinamico,
        String urlGlpi,
        List<String> avisos) {

    /** Valor de dropdown do GLPI: id (para editar) e nome (para mostrar). */
    public record Ref(Long id, String nome) {
    }

    public record Memoria(long totalMb, List<Modulo> modulos) {
    }

    /** Um pente de memória. {@code slot} é o busID informado pelo agente. */
    public record Modulo(String slot, long tamanhoMb, String tipo, String frequencia, String fabricante,
            String descricao, String serial) {
    }

    public record Armazenamento(long totalMb, List<Disco> discos, List<Volume> volumes) {
    }

    /**
     * Disco físico.
     *
     * @param tipo     "SSD", "HDD" ou null (não identificado)
     * @param inferido o tipo foi deduzido pelo nome do modelo, não informado pelo GLPI (RegraDisco)
     */
    public record Disco(String modelo, long capacidadeMb, String tipo, boolean inferido, String interfaceTipo,
            String fabricante, String serial) {
    }

    /** Partição/volume (C:, D:). */
    public record Volume(String nome, String ponto, long totalMb, long livreMb, String sistemaArquivos) {
    }

    public record SistemaOperacional(String nome, String versao, String edicao, String arquitetura, String kernel) {
    }

    public record Rede(String ipPrincipal, List<Interface> interfaces) {
    }

    public record Interface(String nome, String mac, List<String> ips) {
    }
}
