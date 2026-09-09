package com.questoes.repository;

import com.questoes.entity.Turma;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TurmaRepository extends JpaRepository<Turma, Long> {
    List<Turma> findByAtivoTrueOrderByNomeAsc();

    @Query("SELECT t FROM Turma t WHERE t.ativo = true AND " +
           "(:busca IS NULL OR LOWER(t.nome) LIKE LOWER(CONCAT('%',:busca,'%')) " +
           "OR LOWER(t.descricao) LIKE LOWER(CONCAT('%',:busca,'%')))")
    Page<Turma> buscar(@Param("busca") String busca, Pageable pageable);
}
