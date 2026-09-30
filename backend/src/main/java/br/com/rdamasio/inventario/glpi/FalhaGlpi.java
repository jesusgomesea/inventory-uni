package br.com.rdamasio.inventario.glpi;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Erro devolvido pelo GLPI (ou GLPI fora do ar). A API legada responde erro como
 * {@code ["ERROR_SESSION_TOKEN_INVALID", "mensagem"]}; o primeiro item é o código, estável entre versões,
 * e é por ele que o {@code TratadorErros} decide a mensagem e o status para a tela.
 */
public class FalhaGlpi extends RuntimeException {

    public static final String SEM_RESPOSTA = "SEM_RESPOSTA";

    private final int status;
    private final String codigo;

    public FalhaGlpi(int status, String codigo, String mensagem) {
        super(mensagem);
        this.status = status;
        this.codigo = codigo == null ? "" : codigo;
    }

    static FalhaGlpi deResposta(int status, byte[] corpo, JsonMapper json) {
        String codigo = "";
        String mensagem = "";
        try {
            JsonNode raiz = json.readTree(corpo);
            if (raiz.isArray() && raiz.size() > 0) {
                codigo = raiz.get(0).asString();
                if (raiz.size() > 1) mensagem = raiz.get(1).asString();
            } else if (raiz.isObject()) {
                mensagem = raiz.toString();
            }
        } catch (RuntimeException e) {
            mensagem = new String(corpo, java.nio.charset.StandardCharsets.UTF_8);
        }
        if (mensagem.length() > 300) mensagem = mensagem.substring(0, 300) + "…";
        return new FalhaGlpi(status, codigo, mensagem);
    }

    public int status() {
        return status;
    }

    public String codigo() {
        return codigo;
    }

    public boolean sessaoInvalida() {
        return codigo.startsWith("ERROR_SESSION_TOKEN");
    }

    public boolean naoEncontrado() {
        return status == 404 || codigo.equals("ERROR_ITEM_NOT_FOUND");
    }
}
