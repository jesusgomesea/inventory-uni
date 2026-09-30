package br.com.rdamasio.inventario.glpi;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** O "+" precisa ir codificado: o PHP lê "+" como espaço. */
class ParametrosTest {

    @Test
    void codificaNoFormatoDoPhp() {
        String q = Parametros.de().criterio("criteria[0]", null, 1, "contains", "C++ 10").consulta();
        assertEquals("criteria%5B0%5D%5Bfield%5D=1&criteria%5B0%5D%5Bsearchtype%5D=contains&criteria%5B0%5D%5Bvalue%5D=C%2B%2B+10", q);
    }
}
