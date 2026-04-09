package com.questoes.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "alternativa")
@Getter @Setter @NoArgsConstructor
public class Alternativa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "questao_id", nullable = false)
    private Questao questao;

    @Column(nullable = false)
    private Integer ordem; // 0=A, 1=B, 2=C, etc.

    @Column(nullable = false, columnDefinition = "TEXT")
    private String texto; // Markdown

    @Column(name = "correta")
    private Boolean correta = false;

    public String getLetra() {
        return String.valueOf((char) ('A' + ordem));
    }
}
