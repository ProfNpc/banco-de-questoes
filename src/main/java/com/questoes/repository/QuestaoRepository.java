package com.questoes.repository;

import com.questoes.entity.Questao;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface QuestaoRepository extends JpaRepository<Questao, Long> {

    List<Questao> findByAtivoTrueOrderByAtualizadoEmDesc();

    @Query("SELECT q FROM Questao q WHERE q.ativo = true AND " +
           "(:disciplina IS NULL OR q.disciplina = :disciplina) AND " +
           "(:tipo IS NULL OR q.tipo = :tipo) AND " +
           "(:busca IS NULL OR LOWER(q.titulo) LIKE LOWER(CONCAT('%',:busca,'%')) OR LOWER(q.enunciado) LIKE LOWER(CONCAT('%',:busca,'%')))")
    Page<Questao> buscar(@Param("disciplina") String disciplina,
                         @Param("tipo") Questao.TipoQuestao tipo,
                         @Param("busca") String busca,
                         Pageable pageable);

    List<Questao> findByDisciplinaAndAtivoTrue(String disciplina);

    @Query("SELECT DISTINCT q.disciplina FROM Questao q WHERE q.disciplina IS NOT NULL AND q.ativo = true ORDER BY q.disciplina")
    List<String> findDisciplinasDistintas();
}
