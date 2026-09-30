package br.com.rdamasio.inventario.sessao;

import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import br.com.rdamasio.inventario.comum.ErroNegocio;
import br.com.rdamasio.inventario.glpi.SessaoGlpi;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * Sessão GLPI do técnico da requisição atual, guardada na sessão HTTP desta aplicação (cookie
 * {@code INVENTARIO_SESSAO}). É o que faz cada técnico agir no GLPI com o próprio perfil e aparecer no histórico
 * com o próprio nome (docs/PROJETO.md §3).
 */
@Component
public class SessaoAtual {

    static final String ATRIBUTO = "glpi";

    /** Sessão do técnico; sem login → 401 SESSAO_EXPIRADA (a tela manda para o login). */
    public SessaoGlpi exigir() {
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
