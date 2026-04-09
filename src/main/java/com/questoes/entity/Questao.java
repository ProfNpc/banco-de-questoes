package com.questoes.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "questao")
@Getter @Setter @NoArgsConstructor
public class Questao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String titulo;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String enunciado; // Markdown

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoQuestao tipo;

    @Column(length = 100)
    private String disciplina;

    @Column(length = 100)
    private String assunto;

    @Enumerated(EnumType.STRING)
    private Dificuldade dificuldade;

    @Column(columnDefinition = "TEXT")
    private String gabarito; // Para discursivas: gabarito esperado

    @OneToMany(mappedBy = "questao", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("ordem ASC")
    private List<Alternativa> alternativas = new ArrayList<>();

    @Column(name = "criado_em")
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;

    @Column(name = "git_commit_hash", length = 100)
    private String gitCommitHash;

    @Column(name = "versao")
    private Integer versao = 1;

    @Column(name = "ativo")
    private Boolean ativo = true;

    @PrePersist
    protected void onCreate() {
        criadoEm = LocalDateTime.now();
        atualizadoEm = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        atualizadoEm = LocalDateTime.now();
    }

    public enum TipoQuestao {
        OBJETIVA("Objetiva (Múltipla Escolha)"),
        DISCURSIVA("Discursiva");

        private final String descricao;
        TipoQuestao(String descricao) { this.descricao = descricao; }
        public String getDescricao() { return descricao; }
    }

    public enum Dificuldade {
        FACIL("Fácil"),
        MEDIO("Médio"),
        DIFICIL("Difícil");

        private final String descricao;
        Dificuldade(String descricao) { this.descricao = descricao; }
        public String getDescricao() { return descricao; }
    }
}
