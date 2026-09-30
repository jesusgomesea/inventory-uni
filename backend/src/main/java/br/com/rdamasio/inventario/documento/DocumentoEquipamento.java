package br.com.rdamasio.inventario.documento;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Documento anexado a um equipamento por esta aplicação (tabela {@code documento_equipamento}, migration V1).
 * O arquivo fica no disco ({@link ArmazenamentoDocumentos}); aqui ficam os dados e o caminho.
 * Remoção é lógica ({@code removidoEm}): termo assinado é registro e não deve sumir.
 */
@Entity
@Table(name = "documento_equipamento")
public class DocumentoEquipamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tipo_item", nullable = false)
    private String tipoItem;
    @Column(name = "item_id", nullable = false)
    private long itemId;
    @Column(name = "item_nome")
    private String itemNome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CategoriaDocumento categoria;
    @Column(nullable = false)
    private String titulo;
    private String observacao;
    private LocalDate validade;

    @Column(name = "nome_arquivo", nullable = false)
    private String nomeArquivo;
    @Column(name = "tipo_conteudo", nullable = false)
    private String tipoConteudo;
    @Column(name = "tamanho_bytes", nullable = false)
    private long tamanhoBytes;
    @Column(nullable = false)
    private String sha256;
    @Column(nullable = false)
    private String caminho;

    @Column(name = "responsavel_id")
    private Long responsavelId;
    @Column(name = "responsavel_nome")
    private String responsavelNome;

    @Column(name = "enviado_por_id", nullable = false)
    private long enviadoPorId;
    @Column(name = "enviado_por_nome", nullable = false)
    private String enviadoPorNome;
    @Column(name = "enviado_em", nullable = false)
    private LocalDateTime enviadoEm;

    @Column(name = "removido_em")
    private LocalDateTime removidoEm;
    @Column(name = "removido_por_nome")
    private String removidoPorNome;
    @Column(name = "motivo_remocao")
    private String motivoRemocao;

    protected DocumentoEquipamento() {
    }

    public DocumentoEquipamento(String tipoItem, long itemId, String itemNome, CategoriaDocumento categoria,
            String titulo, String observacao, LocalDate validade, String nomeArquivo, String tipoConteudo,
            long tamanhoBytes, String sha256, String caminho, Long responsavelId, String responsavelNome,
            long enviadoPorId, String enviadoPorNome, LocalDateTime enviadoEm) {
        this.tipoItem = tipoItem;
        this.itemId = itemId;
        this.itemNome = itemNome;
        this.categoria = categoria;
        this.titulo = titulo;
        this.observacao = observacao;
        this.validade = validade;
        this.nomeArquivo = nomeArquivo;
        this.tipoConteudo = tipoConteudo;
        this.tamanhoBytes = tamanhoBytes;
        this.sha256 = sha256;
        this.caminho = caminho;
        this.responsavelId = responsavelId;
        this.responsavelNome = responsavelNome;
        this.enviadoPorId = enviadoPorId;
        this.enviadoPorNome = enviadoPorNome;
        this.enviadoEm = enviadoEm;
    }

    public void remover(String quem, String motivo, LocalDateTime quando) {
        this.removidoEm = quando;
        this.removidoPorNome = quem;
        this.motivoRemocao = motivo;
    }

    public boolean removido() {
        return removidoEm != null;
    }

    public Long getId() { return id; }
    public String getTipoItem() { return tipoItem; }
    public long getItemId() { return itemId; }
    public String getItemNome() { return itemNome; }
    public CategoriaDocumento getCategoria() { return categoria; }
    public String getTitulo() { return titulo; }
    public String getObservacao() { return observacao; }
    public LocalDate getValidade() { return validade; }
    public String getNomeArquivo() { return nomeArquivo; }
    public String getTipoConteudo() { return tipoConteudo; }
    public long getTamanhoBytes() { return tamanhoBytes; }
    public String getSha256() { return sha256; }
    public String getCaminho() { return caminho; }
    public Long getResponsavelId() { return responsavelId; }
    public String getResponsavelNome() { return responsavelNome; }
    public long getEnviadoPorId() { return enviadoPorId; }
    public String getEnviadoPorNome() { return enviadoPorNome; }
    public LocalDateTime getEnviadoEm() { return enviadoEm; }
    public LocalDateTime getRemovidoEm() { return removidoEm; }
    public String getRemovidoPorNome() { return removidoPorNome; }
    public String getMotivoRemocao() { return motivoRemocao; }
}
