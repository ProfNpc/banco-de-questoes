package com.questoes.controller;

import com.questoes.entity.Aluno;
import com.questoes.service.AlunoService;
import com.questoes.service.SenhaService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/alunos")
@RequiredArgsConstructor
public class AlunoController {

    private final AlunoService alunoService;
    private final SenhaService senhaService;

    @GetMapping
    public String listar(@RequestParam(defaultValue = "0") int page,
                         @RequestParam(defaultValue = "15") int size,
                         @RequestParam(required = false) String busca,
                         Model model) {
        Page<Aluno> resultado = alunoService.buscar(busca,
                PageRequest.of(page, size, Sort.by("nome")));
        model.addAttribute("alunos", resultado);
        model.addAttribute("busca", busca);
        model.addAttribute("pageTitle", "Alunos");
        return "aluno/lista";
    }

    @GetMapping("/novo")
    public String novo(Model model) {
        model.addAttribute("pageTitle", "Novo Aluno");
        // Gerar senha sugerida
        model.addAttribute("senhaSugerida", senhaService.gerarSenhaAleatoria(5));
        return "aluno/form";
    }

    @PostMapping("/novo")
    public String salvar(@RequestParam String nome,
                         @RequestParam String email,
                         @RequestParam String senha,
                         RedirectAttributes ra) {
        try {
            Aluno a = alunoService.salvar(nome, email, senha);
            ra.addFlashAttribute("sucesso", "Aluno criado: " + a.getNome());
            return "redirect:/alunos";
        } catch (Exception e) {
            ra.addFlashAttribute("erro", "Erro: " + e.getMessage());
            return "redirect:/alunos/novo";
        }
    }

    @GetMapping("/{id}/editar")
    public String editar(@PathVariable Long id, Model model) {
        Aluno a = alunoService.buscarPorId(id)
                .orElseThrow(() -> new RuntimeException("Aluno não encontrado"));
        model.addAttribute("aluno", a);
        model.addAttribute("pageTitle", "Editar Aluno");
        return "aluno/editar";
    }

    @PostMapping("/{id}/editar")
    public String atualizar(@PathVariable Long id,
                            @RequestParam String nome,
                            @RequestParam String email,
                            RedirectAttributes ra) {
        try {
            alunoService.atualizar(id, nome, email);
            ra.addFlashAttribute("sucesso", "Aluno atualizado!");
        } catch (Exception e) {
            ra.addFlashAttribute("erro", "Erro: " + e.getMessage());
        }
        return "redirect:/alunos";
    }

    @PostMapping("/{id}/excluir")
    public String excluir(@PathVariable Long id, RedirectAttributes ra) {
        try {
            alunoService.excluir(id);
            ra.addFlashAttribute("sucesso", "Aluno removido.");
        } catch (Exception e) {
            ra.addFlashAttribute("erro", "Erro: " + e.getMessage());
        }
        return "redirect:/alunos";
    }

    /** Tela de importação em lote */
    @GetMapping("/importar")
    public String importar(Model model) {
        model.addAttribute("pageTitle", "Importar Alunos em Lote");
        return "aluno/importar";
    }

    @PostMapping("/importar")
    public String processarImportacao(@RequestParam String texto, RedirectAttributes ra) {
        try {
            AlunoService.ImportacaoResultado resultado = alunoService.importarLote(texto);
            ra.addFlashAttribute("sucesso",
                    resultado.totalSucesso() + " aluno(s) importado(s) com sucesso.");
            if (!resultado.getErros().isEmpty()) {
                ra.addFlashAttribute("avisos", resultado.getErros());
            }
        } catch (Exception e) {
            ra.addFlashAttribute("erro", "Erro na importação: " + e.getMessage());
        }
        return "redirect:/alunos";
    }
}
