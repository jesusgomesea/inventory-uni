package br.com.rdamasio.inventario.computador;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import br.com.rdamasio.inventario.config.InventarioPropriedades;

/**
 * Regra SSD × HDD (docs/PROJETO.md §2). O GLPI Agent nem sempre informa o tipo do disco, e no Windows
 * muitas vezes não informa. A ordem vai do mais confiável ao menos confiável:
 * <ol>
 *   <li>tipo informado no GLPI (campo do modelo de disco, quando existe);</li>
 *   <li>interface NVMe → SSD; rotação (rpm) informada → HDD;</li>
 *   <li>nome do modelo bate com um padrão de SSD, depois de HDD → tipo <b>inferido</b>;</li>
 *   <li>nada bateu → não identificado (null). Não chutamos HDD para não enganar quem planeja upgrade.</li>
 * </ol>
 * Os padrões dos passos 3 ficam em application.yml ({@code inventario.discos}), para acrescentar modelos novos
 * sem mexer no código. SSD é testado antes de HDD porque "WDC WDS240..." (SSD) começa como "WDC WD..." (HDD).
 */
@Component
public class RegraDisco {

    public record Resultado(String tipo, boolean inferido) {
        static final Resultado NAO_IDENTIFICADO = new Resultado(null, false);
    }

    private final List<Pattern> ssd;
    private final List<Pattern> hdd;

    public RegraDisco(InventarioPropriedades props) {
        this.ssd = compilar(props.discos() == null ? List.of() : props.discos().padroesSsd());
        this.hdd = compilar(props.discos() == null ? List.of() : props.discos().padroesHdd());
    }

    /**
     * @param tipoGlpi    tipo informado no GLPI (pode ser null)
     * @param interfaceTipo interface (SATA, NVMe...) (pode ser null)
     * @param rpm         rotação por minuto; 0 ou null = não informado
     * @param nomes       modelo, designação, fabricante: tudo que ajude a reconhecer o disco
     */
    public Resultado classificar(String tipoGlpi, String interfaceTipo, Long rpm, String... nomes) {
        if (tipoGlpi != null) {
            String t = tipoGlpi.toUpperCase(Locale.ROOT);
            if (t.contains("SSD") || t.contains("NVME") || t.contains("SOLID") || t.contains("FLASH")) return new Resultado("SSD", false);
            if (t.contains("HDD") || t.contains("HARD") || t.contains("MAGN") || t.contains("ROTA")) return new Resultado("HDD", false);
        }
        if (interfaceTipo != null && interfaceTipo.toUpperCase(Locale.ROOT).contains("NVME")) return new Resultado("SSD", false);
        if (rpm != null && rpm > 0) return new Resultado("HDD", false);
        for (String nome : nomes) {
            if (nome == null || nome.isBlank()) continue;
            if (bate(ssd, nome)) return new Resultado("SSD", true);
        }
        for (String nome : nomes) {
            if (nome == null || nome.isBlank()) continue;
            if (bate(hdd, nome)) return new Resultado("HDD", true);
        }
        return Resultado.NAO_IDENTIFICADO;
    }

    private static boolean bate(List<Pattern> padroes, String texto) {
        String t = texto.trim();
        for (Pattern p : padroes) {
            if (p.matcher(t).find()) return true;
        }
        return false;
    }

    private static List<Pattern> compilar(List<String> padroes) {
        return padroes.stream().map(p -> Pattern.compile(p, Pattern.CASE_INSENSITIVE)).toList();
    }
}
