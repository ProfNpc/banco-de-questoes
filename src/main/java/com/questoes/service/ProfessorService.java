package com.questoes.service;

import com.questoes.entity.Professor;
import com.questoes.repository.ProfessorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProfessorService {

    private final ProfessorRepository professorRepo;
    private final SenhaService senhaService;

    public Optional<Professor> buscarPorId(Long id) {
        return professorRepo.findById(id);
    }

    public Optional<Professor> buscarPorEmail(String email) {
        return professorRepo.findByEmailAndAtivoTrue(email);
    }

    @Transactional
    public Professor salvar(String nome, String email, String senha) {
        Professor p = new Professor();
        p.setNome(nome.trim());
        p.setEmail(email.trim().toLowerCase());
        String salto = senhaService.gerarSalto();
        p.setSalto(salto);
        p.setHashSenha(senhaService.calcularHash(salto, senha));
        return professorRepo.save(p);
    }

    public boolean verificarSenha(Professor professor, String senha) {
        return senhaService.verificar(professor.getSalto(), professor.getHashSenha(), senha);
    }

    public boolean existeProfessor() {
        return professorRepo.count() > 0;
    }
}
