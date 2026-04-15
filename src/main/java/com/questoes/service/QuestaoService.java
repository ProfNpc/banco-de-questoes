package com.questoes.service;

import com.questoes.dto.QuestaoForm;
import com.questoes.entity.Alternativa;
import com.questoes.entity.Questao;
import com.questoes.repository.AlternativaRepository;
import com.questoes.repository.QuestaoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class QuestaoService {

    private final QuestaoRepository questaoRepository;
    private final AlternativaRepository alternativaRepository;
    private final GitService gitService;

    @Transactional
    public Questao salvar(QuestaoForm form) {
        Questao questao = new Questao();
        questao.setVersao(1);
        preencherQuestao(questao, form);
        questao = questaoRepository.save(questao);
        salvarAlternativas(questao, form);
        questao = questaoRepository.save(questao);
        String hash = gitService.commitQuestao(questao,
                "Criação da questão #" + questao.getId() + ": " + questao.getTitulo());
        questao.setGitCommitHash(hash);
        return questaoRepository.save(questao);
    }

    @Transactional
    public Questao atualizar(Long id, QuestaoForm form) {
        Questao questao = questaoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Questão não encontrada: " + id));
        questao.setVersao(questao.getVersao() + 1);
        preencherQuestao(questao, form);
        alternativaRepository.deleteByQuestaoId(id);
        questao.getAlternativas().clear();
        questao = questaoRepository.save(questao);
        salvarAlternativas(questao, form);
        questao = questaoRepository.save(questao);
        String hash = gitService.commitQuestao(questao,
                "Atualização v" + questao.getVersao() + " da questão #" + questao.getId() + ": " + questao.getTitulo());
        questao.setGitCommitHash(hash);
        return questaoRepository.save(questao);
    }

    @Transactional
    public void excluir(Long id) {
        Questao questao = questaoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Questão não encontrada: " + id));
        questao.setAtivo(false);
        questaoRepository.save(questao);
    }

    /**
     * Monta um QuestaoForm pré-preenchido a partir do conteúdo Markdown
     * de uma versão específica (ou da versão atual, com hash="current").
     */
    public QuestaoForm montarFormDeVersao(Long questaoId, String hash) {
        if ("current".equals(hash)) {
            // Usar dados atuais do banco
            Questao q = questaoRepository.findById(questaoId)
                    .orElseThrow(() -> new RuntimeException("Questão não encontrada"));
            return questaoParaForm(q);
        }

        String conteudoMd = gitService.obterConteudoVersao(questaoId, hash);
        if (conteudoMd == null) throw new RuntimeException("Versão não encontrada: " + hash);

        GitService.QuestaoVersaoData data = gitService.parsearVersaoParaForm(conteudoMd);
        QuestaoForm form = new QuestaoForm();
        form.setTitulo(data.getTitulo() != null ? data.getTitulo() + " (cópia)" : "");
        form.setEnunciado(data.getEnunciado());
        form.setGabarito(data.getGabarito());
        form.setDisciplina(data.getDisciplina());
        form.setAssunto(data.getAssunto());

        if (data.getTipo() != null) {
            try { form.setTipo(Questao.TipoQuestao.valueOf(data.getTipo())); } catch (Exception ignored) {}
        }
        if (data.getDificuldade() != null) {
            try { form.setDificuldade(Questao.Dificuldade.valueOf(data.getDificuldade())); } catch (Exception ignored) {}
        }
        form.setAlternativas(data.getAlternativas());
        form.setAlternativasCorretas(data.getAlternativasCorretas());
        return form;
    }

    public Optional<Questao> buscarPorId(Long id) {
        return questaoRepository.findById(id);
    }

    public List<Questao> listarTodas() {
        return questaoRepository.findByAtivoTrueOrderByAtualizadoEmDesc();
    }

    public Page<Questao> buscar(String disciplina, Questao.TipoQuestao tipo, String busca, Pageable pageable) {
        String disciplinaFiltro = (disciplina == null || disciplina.isBlank()) ? null : disciplina;
        String buscaFiltro = (busca == null || busca.isBlank()) ? null : busca;
        return questaoRepository.buscar(disciplinaFiltro, tipo, buscaFiltro, pageable);
    }

    public List<String> listarDisciplinas() {
        return questaoRepository.findDisciplinasDistintas();
    }

    public List<GitService.VersaoQuestao> listarVersoes(Long id) {
        return gitService.listarVersoes(id);
    }

    public String obterConteudoVersao(Long id, String hash) {
        return gitService.obterConteudoVersao(id, hash);
    }

    public String gerarDiff(Long id, String hashA, String hashB) {
        return gitService.gerarDiff(id, hashA, hashB);
    }

    private QuestaoForm questaoParaForm(Questao q) {
        QuestaoForm form = new QuestaoForm();
        form.setTitulo(q.getTitulo() + " (cópia)");
        form.setEnunciado(q.getEnunciado());
        form.setTipo(q.getTipo());
        form.setDisciplina(q.getDisciplina());
        form.setAssunto(q.getAssunto());
        form.setDificuldade(q.getDificuldade());
        form.setGabarito(q.getGabarito());
        for (var alt : q.getAlternativas()) {
            form.getAlternativas().add(alt.getTexto());
            if (Boolean.TRUE.equals(alt.getCorreta())) {
                form.getAlternativasCorretas().add(alt.getOrdem());
            }
        }
        return form;
    }

    private void preencherQuestao(Questao questao, QuestaoForm form) {
        questao.setTitulo(form.getTitulo());
        questao.setEnunciado(form.getEnunciado());
        questao.setTipo(form.getTipo());
        questao.setDisciplina(form.getDisciplina());
        questao.setAssunto(form.getAssunto());
        questao.setDificuldade(form.getDificuldade());
        questao.setGabarito(form.getGabarito());
    }

    private void salvarAlternativas(Questao questao, QuestaoForm form) {
        if (questao.getTipo() == Questao.TipoQuestao.OBJETIVA
                && form.getAlternativas() != null
                && !form.getAlternativas().isEmpty()) {
            List<Integer> corretas = form.getAlternativasCorretas() != null
                    ? form.getAlternativasCorretas() : List.of();
            for (int i = 0; i < form.getAlternativas().size(); i++) {
                String texto = form.getAlternativas().get(i);
                if (texto == null || texto.isBlank()) continue;
                Alternativa alt = new Alternativa();
                alt.setQuestao(questao);
                alt.setOrdem(i);
                alt.setTexto(texto);
                alt.setCorreta(corretas.contains(i));
                alternativaRepository.save(alt);
                questao.getAlternativas().add(alt);
            }
        }
    }
}
