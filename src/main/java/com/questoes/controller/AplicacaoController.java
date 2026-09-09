package com.questoes.controller;

import com.questoes.entity.*;
import com.questoes.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Optional;

/**
 * Portal de Provas — /prova/**
 *
 * Atributos de sessão deste contexto (SEPARADOS do painel admin):
 *   "alunoId" / "alunoNome"       → aluno autenticado
 *   "professorId" / "professorNome" → professor no painel de acompanhamento
 *
 * Isolamentos aplicados:
 *   1. Aluno só acessa suas próprias AplicacaoProvas (verifica ap.aluno.id == alunoId)
 *   2. Professor do portal acessa qualquer resultado, mas não o painel admin
 *   3. Session fixation: invalidamos a sessão antiga ao fazer login
 */
@Controller
@RequestMapping("/prova")
@RequiredArgsConstructor
public class AplicacaoController {

    private final AplicacaoProvaService aplicacaoService;
    private final AlunoService alunoService;
    private final ProfessorService professorService;
    private final ProvaService provaService;

    // ── helpers ──────────────────────────────────────────────────────────────

    private static Long alunoId(HttpSession s)    { return s == null ? null : (Long) s.getAttribute("alunoId"); }
    private static Long profPortalId(HttpSession s){ return s == null ? null : (Long) s.getAttribute("professorId"); }

    /** Após invalidar a sessão, cria uma nova e a preenche com o atributo desejado */
    private HttpSession renovarSessao(HttpServletRequest req) {
        HttpSession old = req.getSession(false);
        String redirect = old != null ? (String) old.getAttribute("portalRedirect") : null;
        if (old != null) old.invalidate();
        HttpSession nova = req.getSession(true);
        if (redirect != null) nova.setAttribute("portalRedirect", redirect);
        return nova;
    }

    private String irParaLogin(HttpSession session, String destino) {
        if (session != null && destino != null) session.setAttribute("portalRedirect", destino);
        return "redirect:/prova/fazer";
    }

    // ── Login / Logout ────────────────────────────────────────────────────────

    @GetMapping("/fazer")
    public String loginPage(HttpSession session, Model model) {
        if (alunoId(session) != null)    return "redirect:/prova/minha-lista";
        if (profPortalId(session) != null) return "redirect:/prova/acompanhar";
        model.addAttribute("pageTitle", "Portal de Provas");
        return "aplicacao/login";
    }

    @PostMapping("/fazer")
    public String login(@RequestParam String email,
                        @RequestParam String senha,
                        HttpServletRequest req,
                        RedirectAttributes ra) {

        HttpSession session = req.getSession(false);

        // Tentar como aluno
        Optional<Aluno> aluno = alunoService.buscarPorEmail(email);
        if (aluno.isPresent() && alunoService.verificarSenha(aluno.get(), senha)) {
            HttpSession nova = renovarSessao(req);
            String redirect = (String) nova.getAttribute("portalRedirect");
            nova.setAttribute("alunoId",   aluno.get().getId());
            nova.setAttribute("alunoNome", aluno.get().getNome());
            nova.removeAttribute("portalRedirect");
            if (Boolean.TRUE.equals(aluno.get().getDeveTrocarSenha())) return "redirect:/prova/trocar-senha";
            if (redirect != null && redirect.startsWith("/prova/")) return "redirect:" + redirect;
            return "redirect:/prova/minha-lista";
        }

        // Tentar como professor
        Optional<Professor> prof = professorService.buscarPorEmail(email);
        if (prof.isPresent() && professorService.verificarSenha(prof.get(), senha)) {
            HttpSession nova = renovarSessao(req);
            String redirect = (String) nova.getAttribute("portalRedirect");
            nova.setAttribute("professorId",   prof.get().getId());
            nova.setAttribute("professorNome", prof.get().getNome());
            nova.removeAttribute("portalRedirect");
            if (redirect != null && redirect.startsWith("/prova/")) return "redirect:" + redirect;
            return "redirect:/prova/acompanhar";
        }

        ra.addFlashAttribute("erro", "E-mail ou senha inválidos.");
        return "redirect:/prova/fazer";
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        if (session != null) session.invalidate();
        return "redirect:/prova/fazer";
    }

    // ── Troca de senha ────────────────────────────────────────────────────────

    @GetMapping("/trocar-senha")
    public String trocarSenhaPage(HttpSession session, Model model) {
        if (alunoId(session) == null) return "redirect:/prova/fazer";
        model.addAttribute("pageTitle", "Criar nova senha");
        return "aplicacao/trocar-senha";
    }

    @PostMapping("/trocar-senha")
    public String trocarSenha(@RequestParam String novaSenha,
                               @RequestParam String confirmarSenha,
                               HttpSession session, RedirectAttributes ra) {
        Long id = alunoId(session);
        if (id == null) return "redirect:/prova/fazer";
        if (!novaSenha.equals(confirmarSenha)) { ra.addFlashAttribute("erro", "As senhas não conferem."); return "redirect:/prova/trocar-senha"; }
        if (novaSenha.length() < 4) { ra.addFlashAttribute("erro", "Mínimo 4 caracteres."); return "redirect:/prova/trocar-senha"; }
        alunoService.trocarSenha(id, novaSenha);
        ra.addFlashAttribute("sucesso", "Senha atualizada!");
        return "redirect:/prova/minha-lista";
    }

    // ── Lista de provas do aluno ──────────────────────────────────────────────

    @GetMapping("/minha-lista")
    public String minhaLista(HttpSession session, Model model) {
        Long id = alunoId(session);
        if (id == null) return irParaLogin(session, "/prova/minha-lista");
        aplicacaoService.verificarExpiracoes();
        model.addAttribute("aplicacoes", aplicacaoService.listarPorAluno(id));
        model.addAttribute("alunoNome", session.getAttribute("alunoNome"));
        model.addAttribute("pageTitle", "Minhas Provas");
        return "aplicacao/minha-lista";
    }

    // ── Realizar prova ────────────────────────────────────────────────────────

    @GetMapping("/realizar/{apId}")
    public String realizarProva(@PathVariable Long apId,
                                 @RequestParam(defaultValue = "0") int questao,
                                 HttpSession session, Model model) {
        Long id = alunoId(session);
        if (id == null) return irParaLogin(session, "/prova/realizar/" + apId + "?questao=" + questao);

        AplicacaoProva ap = aplicacaoService.buscarPorId(apId).orElse(null);
        // ISOLAMENTO: só o dono acessa
        if (ap == null || !ap.getAluno().getId().equals(id)) return "redirect:/prova/minha-lista";

        if (ap.getStatus() == AplicacaoProva.Status.AGENDADA) ap = aplicacaoService.iniciarProva(apId);
        if (ap.getStatus() == AplicacaoProva.Status.ENCERRADA || ap.getStatus() == AplicacaoProva.Status.EXPIRADA)
            return "redirect:/prova/resultado/" + apId;

        List<ProvaQuestao> questoes = ap.getProva().getProvaQuestoes();
        if (questoes.isEmpty()) return "redirect:/prova/minha-lista";

        int idx = Math.max(0, Math.min(questao, questoes.size() - 1));
        ProvaQuestao pq = questoes.get(idx);
        List<RespostaAluno> respostas = aplicacaoService.listarRespostas(apId);
        Long altMarcada = respostas.stream()
                .filter(r -> r.getQuestao().getId().equals(pq.getQuestao().getId()))
                .findFirst().map(r -> r.getAlternativa() != null ? r.getAlternativa().getId() : null)
                .orElse(null);

        model.addAttribute("ap", ap);
        model.addAttribute("pq", pq);
        model.addAttribute("questaoIdx", idx);
        model.addAttribute("totalQuestoes", questoes.size());
        model.addAttribute("altMarcada", altMarcada);
        model.addAttribute("respostas", respostas);
        model.addAttribute("pageTitle", "Realizando: " + ap.getProva().getTitulo());
        return "aplicacao/realizar";
    }

    @PostMapping("/realizar/{apId}/responder")
    public String responder(@PathVariable Long apId,
                             @RequestParam Long questaoId,
                             @RequestParam(required = false) Long alternativaId,
                             @RequestParam(defaultValue = "0") int proximaQuestao,
                             HttpSession session, RedirectAttributes ra) {
        Long id = alunoId(session);
        if (id == null) return irParaLogin(session, "/prova/minha-lista");

        AplicacaoProva ap = aplicacaoService.buscarPorId(apId).orElse(null);
        // ISOLAMENTO: só o dono responde
        if (ap == null || !ap.getAluno().getId().equals(id)) return "redirect:/prova/minha-lista";

        try { aplicacaoService.responder(apId, questaoId, alternativaId); }
        catch (Exception e) { ra.addFlashAttribute("erro", e.getMessage()); }
        return "redirect:/prova/realizar/" + apId + "?questao=" + proximaQuestao;
    }

    @PostMapping("/realizar/{apId}/encerrar")
    public String encerrar(@PathVariable Long apId, HttpSession session) {
        Long id = alunoId(session);
        if (id == null) return "redirect:/prova/fazer";
        AplicacaoProva ap = aplicacaoService.buscarPorId(apId).orElse(null);
        // ISOLAMENTO
        if (ap == null || !ap.getAluno().getId().equals(id)) return "redirect:/prova/minha-lista";
        aplicacaoService.encerrar(apId);
        return "redirect:/prova/resultado/" + apId;
    }

    // ── Resultado ─────────────────────────────────────────────────────────────

    @GetMapping("/resultado/{apId}")
    public String resultado(@PathVariable Long apId, HttpSession session, Model model) {
        Long aId  = alunoId(session);
        Long pId  = profPortalId(session);
        if (aId == null && pId == null) return irParaLogin(session, "/prova/resultado/" + apId);

        AplicacaoProva ap = aplicacaoService.buscarPorId(apId).orElse(null);
        if (ap == null) return "redirect:/prova/minha-lista";

        // ISOLAMENTO: aluno só vê o próprio resultado
        if (aId != null && !ap.getAluno().getId().equals(aId)) return "redirect:/prova/minha-lista";

        model.addAttribute("ap", ap);
        model.addAttribute("respostas", aplicacaoService.listarRespostas(apId));
        model.addAttribute("pageTitle", "Resultado: " + ap.getProva().getTitulo());
        model.addAttribute("isProfessor", pId != null);
        return "aplicacao/resultado";
    }

    // ── Painel do professor (portal) ──────────────────────────────────────────

    @GetMapping("/acompanhar")
    public String acompanhar(HttpSession session, Model model) {
        Long pId = profPortalId(session);
        if (pId == null) return irParaLogin(session, "/prova/acompanhar");
        aplicacaoService.verificarExpiracoes();
        model.addAttribute("provas", provaService.listarTodas());
        model.addAttribute("professorNome", session.getAttribute("professorNome"));
        model.addAttribute("pageTitle", "Painel do Professor");
        return "aplicacao/acompanhar";
    }

    @GetMapping("/acompanhar/{provaId}")
    public String acompanharProva(@PathVariable Long provaId, HttpSession session, Model model) {
        Long pId = profPortalId(session);
        if (pId == null) return irParaLogin(session, "/prova/acompanhar/" + provaId);
        aplicacaoService.verificarExpiracoes();
        Prova prova = provaService.buscarPorId(provaId).orElseThrow();
        model.addAttribute("prova", prova);
        model.addAttribute("aplicacoes", aplicacaoService.listarPorProva(provaId));
        model.addAttribute("pageTitle", "Acompanhar: " + prova.getTitulo());
        return "aplicacao/acompanhar-prova";
    }
}
