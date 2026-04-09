package com.questoes.repository;

import com.questoes.entity.Prova;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ProvaRepository extends JpaRepository<Prova, Long> {
    List<Prova> findByAtivoTrueOrderByAtualizadoEmDesc();
}
