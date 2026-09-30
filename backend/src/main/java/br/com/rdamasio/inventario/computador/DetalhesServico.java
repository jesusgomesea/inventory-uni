package br.com.rdamasio.inventario.computador;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import org.springframework.stereotype.Service;

import br.com.rdamasio.inventario.comum.Json;
import br.com.rdamasio.inventario.comum.Paralelo;
import br.com.rdamasio.inventario.glpi.Catalogo;
import br.com.rdamasio.inventario.glpi.FalhaGlpi;
import br.com.rdamasio.inventario.glpi.GlpiCliente;
import br.com.rdamasio.inventario.glpi.OpcoesBusca;
import br.com.rdamasio.inventario.glpi.Parametros;
import tools.jackson.databind.JsonNode;

/**
 * "Ver mais informações": o que não cabe na ficha e só é carregado quando o técnico pede — processador, placa
 * de vídeo, placa-mãe, BIOS, softwares, antivírus, itens conectados, dados financeiros, chamados e histórico.
 *
 * <p>Cada seção é independente: se uma falhar (perfil sem direito a ver chamados, por exemplo), ela volta com
 * {@code erro} preenchido e as outras aparecem normalmente.
 */
@Service
public class DetalhesServico {

    public record Secao<T>(T dados, String erro) {
        static <T> Secao<T> ok(T dados) {
            return new Secao<>(dados, null);
        }
    }

    /** Peça genérica: nome e pares "rótulo → valor" já prontos para mostrar. */
    public record Componente(String tipo, String nome, String fabricante, List<Par> detalhes) {
    }

    public record Par(String rotulo, String valor) {
    }

    public record Software(String nome, String versao) {
    }

    public record Antivirus(String nome, String versao, String assinatura, boolean ativo, boolean atualizado,
            String validade) {
    }

    public record Conectado(String tipo, String nome, String modelo, String serial) {
    }

    /** Dados financeiros (Infocom). {@code fimGarantia} = início da garantia + meses de duração. */
    public record Financeiro(String compra, String inicioUso, String inicioGarantia, Integer mesesGarantia,
            String fimGarantia, String valor, String fornecedor, String pedido, String notaFiscal) {
    }

    public record Chamado(long id, String titulo, String status, String abertoEm) {
    }

    public record Evento(String data, String usuario, String descricao) {
    }

    public record Detalhes(Secao<List<Componente>> componentes, Secao<List<Software>> softwares,
            Secao<List<Antivirus>> antivirus, Secao<List<Conectado>> conectados, Secao<Financeiro> financeiro,
            Secao<List<Chamado>> chamados, Secao<List<Evento>> historico) {
    }

    private final GlpiCliente glpi;
    private final Catalogo catalogo;
    private final OpcoesBusca opcoes;

    public DetalhesServico(GlpiCliente glpi, Catalogo catalogo, OpcoesBusca opcoes) {
        this.glpi = glpi;
        this.catalogo = catalogo;
        this.opcoes = opcoes;
    }

    public Detalhes carregar(String token, long id) {
        var comp = secao(() -> componentes(token, id));
        var soft = secao(() -> softwares(token, id));
        var av = secao(() -> antivirus(token, id));
        var con = secao(() -> conectados(token, id));
        var fin = secao(() -> financeiro(token, id));
        var cha = secao(() -> chamados(token, id));
        var his = secao(() -> historico(token, id));
        return new Detalhes(Paralelo.esperar(comp), Paralelo.esperar(soft), Paralelo.esperar(av),
                Paralelo.esperar(con), Paralelo.esperar(fin), Paralelo.esperar(cha), Paralelo.esperar(his));
    }

    // ------------------------------------------------------------------ seções

    private List<Componente> componentes(String token, long id) {
        List<Componente> r = new ArrayList<>();
        r.addAll(pecas(token, id, "Processador", "Item_DeviceProcessor", "DeviceProcessor", "deviceprocessors_id", (l, d) -> List.of(
                par("Frequência", mhz(Json.texto(l, "frequency"), Json.texto(d, "frequence"))),
                par("Núcleos", primeiro(Json.texto(l, "nbcores"), Json.texto(d, "nbcores_default"))),
                par("Threads", primeiro(Json.texto(l, "nbthreads"), Json.texto(d, "nbthreads_default"))))));
        r.addAll(pecas(token, id, "Placa de vídeo", "Item_DeviceGraphicCard", "DeviceGraphicCard", "devicegraphiccards_id", (l, d) -> List.of(
                par("Memória", mb(primeiro(Json.texto(l, "memory"), Json.texto(d, "memory_default")))),
                par("Chipset", Json.texto(d, "chipset")))));
        r.addAll(pecas(token, id, "Placa-mãe", "Item_DeviceMotherboard", "DeviceMotherboard", "devicemotherboards_id", (l, d) -> List.of(
                par("Chipset", Json.texto(d, "chipset")),
                par("Serial", Json.texto(l, "serial")))));
        r.addAll(pecas(token, id, "BIOS / firmware", "Item_DeviceFirmware", "DeviceFirmware", "devicefirmwares_id", (l, d) -> List.of(
                par("Versão", Json.texto(d, "version")),
                par("Data", Json.texto(d, "date")))));
        r.addAll(pecas(token, id, "Placa de rede", "Item_DeviceNetworkCard", "DeviceNetworkCard", "devicenetworkcards_id", (l, d) -> List.of(
                par("MAC", Json.texto(l, "mac")),
                par("Velocidade", Json.texto(d, "bandwidth")))));
        return r;
    }

    private interface Detalhador {
        List<Par> detalhes(JsonNode linha, JsonNode peca);
    }

    private List<Componente> pecas(String token, long id, String rotulo, String item, String device, String campo, Detalhador det) {
        List<Componente> r = new ArrayList<>();
        List<JsonNode> linhas;
        try {
            linhas = Json.itens(glpi.get(token, "/Computer/" + id + "/" + item, Parametros.de().com("range", "0-49")));
        } catch (FalhaGlpi e) {
            if (e.sessaoInvalida()) throw e;
            return r; // sem essa peça (ou sem direito a ver): não é erro da seção inteira
        }
        for (JsonNode l : linhas) {
            if (Json.verdadeiro(l, "is_deleted")) continue;
            JsonNode d = catalogo.item(token, device, Json.id(l, campo)).orElse(l);
            List<Par> pares = det.detalhes(l, d).stream().filter(p -> p.valor() != null).toList();
            r.add(new Componente(rotulo, Json.texto(d, "designation"), FichaServico.nomeDropdown(d, "manufacturers_id"), pares));
        }
        return r;
    }

    /**
     * {@code with_softwares} traz os ids do software e da versão; quando o GLPI não os expande, os nomes vêm do
     * {@link Catalogo} (cache de 1 h). Limitado a 400 itens para uma máquina "suja" não travar a tela.
     */
    private List<Software> softwares(String token, long id) {
        JsonNode r = glpi.get(token, "/Computer/" + id, Parametros.de().com("with_softwares", "true").com("expand_dropdowns", "true"));
        List<JsonNode> linhas = Json.itens(r.path("_softwares")).stream().limit(400).toList();
        List<CompletableFuture<Software>> fs = linhas.stream().map(l -> Paralelo.rodar(() -> {
            String nome = Json.texto(l, "softwares_id");
            if (Json.soDigitos(nome)) nome = catalogo.nome(token, "Software", Long.parseLong(nome));
            String versao = Json.texto(l, "softwareversions_id");
            if (Json.soDigitos(versao)) versao = catalogo.nome(token, "SoftwareVersion", Long.parseLong(versao));
            return new Software(nome, versao);
        })).toList();
        return fs.stream().map(Paralelo::esperar).filter(s -> s.nome() != null)
                .sorted(Comparator.comparing(s -> s.nome().toLowerCase())).toList();
    }

    private List<Antivirus> antivirus(String token, long id) {
        List<Antivirus> r = new ArrayList<>();
        for (JsonNode l : subitens(token, id, "ComputerAntivirus")) {
            if (Json.verdadeiro(l, "is_deleted")) continue;
            r.add(new Antivirus(Json.texto(l, "name"), Json.texto(l, "antivirus_version"), Json.texto(l, "signature_version"),
                    Json.verdadeiro(l, "is_active"), Json.verdadeiro(l, "is_uptodate"), Json.texto(l, "date_expiration")));
        }
        return r;
    }

    /** Monitores, periféricos, impressoras e telefones ligados à máquina (Computer_Item no GLPI 10). */
    private List<Conectado> conectados(String token, long id) {
        List<Conectado> r = new ArrayList<>();
        for (JsonNode l : subitens(token, id, "Computer_Item")) {
            String tipo = Json.texto(l, "itemtype");
            Long itemId = Json.id(l, "items_id");
            if (tipo == null || itemId == null) continue;
            JsonNode it = catalogo.item(token, tipo, itemId).orElse(null);
            if (it == null) continue;
            String modelo = null;
            for (Map.Entry<String, JsonNode> e : it.properties()) {
                if (e.getKey().endsWith("models_id")) modelo = FichaServico.nomeDropdown(it, e.getKey());
            }
            r.add(new Conectado(rotuloTipo(tipo), Json.texto(it, "name"), modelo, Json.texto(it, "serial")));
        }
        return r;
    }

    private Financeiro financeiro(String token, long id) {
        JsonNode r = glpi.get(token, "/Computer/" + id, Parametros.de().com("with_infocoms", "true").com("expand_dropdowns", "true"));
        JsonNode i = r.path("_infocoms");
        if (!i.isObject() || i.isEmpty()) return null;
        Long meses = Json.numero(i, "warranty_duration");
        String inicio = Json.texto(i, "warranty_date");
        return new Financeiro(Json.texto(i, "buy_date"), Json.texto(i, "use_date"), inicio,
                meses == null ? null : meses.intValue(), fimGarantia(inicio, meses), Json.texto(i, "value"),
                FichaServico.nomeDropdown(i, "suppliers_id"), Json.texto(i, "order_number"), Json.texto(i, "bill"));
    }

    private List<Chamado> chamados(String token, long id) {
        JsonNode r = glpi.get(token, "/Computer/" + id, Parametros.de().com("with_tickets", "true"));
        List<Chamado> lista = new ArrayList<>();
        for (JsonNode t : Json.itens(r.path("_tickets"))) {
            Long tid = Json.id(t, "id");
            if (tid == null) continue;
            lista.add(new Chamado(tid, Json.texto(t, "name"), statusChamado(Json.numero(t, "status")), Json.texto(t, "date")));
        }
        lista.sort(Comparator.comparing((Chamado c) -> c.abertoEm() == null ? "" : c.abertoEm()).reversed());
        return lista.stream().limit(30).toList();
    }

    /**
     * Histórico do GLPI ({@code with_logs}). Mudança de campo tem {@code linked_action = 0} e o campo vem como
     * número da opção de busca, que traduzimos para o nome ("Localização"); as demais ações (componente
     * adicionado, documento vinculado...) já trazem a descrição em old/new_value.
     */
    private List<Evento> historico(String token, long id) {
        JsonNode r = glpi.get(token, "/Computer/" + id, Parametros.de().com("with_logs", "true"));
        List<Evento> lista = new ArrayList<>();
        for (JsonNode l : Json.itens(r.path("_logs"))) {
            String antes = Json.texto(l, "old_value");
            String depois = Json.texto(l, "new_value");
            Long acao = Json.numero(l, "linked_action");
            String descricao;
            if (acao == null || acao == 0) {
                Long opcao = Json.numero(l, "id_search_option");
                String campo = opcao == null ? null : opcoes.nome(token, "Computer", opcao.intValue());
                descricao = (campo == null ? "Campo" : campo) + ": " + (antes == null ? "—" : antes) + " → " + (depois == null ? "—" : depois);
            } else {
                String alvo = Json.texto(l, "itemtype_link");
                descricao = (alvo == null ? "" : alvo + ": ") + (depois != null ? depois : antes != null ? antes : "");
            }
            lista.add(new Evento(Json.texto(l, "date_mod"), Json.texto(l, "user_name"), descricao));
        }
        lista.sort(Comparator.comparing((Evento e) -> e.data() == null ? "" : e.data()).reversed());
        return lista.stream().limit(60).toList();
    }

    // ------------------------------------------------------------------ apoio

    private static <T> CompletableFuture<Secao<T>> secao(Supplier<T> carregar) {
        return Paralelo.rodar(() -> {
            try {
                return Secao.ok(carregar.get());
            } catch (FalhaGlpi e) {
                if (e.sessaoInvalida()) throw e;
                return new Secao<>(null, e.status() == 403 || e.codigo().equals("ERROR_RIGHT_MISSING")
                        ? "Seu perfil no GLPI não permite ver isto." : e.getMessage());
            }
        });
    }

    private List<JsonNode> subitens(String token, long id, String tipo) {
        try {
            return Json.itens(glpi.get(token, "/Computer/" + id + "/" + tipo,
                    Parametros.de().com("range", "0-99").com("expand_dropdowns", "true")));
        } catch (FalhaGlpi e) {
            if (e.naoEncontrado()) return List.of();
            throw e;
        }
    }

    static String fimGarantia(String inicio, Long meses) {
        if (inicio == null || meses == null || meses <= 0) return null;
        try {
            return LocalDate.parse(inicio.substring(0, Math.min(10, inicio.length()))).plusMonths(meses).toString();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** Status dos chamados do GLPI (constantes fixas do CommonITILObject). */
    static String statusChamado(Long s) {
        if (s == null) return null;
        return switch (s.intValue()) {
            case 1 -> "Novo";
            case 2 -> "Em atendimento (atribuído)";
            case 3 -> "Em atendimento (planejado)";
            case 4 -> "Pendente";
            case 5 -> "Solucionado";
            case 6 -> "Fechado";
            default -> "Status " + s;
        };
    }

    private static String rotuloTipo(String itemtype) {
        return switch (itemtype) {
            case "Monitor" -> "Monitor";
            case "Peripheral" -> "Periférico";
            case "Printer" -> "Impressora";
            case "Phone" -> "Telefone";
            default -> itemtype;
        };
    }

    private static Par par(String rotulo, String valor) {
        return new Par(rotulo, valor);
    }

    private static String primeiro(String a, String b) {
        return a != null && !a.equals("0") ? a : (b != null && !b.equals("0") ? b : null);
    }

    private static String mhz(String a, String b) {
        String v = primeiro(a, b);
        return Json.soDigitos(v) ? v + " MHz" : v;
    }

    private static String mb(String v) {
        return Json.soDigitos(v) ? v + " MB" : v;
    }
}
