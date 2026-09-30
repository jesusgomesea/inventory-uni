package br.com.rdamasio.inventario.documento;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.stereotype.Component;

import br.com.rdamasio.inventario.config.InventarioPropriedades;

/**
 * Arquivos dos documentos no disco, em {@code <pasta>/<ano>/<mês>/<uuid>}. O nome no disco é gerado (nunca o
 * nome enviado pelo usuário), o que elimina nome duplicado e caminho malicioso ("../../"). O nome original fica
 * no banco. Grava em arquivo temporário e move no fim: um envio interrompido não deixa arquivo pela metade.
 */
@Component
public class ArmazenamentoDocumentos {

    public record Gravado(String caminho, String sha256, long tamanho) {
    }

    private final Path raiz;

    public ArmazenamentoDocumentos(InventarioPropriedades props) {
        String pasta = props.documentos() == null ? "./dados/documentos" : props.documentos().pasta();
        this.raiz = Path.of(pasta).toAbsolutePath().normalize();
    }

    public Gravado gravar(byte[] conteudo) {
        LocalDate hoje = LocalDate.now();
        String relativo = "%d/%02d/%s".formatted(hoje.getYear(), hoje.getMonthValue(), UUID.randomUUID());
        Path destino = resolver(relativo);
        try {
            Files.createDirectories(destino.getParent());
            Path tmp = Files.createTempFile(destino.getParent(), "envio-", ".tmp");
            Files.write(tmp, conteudo);
            Files.move(tmp, destino, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível gravar o arquivo em " + raiz, e);
        }
        return new Gravado(relativo, sha256(conteudo), conteudo.length);
    }

    public byte[] ler(String relativo) {
        try {
            return Files.readAllBytes(resolver(relativo));
        } catch (IOException e) {
            throw new UncheckedIOException("Arquivo do documento não encontrado no disco: " + relativo, e);
        }
    }

    static String sha256(byte[] conteudo) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(conteudo));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Caminho absoluto, garantindo que continua dentro da pasta (defesa extra; o nome já é gerado por nós). */
    private Path resolver(String relativo) {
        Path p = raiz.resolve(relativo).normalize();
        if (!p.startsWith(raiz)) throw new IllegalArgumentException("Caminho fora da pasta de documentos");
        return p;
    }
}
