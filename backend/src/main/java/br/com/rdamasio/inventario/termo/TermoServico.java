package br.com.rdamasio.inventario.termo;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import br.com.rdamasio.inventario.comum.ErroNegocio;
import br.com.rdamasio.inventario.computador.Ficha;
import br.com.rdamasio.inventario.config.InventarioPropriedades;
import br.com.rdamasio.inventario.glpi.SessaoGlpi;
import tools.jackson.databind.json.JsonMapper;

/**
 * Termos de equipamento: integra os modelos HTML que a TI já usa (pasta "Model Termos Eqp"), sem alterá-los.
 *
 * <p>Como funciona: lê o modelo do disco, injeta antes do {@code </body>} os dados da ficha (em
 * {@code window.__termo}) e o script {@code termo/preencher.js}, que preenche os campos pelo texto dos rótulos.
 * O técnico confere, completa o que o GLPI não tem (cargo, setor, chamado), imprime pelo botão do próprio modelo,
 * colhe a assinatura e anexa o termo assinado em "Documentos e termos" da ficha.
 *
 * <p>Os modelos são da TI: se mudarem o texto de um rótulo, o campo correspondente deixa de ser preenchido, mas o
 * termo continua funcionando. Os rótulos esperados estão em {@link #campos}.
 */
@Service
public class TermoServico {

    /** Modelos disponíveis: id usado na URL → arquivo na pasta de modelos. */
    public enum Modelo {
        MOVIMENTACAO("termo_movimentacao_equipamento_ti.html", "Termo de movimentação",
                "Recebimento, devolução, empréstimo ou transferência"),
        SUBSTITUICAO("termo_substituicao_equipamento_ti.html", "Termo de substituição",
                "Troca de um equipamento por outro; esta máquina entra como o equipamento antigo");

        final String arquivo;
        final String nome;
        final String descricao;

        Modelo(String arquivo, String nome, String descricao) {
            this.arquivo = arquivo;
            this.nome = nome;
            this.descricao = descricao;
        }

        public String nome() {
            return nome;
        }

        public String descricao() {
            return descricao;
        }

        static Modelo de(String id) {
            try {
                return valueOf(id.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw ErroNegocio.naoEncontrado("Modelo de termo desconhecido: " + id);
            }
        }
    }

    /** Um campo a preencher: rótulo como está no modelo, e a seção (título h2) onde procurar. */
    record Campo(String secao, String rotulo, String valor) {
    }

    private final Path pasta;
    private final JsonMapper json;
    private final String script;

    public TermoServico(InventarioPropriedades props, JsonMapper json) {
        String p = props.termos() == null ? "../Model Termos Eqp" : props.termos().pasta();
        this.pasta = Path.of(p).toAbsolutePath().normalize();
        this.json = json;
        try (InputStream in = new ClassPathResource("termo/preencher.js").getInputStream()) {
            this.script = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** HTML do modelo, já com os dados da máquina. {@code tecnico} null = login desligado (encarregado em branco). */
    public String gerar(String modelo, Ficha f, SessaoGlpi tecnico, String chamado) {
        Modelo m = Modelo.de(modelo);
        String html = ler(m);
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("campos", campos(m, f, tecnico));
        dados.put("data", LocalDate.now().toString());
        if (chamado != null && chamado.matches("\\d{1,10}")) dados.put("chamado", chamado);
        String injecao = "<script>window.__termo = " + jsonParaScript(dados) + ";</script>\n<script>" + script + "</script>\n";
        int fim = html.toLowerCase(Locale.ROOT).lastIndexOf("</body>");
        return fim < 0 ? html + injecao : html.substring(0, fim) + injecao + html.substring(fim);
    }

    /**
     * O que vem do GLPI para cada modelo. Rótulos exatamente como no HTML (sem acento, como a TI escreveu);
     * a comparação ignora maiúsculas e acentos e aceita o começo do texto.
     */
    static List<Campo> campos(Modelo m, Ficha f, SessaoGlpi tecnico) {
        List<Campo> c = new ArrayList<>();
        String colaborador = f.responsavel() == null ? null : f.responsavel().nome();
        String local = f.local() == null ? null : f.local().nome();
        String modelo = juntar(f.fabricante(), f.modelo());
        c.add(new Campo("1. Dados do Colaborador", "Nome do Colaborador", colaborador));
        c.add(new Campo("1. Dados do Colaborador", "Loja / Unidade", local));
        if (m == Modelo.MOVIMENTACAO) {
            // sem login (conta de serviço) não se sabe quem é o técnico: o campo fica para ele preencher
            if (tecnico != null) c.add(new Campo("Dados do Encarregado", "Nome do Encarregado", tecnico.nome()));
            c.add(new Campo("Dados do Equipamento", "Tipo de Equipamento", tipoEquipamento(f.tipo())));
            c.add(new Campo("Dados do Equipamento", "Modelo", modelo));
            c.add(new Campo("Dados do Equipamento", "No de Serie", f.serial()));
            c.add(new Campo("Dados do Equipamento", "No de Patrimonio", f.patrimonio()));
        } else {
            c.add(new Campo("Equipamento Substituido", "Tipo de Equipamento", tipoEquipamento(f.tipo())));
            c.add(new Campo("Equipamento Substituido", "Marca / Modelo", modelo));
            c.add(new Campo("Equipamento Substituido", "No de Serie", f.serial()));
            c.add(new Campo("Equipamento Substituido", "No de Patrimonio", f.patrimonio()));
        }
        c.removeIf(x -> x.valor() == null || x.valor().isBlank());
        return c;
    }

    /** Tipo do GLPI (Desktop, Notebook, Laptop, Mini PC...) → opção da lista do modelo. Sem par, fica em branco. */
    static String tipoEquipamento(String tipoGlpi) {
        if (tipoGlpi == null) return null;
        String t = tipoGlpi.toLowerCase(Locale.ROOT);
        if (t.contains("note") || t.contains("laptop")) return "Notebook";
        if (t.contains("desktop") || t.contains("mini") || t.contains("tower") || t.contains("torre") || t.contains("all")) return "Desktop";
        return null;
    }

    /**
     * JSON seguro dentro de {@code <script>}: nomes vêm do GLPI e poderiam conter "</script>". Os valores entram
     * nos campos por {@code .value}, nunca como HTML.
     */
    String jsonParaScript(Object dados) {
        return json.writeValueAsString(dados)
                .replace("<", "\\u003c").replace(">", "\\u003e").replace("&", "\\u0026")
                .replace("\u2028", "\\u2028").replace("\u2029", "\\u2029");
    }

    private String ler(Modelo m) {
        Path arquivo = pasta.resolve(m.arquivo).normalize();
        try {
            return Files.readString(arquivo, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ErroNegocio(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "MODELO_AUSENTE",
                    "O modelo \"" + m.arquivo + "\" não foi encontrado em " + pasta + " (inventario.termos.pasta).");
        }
    }

    private static String juntar(String a, String b) {
        if (a == null) return b;
        if (b == null) return a;
        return a + " " + b;
    }
}
