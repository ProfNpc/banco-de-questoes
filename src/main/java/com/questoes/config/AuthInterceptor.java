package com.questoes.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Interceptor central de autenticação/autorização.
 *
 * Existem DOIS contextos de sessão completamente separados:
 *
 *  1. PAINEL ADMINISTRATIVO (/admin/**, /, /questoes/**, /provas/**, /alunos/**, /turmas/**, /exportar-pdf, /backup/**)
 *     → Requer atributo de sessão "professorAdminId" (Long)
 *     → Login em: GET/POST /admin/login
 *     → Logout em: GET /admin/logout
 *
 *  2. PORTAL DE PROVAS (/prova/**)
 *     → Gerenciado pelo AplicacaoController com seus próprios atributos:
 *        "alunoId" para alunos e "professorId" para professor no painel de acompanhamento
 *     → Login em: GET/POST /prova/fazer
 *     → Interceptor NÃO interfere neste contexto
 *
 * Segurança adicional:
 *  - Aluno com "alunoId" na sessão NÃO consegue acessar o painel admin
 *    (não possui "professorAdminId").
 *  - Professor com "professorId" (portal provas) NÃO consegue acessar o painel admin
 *    (são sessões diferentes — professorId vs professorAdminId).
 *  - Um aluno não consegue acessar a AplicacaoProva de outro aluno porque
 *    o AplicacaoController verifica ap.getAluno().getId().equals(alunoId).
 */
public class AuthInterceptor implements HandlerInterceptor {

    /** URIs do painel admin que NÃO precisam de autenticação (a própria tela de login) */
    private static final String[] URLS_PUBLICAS_ADMIN = {
        "/admin/login",
        "/admin/login/"
    };

    /** Prefixos que pertencem ao painel admin e precisam de "professorAdminId" */
    private static final String[] PREFIXOS_ADMIN = {
        "/",
        "/questoes",
        "/provas",
        "/alunos",
        "/turmas",
        "/exportar-pdf",
        "/backup",
        "/admin",
        "/h2-console"
    };

    /** Recursos estáticos e portal de provas: não interceptar */
    private static final String[] PREFIXOS_LIVRES = {
        "/css/",
        "/js/",
        "/images/",
        "/uploads/",
        "/static/",
        "/prova/",       // gerenciado pelo AplicacaoController
        "/favicon"
    };

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse resp,
                             Object handler) throws Exception {

        String uri = req.getRequestURI();

        // 1. Recursos estáticos e portal de provas → liberar sempre
        for (String livre : PREFIXOS_LIVRES) {
            if (uri.startsWith(req.getContextPath() + livre) ||
                uri.equals(req.getContextPath() + livre.stripTrailing())) {
                return true;
            }
        }

        // 2. Tela de login do admin → liberar sempre
        String uriSemCtx = uri.replaceFirst("^" + req.getContextPath(), "");
        for (String publica : URLS_PUBLICAS_ADMIN) {
            if (uriSemCtx.equals(publica) || uriSemCtx.startsWith(publica + "?")) {
                return true;
            }
        }

        // 3. Para todas as demais URIs do painel admin: exigir professorAdminId
        HttpSession session = req.getSession(false);
        boolean autenticado = session != null
                && session.getAttribute("professorAdminId") != null;

        if (!autenticado) {
            // Guardar a URL que o usuário queria acessar para redirect pós-login
            String queryString = req.getQueryString();
            String urlDestino = uriSemCtx + (queryString != null ? "?" + queryString : "");
            // Não redirecionar para si mesmo
            if (!urlDestino.contains("/admin/login")) {
                req.getSession(true).setAttribute("loginRedirect", urlDestino);
            }
            resp.sendRedirect(req.getContextPath() + "/admin/login");
            return false;
        }

        return true;
    }
}
