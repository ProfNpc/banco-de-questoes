package com.questoes.service;

import com.questoes.entity.Aluno;
import com.questoes.entity.Turma;
import com.questoes.repository.AlunoRepository;
import com.questoes.repository.TurmaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class TurmaService {

    private final TurmaRepository turmaRepository;
    private final AlunoRepository alunoRepository;

    public Page<Turma> buscar(String busca, Pageable pageable) {
        String b = (busca == null || busca.isBlank()) ? null : busca;
        return turmaRepository.buscar(b, pageable);
    }

    public List<Turma> listarTodas() {
        return turmaRepository.findByAtivoTrueOrderByNomeAsc();
    }

    public Optional<Turma> buscarPorId(Long id) {
        return turmaRepository.findById(id);
    }

    @Transactional
    public Turma salvar(String nome, String descricao) {
        Turma t = new Turma();
        t.setNome(nome.trim());
        t.setDescricao(descricao);
        return turmaRepository.save(t);
    }

    @Transactional
    public Turma atualizar(Long id, String nome, String descricao) {
        Turma t = turmaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Turma não encontrada: " + id));
        t.setNome(nome.trim());
        t.setDescricao(descricao);
        return turmaRepository.save(t);
    }

    @Transactional
    public void excluir(Long id) {
        Turma t = turmaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Turma não encontrada: " + id));
        t.setAtivo(false);
        turmaRepository.save(t);
    }

    @Transactional
    public void adicionarAluno(Long turmaId, Long alunoId) {
        Turma t = turmaRepository.findById(turmaId)
                .orElseThrow(() -> new RuntimeException("Turma não encontrada"));
        Aluno a = alunoRepository.findById(alunoId)
                .orElseThrow(() -> new RuntimeException("Aluno não encontrado"));
        t.getAlunos().add(a);
        turmaRepository.save(t);
    }

    @Transactional
    public void removerAluno(Long turmaId, Long alunoId) {
        Turma t = turmaRepository.findById(turmaId)
                .orElseThrow(() -> new RuntimeException("Turma não encontrada"));
        t.getAlunos().removeIf(a -> a.getId().equals(alunoId));
        turmaRepository.save(t);
    }

    @Transactional
    public void sincronizarAlunos(Long turmaId, Set<Long> alunoIds) {
        Turma t = turmaRepository.findById(turmaId)
                .orElseThrow(() -> new RuntimeException("Turma não encontrada"));
        t.getAlunos().clear();
        if (alunoIds != null) {
            alunoIds.forEach(aid ->
                alunoRepository.findById(aid).ifPresent(a -> t.getAlunos().add(a)));
        }
        turmaRepository.save(t);
    }
}
