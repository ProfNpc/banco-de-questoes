package com.questoes.config;

import com.questoes.service.ProfessorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements ApplicationRunner {

    private final ProfessorService professorService;

    @Value("${app.professor.email:professor@questoes.local}")
    private String professorEmail;

    @Value("${app.professor.senha:professor123}")
    private String professorSenha;

    @Value("${app.professor.nome:Professor}")
    private String professorNome;

    @Override
    public void run(ApplicationArguments args) {
        if (!professorService.existeProfessor()) {
            professorService.salvar(professorNome, professorEmail, professorSenha);
            log.info("Professor padrão criado: {} / senha: {}", professorEmail, professorSenha);
        }
    }
}
