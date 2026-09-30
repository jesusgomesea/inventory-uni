package br.com.rdamasio.inventario.comum;

import java.nio.charset.StandardCharsets;
import java.util.Set;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Resposta HTTP para devolver um arquivo enviado por alguém (documento do GLPI ou do banco próprio).
 *
 * <p><b>Segurança:</b> o arquivo é servido pelo mesmo endereço da aplicação. Um HTML ou SVG aberto no navegador
 * rodaria script com a sessão do técnico. Por isso só PDF e imagens comuns abrem na tela (inline); o resto
 * sempre baixa (attachment), e a resposta leva {@code nosniff} e uma CSP que proíbe script.
 */
public final class RespostaArquivo {

    private static final Set<String> ABREM_NA_TELA = Set.of(
            "application/pdf", "image/png", "image/jpeg", "image/gif", "image/webp");

    private RespostaArquivo() {
    }

    public static ResponseEntity<byte[]> de(String nome, String mime, byte[] conteudo) {
        MediaType tipo;
        try {
            tipo = mime == null || mime.isBlank() ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(mime);
        } catch (RuntimeException e) {
            tipo = MediaType.APPLICATION_OCTET_STREAM;
        }
        String base = tipo.getType() + "/" + tipo.getSubtype();
        boolean naTela = ABREM_NA_TELA.contains(base.toLowerCase());
        ContentDisposition cd = (naTela ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(nome == null || nome.isBlank() ? "documento" : nome, StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(naTela ? tipo : MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, cd.toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; img-src 'self'; style-src 'unsafe-inline'; sandbox")
                .body(conteudo);
    }
}
