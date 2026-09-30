package br.com.rdamasio.inventario.documento;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/** Acesso à tabela documento_equipamento. */
public interface DocumentoRepositorio extends JpaRepository<DocumentoEquipamento, Long> {

    List<DocumentoEquipamento> findByTipoItemAndItemIdOrderByEnviadoEmDesc(String tipoItem, long itemId);
}
