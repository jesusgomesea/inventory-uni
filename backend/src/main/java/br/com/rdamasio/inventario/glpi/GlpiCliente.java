package br.com.rdamasio.inventario.glpi;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import br.com.rdamasio.inventario.comum.Json;
import br.com.rdamasio.inventario.config.InventarioPropriedades;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.NullNode;

/**
 * Única classe que fala HTTP com o GLPI (API legada {@code apirest.php}, a única do GLPI 10). Todo o resto do
 * sistema passa por aqui, então uma troca futura para a API v2 do GLPI 11 fica isolada nesta camada.
 *
 * <p>Toda chamada leva o {@code App-Token} (da configuração) e o {@code Session-Token} do técnico. Erros do GLPI
 * viram {@link FalhaGlpi}; GLPI fora do ar vira {@code FalhaGlpi} com código {@value FalhaGlpi#SEM_RESPOSTA}.
 * Senha, App-Token e Session-Token nunca vão para o log.
 */
@Component
public class GlpiCliente {

    private static final Logger log = LoggerFactory.getLogger(GlpiCliente.class);

    private final InventarioPropriedades.Glpi cfg;
    private final JsonMapper json;
    private final RestClient http;

    public GlpiCliente(InventarioPropriedades props, JsonMapper json) {
        this.cfg = props.glpi();
        this.json = json;
        HttpClient jdk = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(jdk);
        fabrica.setReadTimeout(cfg.timeout());
        this.http = RestClient.builder().requestFactory(fabrica).build();
    }

    public boolean configurado() {
        return cfg.configurado();
    }

    // ------------------------------------------------------------------ sessão

    /** Abre sessão com usuário e senha do GLPI (exige "Habilitar login com credenciais" no cliente de API). */
    public SessaoGlpi iniciarComSenha(String login, String senha) {
        String basic = Base64.getEncoder().encodeToString((login + ":" + senha).getBytes(StandardCharsets.UTF_8));
        return iniciar("Basic " + basic);
    }

    /** Abre sessão com o token pessoal do técnico (Preferências → Chaves de acesso remoto → API). */
    public SessaoGlpi iniciarComToken(String tokenPessoal) {
        return iniciar("user_token " + tokenPessoal.trim());
    }

    private SessaoGlpi iniciar(String autorizacao) {
        exigirConfiguracao();
        JsonNode r = executar(http.get()
                .uri(uri("/initSession", Parametros.de().com("get_full_session", "true")))
                .header("App-Token", cfg.appToken())
                .header(HttpHeaders.AUTHORIZATION, autorizacao), "initSession");
        String token = Json.texto(r, "session_token");
        if (token == null) throw new FalhaGlpi(502, "SEM_TOKEN", "O GLPI não devolveu o token da sessão.");
        JsonNode s = r.path("session");
        Long id = Json.numero(s, "glpiID");
        String login = Json.texto(s, "glpiname");
        String nome = String.join(" ", naoNulo(Json.texto(s, "glpifirstname")), naoNulo(Json.texto(s, "glpirealname"))).trim();
        String perfil = Json.texto(s.path("glpiactiveprofile"), "name");
        SessaoGlpi sessao = new SessaoGlpi(token, id == null ? 0 : id, login, nome.isEmpty() ? login : nome, perfil);
        ativarTodasEntidades(sessao);
        return sessao;
    }

    /**
     * Depois do login o GLPI deixa ativa só a entidade padrão do técnico. Ativamos todas as que o perfil alcança,
     * como o técnico faria no seletor de entidades da tela, para a busca achar máquinas de todas as lojas.
     * Falhar aqui não impede o uso: segue com a entidade padrão.
     */
    private void ativarTodasEntidades(SessaoGlpi s) {
        try {
            post(s.token(), "/changeActiveEntities", Map.of("entities_id", "all", "is_recursive", true));
        } catch (FalhaGlpi e) {
            log.warn("[GLPI] changeActiveEntities falhou para {}: {} {}", s.login(), e.codigo(), e.getMessage());
        }
    }

    public void encerrar(String token) {
        try {
            get(token, "/killSession", null);
        } catch (FalhaGlpi e) {
            // sessão já expirada no GLPI: nada a fazer
            log.debug("[GLPI] killSession: {}", e.codigo());
        }
    }

    // ------------------------------------------------------------------ chamadas

    public JsonNode get(String token, String caminho, Parametros params) {
        return comRenovacao(token, t -> executar(comTokens(http.get().uri(uri(caminho, params)), t), "GET " + caminho));
    }

    public JsonNode put(String token, String caminho, Object corpo) {
        return comRenovacao(token, t -> executar(comTokens(http.put().uri(uri(caminho, null))
                .contentType(MediaType.APPLICATION_JSON).body(corpo), t), "PUT " + caminho));
    }

    public JsonNode post(String token, String caminho, Object corpo) {
        return comRenovacao(token, t -> executar(comTokens(http.post().uri(uri(caminho, null))
                .contentType(MediaType.APPLICATION_JSON).body(corpo), t), "POST " + caminho));
    }

    public JsonNode delete(String token, String caminho) {
        return comRenovacao(token, t -> executar(comTokens(http.delete().uri(uri(caminho, null)), t), "DELETE " + caminho));
    }

    /**
     * Com o login desligado, todos usam a sessão da conta de serviço, que o GLPI encerra depois de um tempo parado.
     * Quando isso acontece numa chamada, o {@link RenovadorSessao} abre outra e a chamada é repetida uma vez, sem o
     * usuário perceber. Com login ligado não há renovador: sessão expirada vira 401 e a tela pede login.
     */
    private <T> T comRenovacao(String token, java.util.function.Function<String, T> chamada) {
        try {
            return chamada.apply(token);
        } catch (FalhaGlpi e) {
            RenovadorSessao r = renovador;
            if (!e.sessaoInvalida() || r == null) throw e;
            String novo = r.renovar(token);
            if (novo == null || novo.equals(token)) throw e;
            return chamada.apply(novo);
        }
    }

    /** Registrado pela sessão da conta de serviço (sessao/ContaServico). */
    public interface RenovadorSessao {
        /** Token novo no lugar de {@code expirado}; null se {@code expirado} não é da conta de serviço. */
        String renovar(String expirado);
    }

    private volatile RenovadorSessao renovador;

    public void registrarRenovador(RenovadorSessao r) {
        this.renovador = r;
    }

    /**
     * Conteúdo do arquivo de um Document ({@code Accept: application/octet-stream}). Os documentos do GLPI são só
     * lidos: os anexados por esta aplicação ficam no banco próprio (pacote documento).
     */
    public byte[] baixar(String token, long documentoId) {
        String caminho = "/Document/" + documentoId;
        return comRenovacao(token, t -> baixar(t, caminho));
    }

    private byte[] baixar(String token, String caminho) {
        try {
            return comTokens(http.get().uri(uri(caminho, null)), token)
                    .accept(MediaType.APPLICATION_OCTET_STREAM)
                    .exchange((req, res) -> {
                        byte[] corpo = res.getBody().readAllBytes();
                        if (res.getStatusCode().value() >= 400) throw FalhaGlpi.deResposta(res.getStatusCode().value(), corpo, json);
                        return corpo;
                    });
        } catch (ResourceAccessException e) {
            throw semResposta("GET " + caminho, e);
        }
    }

    // ------------------------------------------------------------------ interno

    private RestClient.RequestHeadersSpec<?> comTokens(RestClient.RequestHeadersSpec<?> spec, String token) {
        return spec.header("App-Token", cfg.appToken()).header("Session-Token", token);
    }

    private JsonNode executar(RestClient.RequestHeadersSpec<?> spec, String descricao) {
        long inicio = System.nanoTime();
        try {
            JsonNode r = spec.accept(MediaType.APPLICATION_JSON).exchange((req, res) -> {
                int status = res.getStatusCode().value();
                byte[] corpo = res.getBody().readAllBytes();
                if (status >= 400) throw FalhaGlpi.deResposta(status, corpo, json);
                if (corpo.length == 0) return NullNode.getInstance();
                return lerJson(corpo, descricao);
            });
            log.debug("[GLPI] {} em {} ms", descricao, (System.nanoTime() - inicio) / 1_000_000);
            return r;
        } catch (ResourceAccessException e) {
            throw semResposta(descricao, e);
        }
    }

    private JsonNode lerJson(byte[] corpo, String descricao) throws IOException {
        try {
            return json.readTree(corpo);
        } catch (RuntimeException e) {
            // O GLPI às vezes prefixa avisos do PHP (HTML) antes do JSON. Não dá para confiar na resposta.
            log.warn("[GLPI] {} devolveu algo que não é JSON: {}", descricao,
                    new String(corpo, 0, Math.min(corpo.length, 200), StandardCharsets.UTF_8));
            throw new FalhaGlpi(502, "RESPOSTA_INVALIDA", "O GLPI devolveu uma resposta que não é JSON.");
        }
    }

    private FalhaGlpi semResposta(String descricao, Exception e) {
        log.warn("[GLPI] {} sem resposta: {}", descricao, e.getMessage());
        return new FalhaGlpi(504, FalhaGlpi.SEM_RESPOSTA, "O GLPI não respondeu. Verifique se ele está no ar.");
    }

    private URI uri(String caminho, Parametros params) {
        String base = cfg.url().endsWith("/") ? cfg.url().substring(0, cfg.url().length() - 1) : cfg.url();
        String q = params == null || params.vazio() ? "" : "?" + params.consulta();
        return URI.create(base + caminho + q);
    }

    private void exigirConfiguracao() {
        if (!cfg.configurado()) {
            throw new FalhaGlpi(503, "NAO_CONFIGURADO",
                    "O endereço do GLPI ou o App-Token não foram configurados (backend/config/application-local.yml).");
        }
    }

    private static String naoNulo(String s) {
        return s == null ? "" : s;
    }
}
