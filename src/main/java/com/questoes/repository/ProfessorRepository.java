package com.questoes.repository;

import com.questoes.entity.Professor;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ProfessorRepository extends JpaRepository<Professor, Long> {
    Optional<Professor> findByEmailAndAtivoTrue(String email);
    boolean existsByEmail(String email);
}
