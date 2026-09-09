package com.questoes.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "aluno")
@Getter @Setter @NoArgsConstructor
public class Aluno {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String nome;

    @Column(nullable = false, unique = true, length = 200)
    private String email;

    @Column(name = "hash_senha", nullable = false, length = 256)
    private String hashSenha;

    @Column(nullable = false, length = 64)
    private String salto;

    @Column(name = "deve_trocar_senha")
    private Boolean deveTrocarSenha = true;

    @Column(name = "criado_em")
    private LocalDateTime criadoEm;

    @Column(name = "ativo")
    private Boolean ativo = true;

    @ManyToMany(mappedBy = "alunos", fetch = FetchType.LAZY)
    private Set<Turma> turmas = new HashSet<>();

    @PrePersist
    protected void onCreate() { criadoEm = LocalDateTime.now(); }
}
