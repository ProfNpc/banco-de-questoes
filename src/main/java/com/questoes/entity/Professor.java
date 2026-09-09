package com.questoes.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "professor")
@Getter @Setter @NoArgsConstructor
public class Professor {

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

    @Column(name = "criado_em")
    private LocalDateTime criadoEm;

    @Column(name = "ativo")
    private Boolean ativo = true;

    @PrePersist
    protected void onCreate() { criadoEm = LocalDateTime.now(); }
}
