package com.questoes.service;

import com.questoes.dto.ProvaForm;
import com.questoes.entity.Prova;
import com.questoes.entity.ProvaQuestao;
import com.questoes.entity.Questao;
import com.questoes.repository.ProvaQuestaoRepository;
import com.questoes.repository.ProvaRepository;
import com.questoes.repository.QuestaoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProvaService {

    private final ProvaRepository provaRepository;
    private final ProvaQuestaoRepository provaQuestaoRepository;
    private final QuestaoRepository questaoRepository;

    @Transactional
    public Prova salvar(ProvaForm form) {
        Prova prova = new Prova();
        preencherProva(prova, form);
        prova = provaRepository.save(prova);
        adicionarQuestoes(prova, form);
        return provaRepository.save(prova);
    }

    @Transactional
    public Prova atualizar(Long id, ProvaForm form) {
        Prova prova = provaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Prova não encontrada: " + id));
        preencherProva(prova, form);
        provaQuestaoRepository.deleteByProvaId(id);
        prova.getProvaQuestoes().clear();
        prova = provaRepository.save(prova);
        adicionarQuestoes(prova, form);
        return provaRepository.save(prova);
    }

    @Transactional
    public void excluir(Long id) {
        Prova prova = provaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Prova não encontrada: " + id));
        prova.setAtivo(false);
        provaRepository.save(prova);
    }

    public Optional<Prova> buscarPorId(Long id) {
        return provaRepository.findById(id);
    }

    public List<Prova> listarTodas() {
        return provaRepository.findByAtivoTrueOrderByAtualizadoEmDesc();
    }

    private void preencherProva(Prova prova, ProvaForm form) {
        prova.setTitulo(form.getTitulo());
        prova.setDescricao(form.getDescricao());
        prova.setDisciplina(form.getDisciplina());
    }

    private void adicionarQuestoes(Prova prova, ProvaForm form) {
        if (form.getQuestoesIds() == null) return;
        for (int i = 0; i < form.getQuestoesIds().size(); i++) {
            Long qId = form.getQuestoesIds().get(i);
            Questao q = questaoRepository.findById(qId).orElse(null);
            if (q == null) continue;
            ProvaQuestao pq = new ProvaQuestao();
            pq.setProva(prova);
            pq.setQuestao(q);
            pq.setOrdem(i + 1);
            Double pontos = (form.getPontos() != null && i < form.getPontos().size())
                    ? form.getPontos().get(i) : 1.0;
            pq.setPontos(pontos != null ? pontos : 1.0);
            provaQuestaoRepository.save(pq);
            prova.getProvaQuestoes().add(pq);
        }
    }
}
