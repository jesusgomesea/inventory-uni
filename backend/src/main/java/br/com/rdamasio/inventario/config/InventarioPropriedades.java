package br.com.rdamasio.inventario.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuração do sistema (prefixo {@code inventario} em application.yml). O App-Token nunca fica em arquivo
 * versionado: vem de {@code backend/config/application-local.yml} (fora do git) ou da variável GLPI_APP_TOKEN.
 */
@ConfigurationProperties("inventario")
public record InventarioPropriedades(Glpi glpi, Discos discos, Documentos documentos, Termos termos, Login login,
        @DefaultValue("10m") Duration cacheListas) {

    /**
     * Login dos técnicos (cada um com a própria sessão do GLPI). <b>Em espera desde 30/09/2026</b>, por decisão da TI:
     * desligado, ninguém faz login e o backend usa a conta de serviço ({@link Glpi#contaServico()}) para todos.
     * O código do login continua no lugar (sessao/); para religar basta {@code inventario.login.habilitado: true}.
     */
    public record Login(@DefaultValue("false") boolean habilitado) {
    }

    public boolean loginHabilitado() {
        return login != null && login.habilitado();
    }

    /**
     * Modelos de termo em HTML mantidos pela TI (pasta "Model Termos Eqp" na raiz do repositório). São lidos a cada
     * pedido, então editar o modelo vale na hora, sem reiniciar. O caminho é relativo à pasta onde o backend roda
     * ({@code backend/}).
     */
    public record Termos(@DefaultValue("../Model Termos Eqp") String pasta) {
    }

    /**
     * Documentos anexados por esta aplicação (fora do GLPI).
     *
     * @param pasta onde os arquivos ficam no disco. <b>Entra no backup</b> junto com o banco: um sem o outro não
     *              serve (o banco guarda o caminho e o SHA-256; a pasta guarda o arquivo).
     */
    public record Documentos(@DefaultValue("./dados/documentos") String pasta) {
    }

    /**
     * @param url      endereço da API legada, terminando em {@code /apirest.php}
     * @param appToken token do cliente de API cadastrado no GLPI (Configurar → Geral → API)
     * @param urlWeb   endereço das telas do GLPI, para o botão "Abrir no GLPI"; vazio = deriva de {@code url}
     * @param contaServico usuário do GLPI que a aplicação usa quando o login está desligado
     */
    public record Glpi(
            String url,
            String appToken,
            String urlWeb,
            @DefaultValue("30s") Duration timeout,
            ContaServico contaServico) {

        public boolean configurado() {
            return url != null && !url.isBlank() && appToken != null && !appToken.isBlank();
        }

        /** Endereço das telas: {@code https://glpi/apirest.php} → {@code https://glpi}. */
        public String enderecoWeb() {
            if (urlWeb != null && !urlWeb.isBlank()) return semBarraFinal(urlWeb);
            if (url == null) return "";
            return semBarraFinal(url.replaceFirst("/apirest\\.php/?$", ""));
        }

        private static String semBarraFinal(String s) {
            return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
        }
    }

    /**
     * Conta de serviço do GLPI (login desligado). Preferir o token pessoal da conta ({@code token}: Preferências →
     * Chaves de acesso remoto) a usuário e senha. O perfil dessa conta decide o que a aplicação vê e altera, e o
     * histórico do GLPI registra as alterações em nome dela.
     */
    public record ContaServico(String token, String usuario, String senha) {

        public boolean configurada() {
            return (token != null && !token.isBlank()) || (usuario != null && !usuario.isBlank() && senha != null && !senha.isEmpty());
        }
    }

    /**
     * Padrões (regex, sem diferenciar maiúsculas) para deduzir SSD × HDD pelo nome do modelo, quando o GLPI não
     * informa o tipo. Ficam na configuração porque aparecem modelos novos o tempo todo: quem encontrar um disco
     * "não identificado" acrescenta o padrão aqui, sem mexer no código (docs/MANUTENCAO.md, "Regra SSD × HDD").
     */
    public record Discos(List<String> padroesSsd, List<String> padroesHdd) {
        public Discos {
            padroesSsd = padroesSsd == null ? List.of() : List.copyOf(padroesSsd);
            padroesHdd = padroesHdd == null ? List.of() : List.copyOf(padroesHdd);
        }
    }
}
