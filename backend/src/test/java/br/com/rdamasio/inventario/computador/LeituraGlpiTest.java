package br.com.rdamasio.inventario.computador;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

/** Leitura dos formatos irregulares da API do GLPI: células da busca e portas de rede. */
class LeituraGlpiTest {

    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void celulaDaBuscaEmArray() {
        assertEquals(List.of("10.0.0.5", "fe80::1"), BuscaServico.valores(json.readTree("[\"10.0.0.5\", \"fe80::1\"]")));
    }

    @Test
    void celulaDaBuscaComSeparadoresInternosDoGlpi() {
        assertEquals(List.of("10.0.0.5", "10.0.0.6"),
                BuscaServico.valores(json.readTree("\"10.0.0.5$#$12$$##$$10.0.0.6$#$13\"")));
    }

    @Test
    void celulaVaziaOuNula() {
        assertEquals(List.of(), BuscaServico.valores(json.readTree("null")));
        assertEquals(List.of(), BuscaServico.valores(json.readTree("\"&nbsp;\"")));
    }

    @Test
    void ipPrincipalIgnoraLoopbackEAutoconfiguracao() {
        String portas = """
                {
                  "NetworkPortLocal": [ { "name": "lo", "NetworkName": { "IPAddress": [ { "name": "127.0.0.1" } ] } } ],
                  "NetworkPortWifi": [ { "name": "Wi-Fi", "mac": "aa:bb", "NetworkName": { "IPAddress": [ { "name": "169.254.1.2" }, { "name": "10.0.2.77" } ] } } ]
                }""";
        var rede = FichaServico.rede(json.readTree(portas));
        assertEquals("10.0.2.77", rede.ipPrincipal());
        assertEquals(1, rede.interfaces().size()); // a porta local só com loopback não aparece
    }

    @Test
    void semPortas() {
        assertNull(FichaServico.rede(json.readTree("[]")).ipPrincipal());
    }

    @Test
    void fimDaGarantia() {
        assertEquals("2027-02-10", DetalhesServico.fimGarantia("2024-02-10", 36L));
        assertNull(DetalhesServico.fimGarantia(null, 36L));
    }
}
