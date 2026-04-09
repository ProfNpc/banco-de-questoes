package com.questoes.repository;

import com.questoes.entity.ProvaQuestao;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ProvaQuestaoRepository extends JpaRepository<ProvaQuestao, Long> {
    List<ProvaQuestao> findByProvaIdOrderByOrdem(Long provaId);
    void deleteByProvaId(Long provaId);
}
