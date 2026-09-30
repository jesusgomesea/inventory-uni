package br.com.rdamasio.inventario.opcoes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.rdamasio.inventario.comum.CacheTempo;
import br.com.rdamasio.inventario.comum.Json;
import br.com.rdamasio.inventario.config.InventarioPropriedades;
import br.com.rdamasio.inventario.glpi.GlpiCliente;
import br.com.rdamasio.inventario.glpi.OpcoesBusca;
import br.com.rdamasio.inventario.glpi.OpcoesBusca.Campo;
import br.com.rdamasio.inventario.glpi.Parametros;
import br.com.rdamasio.inventario.glpi.SessaoGlpi;
import br.com.rdamasio.inventario.sessao.SessaoAtual;
import tools.jackson.databind.JsonNode;

/**
 * Listas dos campos editáveis e dos filtros: status, localizações, grupos e busca de usuários.
 * Status, locais e grupos ficam em cache por técnico ({@code inventario.cache-listas}, 10 min): mudam pouco, e
 * o cache é por técnico porque o GLPI filtra pelas entidades de cada um. Usuários são buscados a cada letra
 * digitada (podem ser milhares).
 */
@RestController
@RequestMapping("/api/opcoes")
public class OpcoesController {

    public record Opcao(long id, String nome) {
    }

    public record Usuario(long id, String nome, String login) {
    }

    private final GlpiCliente glpi;
    private final SessaoAtual sessao;
    private final OpcoesBusca opcoes;
    private final CacheTempo<String, List<Opcao>> cache;

    public OpcoesController(GlpiCliente glpi, SessaoAtual sessao, OpcoesBusca opcoes, InventarioPropriedades props) {
        this.glpi = glpi;
        this.sessao = sessao;
        this.opcoes = opcoes;
        this.cache = new CacheTempo<>(props.cacheListas(), 2_000);
    }

    /** Status que o GLPI permite usar em computadores (is_visible_computer). */
    @GetMapping("/status")
    public List<Opcao> status() {
        return lista("State", "is_visible_computer");
    }

    @GetMapping("/locais")
    public List<Opcao> locais() {
        return lista("Location", null);
    }

    /** Grupos que podem ser responsáveis técnicos (is_assign). */
    @GetMapping("/grupos")
    public List<Opcao> grupos() {
        return lista("Group", "is_assign");
    }

    @GetMapping("/usuarios")
    public List<Usuario> usuarios(@RequestParam String q) {
        String t = q == null ? "" : q.trim();
        if (t.length() < 2) return List.of();
        String token = sessao.token();
        int login = opcoes.id(token, Campo.USUARIO_LOGIN);
        int nome = opcoes.id(token, Campo.USUARIO_NOME);
        int sobrenome = opcoes.id(token, Campo.USUARIO_SOBRENOME);
        int id = opcoes.id(token, Campo.USUARIO_ID);
        Parametros p = Parametros.de();
        p.com("criteria[0][link]", "AND");
        p.criterio("criteria[0][criteria][0]", null, login, "contains", t);
        p.criterio("criteria[0][criteria][1]", "OR", nome, "contains", t);
        p.criterio("criteria[0][criteria][2]", "OR", sobrenome, "contains", t);
        p.criterio("criteria[1]", "AND", opcoes.id(token, Campo.USUARIO_ATIVO), "equals", 1);
        int i = 0;
        for (int c : new int[] { id, login, nome, sobrenome }) p.com("forcedisplay[" + i++ + "]", c);
        p.com("sort", sobrenome).com("order", "ASC").com("range", "0-19");
        JsonNode r = glpi.get(token, "/search/User", p);
        List<Usuario> lista = new ArrayList<>();
        for (JsonNode l : Json.itens(r.path("data"))) {
            Long uid = Json.numero(l, String.valueOf(id));
            if (uid == null) continue;
            String n = String.join(" ", naoNulo(Json.texto(l, String.valueOf(nome))), naoNulo(Json.texto(l, String.valueOf(sobrenome)))).trim();
            String lg = Json.texto(l, String.valueOf(login));
            lista.add(new Usuario(uid, n.isEmpty() ? lg : n, lg));
        }
        return lista;
    }

    private List<Opcao> lista(String tipo, String filtroVerdadeiro) {
        SessaoGlpi s = sessao.exigir();
        return cache.obter(s.usuarioId() + "#" + tipo, () -> {
            JsonNode r = glpi.get(s.token(), "/" + tipo, Parametros.de().com("range", "0-4999"));
            List<Opcao> lista = new ArrayList<>();
            for (JsonNode l : Json.itens(r)) {
                // campo ausente = versão do GLPI sem esse filtro: mostra tudo
                if (filtroVerdadeiro != null && l.has(filtroVerdadeiro) && !Json.verdadeiro(l, filtroVerdadeiro)) continue;
                Long id = Json.id(l, "id");
                String nome = Json.texto(l, "completename");
                if (nome == null) nome = Json.texto(l, "name");
                if (id != null && nome != null) lista.add(new Opcao(id, nome));
            }
            lista.sort(Comparator.comparing(o -> o.nome().toLowerCase()));
            return List.copyOf(lista);
        });
    }

    private static String naoNulo(String s) {
        return s == null ? "" : s;
    }
}
