package br.com.rdamasio.inventario.glpi;

import java.time.Duration;
import java.util.Optional;

import org.springframework.stereotype.Component;

import br.com.rdamasio.inventario.comum.CacheTempo;
import br.com.rdamasio.inventario.comum.Json;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.NullNode;

/**
 * Catálogo de modelos de peças do GLPI ({@code DeviceMemory}, {@code DeviceHardDrive}, {@code DeviceProcessor}...)
 * e nomes de itens por id, com cache de 1 hora.
 *
 * <p>Por que existe: no GLPI a memória instalada ({@code Item_DeviceMemory}) guarda só tamanho e slot; o tipo
 * (DDR4), a frequência e o fabricante ficam no modelo ({@code DeviceMemory}). A mesma peça se repete em centenas
 * de máquinas, então vale guardar. O cache é global (não por técnico) porque é catálogo, não dado da máquina:
 * quem pede já passou pela checagem de permissão do GLPI ao ler a máquina.
 */
@Component
public class Catalogo {

    private final GlpiCliente glpi;
    private final CacheTempo<String, JsonNode> itens = new CacheTempo<>(Duration.ofHours(1), 20_000);

    public Catalogo(GlpiCliente glpi) {
        this.glpi = glpi;
    }

    /** Item com os dropdowns expandidos (nomes em vez de ids). Vazio se não existir ou sem permissão. */
    public Optional<JsonNode> item(String token, String tipo, Long id) {
        if (id == null || id <= 0) return Optional.empty();
        JsonNode no = itens.obter(tipo + "#" + id, () -> {
            try {
                return glpi.get(token, "/" + tipo + "/" + id, Parametros.de().com("expand_dropdowns", "true"));
            } catch (FalhaGlpi e) {
                if (e.sessaoInvalida()) throw e;
                return NullNode.getInstance();
            }
        });
        return no.isObject() ? Optional.of(no) : Optional.empty();
    }

    /** Nome de um item (campo {@code name}, ou {@code designation} nas peças). */
    public String nome(String token, String tipo, Long id) {
        return item(token, tipo, id).map(n -> {
            String s = Json.texto(n, "name");
            return s != null ? s : Json.texto(n, "designation");
        }).orElse(null);
    }
}
