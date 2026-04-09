package com.questoes.controller;

import com.questoes.dto.QuestaoForm;
import com.questoes.entity.Questao;
import com.questoes.service.FileStorageService;
import com.questoes.service.GitService;
import com.questoes.service.MarkdownService;
import com.questoes.service.QuestaoService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/questoes")
@RequiredArgsConstructor
public class QuestaoController {

    private final QuestaoService questaoService;
    private final MarkdownService markdownService;
    private final FileStorageService fileStorageService;

    @GetMapping
    public String listar(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(required = false) String disciplina,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) String busca,
            Model model) {

        Questao.TipoQuestao tipoEnum = null;
        if (tipo != null && !tipo.isBlank()) {
            try { tipoEnum = Questao.TipoQuestao.valueOf(tipo); } catch (Exception ignored) {}
        }

        Page<Questao> questoesPage = questaoService.buscar(disciplina, tipoEnum, busca,
                PageRequest.of(page, size, Sort.by("atualizadoEm").descending()));

        model.addAttribute("questoes", questoesPage);
        model.addAttribute("disciplinas", questaoService.listarDisciplinas());
        model.addAttribute("tiposQuestao", Questao.TipoQuestao.values());
        model.addAttribute("dificuldades", Questao.Dificuldade.values());
        model.addAttribute("filtroDisc", disciplina);
        model.addAttribute("filtroTipo", tipo);
        model.addAttribute("filtroBusca", busca);
        model.addAttribute("pageTitle", "Banco de Questões");
        return "questao/lista";
    }

    @GetMapping("/nova")
    public String nova(Model model) {
        model.addAttribute("form", new QuestaoForm());
        model.addAttribute("tiposQuestao", Questao.TipoQuestao.values());
        model.addAttribute("dificuldades", Questao.Dificuldade.values());
        model.addAttribute("disciplinas", questaoService.listarDisciplinas());
        model.addAttribute("pageTitle", "Nova Questão");
        model.addAttribute("isNova", true);
        return "questao/form";
    }

    @PostMapping("/nova")
    public String salvar(@ModelAttribute QuestaoForm form,
                         RedirectAttributes redirectAttributes) {
        try {
            Questao q = questaoService.salvar(form);
            redirectAttributes.addFlashAttribute("sucesso", "Questão criada com sucesso!");
            return "redirect:/questoes/" + q.getId();
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("erro", "Erro ao salvar: " + e.getMessage());
            return "redirect:/questoes/nova";
        }
    }

    @GetMapping("/{id}")
    public String ver(@PathVariable Long id, Model model) {
        Questao q = questaoService.buscarPorId(id)
                .orElseThrow(() -> new RuntimeException("Questão não encontrada"));

        model.addAttribute("questao", q);
        model.addAttribute("enunciadoHtml", markdownService.renderizar(q.getEnunciado()));
        model.addAttribute("gabaritoHtml", markdownService.renderizar(q.getGabarito()));
        model.addAttribute("versoes", questaoService.listarVersoes(id));
        model.addAttribute("pageTitle", q.getTitulo());
        return "questao/detalhe";
    }

    @GetMapping("/{id}/editar")
    public String editar(@PathVariable Long id, Model model) {
        Questao q = questaoService.buscarPorId(id)
                .orElseThrow(() -> new RuntimeException("Questão não encontrada"));

        QuestaoForm form = new QuestaoForm();
        form.setTitulo(q.getTitulo());
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

        model.addAttribute("form", form);
        model.addAttribute("questaoId", id);
        model.addAttribute("tiposQuestao", Questao.TipoQuestao.values());
        model.addAttribute("dificuldades", Questao.Dificuldade.values());
        model.addAttribute("disciplinas", questaoService.listarDisciplinas());
        model.addAttribute("pageTitle", "Editar Questão");
        model.addAttribute("isNova", false);
        return "questao/form";
    }

    @PostMapping("/{id}/editar")
    public String atualizar(@PathVariable Long id,
                            @ModelAttribute QuestaoForm form,
                            RedirectAttributes redirectAttributes) {
        try {
            questaoService.atualizar(id, form);
            redirectAttributes.addFlashAttribute("sucesso", "Questão atualizada com sucesso!");
            return "redirect:/questoes/" + id;
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("erro", "Erro ao atualizar: " + e.getMessage());
            return "redirect:/questoes/" + id + "/editar";
        }
    }

    @PostMapping("/{id}/excluir")
    public String excluir(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            questaoService.excluir(id);
            redirectAttributes.addFlashAttribute("sucesso", "Questão excluída.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("erro", "Erro ao excluir: " + e.getMessage());
        }
        return "redirect:/questoes";
    }

    @GetMapping("/{id}/versoes/{hash}")
    public String verVersao(@PathVariable Long id, @PathVariable String hash, Model model) {
        Questao q = questaoService.buscarPorId(id)
                .orElseThrow(() -> new RuntimeException("Questão não encontrada"));
        String conteudo = questaoService.obterConteudoVersao(id, hash);
        List<GitService.VersaoQuestao> versoes = questaoService.listarVersoes(id);

        model.addAttribute("questao", q);
        model.addAttribute("conteudoMd", conteudo);
        model.addAttribute("conteudoHtml", markdownService.renderizar(conteudo));
        model.addAttribute("hashAtual", hash);
        model.addAttribute("versoes", versoes);
        model.addAttribute("pageTitle", "Versão " + hash.substring(0, 8) + " - " + q.getTitulo());
        return "questao/versao";
    }

    // Upload de imagem via AJAX
    @PostMapping("/upload-imagem")
    @ResponseBody
    public ResponseEntity<Map<String, String>> uploadImagem(@RequestParam("file") MultipartFile file) {
        try {
            String url = fileStorageService.salvarImagem(file);
            return ResponseEntity.ok(Map.of("url", url, "markdown", "![](" + url + ")"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        }
    }

    // Preview markdown via AJAX
    @PostMapping("/preview-markdown")
    @ResponseBody
    public ResponseEntity<Map<String, String>> previewMarkdown(@RequestBody Map<String, String> body) {
        String html = markdownService.renderizar(body.get("markdown"));
        return ResponseEntity.ok(Map.of("html", html));
    }
}
