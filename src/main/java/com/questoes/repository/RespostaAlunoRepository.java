package com.questoes.repository;

import com.questoes.entity.RespostaAluno;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RespostaAlunoRepository extends JpaRepository<RespostaAluno, Long> {

    List<RespostaAluno> findByAplicacaoId(Long aplicacaoId);

    Optional<RespostaAluno> findByAplicacaoIdAndQuestaoId(Long aplicacaoId, Long questaoId);

    @Query("SELECT COUNT(r) FROM RespostaAluno r WHERE r.aplicacao.id = :id AND r.correta = true")
    long countCorretas(@Param("id") Long aplicacaoId);

    @Query("SELECT COUNT(r) FROM RespostaAluno r WHERE r.aplicacao.id = :id")
    long countRespondidas(@Param("id") Long aplicacaoId);
}
