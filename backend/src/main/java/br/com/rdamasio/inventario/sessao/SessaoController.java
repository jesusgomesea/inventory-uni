package br.com.rdamasio.inventario.sessao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.rdamasio.inventario.comum.ErroNegocio;
import br.com.rdamasio.inventario.config.InventarioPropriedades;
import br.com.rdamasio.inventario.glpi.GlpiCliente;
import br.com.rdamasio.inventario.glpi.SessaoGlpi;

/**
 * Login e logout. O login da aplicação é o login do GLPI: usuário e senha, ou o token pessoal do técnico
 * (Preferências → Chaves de acesso remoto no GLPI). A senha passa direto para o GLPI e não é guardada.
 *
 * <p>Login em espera: com {@code inventario.login.habilitado: false}, {@code GET} devolve a conta de serviço (com
 * {@code loginHabilitado = false}, para a tela esconder "Sair" e a página de login) e {@code POST} é recusado.
 */
@RestController
@RequestMapping("/api/sessao")
public class SessaoController {

    private static final Logger log = LoggerFactory.getLogger(SessaoController.class);

    public record Entrada(String login, String senha, String tokenPessoal) {
    }

    /** O que a tela sabe do técnico logado. {@code urlGlpi} alimenta os botões "Abrir no GLPI". */
    public record Usuario(long id, String login, String nome, String perfil, String urlGlpi, boolean loginHabilitado) {
    }

    private final GlpiCliente glpi;
    private final SessaoAtual sessao;
    private final InventarioPropriedades props;

    public SessaoController(GlpiCliente glpi, SessaoAtual sessao, InventarioPropriedades props) {
        this.glpi = glpi;
        this.sessao = sessao;
        this.props = props;
    }

    @PostMapping
    public Usuario entrar(@RequestBody Entrada e) {
        if (!sessao.loginHabilitado()) {
            throw new ErroNegocio(org.springframework.http.HttpStatus.CONFLICT, "LOGIN_DESLIGADO",
                    "O login está desligado nesta instalação; a aplicação usa a conta de serviço do GLPI.");
        }
        SessaoGlpi s;
        if (e.tokenPessoal() != null && !e.tokenPessoal().isBlank()) {
            s = glpi.iniciarComToken(e.tokenPessoal());
        } else {
            if (e.login() == null || e.login().isBlank() || e.senha() == null || e.senha().isEmpty()) {
                throw ErroNegocio.invalido("Informe usuário e senha do GLPI.");
            }
            s = glpi.iniciarComSenha(e.login().trim(), e.senha());
        }
        sessao.guardar(s);
        log.info("Login de {} ({})", s.login(), s.perfil());
        return usuario(s);
    }

    @GetMapping
    public Usuario atual() {
        return usuario(sessao.exigir());
    }

    @DeleteMapping
    public ResponseEntity<Void> sair() {
        if (!sessao.loginHabilitado()) return ResponseEntity.noContent().build(); // conta de serviço não sai
        try {
            SessaoGlpi s = sessao.exigir();
            glpi.encerrar(s.token());
            log.info("Logout de {}", s.login());
        } catch (ErroNegocio semSessao) {
            // já estava fora
        }
        sessao.descartar();
        return ResponseEntity.noContent().build();
    }

    private Usuario usuario(SessaoGlpi s) {
        return new Usuario(s.usuarioId(), s.login(), s.nome(), s.perfil(), props.glpi().enderecoWeb(), props.loginHabilitado());
    }
}
