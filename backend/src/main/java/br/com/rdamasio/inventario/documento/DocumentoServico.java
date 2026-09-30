package br.com.rdamasio.inventario.documento;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.rdamasio.inventario.comum.ErroNegocio;
import br.com.rdamasio.inventario.comum.Json;
import br.com.rdamasio.inventario.comum.Paralelo;
import br.com.rdamasio.inventario.glpi.FalhaGlpi;
import br.com.rdamasio.inventario.glpi.GlpiCliente;
import br.com.rdamasio.inventario.glpi.Parametros;
import br.com.rdamasio.inventario.glpi.SessaoGlpi;
import tools.jackson.databind.JsonNode;

/**
 * Documentos e termos dos equipamentos, guardados no banco próprio (fora do GLPI).
 *
 * <p><b>Permissão:</b> quem pode ver o equipamento no GLPI pode ver e anexar documentos dele. Antes de qualquer
 * operação lemos o equipamento no GLPI com a sessão do técnico; se o GLPI negar, negamos também. Assim não há um
 * segundo cadastro de permissões para manter.
 *
 * <p>No envio guardamos quem era o responsável do equipamento naquele momento: num termo de responsabilidade é
 * quem assinou, e isso não pode mudar quando a máquina trocar de dono.
 */
@Service
public class DocumentoServico {

    private static final Logger log = LoggerFactory.getLogger(DocumentoServico.class);

    /** Tipos de equipamento do GLPI aceitos. Para abrir para monitores e impressoras, basta incluir aqui. */
    static final Set<String> TIPOS = Set.of("Computer");

    /** Extensões aceitas: documentos, planilhas, imagens e compactados. Nada executável. */
    static final Set<String> EXTENSOES = Set.of("pdf", "png", "jpg", "jpeg", "webp", "gif", "doc", "docx", "odt",
            "xls", "xlsx", "ods", "csv", "txt", "zip", "7z", "msg", "eml");

    public record Envio(CategoriaDocumento categoria, String titulo, String observacao, LocalDate validade,
            String nomeArquivo, String tipoConteudo, byte[] conteudo) {
    }

    /** O que a tela mostra de cada documento. */
    public record Resumo(long id, String categoria, String categoriaRotulo, String titulo, String observacao,
            LocalDate validade, String nomeArquivo, String tipoConteudo, long tamanhoBytes, String sha256,
            String responsavelNome, String enviadoPor, LocalDateTime enviadoEm, LocalDateTime removidoEm,
            String removidoPor, String motivoRemocao) {

        static Resumo de(DocumentoEquipamento d) {
            return new Resumo(d.getId(), d.getCategoria().name(), d.getCategoria().rotulo(), d.getTitulo(),
                    d.getObservacao(), d.getValidade(), d.getNomeArquivo(), d.getTipoConteudo(), d.getTamanhoBytes(),
                    d.getSha256(), d.getResponsavelNome(), d.getEnviadoPorNome(), d.getEnviadoEm(), d.getRemovidoEm(),
                    d.getRemovidoPorNome(), d.getMotivoRemocao());
        }
    }

    public record Arquivo(String nome, String tipo, byte[] conteudo) {
    }

    private record Equipamento(String nome, Long responsavelId, String responsavelNome) {
    }

    private final DocumentoRepositorio repositorio;
    private final ArmazenamentoDocumentos armazenamento;
    private final GlpiCliente glpi;

    public DocumentoServico(DocumentoRepositorio repositorio, ArmazenamentoDocumentos armazenamento, GlpiCliente glpi) {
        this.repositorio = repositorio;
        this.armazenamento = armazenamento;
        this.glpi = glpi;
    }

    @Transactional(readOnly = true)
    public List<Resumo> listar(SessaoGlpi s, String tipo, long itemId, boolean incluirRemovidos) {
        equipamento(s.token(), tipo, itemId);
        return repositorio.findByTipoItemAndItemIdOrderByEnviadoEmDesc(tipo, itemId).stream()
                .filter(d -> incluirRemovidos || !d.removido())
                .map(Resumo::de)
                .toList();
    }

    @Transactional
    public Resumo anexar(SessaoGlpi s, String tipo, long itemId, Envio e) {
        if (e.categoria() == null) throw ErroNegocio.invalido("Escolha o tipo do documento.");
        if (e.conteudo() == null || e.conteudo().length == 0) throw ErroNegocio.invalido("O arquivo está vazio.");
        String nomeArquivo = limparNome(e.nomeArquivo());
        String ext = extensao(nomeArquivo);
        if (!EXTENSOES.contains(ext)) {
            throw ErroNegocio.invalido("Tipo de arquivo não aceito (." + ext + "). Aceitos: " + String.join(", ", EXTENSOES.stream().sorted().toList()) + ".");
        }
        String titulo = e.titulo() == null || e.titulo().isBlank() ? e.categoria().rotulo() : e.titulo().trim();
        if (titulo.length() > 255) throw ErroNegocio.invalido("O título passa de 255 caracteres.");
        String obs = e.observacao() == null || e.observacao().isBlank() ? null : e.observacao().trim();
        if (obs != null && obs.length() > 2000) throw ErroNegocio.invalido("A observação passa de 2000 caracteres.");

        Equipamento eq = equipamento(s.token(), tipo, itemId);
        ArmazenamentoDocumentos.Gravado g = armazenamento.gravar(e.conteudo());
        DocumentoEquipamento d = repositorio.save(new DocumentoEquipamento(tipo, itemId, eq.nome(), e.categoria(), titulo,
                obs, e.validade(), nomeArquivo, tipoConteudo(e.tipoConteudo(), ext), g.tamanho(), g.sha256(), g.caminho(),
                eq.responsavelId(), eq.responsavelNome(), s.usuarioId(), s.nome(), LocalDateTime.now()));
        log.info("Documento {} ({}) anexado a {} {} por {}", d.getId(), e.categoria(), tipo, itemId, s.login());
        return Resumo.de(d);
    }

    @Transactional(readOnly = true)
    public Arquivo baixar(SessaoGlpi s, long documentoId) {
        DocumentoEquipamento d = buscar(documentoId);
        equipamento(s.token(), d.getTipoItem(), d.getItemId());
        return new Arquivo(d.getNomeArquivo(), d.getTipoConteudo(), armazenamento.ler(d.getCaminho()));
    }

    /** Remoção lógica, com motivo obrigatório. O arquivo continua no disco. */
    @Transactional
    public Resumo remover(SessaoGlpi s, long documentoId, String motivo) {
        if (motivo == null || motivo.isBlank()) throw ErroNegocio.invalido("Informe o motivo da remoção.");
        if (motivo.length() > 500) throw ErroNegocio.invalido("O motivo passa de 500 caracteres.");
        DocumentoEquipamento d = buscar(documentoId);
        equipamento(s.token(), d.getTipoItem(), d.getItemId());
        if (!d.removido()) {
            d.remover(s.nome(), motivo.trim(), LocalDateTime.now());
            log.info("Documento {} removido por {}: {}", documentoId, s.login(), motivo.trim());
        }
        return Resumo.de(d);
    }

    // ------------------------------------------------------------------ apoio

    private DocumentoEquipamento buscar(long id) {
        return repositorio.findById(id).orElseThrow(() -> ErroNegocio.naoEncontrado("Documento " + id + " não encontrado."));
    }

    /** Lê o equipamento no GLPI com a sessão do técnico: é a checagem de permissão (ver javadoc da classe). */
    private Equipamento equipamento(String token, String tipo, long itemId) {
        if (!TIPOS.contains(tipo)) throw ErroNegocio.invalido("Tipo de equipamento não suportado: " + tipo);
        var fCru = Paralelo.rodar(() -> glpi.get(token, "/" + tipo + "/" + itemId, null));
        var fExp = Paralelo.rodar(() -> glpi.get(token, "/" + tipo + "/" + itemId, Parametros.de().com("expand_dropdowns", "true")));
        try {
            JsonNode cru = Paralelo.esperar(fCru);
            JsonNode exp = Paralelo.esperar(fExp);
            Long resp = Json.id(cru, "users_id");
            return new Equipamento(Json.texto(cru, "name"), resp, resp == null ? null : Json.texto(exp, "users_id"));
        } catch (FalhaGlpi e) {
            if (e.naoEncontrado()) throw ErroNegocio.naoEncontrado("Equipamento não encontrado no GLPI (ou fora das suas entidades).");
            throw e;
        }
    }

    /** Só o nome do arquivo, sem pastas e sem caracteres de controle. */
    static String limparNome(String nome) {
        if (nome == null || nome.isBlank()) return "documento";
        String n = nome.replace('\\', '/');
        n = n.substring(n.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}\"]", "").trim();
        if (n.isEmpty()) n = "documento";
        return n.length() > 200 ? n.substring(n.length() - 200) : n;
    }

    static String extensao(String nome) {
        int i = nome.lastIndexOf('.');
        return i < 0 ? "" : nome.substring(i + 1).toLowerCase(Locale.ROOT);
    }

    private static String tipoConteudo(String informado, String ext) {
        return switch (ext) {
            case "pdf" -> "application/pdf";
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "webp" -> "image/webp";
            case "gif" -> "image/gif";
            // nos demais o navegador manda algo razoável; sem nada, binário genérico
            default -> informado == null || informado.isBlank() || informado.length() > 120 ? "application/octet-stream" : informado;
        };
    }
}
