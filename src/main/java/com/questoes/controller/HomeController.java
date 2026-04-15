package com.questoes.controller;

import com.questoes.entity.Questao;
import com.questoes.repository.ProvaRepository;
import com.questoes.repository.QuestaoRepository;
import com.questoes.service.BackupService;
import com.questoes.service.PdfService;
import com.questoes.service.QuestaoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.List;

@Controller
@RequiredArgsConstructor
@Slf4j
public class HomeController {

    private final QuestaoRepository questaoRepository;
    private final ProvaRepository provaRepository;
    private final QuestaoService questaoService;
    private final PdfService pdfService;
    private final BackupService backupService;

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
            for (Long id : questoesIds) questaoService.buscarPorId(id).ifPresent(questoes::add);
            byte[] pdf = pdfService.gerarPdfQuestoesSelecionadas(questoes, titulo);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"questoes-selecionadas.pdf\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdf);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    // ── Backup ────────────────────────────────────────────────────────────────

    @GetMapping("/backup/download")
    public ResponseEntity<byte[]> baixarBackup() {
        try {
            byte[] dados = backupService.gerarBackup();
            String nome = backupService.gerarNomeArquivoBackup();
            log.info("Backup gerado: {} ({} bytes)", nome, dados.length);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nome + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(dados);
        } catch (Exception e) {
            log.error("Erro ao gerar backup", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/backup/restaurar")
    public String restaurarBackup(@RequestParam("arquivo") MultipartFile arquivo,
                                   RedirectAttributes ra) {
        if (arquivo.isEmpty()) {
            ra.addFlashAttribute("erro", "Nenhum arquivo selecionado.");
            return "redirect:/";
        }
        try {
            backupService.restaurarBackup(arquivo);
            ra.addFlashAttribute("sucesso",
                    "Backup restaurado com sucesso! Os dados foram substituídos pelo conteúdo do arquivo.");
        } catch (Exception e) {
            log.error("Erro ao restaurar backup", e);
            ra.addFlashAttribute("erro", "Erro ao restaurar backup: " + e.getMessage());
        }
        return "redirect:/";
    }
}
