package br.com.rdamasio.inventario.comum;

import org.springframework.http.HttpStatus;

/**
 * Erro que o usuário entende e pode resolver (dado inválido, máquina alterada por outra pessoa, sem sessão).
 * Carrega o status HTTP e um código estável que a tela usa para decidir o que fazer (ex.: SESSAO_EXPIRADA → login).
 */
public class ErroNegocio extends RuntimeException {

    private final HttpStatus status;
    private final String codigo;

    public ErroNegocio(HttpStatus status, String codigo, String mensagem) {
        super(mensagem);
        this.status = status;
        this.codigo = codigo;
    }

    public static ErroNegocio invalido(String mensagem) {
        return new ErroNegocio(HttpStatus.UNPROCESSABLE_CONTENT, "DADOS_INVALIDOS", mensagem);
    }

    public static ErroNegocio naoEncontrado(String mensagem) {
        return new ErroNegocio(HttpStatus.NOT_FOUND, "NAO_ENCONTRADO", mensagem);
    }

    public static ErroNegocio semSessao() {
        return new ErroNegocio(HttpStatus.UNAUTHORIZED, "SESSAO_EXPIRADA", "Sua sessão terminou. Entre de novo.");
    }

    public HttpStatus status() {
        return status;
    }

    public String codigo() {
        return codigo;
    }
}
