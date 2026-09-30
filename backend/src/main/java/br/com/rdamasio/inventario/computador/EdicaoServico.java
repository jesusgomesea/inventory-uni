package br.com.rdamasio.inventario.computador;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import br.com.rdamasio.inventario.comum.ErroNegocio;
import br.com.rdamasio.inventario.comum.Json;
import br.com.rdamasio.inventario.glpi.FalhaGlpi;
import br.com.rdamasio.inventario.glpi.GlpiCliente;
import tools.jackson.databind.JsonNode;

/**
 * Edição dos campos administrativos de um computador (docs/PROJETO.md §5).
 *
 * <p>Só estes campos são editáveis. Hardware, sistema, IP, serial e modelo vêm do GLPI Agent e o próximo
 * inventário desfaria a mudança. Nos campos abaixo, em máquinas do inventário, o GLPI 10 cria um "campo bloqueado"
 * ao editar à mão, para o agente não sobrescrever — é o comportamento desejado.
 *
 * <p>Controle de concorrência: a tela devolve o {@code date_mod} que leu. Se no GLPI ele já for outro, alguém
 * mudou a máquina no meio da edição e recusamos (409) em vez de sobrescrever calado. Só os campos que mudaram
 * são enviados ao GLPI.
 */
@Service
public class EdicaoServico {

    private static final Logger log = LoggerFactory.getLogger(EdicaoServico.class);

    /**
     * Campos alterados. Null = não mexer. Nos ids, 0 = "nenhum" (limpar o campo), como no GLPI.
     *
     * @param dataModificacao o {@code date_mod} da ficha que o técnico estava vendo
     */
    public record Alteracao(String dataModificacao, String nome, String descricao, String patrimonio, Long statusId,
            Long responsavelId, Long localId, Long tecnicoId, Long grupoTecnicoId) {
    }

    private final GlpiCliente glpi;

    public EdicaoServico(GlpiCliente glpi) {
        this.glpi = glpi;
    }

    public void salvar(String token, long id, Alteracao a) {
        Map<String, Object> campos = new LinkedHashMap<>();
        if (a.nome() != null) {
            String nome = a.nome().trim();
            if (nome.isEmpty()) throw ErroNegocio.invalido("O nome não pode ficar vazio.");
            if (nome.length() > 255) throw ErroNegocio.invalido("O nome passa de 255 caracteres.");
            campos.put("name", nome);
        }
        if (a.descricao() != null) campos.put("comment", a.descricao().trim());
        if (a.patrimonio() != null) {
            if (a.patrimonio().trim().length() > 255) throw ErroNegocio.invalido("O patrimônio passa de 255 caracteres.");
            campos.put("otherserial", a.patrimonio().trim());
        }
        colocarId(campos, "states_id", a.statusId());
        colocarId(campos, "users_id", a.responsavelId());
        colocarId(campos, "locations_id", a.localId());
        colocarId(campos, "users_id_tech", a.tecnicoId());
        colocarId(campos, "groups_id_tech", a.grupoTecnicoId());
        if (campos.isEmpty()) return;

        JsonNode atual;
        try {
            atual = glpi.get(token, "/Computer/" + id, null);
        } catch (FalhaGlpi e) {
            if (e.naoEncontrado()) throw ErroNegocio.naoEncontrado("Computador " + id + " não encontrado no GLPI.");
            throw e;
        }
        String dataAtual = Json.texto(atual, "date_mod");
        if (a.dataModificacao() != null && !Objects.equals(a.dataModificacao(), dataAtual)) {
            throw new ErroNegocio(HttpStatus.CONFLICT, "ALTERADO_POR_OUTRO",
                    "Esta máquina foi alterada no GLPI enquanto você editava (" + dataAtual
                            + "). Recarregue a ficha e refaça a alteração.");
        }
        // não manda o que já está igual: evita histórico e bloqueios de campo à toa no GLPI
        campos.entrySet().removeIf(e -> Objects.equals(String.valueOf(e.getValue()), naoNulo(Json.texto(atual, e.getKey()), e.getKey())));
        if (campos.isEmpty()) return;

        JsonNode r = glpi.put(token, "/Computer/" + id, Map.of("input", campos));
        verificarResposta(r, id);
        log.info("Computador {} alterado: {}", id, campos.keySet());
    }

    /** O PUT responde {@code [{"5": true, "message": ""}]}; {@code false} = o GLPI recusou (regra, direito...). */
    private static void verificarResposta(JsonNode r, long id) {
        for (JsonNode item : Json.itens(r.isArray() ? r : null)) {
            JsonNode ok = item.get(String.valueOf(id));
            if (ok != null && !ok.asBoolean(true)) {
                String msg = Json.texto(item, "message");
                throw new ErroNegocio(HttpStatus.UNPROCESSABLE_CONTENT, "GLPI_RECUSOU",
                        "O GLPI não aceitou a alteração" + (msg == null ? "." : ": " + msg));
            }
        }
    }

    private static void colocarId(Map<String, Object> campos, String campo, Long valor) {
        if (valor == null) return;
        if (valor < 0) throw ErroNegocio.invalido("Valor inválido em " + campo + ".");
        campos.put(campo, valor);
    }

    /** Valor atual para comparar: ids vazios valem "0" no GLPI; textos vazios valem "". */
    private static String naoNulo(String atual, String campo) {
        if (atual != null) return atual;
        return campo.endsWith("_id") || campo.endsWith("_tech") ? "0" : "";
    }
}
