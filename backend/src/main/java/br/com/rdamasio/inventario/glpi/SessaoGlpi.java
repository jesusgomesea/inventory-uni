package br.com.rdamasio.inventario.glpi;

import java.io.Serializable;

/**
 * Sessão aberta no GLPI em nome de um técnico. Fica só no servidor (na sessão HTTP): o navegador recebe apenas
 * o cookie da sessão desta aplicação, nunca o token do GLPI.
 *
 * @param token    Session-Token devolvido pelo initSession
 * @param usuarioId id do usuário no GLPI (glpiID)
 * @param login    nome de login (glpiname)
 * @param nome     nome para exibir ("Maria Souza"), cai para o login quando o cadastro não tem nome
 * @param perfil   perfil ativo no GLPI (ex.: "Technician"), só para exibir
 */
public record SessaoGlpi(String token, long usuarioId, String login, String nome, String perfil)
        implements Serializable {

    @Override
    public String toString() {
        // nunca imprimir o token em log
        return "SessaoGlpi[usuario=" + login + " (" + usuarioId + ")]";
    }
}
