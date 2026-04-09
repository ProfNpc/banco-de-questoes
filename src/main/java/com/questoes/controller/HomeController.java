package com.questoes.controller;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.questoes.entity.Questao;
import com.questoes.repository.ProvaRepository;
import com.questoes.repository.QuestaoRepository;
import com.questoes.service.PdfService;
import com.questoes.service.QuestaoService;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final QuestaoRepository questaoRepository;
    private final ProvaRepository provaRepository;
    private final QuestaoService questaoService;
    private final PdfService pdfService;


	@GetMapping("/")
    public String home(Model model) {
        long totalQuestoes = questaoRepository.findByAtivoTrueOrderByAtualizadoEmDesc().size();
        long totalProvas = provaRepository.findByAtivoTrueOrderByAtualizadoEmDesc().size();
        model.addAttribute("totalQuestoes", totalQuestoes);
        model.addAttribute("totalProvas", totalProvas);
        model.addAttribute("ultimasQuestoes",
                questaoRepository.findByAtivoTrueOrderByAtualizadoEmDesc()
                        .stream().limit(5).toList());
        model.addAttribute("pageTitle", "Dashboard");
        return "index";
    }

    @PostMapping("/exportar-pdf")
    public ResponseEntity<byte[]> exportarQuestoesPdf(
            @RequestParam List<Long> questoesIds,
            @RequestParam(defaultValue = "Questões Selecionadas") String titulo) {
        try {
            List<Questao> questoes = new ArrayList<>();
            for (Long id : questoesIds) {
                questaoService.buscarPorId(id).ifPresent(questoes::add);
            }
            byte[] pdf = pdfService.gerarPdfQuestoesSelecionadas(questoes, titulo);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"questoes-selecionadas.pdf\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdf);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
