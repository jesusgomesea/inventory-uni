package br.com.rdamasio.inventario.glpi;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import br.com.rdamasio.inventario.comum.Json;
import tools.jackson.databind.JsonNode;

/**
 * Números das colunas da busca do GLPI ({@code search/Computer?forcedisplay[]=31}).
 *
 * <p><b>Armadilha:</b> esses números podem mudar com plugins e versões. Em vez de confiar neles cegamente, cada
 * {@link Campo} diz qual tabela e coluna do banco espera; na primeira consulta lemos {@code listSearchOptions} e
 * conferimos. Se o número padrão não bater, procuramos outro com a mesma tabela/coluna e registramos um aviso
 * no log. O resultado fica em cache até o backend reiniciar (as opções só mudam com atualização do GLPI).
 */
@Component
public class OpcoesBusca {

    private static final Logger log = LoggerFactory.getLogger(OpcoesBusca.class);

    /** Colunas usadas pela aplicação, com o número padrão do GLPI 10 e a tabela/coluna esperada. */
    public enum Campo {
        COMPUTADOR_NOME("Computer", 1, "glpi_computers", "name"),
        COMPUTADOR_ID("Computer", 2, "glpi_computers", "id"),
        COMPUTADOR_LOCAL("Computer", 3, "glpi_locations", "completename"),
        COMPUTADOR_SERIAL("Computer", 5, "glpi_computers", "serial"),
        COMPUTADOR_PATRIMONIO("Computer", 6, "glpi_computers", "otherserial"),
        COMPUTADOR_CONTATO("Computer", 7, "glpi_computers", "contact"),
        COMPUTADOR_MODIFICADO("Computer", 19, "glpi_computers", "date_mod"),
        COMPUTADOR_FABRICANTE("Computer", 23, "glpi_manufacturers", "name"),
        COMPUTADOR_STATUS("Computer", 31, "glpi_states", "completename"),
        COMPUTADOR_MODELO("Computer", 40, "glpi_computermodels", "name"),
        COMPUTADOR_SO("Computer", 45, "glpi_operatingsystems", "name"),
        COMPUTADOR_SO_VERSAO("Computer", 46, "glpi_operatingsystemversions", "name"),
        COMPUTADOR_USUARIO("Computer", 70, "glpi_users", "name"),
        COMPUTADOR_IP("Computer", 126, "glpi_ipaddresses", "name"),
        USUARIO_LOGIN("User", 1, "glpi_users", "name"),
        USUARIO_ID("User", 2, "glpi_users", "id"),
        USUARIO_ATIVO("User", 8, "glpi_users", "is_active"),
        USUARIO_NOME("User", 9, "glpi_users", "firstname"),
        USUARIO_SOBRENOME("User", 34, "glpi_users", "realname");

        final String tipo;
        final int padrao;
        final String tabela;
        final String coluna;

        Campo(String tipo, int padrao, String tabela, String coluna) {
            this.tipo = tipo;
            this.padrao = padrao;
            this.tabela = tabela;
            this.coluna = coluna;
        }
    }

    private record Opcao(String nome, String tabela, String coluna) {
    }

    private final GlpiCliente glpi;
    private final Map<String, Map<Integer, Opcao>> opcoes = new ConcurrentHashMap<>();
    private final Map<Campo, Integer> resolvidos = new ConcurrentHashMap<>();

    public OpcoesBusca(GlpiCliente glpi) {
        this.glpi = glpi;
    }

    /** Número da coluna no GLPI em uso. */
    public int id(String token, Campo campo) {
        Integer r = resolvidos.get(campo);
        if (r != null) return r;
        Map<Integer, Opcao> mapa = carregar(token, campo.tipo);
        int escolhido = resolver(campo, mapa);
        resolvidos.put(campo, escolhido);
        return escolhido;
    }

    /** Nome da coluna (ex.: "Localização"), para descrever o histórico. Null se não souber. */
    public String nome(String token, String tipo, int id) {
        Opcao o = carregar(token, tipo).get(id);
        return o == null ? null : o.nome();
    }

    private int resolver(Campo campo, Map<Integer, Opcao> mapa) {
        if (mapa.isEmpty()) return campo.padrao; // não conseguimos ler: fica com o padrão
        Opcao padrao = mapa.get(campo.padrao);
        if (padrao != null && confere(padrao, campo)) return campo.padrao;
        for (Map.Entry<Integer, Opcao> e : mapa.entrySet()) {
            if (confere(e.getValue(), campo)) {
                log.warn("[GLPI] coluna {} não é a {} neste GLPI; usando a {} ({})", campo, campo.padrao, e.getKey(), e.getValue().nome());
                return e.getKey();
            }
        }
        log.warn("[GLPI] coluna {} ({}.{}) não encontrada em listSearchOptions/{}; usando o padrão {}",
                campo, campo.tabela, campo.coluna, campo.tipo, campo.padrao);
        return campo.padrao;
    }

    private static boolean confere(Opcao o, Campo c) {
        return c.tabela.equals(o.tabela()) && c.coluna.equals(o.coluna());
    }

    private Map<Integer, Opcao> carregar(String token, String tipo) {
        Map<Integer, Opcao> existente = opcoes.get(tipo);
        if (existente != null) return existente;
        Map<Integer, Opcao> mapa = new HashMap<>();
        try {
            JsonNode r = glpi.get(token, "/listSearchOptions/" + tipo, null);
            // a resposta mistura títulos de seção ("common": "Características") com as opções (objetos)
            for (Map.Entry<String, JsonNode> e : r.properties()) {
                if (!e.getValue().isObject() || !Json.soDigitos(e.getKey())) continue;
                JsonNode o = e.getValue();
                mapa.put(Integer.parseInt(e.getKey()), new Opcao(Json.texto(o, "name"), Json.texto(o, "table"), Json.texto(o, "field")));
            }
            opcoes.put(tipo, Map.copyOf(mapa));
        } catch (FalhaGlpi e) {
            if (e.sessaoInvalida()) throw e;
            // sem cache: tenta de novo na próxima vez
            log.warn("[GLPI] listSearchOptions/{} falhou: {} {}", tipo, e.codigo(), e.getMessage());
        }
        return mapa;
    }
}
