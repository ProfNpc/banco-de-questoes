package com.questoes.dto;

import com.questoes.entity.Questao;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Getter @Setter @NoArgsConstructor
public class QuestaoForm {
    private String titulo;
    private String enunciado;
    private Questao.TipoQuestao tipo;
    private String disciplina;
    private String assunto;
    private Questao.Dificuldade dificuldade;
    private String gabarito;
    private List<String> alternativas = new ArrayList<>();
    private List<Integer> alternativasCorretas = new ArrayList<>();
}
