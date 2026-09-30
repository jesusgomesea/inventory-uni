package br.com.rdamasio.inventario.computador;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.springframework.stereotype.Service;

import br.com.rdamasio.inventario.comum.ErroNegocio;
import br.com.rdamasio.inventario.comum.Json;
import br.com.rdamasio.inventario.comum.Paralelo;
import br.com.rdamasio.inventario.config.InventarioPropriedades;
import br.com.rdamasio.inventario.glpi.Catalogo;
import br.com.rdamasio.inventario.glpi.FalhaGlpi;
import br.com.rdamasio.inventario.glpi.GlpiCliente;
import br.com.rdamasio.inventario.glpi.Parametros;
import tools.jackson.databind.JsonNode;

/**
 * Monta a {@link Ficha} de um computador a partir de 6 consultas ao GLPI feitas em paralelo:
 * <ul>
 *   <li>{@code Computer/{id}} cru (ids dos dropdowns, para editar) e expandido (nomes, portas de rede, documentos);</li>
 *   <li>{@code Item_DeviceMemory}, {@code Item_DeviceHardDrive}, {@code Item_OperatingSystem}, {@code Item_Disk}.</li>
 * </ul>
 * Tipo e frequência da memória e interface do disco estão no modelo da peça, lido pelo {@link Catalogo}.
 * Se uma parte falhar (ex.: perfil sem direito a ver componentes), a ficha sai assim mesmo, com um aviso;
 * só a falta da máquina em si é erro.
 */
@Service
public class FichaServico {

    private final GlpiCliente glpi;
    private final Catalogo catalogo;
    private final RegraDisco regraDisco;
    private final InventarioPropriedades props;

    public FichaServico(GlpiCliente glpi, Catalogo catalogo, RegraDisco regraDisco, InventarioPropriedades props) {
        this.glpi = glpi;
        this.catalogo = catalogo;
        this.regraDisco = regraDisco;
        this.props = props;
    }

    public Ficha montar(String token, long id) {
        String caminho = "/Computer/" + id;
        var fBase = Paralelo.rodar(() -> glpi.get(token, caminho, null));
        var fExp = Paralelo.rodar(() -> glpi.get(token, caminho, Parametros.de()
                .com("expand_dropdowns", "true").com("with_networkports", "true").com("with_documents", "true")));
        var fMem = Paralelo.rodar(() -> subitens(token, id, "Item_DeviceMemory", false));
        var fHd = Paralelo.rodar(() -> subitens(token, id, "Item_DeviceHardDrive", false));
        var fSo = Paralelo.rodar(() -> subitens(token, id, "Item_OperatingSystem", true));
        var fVol = Paralelo.rodar(() -> subitens(token, id, "Item_Disk", true));

        JsonNode base;
        try {
            base = Paralelo.esperar(fBase);
        } catch (FalhaGlpi e) {
            if (e.naoEncontrado()) throw ErroNegocio.naoEncontrado("Computador " + id + " não encontrado no GLPI.");
            throw e;
        }
        if (Json.verdadeiro(base, "is_template")) throw ErroNegocio.naoEncontrado("O item " + id + " é um modelo, não um computador.");
        JsonNode exp = Paralelo.esperar(fExp);

        List<String> avisos = new ArrayList<>();
        if (Json.verdadeiro(base, "is_deleted")) avisos.add("Este computador está na lixeira do GLPI.");
        List<JsonNode> mem = parte(fMem, "a memória", avisos);
        List<JsonNode> hd = parte(fHd, "os discos", avisos);
        List<JsonNode> so = parte(fSo, "o sistema operacional", avisos);
        List<JsonNode> vol = parte(fVol, "os volumes", avisos);

        precarregar(token, "DeviceMemory", mem, "devicememories_id");
        precarregar(token, "DeviceHardDrive", hd, "deviceharddrives_id");

        String web = props.glpi().enderecoWeb();
        return new Ficha(
                id,
                Json.texto(base, "name"),
                Json.texto(base, "comment"),
                ref(base, exp, "states_id"),
                ref(base, exp, "users_id"),
                Json.texto(base, "contact"),
                ref(base, exp, "locations_id"),
                ref(base, exp, "users_id_tech"),
                ref(base, exp, "groups_id_tech"),
                nomeDropdown(exp, "manufacturers_id"),
                nomeDropdown(exp, "computermodels_id"),
                nomeDropdown(exp, "computertypes_id"),
                Json.texto(base, "serial"),
                Json.texto(base, "otherserial"),
                Json.texto(base, "uuid"),
                memoria(token, mem),
                armazenamento(token, hd, vol),
                sistema(so),
                rede(exp.path("_networkports")),
                documentosGlpi(exp.path("_documents")),
                Json.texto(base, "last_inventory_update"),
                Json.texto(base, "last_boot"),
                Json.texto(base, "date_creation"),
                Json.texto(base, "date_mod"),
                Json.verdadeiro(base, "is_dynamic"),
                web.isEmpty() ? null : web + "/front/computer.form.php?id=" + id,
                List.copyOf(avisos));
    }

    // ------------------------------------------------------------------ partes

    private Ficha.Memoria memoria(String token, List<JsonNode> linhas) {
        List<Ficha.Modulo> modulos = new ArrayList<>();
        long total = 0;
        for (JsonNode l : ativos(linhas)) {
            Optional<JsonNode> dev = catalogo.item(token, "DeviceMemory", Json.id(l, "devicememories_id"));
            Long tamanho = Json.numero(l, "size");
            if (tamanho == null || tamanho <= 0) tamanho = dev.map(d -> Json.numero(d, "size_default")).orElse(0L);
            String freq = dev.map(d -> Json.texto(d, "frequence")).orElse(null);
            if (Json.soDigitos(freq)) freq = freq + " MHz";
            modulos.add(new Ficha.Modulo(
                    Json.texto(l, "busID"),
                    tamanho,
                    dev.map(d -> nomeDropdown(d, "devicememorytypes_id")).orElse(null),
                    freq,
                    dev.map(d -> nomeDropdown(d, "manufacturers_id")).orElse(null),
                    dev.map(d -> Json.texto(d, "designation")).orElse(null),
                    Json.texto(l, "serial")));
            total += tamanho;
        }
        modulos.sort(Comparator.comparing(m -> ordemSlot(m.slot())));
        return new Ficha.Memoria(total, List.copyOf(modulos));
    }

    private Ficha.Armazenamento armazenamento(String token, List<JsonNode> linhas, List<JsonNode> volumes) {
        List<Ficha.Disco> discos = new ArrayList<>();
        long total = 0;
        for (JsonNode l : ativos(linhas)) {
            Optional<JsonNode> dev = catalogo.item(token, "DeviceHardDrive", Json.id(l, "deviceharddrives_id"));
            Long cap = Json.numero(l, "capacity");
            if (cap == null || cap <= 0) cap = dev.map(d -> Json.numero(d, "capacity_default")).orElse(0L);
            String designacao = dev.map(d -> Json.texto(d, "designation")).orElse(null);
            String modeloPeca = dev.map(d -> nomeDropdown(d, "deviceharddrivemodels_id")).orElse(null);
            String fabricante = dev.map(d -> nomeDropdown(d, "manufacturers_id")).orElse(null);
            String interfaceTipo = dev.map(d -> nomeDropdown(d, "interfacetypes_id")).orElse(null);
            // campo existe só em algumas versões do GLPI; sem ele a regra cai para interface e nome do modelo
            String tipoGlpi = dev.map(d -> nomeDropdown(d, "deviceharddrivetypes_id")).orElse(null);
            Long rpm = dev.map(d -> Json.numero(d, "rpm")).orElse(null);
            RegraDisco.Resultado r = regraDisco.classificar(tipoGlpi, interfaceTipo, rpm, designacao, modeloPeca, fabricante);
            discos.add(new Ficha.Disco(designacao != null ? designacao : modeloPeca, cap, r.tipo(), r.inferido(),
                    interfaceTipo, fabricante, Json.texto(l, "serial")));
            total += cap;
        }
        List<Ficha.Volume> vols = new ArrayList<>();
        for (JsonNode v : ativos(volumes)) {
            long tot = Optional.ofNullable(Json.numero(v, "totalsize")).orElse(0L);
            if (tot <= 0) continue;
            vols.add(new Ficha.Volume(Json.texto(v, "name"), Json.texto(v, "mountpoint"), tot,
                    Optional.ofNullable(Json.numero(v, "freesize")).orElse(0L), nomeDropdown(v, "filesystems_id")));
        }
        vols.sort(Comparator.comparing(v -> v.ponto() == null ? "~" : v.ponto()));
        return new Ficha.Armazenamento(total, List.copyOf(discos), List.copyOf(vols));
    }

    private static Ficha.SistemaOperacional sistema(List<JsonNode> linhas) {
        for (JsonNode l : ativos(linhas)) {
            return new Ficha.SistemaOperacional(
                    nomeDropdown(l, "operatingsystems_id"),
                    nomeDropdown(l, "operatingsystemversions_id"),
                    nomeDropdown(l, "operatingsystemeditions_id"),
                    nomeDropdown(l, "operatingsystemarchitectures_id"),
                    nomeDropdown(l, "operatingsystemkernelversions_id"));
        }
        return null;
    }

    /**
     * Portas de rede → IPs. A API entrega {@code _networkports} agrupado por tipo de porta, e o IP fica dentro da
     * porta, em NetworkName → IPAddress. Procuramos "IPAddress" em qualquer nível da porta porque o aninhamento
     * varia. IP principal = primeiro IPv4 que não seja loopback nem autoconfiguração (169.254).
     */
    static Ficha.Rede rede(JsonNode portas) {
        List<Ficha.Interface> interfaces = new ArrayList<>();
        if (portas != null && portas.isObject()) {
            for (Map.Entry<String, JsonNode> grupo : portas.properties()) {
                boolean local = grupo.getKey().equals("NetworkPortLocal");
                for (JsonNode porta : Json.itens(grupo.getValue())) {
                    Set<String> ips = new LinkedHashSet<>();
                    coletarIps(porta, ips);
                    if (local && ips.stream().allMatch(FichaServico::ipDeLoopback)) continue;
                    interfaces.add(new Ficha.Interface(Json.texto(porta, "name"), Json.texto(porta, "mac"), List.copyOf(ips)));
                }
            }
        }
        String principal = interfaces.stream().flatMap(i -> i.ips().stream())
                .filter(FichaServico::ipv4Util).findFirst().orElse(null);
        return new Ficha.Rede(principal, List.copyOf(interfaces));
    }

    private static void coletarIps(JsonNode no, Set<String> ips) {
        if (no == null || !no.isContainer()) return;
        if (no.isArray()) {
            for (JsonNode f : no) coletarIps(f, ips);
            return;
        }
        for (Map.Entry<String, JsonNode> e : no.properties()) {
            JsonNode v = e.getValue();
            if (e.getKey().equals("IPAddress")) {
                for (JsonNode ip : v.isContainer() ? v : List.of(v)) {
                    String s = ip.isObject() ? Json.texto(ip, "name") : ip.asString();
                    if (s != null && !s.isBlank()) ips.add(s.trim());
                }
            } else if (v.isContainer()) {
                coletarIps(v, ips);
            }
        }
    }

    static boolean ipv4Util(String ip) {
        return ip.matches("\\d{1,3}(\\.\\d{1,3}){3}") && !ip.startsWith("127.") && !ip.startsWith("169.254.") && !ip.equals("0.0.0.0");
    }

    private static boolean ipDeLoopback(String ip) {
        return ip.startsWith("127.") || ip.equals("::1");
    }

    /** {@code with_documents} traz o documento e o vínculo; "headings" é a rubrica, "assocdate" a data do vínculo. */
    private static List<DocumentoGlpi> documentosGlpi(JsonNode docs) {
        List<DocumentoGlpi> r = new ArrayList<>();
        for (JsonNode d : Json.itens(docs)) {
            Long id = Json.id(d, "id");
            if (id == null) continue;
            r.add(new DocumentoGlpi(id, Json.texto(d, "name"), Json.texto(d, "filename"), Json.texto(d, "mime"),
                    Json.texto(d, "headings"), Json.texto(d, "link"), Json.texto(d, "assocdate")));
        }
        return List.copyOf(r);
    }

    // ------------------------------------------------------------------ apoio

    private List<JsonNode> subitens(String token, long id, String tipo, boolean expandir) {
        Parametros p = Parametros.de().com("range", "0-199");
        if (expandir) p.com("expand_dropdowns", "true");
        return Json.itens(glpi.get(token, "/Computer/" + id + "/" + tipo, p));
    }

    private static List<JsonNode> parte(CompletableFuture<List<JsonNode>> f, String descricao, List<String> avisos) {
        try {
            return Paralelo.esperar(f);
        } catch (FalhaGlpi e) {
            if (e.sessaoInvalida()) throw e;
            if (e.naoEncontrado()) return List.of(); // alguns GLPI respondem 404 para "nenhum item"
            avisos.add("Não foi possível ler " + descricao + " (" + e.getMessage() + ").");
            return List.of();
        }
    }

    /** Busca em paralelo os modelos de peça ainda fora do cache (evita 1 chamada por vez). */
    private void precarregar(String token, String tipo, List<JsonNode> linhas, String campo) {
        List<CompletableFuture<?>> fs = new ArrayList<>();
        linhas.stream().map(l -> Json.id(l, campo)).distinct()
                .forEach(devId -> fs.add(Paralelo.rodar(() -> catalogo.item(token, tipo, devId))));
        fs.forEach(Paralelo::esperar);
    }

    /** Peças removidas do inventário ficam com is_deleted=1 no GLPI: não contam. */
    private static List<JsonNode> ativos(List<JsonNode> linhas) {
        return linhas.stream().filter(l -> !Json.verdadeiro(l, "is_deleted")).toList();
    }

    private static Ficha.Ref ref(JsonNode base, JsonNode exp, String campo) {
        Long id = Json.id(base, campo);
        if (id == null) return null;
        String nome = nomeDropdown(exp, campo);
        return new Ficha.Ref(id, nome != null ? nome : "#" + id);
    }

    /** Nome de um dropdown expandido; se veio só o número (não expandiu), devolve null. */
    static String nomeDropdown(JsonNode no, String campo) {
        String s = Json.texto(no, campo);
        return s == null || Json.soDigitos(s) ? null : s;
    }

    /** "2" antes de "10"; slots com nome ("DIMM A1") em ordem alfabética. */
    private static String ordemSlot(String slot) {
        if (slot == null) return "~";
        return Json.soDigitos(slot) ? String.format("%06d", Long.parseLong(slot)) : slot;
    }
}
