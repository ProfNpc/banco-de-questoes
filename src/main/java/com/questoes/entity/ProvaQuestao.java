package com.questoes.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "prova_questao")
@Getter @Setter @NoArgsConstructor
public class ProvaQuestao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prova_id", nullable = false)
    private Prova prova;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "questao_id", nullable = false)
    private Questao questao;

    @Column(nullable = false)
    private Integer ordem;

    @Column(name = "pontos")
    private Double pontos = 1.0;
}
