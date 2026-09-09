package com.questoes.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Armazena a alternativa escolhida pelo Aluno para cada questão de uma AplicacaoProva.
 */
@Entity
@Table(name = "resposta_aluno",
       uniqueConstraints = @UniqueConstraint(columnNames = {"aplicacao_id", "questao_id"}))
@Getter @Setter @NoArgsConstructor
public class RespostaAluno {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "aplicacao_id", nullable = false)
    private AplicacaoProva aplicacao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "questao_id", nullable = false)
    private Questao questao;

    /** Alternativa marcada pelo aluno (null = não respondida) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alternativa_id")
    private Alternativa alternativa;

    @Column(name = "respondida_em")
    private LocalDateTime respondidaEm;

    /** true se a alternativa escolhida for a correta */
    @Column(name = "correta")
    private Boolean correta;
}
