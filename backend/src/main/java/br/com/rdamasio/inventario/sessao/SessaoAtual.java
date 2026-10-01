package br.com.rdamasio.inventario.sessao;

import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import br.com.rdamasio.inventario.comum.ErroNegocio;
import br.com.rdamasio.inventario.config.InventarioPropriedades;
import br.com.rdamasio.inventario.glpi.SessaoGlpi;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * Sessão GLPI do técnico da requisição atual, guardada na sessão HTTP desta aplicação (cookie
 * {@code INVENTARIO_SESSAO}). É o que faz cada técnico agir no GLPI com o próprio perfil e aparecer no histórico
 * com o próprio nome (docs/PROJETO.md §3).
 *
 * <p>Com o login desligado ({@code inventario.login.habilitado: false}, o padrão hoje), devolve a sessão da
 * {@link ContaServico} para todos. O resto do sistema não muda: continua pedindo a sessão aqui.
 */
@Component
public class SessaoAtual {

    static final String ATRIBUTO = "glpi";

    private final InventarioPropriedades props;
    private final ContaServico contaServico;

    public SessaoAtual(InventarioPropriedades props, ContaServico contaServico) {
        this.props = props;
        this.contaServico = contaServico;
    }

    public boolean loginHabilitado() {
        return props.loginHabilitado();
    }

    /** Sessão do técnico (ou da conta de serviço, com login desligado); sem login → 401 SESSAO_EXPIRADA. */
    public SessaoGlpi exigir() {
        if (!props.loginHabilitado()) return contaServico.sessao();
        HttpSession s = requisicao().getSession(false);
        Object v = s == null ? null : s.getAttribute(ATRIBUTO);
        if (v instanceof SessaoGlpi g) return g;
        throw ErroNegocio.semSessao();
    }

    public String token() {
        return exigir().token();
    }

    void guardar(SessaoGlpi sessao) {
        HttpServletRequest req = requisicao();
        HttpSession antiga = req.getSession(false);
        if (antiga != null) antiga.invalidate(); // sessão nova a cada login (evita fixação de sessão)
        req.getSession(true).setAttribute(ATRIBUTO, sessao);
    }

    /** Encerra a sessão desta aplicação (o token GLPI já não serve). */
    public void descartar() {
        HttpSession s = requisicao().getSession(false);
        if (s != null) s.invalidate();
    }

    private static HttpServletRequest requisicao() {
        return ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
    }
}
