package br.com.rdamasio.inventario.documento;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Nome do arquivo enviado: só o nome, sem pasta, e a extensão que decide se é aceito. */
class DocumentoServicoTest {

    @Test
    void tiraPastasDoNome() {
        assertEquals("termo.pdf", DocumentoServico.limparNome("C:\\Users\\maria\\Desktop\\termo.pdf"));
        assertEquals("passwd", DocumentoServico.limparNome("../../etc/passwd"));
        assertEquals("documento", DocumentoServico.limparNome("   "));
    }

    @Test
    void extensaoEmMinusculas() {
        assertEquals("pdf", DocumentoServico.extensao("Termo.PDF"));
        assertEquals("", DocumentoServico.extensao("sem-extensao"));
    }

    @Test
    void sha256Conhecido() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", ArmazenamentoDocumentos.sha256(new byte[0]));
    }
}
