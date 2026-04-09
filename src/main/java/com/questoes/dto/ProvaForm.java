package com.questoes.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Getter @Setter @NoArgsConstructor
public class ProvaForm {
    private String titulo;
    private String descricao;
    private String disciplina;
    private List<Long> questoesIds = new ArrayList<>();
    private List<Double> pontos = new ArrayList<>();
}
