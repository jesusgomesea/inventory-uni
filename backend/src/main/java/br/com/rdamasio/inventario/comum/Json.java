package br.com.rdamasio.inventario.comum;

import java.util.ArrayList;
import java.util.List;

import tools.jackson.databind.JsonNode;

/**
 * Leitura tolerante das respostas do GLPI. A API legada é irregular: o mesmo campo vem como número, texto,
 * {@code null} ou ausente conforme a versão e o {@code expand_dropdowns}; listas às vezes vêm como objeto
 * indexado por id. Estes utilitários concentram essas conversões para o resto do código não se preocupar.
 */
public final class Json {

    private Json() {
    }

    /** Texto do campo; null quando ausente, nulo ou vazio. Números viram texto. */
    public static String texto(JsonNode no, String campo) {
        if (no == null) return null;
        JsonNode v = no.get(campo);
        if (v == null || v.isNull() || v.isMissingNode() || v.isContainer()) return null;
        String s = v.asString().trim();
        // dropdown vazio expandido às vezes chega como "&nbsp;" (herança das telas do GLPI)
        return s.isEmpty() || s.equals("&nbsp;") ? null : s;
    }

    /** Número do campo; null quando ausente ou não numérico. Aceita "123" (o GLPI às vezes manda texto). */
    public static Long numero(JsonNode no, String campo) {
        String s = texto(no, campo);
        if (s == null) return null;
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            try {
                return (long) Double.parseDouble(s);
            } catch (NumberFormatException e2) {
                return null;
            }
        }
    }

    /** Id de dropdown: 0 no GLPI significa "nenhum", aqui vira null. */
    public static Long id(JsonNode no, String campo) {
        Long n = numero(no, campo);
        return n == null || n <= 0 ? null : n;
    }

    public static boolean verdadeiro(JsonNode no, String campo) {
        String s = texto(no, campo);
        return s != null && (s.equals("1") || s.equalsIgnoreCase("true"));
    }

    /** Elementos de uma lista, venha ela como array ou como objeto {"id": {...}}. */
    public static List<JsonNode> itens(JsonNode no) {
        List<JsonNode> r = new ArrayList<>();
        if (no == null || !no.isContainer()) return r;
        for (JsonNode filho : no) {
            if (filho != null && filho.isObject()) r.add(filho);
        }
        return r;
    }

    /** O texto é só dígitos? (ex.: dropdown que não foi expandido e veio como id) */
    public static boolean soDigitos(String s) {
        return s != null && !s.isEmpty() && s.chars().allMatch(Character::isDigit);
    }
}
