package br.com.rdamasio.inventario.computador;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.rdamasio.inventario.comum.Json;
import br.com.rdamasio.inventario.comum.RespostaArquivo;
import br.com.rdamasio.inventario.glpi.GlpiCliente;
import br.com.rdamasio.inventario.sessao.SessaoAtual;
import tools.jackson.databind.JsonNode;

/**
 * Computadores: lista, ficha, edição, "ver mais" e download dos documentos que já estão no GLPI.
 * Tudo roda com a sessão GLPI do técnico logado: o que ele não pode ver no GLPI, não vê aqui.
 */
@RestController
@RequestMapping("/api/computadores")
public class ComputadorController {

    private final SessaoAtual sessao;
    private final BuscaServico busca;
    private final FichaServico fichas;
    private final EdicaoServico edicao;
    private final DetalhesServico detalhes;
    private final GlpiCliente glpi;

    public ComputadorController(SessaoAtual sessao, BuscaServico busca, FichaServico fichas, EdicaoServico edicao,
            DetalhesServico detalhes, GlpiCliente glpi) {
        this.sessao = sessao;
        this.busca = busca;
        this.fichas = fichas;
        this.edicao = edicao;
        this.detalhes = detalhes;
        this.glpi = glpi;
    }

    @GetMapping
    public BuscaServico.Pagina listar(@RequestParam(required = false) String q,
            @RequestParam(required = false) Long status,
            @RequestParam(required = false) Long local,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "25") int tamanho) {
        int t = Math.clamp(tamanho, 5, 100);
        return busca.buscar(sessao.token(), new BuscaServico.Filtro(q, status, local, Math.max(0, pagina), t));
    }

    @GetMapping("/{id}")
    public Ficha ficha(@PathVariable long id) {
        return fichas.montar(sessao.token(), id);
    }

    /** Salva e devolve a ficha relida do GLPI (com o date_mod novo, para a próxima edição). */
    @PutMapping("/{id}")
    public Ficha editar(@PathVariable long id, @RequestBody EdicaoServico.Alteracao alteracao) {
        String token = sessao.token();
        edicao.salvar(token, id, alteracao);
        return fichas.montar(token, id);
    }

    @GetMapping("/{id}/detalhes")
    public DetalhesServico.Detalhes detalhes(@PathVariable long id) {
        return detalhes.carregar(sessao.token(), id);
    }

    /** Arquivo de um documento do GLPI. O GLPI confere se o técnico pode ver o documento. */
    @GetMapping("/{id}/documentos-glpi/{documentoId}")
    public ResponseEntity<byte[]> baixarDocumentoGlpi(@PathVariable long id, @PathVariable long documentoId) {
        String token = sessao.token();
        JsonNode doc = glpi.get(token, "/Document/" + documentoId, null);
        byte[] conteudo = glpi.baixar(token, documentoId);
        return RespostaArquivo.de(Json.texto(doc, "filename"), Json.texto(doc, "mime"), conteudo);
    }
}
