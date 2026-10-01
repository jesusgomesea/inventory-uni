package br.com.rdamasio.inventario.termo;

import java.util.Arrays;
import java.util.List;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.rdamasio.inventario.computador.FichaServico;
import br.com.rdamasio.inventario.glpi.SessaoGlpi;
import br.com.rdamasio.inventario.sessao.SessaoAtual;

/**
 * Termos preenchidos a partir da ficha. A tela abre {@code /api/computadores/101/termos/movimentacao} numa aba
 * nova; a página é o modelo da TI com os campos já preenchidos, pronta para conferir e imprimir.
 */
@RestController
public class TermoController {

    public record ModeloTermo(String id, String nome, String descricao) {
    }

    private final TermoServico termos;
    private final FichaServico fichas;
    private final SessaoAtual sessao;

    public TermoController(TermoServico termos, FichaServico fichas, SessaoAtual sessao) {
        this.termos = termos;
        this.fichas = fichas;
        this.sessao = sessao;
    }

    @GetMapping("/api/termos/modelos")
    public List<ModeloTermo> modelos() {
        sessao.exigir();
        return Arrays.stream(TermoServico.Modelo.values())
                .map(m -> new ModeloTermo(m.name().toLowerCase(), m.nome(), m.descricao())).toList();
    }

    @GetMapping("/api/computadores/{id}/termos/{modelo}")
    public ResponseEntity<String> termo(@PathVariable long id, @PathVariable String modelo,
            @RequestParam(required = false) String chamado) {
        SessaoGlpi s = sessao.exigir();
        SessaoGlpi tecnico = sessao.loginHabilitado() ? s : null; // conta de serviço não é o encarregado
        String html = termos.gerar(modelo, fichas.montar(s.token(), id), tecnico, chamado);
        return ResponseEntity.ok()
                .contentType(new MediaType(MediaType.TEXT_HTML, java.nio.charset.StandardCharsets.UTF_8))
                .cacheControl(CacheControl.noStore()) // tem dados pessoais: não fica no cache do navegador
                .header("X-Content-Type-Options", "nosniff")
                .body(html);
    }
}
