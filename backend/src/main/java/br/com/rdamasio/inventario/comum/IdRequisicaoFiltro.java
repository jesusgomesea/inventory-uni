package br.com.rdamasio.inventario.comum;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Identificador de cada requisição ({@value #CABECALHO}), igual ao do HELP-AGENT: as linhas de log saem com
 * {@code [req=...]} e o erro devolvido à tela traz o mesmo id — é o "código para o suporte".
 * Só aceita id curto e sem caracteres estranhos, porque o valor vai para o log.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class IdRequisicaoFiltro extends OncePerRequestFilter {

    public static final String CABECALHO = "X-Request-Id";
    public static final String MDC_CHAVE = "req";
    private static final Pattern VALIDO = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain cadeia)
            throws ServletException, IOException {
        String id = req.getHeader(CABECALHO);
        if (id == null || !VALIDO.matcher(id).matches()) id = UUID.randomUUID().toString();
        MDC.put(MDC_CHAVE, id);
        res.setHeader(CABECALHO, id);
        try {
            cadeia.doFilter(req, res);
        } finally {
            MDC.remove(MDC_CHAVE);
        }
    }

    /** Id da requisição em andamento (null fora de uma requisição HTTP). */
    public static String atual() {
        return MDC.get(MDC_CHAVE);
    }
}
