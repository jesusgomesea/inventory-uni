package br.com.rdamasio.inventario.comum;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import br.com.rdamasio.inventario.glpi.FalhaGlpi;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * Erros no formato RFC 9457 (ProblemDetail) com {@code codigo} (a tela decide por ele) e {@code idRequisicao}
 * (o mesmo das linhas de log). Aqui fica a tradução dos códigos do GLPI para mensagens que o técnico entende.
 */
@RestControllerAdvice
public class TratadorErros {

    private static final Logger log = LoggerFactory.getLogger(TratadorErros.class);

    @ExceptionHandler(ErroNegocio.class)
    ProblemDetail negocio(ErroNegocio e) {
        return problema(e.status(), e.codigo(), e.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail uploadGrande(MaxUploadSizeExceededException e) {
        return problema(HttpStatus.CONTENT_TOO_LARGE, "ARQUIVO_GRANDE", "O arquivo passa do limite de 50 MB.");
    }

    @ExceptionHandler(FalhaGlpi.class)
    ProblemDetail glpi(FalhaGlpi e, HttpServletRequest req) {
        String c = e.codigo();
        if (e.sessaoInvalida()) {
            HttpSession s = req.getSession(false);
            if (s != null) s.invalidate();
            return problema(HttpStatus.UNAUTHORIZED, "SESSAO_EXPIRADA", "Sua sessão no GLPI terminou. Entre de novo.");
        }
        if (c.equals("ERROR_GLPI_LOGIN") || c.equals("ERROR_LOGIN_PARAMETERS_MISSING")) {
            return problema(HttpStatus.UNAUTHORIZED, "LOGIN_INVALIDO", "Usuário, senha ou token incorretos.");
        }
        if (c.contains("APP_TOKEN")) {
            log.error("[GLPI] App-Token recusado: {} {}", c, e.getMessage());
            return problema(HttpStatus.BAD_GATEWAY, "CONFIGURACAO",
                    "O GLPI recusou o App-Token desta aplicação. Avise a TI (configuração do servidor).");
        }
        if (c.equals("ERROR_NOT_ALLOWED_IP")) {
            log.error("[GLPI] IP do servidor não autorizado no cliente de API: {}", e.getMessage());
            return problema(HttpStatus.BAD_GATEWAY, "CONFIGURACAO",
                    "O GLPI não aceita chamadas deste servidor (faixa de IP do cliente de API). Avise a TI.");
        }
        if (c.contains("DISABLED")) {
            return problema(HttpStatus.BAD_GATEWAY, "LOGIN_DESABILITADO",
                    "O GLPI não permite este tipo de login pela API. Tente com o token pessoal, ou peça à TI para habilitar.");
        }
        if (c.equals("ERROR_RIGHT_MISSING") || e.status() == 403) {
            return problema(HttpStatus.FORBIDDEN, "SEM_PERMISSAO", "Seu perfil no GLPI não tem permissão para isso.");
        }
        if (e.naoEncontrado()) {
            return problema(HttpStatus.NOT_FOUND, "NAO_ENCONTRADO", "Não encontrado no GLPI (ou fora das suas entidades).");
        }
        if (c.equals("NAO_CONFIGURADO")) {
            return problema(HttpStatus.SERVICE_UNAVAILABLE, "CONFIGURACAO", e.getMessage());
        }
        if (c.equals(FalhaGlpi.SEM_RESPOSTA)) {
            return problema(HttpStatus.GATEWAY_TIMEOUT, "GLPI_FORA", e.getMessage());
        }
        log.warn("[GLPI] erro não tratado: {} {} {}", e.status(), c, e.getMessage());
        String msg = e.getMessage() == null || e.getMessage().isBlank() ? c : e.getMessage();
        return problema(HttpStatus.BAD_GATEWAY, "GLPI", "O GLPI recusou a operação: " + msg);
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail inesperado(Exception e) {
        // erros do próprio Spring (404 de rota, método errado, JSON malformado) já sabem o status certo
        if (e instanceof ErrorResponse er) return er.getBody();
        log.error("Erro inesperado", e);
        return problema(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNO", "Erro inesperado. Informe o código ao suporte.");
    }

    private static ProblemDetail problema(HttpStatus status, String codigo, String mensagem) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, mensagem);
        pd.setProperty("codigo", codigo);
        String id = IdRequisicaoFiltro.atual();
        if (id != null) pd.setProperty("idRequisicao", id);
        return pd;
    }
}
