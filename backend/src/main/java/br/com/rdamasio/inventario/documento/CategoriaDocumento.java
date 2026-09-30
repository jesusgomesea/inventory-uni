package br.com.rdamasio.inventario.documento;

/**
 * Tipos de documento que se anexam a um equipamento. Gravado pelo nome (coluna {@code categoria}): para incluir
 * um tipo novo basta acrescentar aqui; renomear um existente exige migration que atualize as linhas antigas.
 */
public enum CategoriaDocumento {
    TERMO_RESPONSABILIDADE("Termo de responsabilidade"),
    TERMO_DEVOLUCAO("Termo de devolução"),
    NOTA_FISCAL("Nota fiscal"),
    GARANTIA("Garantia"),
    LAUDO_TECNICO("Laudo técnico"),
    CONTRATO("Contrato"),
    FOTO("Foto"),
    OUTRO("Outro");

    private final String rotulo;

    CategoriaDocumento(String rotulo) {
        this.rotulo = rotulo;
    }

    public String rotulo() {
        return rotulo;
    }
}
