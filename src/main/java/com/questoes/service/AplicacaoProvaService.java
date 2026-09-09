package com.questoes.service;

import com.questoes.entity.*;
import com.questoes.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AplicacaoProvaService {

    private final AplicacaoProvaRepository aplicacaoRepo;
    private final RespostaAlunoRepository respostaRepo;
    private final AlunoRepository alunoRepo;
    private final ProvaRepository provaRepo;
    private final ProvaQuestaoRepository provaQuestaoRepo;

    /** Cria aplicações de prova para uma lista de alunos */
    @Transactional
    public int aplicarParaAlunos(Long provaId, List<Long> alunoIds,
                                  LocalDateTime inicioPrevisto, int duracaoMinutos) {
        Prova prova = provaRepo.findById(provaId)
                .orElseThrow(() -> new RuntimeException("Prova não encontrada"));
        int count = 0;
        for (Long alunoId : alunoIds) {
            Aluno aluno = alunoRepo.findById(alunoId).orElse(null);
            if (aluno == null) continue;
            // Evitar duplicatas
            if (aplicacaoRepo.findByAlunoIdAndProvaId(alunoId, provaId).isPresent()) continue;
            AplicacaoProva ap = new AplicacaoProva();
            ap.setProva(prova);
            ap.setAluno(aluno);
            ap.setInicioPrevisto(inicioPrevisto);
            ap.setDuracaoMinutos(duracaoMinutos);
            ap.setStatus(AplicacaoProva.Status.AGENDADA);
            aplicacaoRepo.save(ap);
            count++;
        }
        return count;
    }

    /** Aluno inicia a prova — registra horário real de início */
    @Transactional
    public AplicacaoProva iniciarProva(Long aplicacaoId) {
        AplicacaoProva ap = aplicacaoRepo.findById(aplicacaoId)
                .orElseThrow(() -> new RuntimeException("Aplicação não encontrada"));
        if (ap.getStatus() == AplicacaoProva.Status.AGENDADA) {
            ap.setInicioReal(LocalDateTime.now());
            ap.setStatus(AplicacaoProva.Status.EM_ANDAMENTO);
            aplicacaoRepo.save(ap);
        }
        return ap;
    }

    /** Registra ou atualiza a resposta de uma questão */
    @Transactional
    public void responder(Long aplicacaoId, Long questaoId, Long alternativaId) {
        AplicacaoProva ap = aplicacaoRepo.findById(aplicacaoId)
                .orElseThrow(() -> new RuntimeException("Aplicação não encontrada"));

        // Verificar tempo
        if (!ap.dentroDoPrazo()) {
            ap.setStatus(AplicacaoProva.Status.EXPIRADA);
            aplicacaoRepo.save(ap);
            throw new RuntimeException("Tempo de prova encerrado!");
        }

        RespostaAluno resp = respostaRepo
                .findByAplicacaoIdAndQuestaoId(aplicacaoId, questaoId)
                .orElse(new RespostaAluno());

        resp.setAplicacao(ap);

        // Carregar questão e alternativa
        Questao questao = ap.getProva().getProvaQuestoes().stream()
                .map(ProvaQuestao::getQuestao)
                .filter(q -> q.getId().equals(questaoId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Questão não pertence a esta prova"));
        resp.setQuestao(questao);

        if (alternativaId != null) {
            Alternativa alt = questao.getAlternativas().stream()
                    .filter(a -> a.getId().equals(alternativaId))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Alternativa inválida"));
            resp.setAlternativa(alt);
            resp.setCorreta(Boolean.TRUE.equals(alt.getCorreta()));
        } else {
            resp.setAlternativa(null);
            resp.setCorreta(false);
        }
        resp.setRespondidaEm(LocalDateTime.now());
        respostaRepo.save(resp);
    }

    /** Encerra a prova, calcula nota e persiste */
    @Transactional
    public AplicacaoProva encerrar(Long aplicacaoId) {
        AplicacaoProva ap = aplicacaoRepo.findById(aplicacaoId)
                .orElseThrow(() -> new RuntimeException("Aplicação não encontrada"));
        if (ap.getStatus() == AplicacaoProva.Status.EM_ANDAMENTO
                || ap.getStatus() == AplicacaoProva.Status.EXPIRADA) {
            ap.setFimReal(LocalDateTime.now());
            ap.setStatus(AplicacaoProva.Status.ENCERRADA);

            // Calcular nota
            int total = ap.getProva().getProvaQuestoes().size();
            long corretas = respostaRepo.countCorretas(ap.getId());
            ap.setNota(total > 0 ? (corretas * 100.0 / total) : 0.0);
            aplicacaoRepo.save(ap);
        }
        return ap;
    }

    public Optional<AplicacaoProva> buscarPorId(Long id) {
        return aplicacaoRepo.findById(id);
    }

    public List<AplicacaoProva> listarPorAluno(Long alunoId) {
        return aplicacaoRepo.findByAlunoIdOrderByInicioPrevisto(alunoId);
    }

    public List<AplicacaoProva> listarPorProva(Long provaId) {
        return aplicacaoRepo.findComAlunosByProvaId(provaId);
    }

    public List<RespostaAluno> listarRespostas(Long aplicacaoId) {
        return respostaRepo.findByAplicacaoId(aplicacaoId);
    }

    /** Atualiza status de aplicações expiradas */
    @Transactional
    public void verificarExpiracoes() {
        aplicacaoRepo.findAll().stream()
                .filter(ap -> ap.getStatus() == AplicacaoProva.Status.EM_ANDAMENTO
                        && !ap.dentroDoPrazo())
                .forEach(ap -> {
                    ap.setStatus(AplicacaoProva.Status.EXPIRADA);
                    ap.setFimReal(ap.calcularFimPermitido());
                    int total = ap.getProva().getProvaQuestoes().size();
                    long corretas = respostaRepo.countCorretas(ap.getId());
                    ap.setNota(total > 0 ? (corretas * 100.0 / total) : 0.0);
                    aplicacaoRepo.save(ap);
                });
    }
}
