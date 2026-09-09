package com.questoes.repository;

import com.questoes.entity.AplicacaoProva;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AplicacaoProvaRepository extends JpaRepository<AplicacaoProva, Long> {

    List<AplicacaoProva> findByAlunoIdOrderByInicioPrevisto(Long alunoId);

    List<AplicacaoProva> findByProvaIdOrderByAluno_Nome(Long provaId);

    @Query("SELECT a FROM AplicacaoProva a WHERE a.aluno.id = :alunoId AND a.prova.id = :provaId")
    Optional<AplicacaoProva> findByAlunoIdAndProvaId(@Param("alunoId") Long alunoId,
                                                      @Param("provaId") Long provaId);

    @Query("SELECT a FROM AplicacaoProva a JOIN FETCH a.aluno JOIN FETCH a.prova " +
           "WHERE a.prova.id = :provaId ORDER BY a.aluno.nome")
    List<AplicacaoProva> findComAlunosByProvaId(@Param("provaId") Long provaId);
}
