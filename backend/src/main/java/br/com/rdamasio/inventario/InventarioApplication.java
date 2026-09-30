package br.com.rdamasio.inventario;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Inventário GLPI: ficha única de computador sobre a API do GLPI 10 (docs/PROJETO.md).
 * O backend é um intermediário (BFF): guarda o App-Token e a sessão GLPI de cada técnico, junta as várias
 * consultas que a ficha precisa e aplica as regras (soma da memória, SSD/HDD, IP principal). O banco próprio
 * guarda só os documentos e termos anexados por aqui (pacote documento); os ativos continuam só no GLPI.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class InventarioApplication {

    public static void main(String[] args) {
        SpringApplication.run(InventarioApplication.class, args);
    }
}
