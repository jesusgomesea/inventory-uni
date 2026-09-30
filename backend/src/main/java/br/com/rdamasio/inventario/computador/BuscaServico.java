package br.com.rdamasio.inventario.computador;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import br.com.rdamasio.inventario.comum.Json;
import br.com.rdamasio.inventario.glpi.GlpiCliente;
import br.com.rdamasio.inventario.glpi.OpcoesBusca;
import br.com.rdamasio.inventario.glpi.OpcoesBusca.Campo;
import br.com.rdamasio.inventario.glpi.Parametros;
import tools.jackson.databind.JsonNode;

/**
 * Lista de computadores pela busca do GLPI ({@code search/Computer}): uma chamada só por página, com as colunas
 * pedidas em {@code forcedisplay}. O texto digitado procura em nome, serial, patrimônio, usuário, último login
 * e IP ao mesmo tempo (grupo de critérios com OU); status e local entram com E.
 *
 * <p>Memória e disco não entram na lista: no GLPI são vários valores por máquina e a busca não soma. Filtrar por
 * "RAM menor que 8 GB" ou "HDD" pede o índice local da fase 5 (docs/PROJETO.md §7).
 */
@Service
public class BuscaServico {

    /** Uma linha da lista. */
    public record Item(long id, String nome, String status, String responsavel, String ultimoLogin, String local,
            String fabricante, String modelo, String serial, String patrimonio, String sistema, String ip,
            String modificadoEm) {
    }

    public record Pagina(long total, int pagina, int tamanho, List<Item> itens) {
    }

    public record Filtro(String texto, Long statusId, Long localId, int pagina, int tamanho) {
    }

    private static final Campo[] COLUNAS = {
            Campo.COMPUTADOR_ID, Campo.COMPUTADOR_NOME, Campo.COMPUTADOR_STATUS, Campo.COMPUTADOR_USUARIO,
            Campo.COMPUTADOR_CONTATO, Campo.COMPUTADOR_LOCAL, Campo.COMPUTADOR_FABRICANTE, Campo.COMPUTADOR_MODELO,
            Campo.COMPUTADOR_SERIAL, Campo.COMPUTADOR_PATRIMONIO, Campo.COMPUTADOR_SO, Campo.COMPUTADOR_SO_VERSAO,
            Campo.COMPUTADOR_IP, Campo.COMPUTADOR_MODIFICADO };

    /** Onde o texto digitado é procurado. */
    private static final Campo[] PROCURA = {
            Campo.COMPUTADOR_NOME, Campo.COMPUTADOR_SERIAL, Campo.COMPUTADOR_PATRIMONIO, Campo.COMPUTADOR_USUARIO,
            Campo.COMPUTADOR_CONTATO, Campo.COMPUTADOR_IP };

    private final GlpiCliente glpi;
    private final OpcoesBusca opcoes;

    public BuscaServico(GlpiCliente glpi, OpcoesBusca opcoes) {
        this.glpi = glpi;
        this.opcoes = opcoes;
    }

    public Pagina buscar(String token, Filtro f) {
        Parametros p = Parametros.de();
        int n = 0;
        String texto = f.texto() == null ? "" : f.texto().trim();
        if (!texto.isEmpty()) {
            String grupo = "criteria[" + n++ + "]";
            p.com(grupo + "[link]", "AND");
            for (int i = 0; i < PROCURA.length; i++) {
                p.criterio(grupo + "[criteria][" + i + "]", i == 0 ? null : "OR", col(token, PROCURA[i]), "contains", texto);
            }
        }
        if (f.statusId() != null) {
            p.criterio("criteria[" + n++ + "]", "AND", col(token, Campo.COMPUTADOR_STATUS), "equals", f.statusId());
        }
        if (f.localId() != null) {
            // "under" inclui as sublocalizações: filtrar "Loja 14" traz também "Loja 14 > Balcão"
            p.criterio("criteria[" + n++ + "]", "AND", col(token, Campo.COMPUTADOR_LOCAL), "under", f.localId());
        }
        for (int i = 0; i < COLUNAS.length; i++) p.com("forcedisplay[" + i + "]", col(token, COLUNAS[i]));
        int inicio = f.pagina() * f.tamanho();
        p.com("sort", col(token, Campo.COMPUTADOR_NOME)).com("order", "ASC")
                .com("range", inicio + "-" + (inicio + f.tamanho() - 1));

        JsonNode r = glpi.get(token, "/search/Computer", p);
        List<Item> itens = new ArrayList<>();
        for (JsonNode linha : Json.itens(r.path("data"))) itens.add(item(token, linha));
        Long total = Json.numero(r, "totalcount");
        return new Pagina(total == null ? itens.size() : total, f.pagina(), f.tamanho(), itens);
    }

    private Item item(String token, JsonNode l) {
        Long id = Json.numero(l, String.valueOf(col(token, Campo.COMPUTADOR_ID)));
        String so = juntar(valor(token, l, Campo.COMPUTADOR_SO), valor(token, l, Campo.COMPUTADOR_SO_VERSAO));
        String ip = valores(l.get(String.valueOf(col(token, Campo.COMPUTADOR_IP)))).stream()
                .filter(FichaServico::ipv4Util).findFirst().orElse(null);
        return new Item(id == null ? 0 : id,
                valor(token, l, Campo.COMPUTADOR_NOME),
                valor(token, l, Campo.COMPUTADOR_STATUS),
                valor(token, l, Campo.COMPUTADOR_USUARIO),
                valor(token, l, Campo.COMPUTADOR_CONTATO),
                valor(token, l, Campo.COMPUTADOR_LOCAL),
                valor(token, l, Campo.COMPUTADOR_FABRICANTE),
                valor(token, l, Campo.COMPUTADOR_MODELO),
                valor(token, l, Campo.COMPUTADOR_SERIAL),
                valor(token, l, Campo.COMPUTADOR_PATRIMONIO),
                so, ip,
                valor(token, l, Campo.COMPUTADOR_MODIFICADO));
    }

    private String valor(String token, JsonNode linha, Campo c) {
        List<String> v = valores(linha.get(String.valueOf(col(token, c))));
        return v.isEmpty() ? null : String.join(", ", v);
    }

    /**
     * Valores de uma célula da busca. Colunas com vários valores (IPs, usuários) chegam como array ou, em algumas
     * versões, como texto com os separadores internos do GLPI: "$$##$$" entre valores e "$#$" entre valor e id.
     */
    static List<String> valores(JsonNode celula) {
        List<String> r = new ArrayList<>();
        if (celula == null || celula.isNull()) return r;
        if (celula.isArray()) {
            for (JsonNode v : celula) r.addAll(valores(v));
            return r;
        }
        if (celula.isObject()) {
            for (Map.Entry<String, JsonNode> e : celula.properties()) r.addAll(valores(e.getValue()));
            return r;
        }
        for (String parte : celula.asString().split("\\$\\$##\\$\\$")) {
            String v = parte.split("\\$#\\$")[0].trim();
            if (!v.isEmpty() && !v.equals("&nbsp;") && !r.contains(v)) r.add(v);
        }
        return r;
    }

    private int col(String token, Campo c) {
        return opcoes.id(token, c);
    }

    private static String juntar(String a, String b) {
        if (a == null) return b;
        if (b == null) return a;
        return a + " " + b;
    }
}
