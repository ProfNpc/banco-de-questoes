package com.questoes.controller;

import com.questoes.entity.Aluno;
import com.questoes.entity.Turma;
import com.questoes.service.*;
import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/turmas")
@RequiredArgsConstructor
public class TurmaController {

    private final TurmaService turmaService;
    private final AlunoService alunoService;
    private final ProvaService provaService;
    private final AplicacaoProvaService aplicacaoService;

    @GetMapping
    public String listar(@RequestParam(defaultValue = "0") int page,
                         @RequestParam(defaultValue = "12") int size,
                         @RequestParam(required = false) String busca,
                         Model model) {
        Page<Turma> resultado = turmaService.buscar(busca,
                PageRequest.of(page, size, Sort.by("nome")));
        model.addAttribute("turmas", resultado);
        model.addAttribute("busca", busca);
        model.addAttribute("pageTitle", "Turmas");
        return "turma/lista";
    }

    @GetMapping("/nova")
    public String nova(Model model) {
        model.addAttribute("pageTitle", "Nova Turma");
        return "turma/form";
    }

    @PostMapping("/nova")
    public String salvar(@RequestParam String nome,
                         @RequestParam(required = false) String descricao,
                         RedirectAttributes ra) {
        try {
            Turma t = turmaService.salvar(nome, descricao);
            ra.addFlashAttribute("sucesso", "Turma criada!");
            return "redirect:/turmas/" + t.getId();
        } catch (Exception e) {
            ra.addFlashAttribute("erro", "Erro: " + e.getMessage());
            return "redirect:/turmas/nova";
        }
    }

    @GetMapping("/{id}")
    public String detalhe(@PathVariable Long id,
                          @RequestParam(required = false) String busca,
                          Model model) {
        Turma t = turmaService.buscarPorId(id)
                .orElseThrow(() -> new RuntimeException("Turma não encontrada"));
        model.addAttribute("turma", t);
        model.addAttribute("busca", busca);
        model.addAttribute("provas", provaService.listarTodas());
        model.addAttribute("pageTitle", "Turma: " + t.getNome());
        return "turma/detalhe";
    }

    @GetMapping("/{id}/editar")
    public String editar(@PathVariable Long id, Model model) {
        Turma t = turmaService.buscarPorId(id)
                .orElseThrow(() -> new RuntimeException("Turma não encontrada"));
        model.addAttribute("turma", t);
        model.addAttribute("pageTitle", "Editar Turma");
        return "turma/form";
    }

    @PostMapping("/{id}/editar")
    public String atualizar(@PathVariable Long id,
                            @RequestParam String nome,
                            @RequestParam(required = false) String descricao,
                            RedirectAttributes ra) {
        try {
            turmaService.atualizar(id, nome, descricao);
            ra.addFlashAttribute("sucesso", "Turma atualizada!");
        } catch (Exception e) {
            ra.addFlashAttribute("erro", "Erro: " + e.getMessage());
        }
        return "redirect:/turmas/" + id;
    }

    @PostMapping("/{id}/excluir")
    public String excluir(@PathVariable Long id, RedirectAttributes ra) {
        try {
            turmaService.excluir(id);
            ra.addFlashAttribute("sucesso", "Turma excluída.");
        } catch (Exception e) {
            ra.addFlashAttribute("erro", "Erro: " + e.getMessage());
        }
        return "redirect:/turmas";
    }

    /** Adicionar/remover aluno da turma */
    @PostMapping("/{id}/alunos/adicionar")
    public String adicionarAluno(@PathVariable Long id,
                                  @RequestParam Long alunoId,
                                  RedirectAttributes ra) {
        try {
            turmaService.adicionarAluno(id, alunoId);
            ra.addFlashAttribute("sucesso", "Aluno adicionado à turma.");
        } catch (Exception e) {
            ra.addFlashAttribute("erro", "Erro: " + e.getMessage());
        }
        return "redirect:/turmas/" + id;
    }

    @PostMapping("/{id}/alunos/remover")
    public String removerAluno(@PathVariable Long id,
                                @RequestParam Long alunoId,
                                RedirectAttributes ra) {
        try {
            turmaService.removerAluno(id, alunoId);
            ra.addFlashAttribute("sucesso", "Aluno removido da turma.");
        } catch (Exception e) {
            ra.addFlashAttribute("erro", "Erro: " + e.getMessage());
        }
        return "redirect:/turmas/" + id;
    }


    /** Busca AJAX: retorna JSON com alunos que combinam com o padrão, excluindo os já na turma */
    @GetMapping("/{id}/alunos/buscar")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> buscarAlunos(
            @PathVariable Long id,
            @RequestParam(defaultValue = "") String q) {
        Turma turma = turmaService.buscarPorId(id).orElse(null);
        if (turma == null) return ResponseEntity.notFound().build();

        Set<Long> jaInscritos = turma.getAlunos().stream()
                .map(Aluno::getId)
                .collect(Collectors.toSet());

        String busca = q.trim().toLowerCase();
        List<Map<String, Object>> resultado = new ArrayList<>();

        alunoService.buscar(busca.isBlank() ? null : busca,
                PageRequest.of(0, 20, Sort.by("nome")))
            .getContent()
            .forEach(a -> {
                // incluir apenas alunos que NÃO estão na turma
                if (!jaInscritos.contains(a.getId())) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id",    a.getId());
                    m.put("nome",  a.getNome());
                    m.put("email", a.getEmail());
                    resultado.add(m);
                }
            });

        return ResponseEntity.ok(resultado);
    }

    /** Adiciona múltiplos alunos de uma vez à turma */
    @PostMapping("/{id}/alunos/adicionar-multiplos")
    public String adicionarMultiplos(@PathVariable Long id,
                                      @RequestParam List<Long> alunoIds,
                                      RedirectAttributes ra) {
        int adicionados = 0;
        for (Long aId : alunoIds) {
            try { turmaService.adicionarAluno(id, aId); adicionados++; }
            catch (Exception ignored) {}
        }
        ra.addFlashAttribute("sucesso", adicionados + " aluno(s) adicionado(s) à turma.");
        return "redirect:/turmas/" + id;
    }


    /** Aplicar prova a alunos selecionados da turma */
    @PostMapping("/{id}/aplicar-prova")
    public String aplicarProva(@PathVariable Long id,
                                @RequestParam Long provaId,
                                @RequestParam List<Long> alunoIds,
                                @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                    LocalDateTime inicioPrevisto,
                                @RequestParam int duracaoMinutos,
                                RedirectAttributes ra) {
        try {
            int qtd = aplicacaoService.aplicarParaAlunos(provaId, alunoIds,
                    inicioPrevisto, duracaoMinutos);
            ra.addFlashAttribute("sucesso",
                    "Prova aplicada para " + qtd + " aluno(s)!");
        } catch (Exception e) {
            ra.addFlashAttribute("erro", "Erro ao aplicar prova: " + e.getMessage());
        }
        return "redirect:/turmas/" + id;
    }
}
