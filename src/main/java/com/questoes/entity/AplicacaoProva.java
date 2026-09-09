package com.questoes.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Representa a aplicação de uma Prova para um Aluno específico.
 * Controla o início, duração permitida e o estado de execução.
 */
@Entity
@Table(name = "aplicacao_prova")
@Getter @Setter @NoArgsConstructor
public class AplicacaoProva {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prova_id", nullable = false)
    private Prova prova;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "aluno_id", nullable = false)
    private Aluno aluno;

    /** Data/hora agendada para início da prova */
    @Column(name = "inicio_previsto", nullable = false)
    private LocalDateTime inicioPrevisto;

    /** Duração em minutos */
    @Column(name = "duracao_minutos", nullable = false)
    private Integer duracaoMinutos;

    /** Momento em que o aluno efetivamente começou a prova */
    @Column(name = "inicio_real")
    private LocalDateTime inicioReal;

    /** Momento em que o aluno entregou/encerrou a prova */
    @Column(name = "fim_real")
    private LocalDateTime fimReal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.AGENDADA;

    /** Nota calculada ao final (percentual 0-100) */
    @Column(name = "nota")
    private Double nota;

    @Column(name = "criado_em")
    private LocalDateTime criadoEm;

    @PrePersist
    protected void onCreate() { criadoEm = LocalDateTime.now(); }

    public enum Status {
        AGENDADA, EM_ANDAMENTO, ENCERRADA, EXPIRADA
    }

    /** Calcula o instante de término permitido com base em quando começou */
    public LocalDateTime calcularFimPermitido() {
        if (inicioReal == null) return null;
        return inicioReal.plusMinutes(duracaoMinutos);
    }

    /** Verifica se o tempo de prova ainda está dentro do prazo */
    public boolean dentroDoPrazo() {
        LocalDateTime fim = calcularFimPermitido();
        return fim != null && LocalDateTime.now().isBefore(fim);
    }
}
