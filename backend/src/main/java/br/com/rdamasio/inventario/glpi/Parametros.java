package br.com.rdamasio.inventario.glpi;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Parâmetros de consulta no formato do PHP ({@code criteria[0][field]=1}), na ordem em que foram incluídos.
 *
 * <p>Codifica cada chave e valor com {@link URLEncoder} de propósito: o PHP lê "+" como espaço, então um "+"
 * dentro do valor (busca por "C++") precisa ir como %2B. O {@code UriComponentsBuilder.encode()} do Spring
 * deixa o "+" passar e a busca chegaria errada ao GLPI.
 */
public final class Parametros {

    private final List<Map.Entry<String, String>> pares = new ArrayList<>();

    public static Parametros de() {
        return new Parametros();
    }

    public Parametros com(String chave, Object valor) {
        if (valor != null) pares.add(Map.entry(chave, String.valueOf(valor)));
        return this;
    }

    /** Critério da busca ({@code search/}): {@code prefixo} é "criteria[0]" ou "criteria[0][criteria][1]". */
    public Parametros criterio(String prefixo, String ligacao, int campo, String tipoBusca, Object valor) {
        if (ligacao != null) com(prefixo + "[link]", ligacao);
        return com(prefixo + "[field]", campo).com(prefixo + "[searchtype]", tipoBusca).com(prefixo + "[value]", valor);
    }

    public boolean vazio() {
        return pares.isEmpty();
    }

    public String consulta() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> p : pares) {
            if (!sb.isEmpty()) sb.append('&');
            sb.append(URLEncoder.encode(p.getKey(), StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(p.getValue(), StandardCharsets.UTF_8));
        }
        return sb.toString();
    }
}
