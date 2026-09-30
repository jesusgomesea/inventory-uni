package br.com.rdamasio.inventario.simulador;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import tools.jackson.databind.json.JsonMapper;

/**
 * Simulador da API legada do GLPI 10 ({@code apirest.php}), só no perfil {@code simulador}. Serve para ver e testar
 * a aplicação sem um GLPI de verdade (desenvolvimento, demonstração, testes da tela).
 *
 * <p><b>Não é o GLPI:</b> imita só as rotas que a aplicação usa, no formato que esperamos do GLPI 10. Um
 * comportamento que funciona aqui ainda precisa ser conferido contra o GLPI real. Os dados vêm de
 * {@code simulador/dados.json} e as alterações ficam só na memória (somem ao reiniciar).
 *
 * <p>Login: {@code tecnico} / {@code tecnico}, ou o token pessoal {@code token-tecnico}.
 */
@Profile("simulador")
@RestController
@RequestMapping("/simulador-glpi/apirest.php")
@SuppressWarnings("unchecked")
public class SimuladorGlpi {

    private static final Logger log = LoggerFactory.getLogger(SimuladorGlpi.class);
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /** Colunas da busca: número → [tabela, campo, nome, chave no computador]. Números do GLPI 10. */
    private static final Map<Integer, String[]> OPCOES_COMPUTADOR = new LinkedHashMap<>();
    private static final Map<Integer, String[]> OPCOES_USUARIO = new LinkedHashMap<>();

    static {
        OPCOES_COMPUTADOR.put(1, new String[] { "glpi_computers", "name", "Nome", "name" });
        OPCOES_COMPUTADOR.put(2, new String[] { "glpi_computers", "id", "ID", "id" });
        OPCOES_COMPUTADOR.put(3, new String[] { "glpi_locations", "completename", "Localização", "locations_id" });
        OPCOES_COMPUTADOR.put(5, new String[] { "glpi_computers", "serial", "Número de série", "serial" });
        OPCOES_COMPUTADOR.put(6, new String[] { "glpi_computers", "otherserial", "Patrimônio", "otherserial" });
        OPCOES_COMPUTADOR.put(7, new String[] { "glpi_computers", "contact", "Contato alternativo", "contact" });
        OPCOES_COMPUTADOR.put(16, new String[] { "glpi_computers", "comment", "Comentários", "comment" });
        OPCOES_COMPUTADOR.put(19, new String[] { "glpi_computers", "date_mod", "Última atualização", "date_mod" });
        OPCOES_COMPUTADOR.put(23, new String[] { "glpi_manufacturers", "name", "Fabricante", "manufacturers_id" });
        OPCOES_COMPUTADOR.put(24, new String[] { "glpi_users", "name", "Técnico responsável", "users_id_tech" });
        OPCOES_COMPUTADOR.put(31, new String[] { "glpi_states", "completename", "Status", "states_id" });
        OPCOES_COMPUTADOR.put(40, new String[] { "glpi_computermodels", "name", "Modelo", "computermodels_id" });
        OPCOES_COMPUTADOR.put(45, new String[] { "glpi_operatingsystems", "name", "Sistema operacional - Nome", "_so_nome" });
        OPCOES_COMPUTADOR.put(46, new String[] { "glpi_operatingsystemversions", "name", "Sistema operacional - Versão", "_so_versao" });
        OPCOES_COMPUTADOR.put(49, new String[] { "glpi_groups", "completename", "Grupo técnico", "groups_id_tech" });
        OPCOES_COMPUTADOR.put(70, new String[] { "glpi_users", "name", "Usuário", "users_id" });
        OPCOES_COMPUTADOR.put(126, new String[] { "glpi_ipaddresses", "name", "Endereço IP", "_ips" });
        OPCOES_USUARIO.put(1, new String[] { "glpi_users", "name", "Login", "name" });
        OPCOES_USUARIO.put(2, new String[] { "glpi_users", "id", "ID", "id" });
        OPCOES_USUARIO.put(8, new String[] { "glpi_users", "is_active", "Ativo", "is_active" });
        OPCOES_USUARIO.put(9, new String[] { "glpi_users", "firstname", "Nome", "firstname" });
        OPCOES_USUARIO.put(34, new String[] { "glpi_users", "realname", "Sobrenome", "realname" });
    }

    private final Map<String, Object> dados;
    private final Map<String, Map<String, Object>> sessoes = new ConcurrentHashMap<>();
    private final Map<Long, List<Map<String, Object>>> logs = new ConcurrentHashMap<>();

    public SimuladorGlpi(JsonMapper json) throws IOException {
        try (InputStream in = new ClassPathResource("simulador/dados.json").getInputStream()) {
            this.dados = json.readValue(in, Map.class);
        }
        log.warn("SIMULADOR DO GLPI ATIVO (perfil simulador): dados fictícios, não use em produção.");
    }

    @RequestMapping("/**")
    public ResponseEntity<Object> atender(HttpServletRequest req, @RequestBody(required = false) Map<String, Object> corpo) {
        String caminho = req.getRequestURI().substring(req.getRequestURI().indexOf("apirest.php") + "apirest.php".length());
        String[] p = caminho.replaceAll("^/+|/+$", "").split("/");
        String metodo = req.getMethod();
        if (req.getHeader("App-Token") == null || req.getHeader("App-Token").equals("errado")) {
            return erro(HttpStatus.BAD_REQUEST, "ERROR_WRONG_APP_TOKEN_PARAMETER", "parameter app_token seems wrong");
        }
        if (p[0].equals("initSession")) return iniciar(req);

        Map<String, Object> usuario = sessoes.get(Objects.requireNonNullElse(req.getHeader("Session-Token"), ""));
        if (usuario == null) return erro(HttpStatus.UNAUTHORIZED, "ERROR_SESSION_TOKEN_INVALID", "session_token seems invalid");

        return switch (p[0]) {
            case "killSession" -> {
                sessoes.remove(req.getHeader("Session-Token"));
                yield ResponseEntity.ok(true);
            }
            case "changeActiveEntities" -> ResponseEntity.ok(true);
            case "listSearchOptions" -> ResponseEntity.ok(opcoes(p[1].equals("User") ? OPCOES_USUARIO : OPCOES_COMPUTADOR));
            case "search" -> ResponseEntity.ok(p[1].equals("User") ? buscarUsuarios(req) : buscarComputadores(req));
            case "Computer" -> computador(req, p, metodo, corpo, usuario);
            case "Document" -> documento(req, Long.parseLong(p[1]));
            case "State" -> ResponseEntity.ok(lista("status"));
            case "Location" -> ResponseEntity.ok(lista("locais"));
            case "Group" -> ResponseEntity.ok(lista("grupos"));
            case "User" -> porId(lista("usuarios"), p).map(u -> ResponseEntity.ok((Object) u)).orElse(naoAchou());
            case "Monitor" -> porId((List<Map<String, Object>>) dados.get("monitores"), p).map(m -> ResponseEntity.ok((Object) m)).orElse(naoAchou());
            case "Software", "SoftwareVersion" -> naoAchou();
            default -> {
                List<Map<String, Object>> pecas = ((Map<String, List<Map<String, Object>>>) dados.get("pecas")).get(p[0]);
                if (pecas == null) yield erro(HttpStatus.BAD_REQUEST, "ERROR_RESOURCE_NOT_FOUND_NOR_COMMONDBTM", "rota não simulada: " + caminho);
                yield porId(pecas, p).map(x -> ResponseEntity.ok((Object) x)).orElse(naoAchou());
            }
        };
    }

    // ------------------------------------------------------------------ sessão

    private ResponseEntity<Object> iniciar(HttpServletRequest req) {
        String aut = Objects.requireNonNullElse(req.getHeader("Authorization"), "");
        Map<String, Object> u = null;
        if (aut.startsWith("Basic ")) {
            String[] ls = new String(Base64.getDecoder().decode(aut.substring(6)), StandardCharsets.UTF_8).split(":", 2);
            u = lista("usuarios").stream().filter(x -> x.get("name").equals(ls[0]) && ls.length > 1 && ls[1].equals(x.get("senha"))).findFirst().orElse(null);
        } else if (aut.startsWith("user_token ")) {
            String t = aut.substring(11);
            u = lista("usuarios").stream().filter(x -> ("token-" + x.get("name")).equals(t) && x.get("senha") != null).findFirst().orElse(null);
        }
        if (u == null) return erro(HttpStatus.UNAUTHORIZED, "ERROR_GLPI_LOGIN", "Incorrect username or password");
        String token = UUID.randomUUID().toString().replace("-", "");
        sessoes.put(token, u);
        Map<String, Object> sessao = new LinkedHashMap<>();
        sessao.put("glpiID", u.get("id"));
        sessao.put("glpiname", u.get("name"));
        sessao.put("glpifirstname", u.get("firstname"));
        sessao.put("glpirealname", u.get("realname"));
        sessao.put("glpiactiveprofile", Map.of("name", u.getOrDefault("perfil", "Self-Service")));
        return ResponseEntity.ok(Map.of("session_token", token, "session", sessao));
    }

    // ------------------------------------------------------------------ computador

    private ResponseEntity<Object> computador(HttpServletRequest req, String[] p, String metodo, Map<String, Object> corpo,
            Map<String, Object> usuario) {
        Map<String, Object> c = porId(computadores(), p).orElse(null);
        if (c == null) return naoAchou();
        long id = numero(c.get("id"));
        if (metodo.equals("PUT")) return alterar(c, (Map<String, Object>) corpo.get("input"), usuario);
        if (p.length > 2) return ResponseEntity.ok(subitens(c, p[2], sim(req, "expand_dropdowns")));

        boolean expandir = sim(req, "expand_dropdowns");
        Map<String, Object> r = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : c.entrySet()) {
            Object v = e.getValue();
            if (v instanceof List || v instanceof Map) continue; // dados auxiliares do simulador
            r.put(e.getKey(), expandir ? expandir(e.getKey(), v) : cru(e.getKey(), v));
        }
        r.put("is_deleted", 0);
        r.put("is_template", 0);
        r.put("entities_id", expandir ? "Entidade raiz" : 0);
        if (sim(req, "with_networkports")) r.put("_networkports", c.getOrDefault("portas", Map.of()));
        if (sim(req, "with_documents")) {
            List<Map<String, Object>> docs = new ArrayList<>();
            for (Map<String, Object> d : (List<Map<String, Object>>) c.getOrDefault("documentos", List.of())) {
                Map<String, Object> x = new LinkedHashMap<>(d);
                x.put("assocID", 5000 + numero(d.get("id")));
                docs.add(x);
            }
            r.put("_documents", docs);
        }
        if (sim(req, "with_softwares")) {
            List<Map<String, Object>> s = new ArrayList<>();
            for (List<String> sw : (List<List<String>>) c.getOrDefault("softwares", List.of())) {
                s.add(Map.of("softwares_id", sw.get(0), "softwareversions_id", sw.get(1), "is_dynamic", 1));
            }
            r.put("_softwares", s);
        }
        if (sim(req, "with_infocoms")) r.put("_infocoms", c.getOrDefault("infocom", List.of()));
        if (sim(req, "with_tickets")) r.put("_tickets", c.getOrDefault("tickets", List.of()));
        if (sim(req, "with_logs")) r.put("_logs", logs.getOrDefault(id, List.of()));
        return ResponseEntity.ok(r);
    }

    private ResponseEntity<Object> alterar(Map<String, Object> c, Map<String, Object> input, Map<String, Object> usuario) {
        long id = numero(c.get("id"));
        String agora = LocalDateTime.now().format(DATA);
        for (Map.Entry<String, Object> e : input.entrySet()) {
            Object antes = c.get(e.getKey());
            c.put(e.getKey(), e.getValue());
            Integer opcao = OPCOES_COMPUTADOR.entrySet().stream().filter(o -> o.getValue()[3].equals(e.getKey()))
                    .map(Map.Entry::getKey).findFirst().orElse(0);
            Map<String, Object> l = new LinkedHashMap<>();
            l.put("id", logs.values().stream().mapToInt(List::size).sum() + 1);
            l.put("date_mod", agora);
            l.put("user_name", usuario.get("firstname") + " " + usuario.get("realname") + " (" + usuario.get("id") + ")");
            l.put("linked_action", 0);
            l.put("id_search_option", opcao);
            l.put("old_value", String.valueOf(expandir(e.getKey(), antes)));
            l.put("new_value", String.valueOf(expandir(e.getKey(), e.getValue())));
            logs.computeIfAbsent(id, k -> new ArrayList<>()).add(l);
        }
        c.put("date_mod", agora);
        return ResponseEntity.ok(List.of(Map.of(String.valueOf(id), true, "message", "")));
    }

    private List<Map<String, Object>> subitens(Map<String, Object> c, String tipo, boolean expandir) {
        String chave = switch (tipo) {
            case "Item_DeviceMemory" -> "memorias";
            case "Item_DeviceHardDrive" -> "discos";
            case "Item_Disk" -> "volumes";
            case "Item_DeviceProcessor" -> "processadores";
            case "Item_DeviceGraphicCard" -> "video";
            case "Item_DeviceFirmware" -> "firmware";
            case "ComputerAntivirus" -> "antivirus";
            case "Computer_Item" -> "conectados";
            default -> null;
        };
        if (tipo.equals("Item_OperatingSystem")) {
            Object so = c.get("so");
            return so == null ? List.of() : List.of((Map<String, Object>) so);
        }
        if (chave == null) return List.of();
        List<Map<String, Object>> r = new ArrayList<>();
        int i = 1;
        for (Map<String, Object> x : (List<Map<String, Object>>) c.getOrDefault(chave, List.of())) {
            Map<String, Object> linha = new LinkedHashMap<>(x);
            linha.put("id", numero(c.get("id")) * 100 + i++);
            linha.put("is_deleted", 0);
            r.add(linha);
        }
        return r;
    }

    private ResponseEntity<Object> documento(HttpServletRequest req, long id) {
        Map<String, Object> doc = computadores().stream()
                .flatMap(c -> ((List<Map<String, Object>>) c.getOrDefault("documentos", List.of())).stream())
                .filter(d -> numero(d.get("id")) == id).findFirst().orElse(null);
        if (doc == null) return naoAchou();
        String aceita = Objects.requireNonNullElse(req.getHeader("Accept"), "");
        if (aceita.contains("octet-stream")) {
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).body(pdfDeExemplo(String.valueOf(doc.get("name"))));
        }
        return ResponseEntity.ok(doc);
    }

    // ------------------------------------------------------------------ busca

    private Map<String, Object> buscarComputadores(HttpServletRequest req) {
        Map<String, String[]> q = req.getParameterMap();
        String texto = null;
        Long status = null;
        Long local = null;
        for (Map.Entry<String, String[]> e : q.entrySet()) {
            String k = e.getKey();
            if (k.matches("criteria\\[0]\\[criteria]\\[0]\\[value]")) texto = e.getValue()[0].toLowerCase();
            if (k.matches("criteria\\[\\d+]\\[field]") && e.getValue()[0].equals("31")) status = Long.parseLong(q.get(k.replace("field", "value"))[0]);
            if (k.matches("criteria\\[\\d+]\\[field]") && e.getValue()[0].equals("3")) local = Long.parseLong(q.get(k.replace("field", "value"))[0]);
        }
        String t = texto;
        Long st = status;
        Long lo = local;
        Predicate<Map<String, Object>> filtro = c -> {
            if (st != null && numero(c.get("states_id")) != st) return false;
            if (lo != null && !dentroDe(numero(c.get("locations_id")), lo)) return false;
            if (t == null) return true;
            return List.of("name", "serial", "otherserial", "contact").stream().anyMatch(k -> String.valueOf(c.get(k)).toLowerCase().contains(t))
                    || String.valueOf(expandir("users_id", c.get("users_id"))).toLowerCase().contains(t)
                    || ips(c).stream().anyMatch(ip -> ip.contains(t));
        };
        List<Map<String, Object>> achados = computadores().stream().filter(filtro)
                .sorted(Comparator.comparing(c -> String.valueOf(c.get("name")))).toList();
        List<Integer> colunas = new ArrayList<>();
        q.forEach((k, v) -> {
            if (k.startsWith("forcedisplay")) colunas.add(Integer.parseInt(v[0]));
        });
        String[] faixa = q.getOrDefault("range", new String[] { "0-49" })[0].split("-");
        int ini = Integer.parseInt(faixa[0]);
        int fim = Math.min(achados.size() - 1, Integer.parseInt(faixa[1]));
        List<Map<String, Object>> linhas = new ArrayList<>();
        for (int i = ini; i <= fim; i++) {
            Map<String, Object> c = achados.get(i);
            Map<String, Object> l = new LinkedHashMap<>();
            for (int col : colunas) {
                String[] o = OPCOES_COMPUTADOR.get(col);
                if (o == null) continue;
                Object v = switch (o[3]) {
                    case "_so_nome" -> ((Map<String, Object>) c.getOrDefault("so", Map.of())).get("operatingsystems_id");
                    case "_so_versao" -> ((Map<String, Object>) c.getOrDefault("so", Map.of())).get("operatingsystemversions_id");
                    case "_ips" -> ips(c);
                    case "users_id", "users_id_tech" -> { Object n = expandir(o[3], c.get(o[3])); yield Objects.equals(n, "") ? null : n; }
                    default -> expandir(o[3], c.get(o[3]));
                };
                l.put(String.valueOf(col), v);
            }
            linhas.add(l);
        }
        return Map.of("totalcount", achados.size(), "count", linhas.size(), "data", linhas);
    }

    private Map<String, Object> buscarUsuarios(HttpServletRequest req) {
        String t = Objects.requireNonNullElse(req.getParameter("criteria[0][criteria][0][value]"), "").toLowerCase();
        List<Map<String, Object>> linhas = new ArrayList<>();
        for (Map<String, Object> u : lista("usuarios")) {
            if (numero(u.get("is_active")) != 1) continue;
            String tudo = (u.get("name") + " " + u.get("firstname") + " " + u.get("realname")).toLowerCase();
            if (!tudo.contains(t)) continue;
            linhas.add(Map.of("2", u.get("id"), "1", u.get("name"), "9", u.get("firstname"), "34", u.get("realname")));
        }
        return Map.of("totalcount", linhas.size(), "count", linhas.size(), "data", linhas);
    }

    // ------------------------------------------------------------------ apoio

    private Map<String, Object> opcoes(Map<Integer, String[]> mapa) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("common", "Características");
        mapa.forEach((k, v) -> r.put(String.valueOf(k), Map.of("table", v[0], "field", v[1], "name", v[2])));
        return r;
    }

    /** Valor como viria com expand_dropdowns=true: o nome no lugar do id. */
    private Object expandir(String campo, Object v) {
        return switch (campo) {
            case "users_id", "users_id_tech" -> nome(lista("usuarios"), v, u -> u.get("firstname") + " " + u.get("realname"));
            case "locations_id" -> nome(lista("locais"), v, x -> String.valueOf(x.get("completename")));
            case "states_id" -> nome(lista("status"), v, x -> String.valueOf(x.get("completename")));
            case "groups_id_tech" -> nome(lista("grupos"), v, x -> String.valueOf(x.get("completename")));
            default -> v;
        };
    }

    /** Valor como viria sem expandir: ids numéricos; dropdowns guardados só como nome viram um id qualquer. */
    private static Object cru(String campo, Object v) {
        if (campo.endsWith("_id") && v instanceof String) return 1;
        return v;
    }

    private static Object nome(List<Map<String, Object>> lista, Object id, java.util.function.Function<Map<String, Object>, String> f) {
        if (id == null || numero(id) == 0) return "";
        return lista.stream().filter(x -> numero(x.get("id")) == numero(id)).findFirst().map(f).orElse("");
    }

    private boolean dentroDe(long local, long pai) {
        if (local == pai) return true;
        String nomePai = String.valueOf(expandir("locations_id", pai));
        return String.valueOf(expandir("locations_id", local)).startsWith(nomePai + " > ");
    }

    private static List<String> ips(Map<String, Object> c) {
        List<String> r = new ArrayList<>();
        Map<String, Object> portas = (Map<String, Object>) c.getOrDefault("portas", Map.of());
        for (Object grupo : portas.values()) {
            for (Map<String, Object> porta : (List<Map<String, Object>>) grupo) {
                Map<String, Object> nn = (Map<String, Object>) porta.getOrDefault("NetworkName", Map.of());
                for (Map<String, Object> ip : (List<Map<String, Object>>) nn.getOrDefault("IPAddress", List.of())) {
                    r.add(String.valueOf(ip.get("name")));
                }
            }
        }
        return r;
    }

    private List<Map<String, Object>> computadores() {
        return (List<Map<String, Object>>) dados.get("computadores");
    }

    private List<Map<String, Object>> lista(String chave) {
        return (List<Map<String, Object>>) dados.get(chave);
    }

    private static java.util.Optional<Map<String, Object>> porId(List<Map<String, Object>> lista, String[] p) {
        if (p.length < 2) return java.util.Optional.empty();
        long id;
        try {
            id = Long.parseLong(p[1]);
        } catch (NumberFormatException e) {
            return java.util.Optional.empty();
        }
        return lista.stream().filter(x -> numero(x.get("id")) == id).findFirst();
    }

    private static boolean sim(HttpServletRequest req, String param) {
        return "true".equals(req.getParameter(param));
    }

    private static long numero(Object o) {
        if (o instanceof Number n) return n.longValue();
        try {
            return o == null ? 0 : Long.parseLong(String.valueOf(o));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static ResponseEntity<Object> erro(HttpStatus status, String codigo, String mensagem) {
        return ResponseEntity.status(status).body(List.of(codigo, mensagem));
    }

    private static ResponseEntity<Object> naoAchou() {
        return erro(HttpStatus.NOT_FOUND, "ERROR_ITEM_NOT_FOUND", "Item not found");
    }

    /** PDF mínimo de uma página com o título, só para o download funcionar no simulador. */
    private static byte[] pdfDeExemplo(String titulo) {
        String texto = titulo.replaceAll("[^A-Za-z0-9 .,:-]", "?");
        String conteudo = "BT /F1 18 Tf 60 760 Td (" + texto + ") Tj 0 -30 Td /F1 12 Tf (Documento de exemplo do simulador do GLPI.) Tj ET";
        String[] objs = {
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>",
                "<< /Length " + conteudo.length() + " >>\nstream\n" + conteudo + "\nendstream",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>" };
        StringBuilder sb = new StringBuilder("%PDF-1.4\n");
        List<Integer> pos = new ArrayList<>();
        for (int i = 0; i < objs.length; i++) {
            pos.add(sb.length());
            sb.append(i + 1).append(" 0 obj\n").append(objs[i]).append("\nendobj\n");
        }
        int xref = sb.length();
        sb.append("xref\n0 ").append(objs.length + 1).append("\n0000000000 65535 f \n");
        for (int p : pos) sb.append(String.format("%010d 00000 n \n", p));
        sb.append("trailer\n<< /Size ").append(objs.length + 1).append(" /Root 1 0 R >>\nstartxref\n").append(xref).append("\n%%EOF");
        return sb.toString().getBytes(StandardCharsets.ISO_8859_1);
    }
}
