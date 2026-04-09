package com.questoes.repository;

import com.questoes.entity.Alternativa;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AlternativaRepository extends JpaRepository<Alternativa, Long> {
    List<Alternativa> findByQuestaoIdOrderByOrdem(Long questaoId);
    void deleteByQuestaoId(Long questaoId);
}
