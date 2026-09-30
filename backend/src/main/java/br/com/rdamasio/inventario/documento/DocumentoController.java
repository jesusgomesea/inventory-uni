package br.com.rdamasio.inventario.documento;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import br.com.rdamasio.inventario.comum.ErroNegocio;
import br.com.rdamasio.inventario.comum.RespostaArquivo;
import br.com.rdamasio.inventario.sessao.SessaoAtual;

/**
 * Documentos e termos dos equipamentos guardados nesta aplicação. O equipamento é identificado como no GLPI:
 * {@code /api/equipamentos/Computer/123/documentos}.
 */
@RestController
public class DocumentoController {

    public record Categoria(String id, String rotulo) {
    }

    private final DocumentoServico servico;
    private final SessaoAtual sessao;

    public DocumentoController(DocumentoServico servico, SessaoAtual sessao) {
        this.servico = servico;
        this.sessao = sessao;
    }

    @GetMapping("/api/documentos/categorias")
    public List<Categoria> categorias() {
        sessao.exigir();
        return Arrays.stream(CategoriaDocumento.values()).map(c -> new Categoria(c.name(), c.rotulo())).toList();
    }

    @GetMapping("/api/equipamentos/{tipo}/{itemId}/documentos")
    public List<DocumentoServico.Resumo> listar(@PathVariable String tipo, @PathVariable long itemId,
            @RequestParam(defaultValue = "false") boolean removidos) {
        return servico.listar(sessao.exigir(), tipo, itemId, removidos);
    }

    @PostMapping("/api/equipamentos/{tipo}/{itemId}/documentos")
    public DocumentoServico.Resumo anexar(@PathVariable String tipo, @PathVariable long itemId,
            @RequestParam MultipartFile arquivo,
            @RequestParam String categoria,
            @RequestParam(required = false) String titulo,
            @RequestParam(required = false) String observacao,
            @RequestParam(required = false) LocalDate validade) {
        CategoriaDocumento cat;
        try {
            cat = CategoriaDocumento.valueOf(categoria);
        } catch (IllegalArgumentException e) {
            throw ErroNegocio.invalido("Tipo de documento inválido: " + categoria);
        }
        byte[] conteudo;
        try {
            conteudo = arquivo.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return servico.anexar(sessao.exigir(), tipo, itemId, new DocumentoServico.Envio(cat, titulo, observacao, validade,
                arquivo.getOriginalFilename(), arquivo.getContentType(), conteudo));
    }

    @GetMapping("/api/documentos/{id}/arquivo")
    public ResponseEntity<byte[]> baixar(@PathVariable long id) {
        DocumentoServico.Arquivo a = servico.baixar(sessao.exigir(), id);
        return RespostaArquivo.de(a.nome(), a.tipo(), a.conteudo());
    }

    @DeleteMapping("/api/documentos/{id}")
    public DocumentoServico.Resumo remover(@PathVariable long id, @RequestParam String motivo) {
        return servico.remover(sessao.exigir(), id, motivo);
    }
}
