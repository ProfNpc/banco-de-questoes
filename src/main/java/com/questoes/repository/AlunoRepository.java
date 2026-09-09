package com.questoes.repository;

import com.questoes.entity.Aluno;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AlunoRepository extends JpaRepository<Aluno, Long> {
    Optional<Aluno> findByEmailAndAtivoTrue(String email);
    boolean existsByEmail(String email);

    /**
     * Busca alunos ativos por nome, e-mail ou ID.
     * O parâmetro :idBusca é o texto convertido para Long quando for numérico
     * (null caso não seja numérico), permitindo busca por ID exato.
     */
    @Query("SELECT a FROM Aluno a WHERE a.ativo = true AND " +
           "(:busca IS NULL OR " +
           " LOWER(a.nome)  LIKE LOWER(CONCAT('%',:busca,'%')) OR " +
           " LOWER(a.email) LIKE LOWER(CONCAT('%',:busca,'%')) OR " +
           " (:idBusca IS NOT NULL AND a.id = :idBusca))")
    Page<Aluno> buscar(@Param("busca")   String busca,
                       @Param("idBusca") Long   idBusca,
                       Pageable pageable);
}
