package br.com.rdamasio.inventario.sessao;

import java.io.IOException;
import java.util.Set;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Proteção contra CSRF: a sessão vive num cookie, então outro site poderia disparar um POST (ex.: um formulário
 * de upload) em nome do técnico. Toda alteração em {@code /api} precisa do cabeçalho {@value #CABECALHO}, que um
 * formulário de outro site não consegue mandar; o frontend põe em todas as chamadas (core/interceptador-api.ts).
 * O cookie também é SameSite=Lax (application.yml). As duas coisas juntas: uma cobre a falha da outra.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class FiltroOrigem extends OncePerRequestFilter {

    public static final String CABECALHO = "X-Inventario";
    private static final Set<String> LEITURA = Set.of("GET", "HEAD", "OPTIONS");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest req) {
        return !req.getRequestURI().startsWith("/api/") || LEITURA.contains(req.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain cadeia)
            throws ServletException, IOException {
        if (!"1".equals(req.getHeader(CABECALHO))) {
            res.setStatus(HttpStatus.FORBIDDEN.value());
            res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            res.setCharacterEncoding("UTF-8");
            res.getWriter().write("{\"status\":403,\"detail\":\"Requisição sem o cabeçalho da aplicação.\",\"codigo\":\"ORIGEM\"}");
            return;
        }
        cadeia.doFilter(req, res);
    }
}
