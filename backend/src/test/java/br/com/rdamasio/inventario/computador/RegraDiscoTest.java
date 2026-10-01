package br.com.rdamasio.inventario.computador;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import br.com.rdamasio.inventario.config.InventarioPropriedades;

/** Regra SSD × HDD com um recorte dos padrões do application.yml. */
class RegraDiscoTest {

    private final RegraDisco regra = new RegraDisco(new InventarioPropriedades(null,
            new InventarioPropriedades.Discos(
                    List.of("SSD", "NVME", "\\bSA400", "\\bWDS[0-9]", "\\bCT[0-9]+(BX|MX|P[0-9])"),
                    List.of("\\bST[0-9]{3,}", "\\bWDC WD[0-9]", "\\bTOSHIBA (DT|MQ|HDW|MG)")),
            null, null, null, null));

    @Test
    void tipoInformadoNoGlpiVenceONome() {
        var r = regra.classificar("HDD", null, null, "Samsung SSD 870");
        assertEquals("HDD", r.tipo());
        assertFalse(r.inferido());
    }

    @Test
    void nvmeEhSsdSemInferencia() {
        var r = regra.classificar(null, "NVMe", null, "Disco qualquer");
        assertEquals("SSD", r.tipo());
        assertFalse(r.inferido());
    }

    @Test
    void rotacaoInformadaEhHdd() {
        assertEquals("HDD", regra.classificar(null, "SATA", 7200L, "Disco qualquer").tipo());
    }

    @Test
    void modelosConhecidos() {
        assertEquals("SSD", regra.classificar(null, "SATA", 0L, "KINGSTON SA400S37240G").tipo());
        assertEquals("SSD", regra.classificar(null, null, null, "CT500MX500SSD1").tipo());
        assertEquals("HDD", regra.classificar(null, null, null, "ST1000DM010-2EP102").tipo());
        assertEquals("HDD", regra.classificar(null, null, null, "TOSHIBA DT01ACA100").tipo());
        assertTrue(regra.classificar(null, null, null, "ST1000DM010-2EP102").inferido());
    }

    /** "WDC WDS240..." é SSD, embora comece como os HDDs "WDC WD...": SSD é testado primeiro. */
    @Test
    void ssdDaWdNaoViraHdd() {
        assertEquals("SSD", regra.classificar(null, null, null, "WDC WDS240G2G0A-00JH30").tipo());
        assertEquals("HDD", regra.classificar(null, null, null, "WDC WD10EZEX-08WN4A0").tipo());
    }

    @Test
    void desconhecidoNaoChuta() {
        var r = regra.classificar(null, "SATA", 0L, "Disco genérico ATA");
        assertNull(r.tipo());
        assertFalse(r.inferido());
    }
}
