package br.com.rdamasio.inventario.sessao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import br.com.rdamasio.inventario.comum.ErroNegocio;
import br.com.rdamasio.inventario.config.InventarioPropriedades;
import br.com.rdamasio.inventario.glpi.GlpiCliente;
import br.com.rdamasio.inventario.glpi.SessaoGlpi;
import jakarta.annotation.PostConstruct;

/**
 * Sessão única do GLPI usada por todos enquanto o login está desligado ({@code inventario.login.habilitado: false},
 * o padrão desde 30/09/2026). Abre na primeira chamada e é renovada sozinha quando o GLPI a encerra
 * ({@link GlpiCliente.RenovadorSessao}).
 *
 * <p>Consequência: o que a aplicação vê e altera é o que o perfil da conta de serviço permite, o histórico do GLPI
 * mostra a conta de serviço como autora, e os documentos anexados registram "enviado por" a conta de serviço.
 */
@Component
public class ContaServico {

    private static final Logger log = LoggerFactory.getLogger(ContaServico.class);

    private final GlpiCliente glpi;
    private final InventarioPropriedades props;
    private volatile SessaoGlpi sessao;

    public ContaServico(GlpiCliente glpi, InventarioPropriedades props) {
        this.glpi = glpi;
        this.props = props;
    }

    @PostConstruct
    void registrar() {
        if (!props.loginHabilitado()) glpi.registrarRenovador(this::renovar);
    }

    public SessaoGlpi sessao() {
        SessaoGlpi s = sessao;
        return s != null ? s : abrir(null);
    }

    /** Chamado quando o GLPI recusa o token: só renova se for o token atual da conta de serviço. */
    private String renovar(String expirado) {
        SessaoGlpi s = sessao;
        if (s == null || !s.token().equals(expirado)) return s == null ? null : s.token();
        log.info("[GLPI] sessão da conta de serviço expirou; abrindo outra");
        return abrir(expirado).token();
    }

    /** Uma thread abre por vez; quem chega depois usa a sessão que a primeira abriu. */
    private synchronized SessaoGlpi abrir(String expirado) {
        SessaoGlpi atual = sessao;
        if (atual != null && !atual.token().equals(expirado)) return atual;
        InventarioPropriedades.ContaServico c = props.glpi() == null ? null : props.glpi().contaServico();
        if (c == null || !c.configurada()) {
            throw new ErroNegocio(HttpStatus.SERVICE_UNAVAILABLE, "CONFIGURACAO",
                    "O login está desligado e a conta de serviço do GLPI não foi configurada "
                            + "(inventario.glpi.conta-servico em backend/config/application-local.yml).");
        }
        SessaoGlpi nova = c.token() != null && !c.token().isBlank()
                ? glpi.iniciarComToken(c.token())
                : glpi.iniciarComSenha(c.usuario(), c.senha());
        log.info("[GLPI] sessão da conta de serviço aberta: {} ({})", nova.login(), nova.perfil());
        sessao = nova;
        return nova;
    }
}
