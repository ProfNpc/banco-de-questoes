package com.questoes.controller;

import com.questoes.entity.Professor;
import com.questoes.service.ProfessorService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Optional;

/**
 * Gerencia o login/logout do painel administrativo (professor).
 *
 * Atributos de sessão gerenciados:
 *   "professorAdminId"   → Long: ID do professor autenticado no painel admin
 *   "professorAdminNome" → String: nome do professor para exibição
 *   "loginRedirect"      → String: URL de destino pós-login (opcional)
 */
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final ProfessorService professorService;

    @GetMapping("/login")
    public String loginPage(HttpSession session, Model model) {
        // Se já autenticado no painel admin, redirecionar para dashboard
        if (session.getAttribute("professorAdminId") != null) {
            return "redirect:/";
        }
        model.addAttribute("pageTitle", "Login — Painel Administrativo");
        return "admin/login";
    }

    @PostMapping("/login")
    public String login(@RequestParam String email,
                        @RequestParam String senha,
                        HttpSession session,
                        RedirectAttributes ra) {

        Optional<Professor> prof = professorService.buscarPorEmail(email);

        if (prof.isPresent() && professorService.verificarSenha(prof.get(), senha)) {
            // Autenticar no contexto admin
            session.setAttribute("professorAdminId",   prof.get().getId());
            session.setAttribute("professorAdminNome", prof.get().getNome());
            session.removeAttribute("loginRedirect");   // limpar antes de usar

            // Redirecionar para URL original que o usuário tentava acessar
            String destino = (String) session.getAttribute("loginRedirect");
            if (destino != null && !destino.isBlank() && !destino.contains("/admin/login")) {
                return "redirect:" + destino;
            }
            return "redirect:/";
        }

        ra.addFlashAttribute("erro", "E-mail ou senha inválidos.");
        return "redirect:/admin/login";
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        // Remover apenas os atributos do contexto admin, preservando sessão do portal de provas
        session.removeAttribute("professorAdminId");
        session.removeAttribute("professorAdminNome");
        return "redirect:/admin/login";
    }
}
