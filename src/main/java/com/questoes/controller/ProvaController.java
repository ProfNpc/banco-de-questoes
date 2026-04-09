package com.questoes.controller;

import com.questoes.dto.ProvaForm;
import com.questoes.entity.Prova;
import com.questoes.entity.Questao;
import com.questoes.service.MarkdownService;
import com.questoes.service.PdfService;
import com.questoes.service.ProvaService;
import com.questoes.service.QuestaoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/provas")
@RequiredArgsConstructor
public class ProvaController {

    private final ProvaService provaService;
    private final QuestaoService questaoService;
    private final PdfService pdfService;
    private final MarkdownService markdownService;

	@GetMapping
    public String listar(Model model) {
        model.addAttribute("provas", provaService.listarTodas());
        model.addAttribute("pageTitle", "Minhas Provas");
        return "prova/lista";
    }

    @GetMapping("/nova")
    public String nova(Model model) {
        model.addAttribute("form", new ProvaForm());
        model.addAttribute("questoes", questaoService.listarTodas());
        model.addAttribute("disciplinas", questaoService.listarDisciplinas());
        model.addAttribute("pageTitle", "Nova Prova");
        model.addAttribute("isNova", true);
        return "prova/form";
    }

    @PostMapping("/nova")
    public String salvar(@ModelAttribute ProvaForm form,
                         RedirectAttributes redirectAttributes) {
        try {
            Prova p = provaService.salvar(form);
            redirectAttributes.addFlashAttribute("sucesso", "Prova criada com sucesso!");
            return "redirect:/provas/" + p.getId();
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("erro", "Erro ao salvar: " + e.getMessage());
            return "redirect:/provas/nova";
        }
    }

    @GetMapping("/{id}")
    public String ver(@PathVariable Long id, Model model) {
        Prova prova = provaService.buscarPorId(id)
                .orElseThrow(() -> new RuntimeException("Prova não encontrada"));
        model.addAttribute("prova", prova);
        model.addAttribute("markdownService", markdownService);
        model.addAttribute("pageTitle", prova.getTitulo());
        return "prova/detalhe";
    }

    @GetMapping("/{id}/editar")
    public String editar(@PathVariable Long id, Model model) {
        Prova prova = provaService.buscarPorId(id)
                .orElseThrow(() -> new RuntimeException("Prova não encontrada"));

        ProvaForm form = new ProvaForm();
        form.setTitulo(prova.getTitulo());
        form.setDescricao(prova.getDescricao());
        form.setDisciplina(prova.getDisciplina());
        prova.getProvaQuestoes().forEach(pq -> {
            form.getQuestoesIds().add(pq.getQuestao().getId());
            form.getPontos().add(pq.getPontos());
        });

        model.addAttribute("form", form);
        model.addAttribute("provaId", id);
        model.addAttribute("questoes", questaoService.listarTodas());
        model.addAttribute("disciplinas", questaoService.listarDisciplinas());
        model.addAttribute("pageTitle", "Editar Prova");
        model.addAttribute("isNova", false);
        return "prova/form";
    }

    @PostMapping("/{id}/editar")
    public String atualizar(@PathVariable Long id, @ModelAttribute ProvaForm form,
                            RedirectAttributes redirectAttributes) {
        try {
            provaService.atualizar(id, form);
            redirectAttributes.addFlashAttribute("sucesso", "Prova atualizada!");
            return "redirect:/provas/" + id;
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("erro", "Erro: " + e.getMessage());
            return "redirect:/provas/" + id + "/editar";
        }
    }

    @PostMapping("/{id}/excluir")
    public String excluir(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            provaService.excluir(id);
            redirectAttributes.addFlashAttribute("sucesso", "Prova excluída.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("erro", "Erro: " + e.getMessage());
        }
        return "redirect:/provas";
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> gerarPdf(
            @PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean gabarito) {
        try {
            Prova prova = provaService.buscarPorId(id)
                    .orElseThrow(() -> new RuntimeException("Prova não encontrada"));
            byte[] pdf = pdfService.gerarPdfProva(prova, gabarito);
            String filename = "prova-" + id + (gabarito ? "-gabarito" : "") + ".pdf";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdf);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
