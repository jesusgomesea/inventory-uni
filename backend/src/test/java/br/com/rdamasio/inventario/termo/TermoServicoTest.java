package br.com.rdamasio.inventario.termo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import br.com.rdamasio.inventario.computador.Ficha;
import br.com.rdamasio.inventario.config.InventarioPropriedades;
import br.com.rdamasio.inventario.glpi.SessaoGlpi;
import tools.jackson.databind.json.JsonMapper;

/** Usa os modelos de verdade da pasta "Model Termos Eqp" (os testes rodam em backend/). */
class TermoServicoTest {

    private final TermoServico servico = new TermoServico(
            new InventarioPropriedades(null, null, null, new InventarioPropriedades.Termos("../Model Termos Eqp"), null),
            JsonMapper.builder().build());

    private final SessaoGlpi tecnico = new SessaoGlpi("t", 2, "tecnico", "Técnico Demonstração", null);

    private Ficha ficha(String responsavel) {
        return new Ficha(101, "RD-LJ014-CX02", null, null, new Ficha.Ref(7L, responsavel), null,
                new Ficha.Ref(5L, "Loja 14 > Balcão"), null, null, "Dell Inc.", "OptiPlex 3080", "Desktop", "7XK2Q93",
                "PAT-00412", null, new Ficha.Memoria(0, List.of()), new Ficha.Armazenamento(0, List.of(), List.of()), null,
                new Ficha.Rede(null, List.of()), List.of(), null, null, null, null, true, null, List.of());
    }

    @Test
    void injetaDadosAntesDoFimDoBody() {
        String html = servico.gerar("movimentacao", ficha("Maria Souza"), tecnico, "6013");
        assertTrue(html.contains("TERMO DE <span id=\"tituloTipo\">"), "o modelo original continua lá");
        int dados = html.indexOf("window.__termo");
        assertTrue(dados > 0 && dados < html.lastIndexOf("</body>"));
        assertTrue(html.contains("\"Maria Souza\""));
        assertTrue(html.contains("\"7XK2Q93\""));
        assertTrue(html.contains("\"chamado\":\"6013\""));
    }

    /** Um nome vindo do GLPI com "</script>" não pode fechar o script e injetar HTML. */
    @Test
    void nomeMaliciosoNaoQuebraOScript() {
        String html = servico.gerar("substituicao", ficha("</script><img src=x onerror=alert(1)>"), tecnico, null);
        assertFalse(html.contains("</script><img"));
        assertTrue(html.contains("\\u003c/script\\u003e"));
    }

    @Test
    void substituicaoPreencheOEquipamentoAntigo() {
        var campos = TermoServico.campos(TermoServico.Modelo.SUBSTITUICAO, ficha("Maria"), tecnico);
        assertTrue(campos.stream().anyMatch(c -> c.secao().equals("Equipamento Substituido") && c.valor().equals("7XK2Q93")));
    }

    @Test
    void tipoDoGlpiParaOpcaoDoModelo() {
        assertEquals("Notebook", TermoServico.tipoEquipamento("Laptop"));
        assertEquals("Desktop", TermoServico.tipoEquipamento("Mini PC"));
        assertEquals(null, TermoServico.tipoEquipamento("Servidor"));
    }
}
