package com.questoes.service;

import com.questoes.entity.Aluno;
import com.questoes.entity.Turma;
import com.questoes.repository.AlunoRepository;
import com.questoes.repository.TurmaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AlunoService {

    private final AlunoRepository alunoRepository;
    private final TurmaRepository turmaRepository;
    private final SenhaService senhaService;

    public Page<Aluno> buscar(String busca, Pageable pageable) {
        String b = (busca == null || busca.isBlank()) ? null : busca;
        Long idBusca = null;
        if (b != null) {
            try { idBusca = Long.parseLong(b.trim()); } catch (NumberFormatException ignored) {}
        }
        return alunoRepository.buscar(b, idBusca, pageable);
    }

    public Optional<Aluno> buscarPorId(Long id) {
        return alunoRepository.findById(id);
    }

    public Optional<Aluno> buscarPorEmail(String email) {
        return alunoRepository.findByEmailAndAtivoTrue(email);
    }

    @Transactional
    public Aluno salvar(String nome, String email, String senha) {
        Aluno a = new Aluno();
        a.setNome(nome.trim());
        a.setEmail(email.trim().toLowerCase());
        String salto = senhaService.gerarSalto();
        a.setSalto(salto);
        a.setHashSenha(senhaService.calcularHash(salto, senha));
        a.setDeveTrocarSenha(true);
        return alunoRepository.save(a);
    }

    @Transactional
    public Aluno atualizar(Long id, String nome, String email) {
        Aluno a = alunoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Aluno não encontrado: " + id));
        a.setNome(nome.trim());
        a.setEmail(email.trim().toLowerCase());
        return alunoRepository.save(a);
    }

    @Transactional
    public void trocarSenha(Long id, String novaSenha) {
        Aluno a = alunoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Aluno não encontrado: " + id));
        String salto = senhaService.gerarSalto();
        a.setSalto(salto);
        a.setHashSenha(senhaService.calcularHash(salto, novaSenha));
        a.setDeveTrocarSenha(false);
        alunoRepository.save(a);
    }

    @Transactional
    public void excluir(Long id) {
        Aluno a = alunoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Aluno não encontrado: " + id));
        a.setAtivo(false);
        alunoRepository.save(a);
    }

    /**
     * Importa alunos a partir de texto multilinha no formato:
     *   nome/email/senha;
     * Retorna relatório de importação.
     */
    @Transactional
    public ImportacaoResultado importarLote(String texto) {
        ImportacaoResultado resultado = new ImportacaoResultado();
        if (texto == null || texto.isBlank()) return resultado;

        String[] linhas = texto.split("[\\r\\n]+");
        for (String linha : linhas) {
            linha = linha.trim();
            if (linha.isBlank()) continue;
            // Remover ponto-e-vírgula opcional no final
            if (linha.endsWith(";")) linha = linha.substring(0, linha.length() - 1).trim();

            String[] partes = linha.split("/");
            if (partes.length < 3) {
                resultado.getErros().add("Linha ignorada (formato inválido): " + linha);
                continue;
            }
            String nome  = partes[0].trim();
            String email = partes[1].trim().toLowerCase();
            String senha = partes[2].trim();

            if (nome.isBlank() || email.isBlank() || senha.isBlank()) {
                resultado.getErros().add("Campos vazios na linha: " + linha);
                continue;
            }
            if (alunoRepository.existsByEmail(email)) {
                resultado.getErros().add("E-mail já cadastrado: " + email);
                continue;
            }
            try {
                salvar(nome, email, senha);
                resultado.getSucesso().add(nome + " <" + email + ">");
            } catch (Exception e) {
                resultado.getErros().add("Erro ao criar " + email + ": " + e.getMessage());
            }
        }
        return resultado;
    }

    public boolean verificarSenha(Aluno aluno, String senha) {
        return senhaService.verificar(aluno.getSalto(), aluno.getHashSenha(), senha);
    }

    public static class ImportacaoResultado {
        private final List<String> sucesso = new ArrayList<>();
        private final List<String> erros   = new ArrayList<>();
        public List<String> getSucesso() { return sucesso; }
        public List<String> getErros()   { return erros; }
        public int totalSucesso() { return sucesso.size(); }
        public int totalErros()   { return erros.size(); }
    }
}
