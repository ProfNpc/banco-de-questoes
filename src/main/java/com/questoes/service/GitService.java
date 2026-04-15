package com.questoes.service;

import com.questoes.entity.Questao;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LogCommand;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class GitService {

    @Value("${app.git.repo.dir:./git-questoes}")
    private String gitRepoDir;

    private Git git;

    @PostConstruct
    public void init() {
        try {
            File repoDir = new File(gitRepoDir);
            repoDir.mkdirs();
            File gitDir = new File(repoDir, ".git");
            if (!gitDir.exists()) {
                git = Git.init().setDirectory(repoDir).call();
                File readme = new File(repoDir, "README.md");
                Files.writeString(readme.toPath(), "# Repositório de Questões\n");
                git.add().addFilepattern("README.md").call();
                git.commit()
                   .setMessage("Inicialização do repositório de questões")
                   .setAuthor(new PersonIdent("Sistema", "sistema@questoes.local"))
                   .call();
            } else {
                Repository repo = new FileRepositoryBuilder().setGitDir(gitDir).build();
                git = new Git(repo);
            }
        } catch (Exception e) {
            log.error("Erro ao inicializar Git", e);
        }
    }

    public String commitQuestao(Questao questao, String mensagem) {
        try {
            String fileName = "questao-" + questao.getId() + ".md";
            Path filePath = Paths.get(gitRepoDir, fileName);
            Files.writeString(filePath, buildMarkdownContent(questao), StandardCharsets.UTF_8);
            git.add().addFilepattern(fileName).call();
            RevCommit commit = git.commit()
                    .setMessage(mensagem)
                    .setAuthor(new PersonIdent("Sistema", "sistema@questoes.local"))
                    .call();
            return commit.getName();
        } catch (Exception e) {
            log.error("Erro ao commitar questão {}", questao.getId(), e);
            return null;
        }
    }

    public List<VersaoQuestao> listarVersoes(Long questaoId) {
        List<VersaoQuestao> versoes = new ArrayList<>();
        String fileName = "questao-" + questaoId + ".md";
        try {
            LogCommand logCmd = git.log();
            logCmd.addPath(fileName);
            for (RevCommit commit : logCmd.call()) {
                VersaoQuestao v = new VersaoQuestao();
                v.setHash(commit.getName());
                v.setHashCurto(commit.getName().substring(0, 8));
                v.setMensagem(commit.getFullMessage().trim());
                v.setData(LocalDateTime.ofInstant(
                        Instant.ofEpochSecond(commit.getCommitTime()), ZoneId.systemDefault()));
                v.setAutor(commit.getAuthorIdent().getName());
                versoes.add(v);
            }
        } catch (Exception e) {
            log.error("Erro ao listar versões da questão {}", questaoId, e);
        }
        return versoes;
    }

    public String obterConteudoVersao(Long questaoId, String commitHash) {
        String fileName = "questao-" + questaoId + ".md";
        try {
            Repository repo = git.getRepository();
            ObjectId commitId = repo.resolve(commitHash);
            if (commitId == null) return null;
            RevCommit commit;
            try (RevWalk rw = new RevWalk(repo)) {
                commit = rw.parseCommit(commitId);
            }
            try (TreeWalk treeWalk = new TreeWalk(repo)) {
                treeWalk.addTree(commit.getTree());
                treeWalk.setRecursive(true);
                treeWalk.setFilter(PathFilter.create(fileName));
                if (treeWalk.next()) {
                    ObjectId blobId = treeWalk.getObjectId(0);
                    return new String(repo.open(blobId).getBytes(), StandardCharsets.UTF_8);
                }
            }
        } catch (Exception e) {
            log.error("Erro ao obter versão {} da questão {}", commitHash, questaoId, e);
        }
        return null;
    }

    /**
     * Gera um diff unificado entre duas versões (hashA = mais antiga, hashB = mais nova).
     * Retorna o diff como texto com marcações +/--.
     */
    public String gerarDiff(Long questaoId, String hashA, String hashB) {
        String fileName = "questao-" + questaoId + ".md";
        try {
            Repository repo = git.getRepository();
            AbstractTreeIterator treeA = prepararTree(repo, hashA);
            AbstractTreeIterator treeB = prepararTree(repo, hashB);
            if (treeA == null || treeB == null) return null;

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (DiffFormatter df = new DiffFormatter(out)) {
                df.setRepository(repo);
                df.setDiffComparator(RawTextComparator.DEFAULT);
                df.setPathFilter(PathFilter.create(fileName));
                df.setContext(3);

                List<DiffEntry> entries = df.scan(treeA, treeB);
                df.format(entries);
                df.flush();
            }
            return out.toString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Erro ao gerar diff da questão {}", questaoId, e);
            return null;
        }
    }

    private AbstractTreeIterator prepararTree(Repository repo, String commitHash) {
        try {
            ObjectId id = repo.resolve(commitHash + "^{tree}");
            if (id == null) return null;
            CanonicalTreeParser parser = new CanonicalTreeParser();
            try (ObjectReader reader = repo.newObjectReader()) {
                parser.reset(reader, id);
            }
            return parser;
        } catch (Exception e) {
            log.error("Erro ao preparar tree para {}", commitHash, e);
            return null;
        }
    }

    /**
     * Parseia o conteúdo Markdown de uma versão e extrai os campos estruturados
     * para pré-preencher o formulário de criação de nova questão.
     */
    public QuestaoVersaoData parsearVersaoParaForm(String conteudoMd) {
        if (conteudoMd == null || conteudoMd.isBlank()) return new QuestaoVersaoData();

        QuestaoVersaoData data = new QuestaoVersaoData();
        String[] linhas = conteudoMd.split("\n");

        // Título: primeira linha # Título
        for (String linha : linhas) {
            if (linha.startsWith("# ")) {
                data.setTitulo(linha.substring(2).trim());
                break;
            }
        }

        // Metadados: **Campo:** valor
        for (String linha : linhas) {
            String l = linha.trim();
            if (l.startsWith("**Tipo:**")) {
                String val = extrairValorMeta(l);
                if (val.contains("Objetiva")) data.setTipo("OBJETIVA");
                else if (val.contains("Discursiva")) data.setTipo("DISCURSIVA");
            } else if (l.startsWith("**Disciplina:**")) {
                data.setDisciplina(extrairValorMeta(l));
            } else if (l.startsWith("**Assunto:**")) {
                data.setAssunto(extrairValorMeta(l));
            } else if (l.startsWith("**Dificuldade:**")) {
                String val = extrairValorMeta(l);
                if (val.contains("Fácil"))   data.setDificuldade("FACIL");
                else if (val.contains("Médio")) data.setDificuldade("MEDIO");
                else if (val.contains("Difícil")) data.setDificuldade("DIFICIL");
            }
        }

        // Enunciado: entre "## Enunciado" e a próxima seção "## "
        data.setEnunciado(extrairSecao(conteudoMd, "## Enunciado", "## "));

        // Gabarito
        data.setGabarito(extrairSecao(conteudoMd, "## Gabarito", "## "));

        // Alternativas: linhas "A) texto" ou "A) texto ✓"
        if ("OBJETIVA".equals(data.getTipo())) {
            String secaoAlt = extrairSecao(conteudoMd, "## Alternativas", "## ");
            if (secaoAlt != null) {
                for (String linha : secaoAlt.split("\n")) {
                    String l = linha.trim();
                    // Padrão: "A) texto" ou "A) texto ✓"
                    if (l.length() >= 3 && l.charAt(1) == ')') {
                        boolean correta = l.endsWith("✓");
                        String texto = l.substring(2).trim();
                        if (correta) texto = texto.substring(0, texto.length() - 1).trim();
                        data.getAlternativas().add(texto);
                        if (correta) data.getAlternativasCorretas().add(data.getAlternativas().size() - 1);
                    }
                }
            }
        }

        return data;
    }

    private String extrairValorMeta(String linha) {
        int idx = linha.indexOf(":**");
        if (idx < 0) return "";
        String resto = linha.substring(idx + 3).trim();
        // Remove marcadores residuais de negrito
        return resto.replace("**", "").trim();
    }

    private String extrairSecao(String conteudo, String marcadorInicio, String prefixoFim) {
        int inicio = conteudo.indexOf(marcadorInicio);
        if (inicio < 0) return null;
        inicio += marcadorInicio.length();
        // Pular o próprio "## Enunciado" e achar o próximo bloco
        int proxSecao = conteudo.indexOf("\n" + prefixoFim, inicio);
        String secao = proxSecao > 0
                ? conteudo.substring(inicio, proxSecao)
                : conteudo.substring(inicio);
        return secao.strip();
    }

    private String buildMarkdownContent(Questao questao) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(questao.getTitulo()).append("\n\n");
        sb.append("**ID:** ").append(questao.getId()).append("  \n");
        sb.append("**Tipo:** ").append(questao.getTipo().getDescricao()).append("  \n");
        if (questao.getDisciplina() != null) sb.append("**Disciplina:** ").append(questao.getDisciplina()).append("  \n");
        if (questao.getAssunto() != null) sb.append("**Assunto:** ").append(questao.getAssunto()).append("  \n");
        if (questao.getDificuldade() != null) sb.append("**Dificuldade:** ").append(questao.getDificuldade().getDescricao()).append("  \n");
        sb.append("**Versão:** ").append(questao.getVersao()).append("  \n");
        sb.append("---\n\n## Enunciado\n\n").append(questao.getEnunciado()).append("\n\n");
        if (questao.getTipo() == Questao.TipoQuestao.OBJETIVA && !questao.getAlternativas().isEmpty()) {
            sb.append("## Alternativas\n\n");
            for (var alt : questao.getAlternativas()) {
                String correta = Boolean.TRUE.equals(alt.getCorreta()) ? " ✓" : "";
                sb.append(alt.getLetra()).append(") ").append(alt.getTexto()).append(correta).append("\n\n");
            }
        }
        if (questao.getGabarito() != null && !questao.getGabarito().isBlank()) {
            sb.append("## Gabarito\n\n").append(questao.getGabarito()).append("\n");
        }
        return sb.toString();
    }

    // ── DTOs internos ──────────────────────────────────────────────────────

    public static class VersaoQuestao {
        private String hash, hashCurto, mensagem, autor;
        private LocalDateTime data;
        public String getHash() { return hash; }
        public void setHash(String h) { this.hash = h; }
        public String getHashCurto() { return hashCurto; }
        public void setHashCurto(String h) { this.hashCurto = h; }
        public String getMensagem() { return mensagem; }
        public void setMensagem(String m) { this.mensagem = m; }
        public LocalDateTime getData() { return data; }
        public void setData(LocalDateTime d) { this.data = d; }
        public String getAutor() { return autor; }
        public void setAutor(String a) { this.autor = a; }
    }

    public static class QuestaoVersaoData {
        private String titulo, tipo, disciplina, assunto, dificuldade, enunciado, gabarito;
        private List<String> alternativas = new ArrayList<>();
        private List<Integer> alternativasCorretas = new ArrayList<>();

        public String getTitulo() { return titulo; }
        public void setTitulo(String t) { this.titulo = t; }
        public String getTipo() { return tipo; }
        public void setTipo(String t) { this.tipo = t; }
        public String getDisciplina() { return disciplina; }
        public void setDisciplina(String d) { this.disciplina = d; }
        public String getAssunto() { return assunto; }
        public void setAssunto(String a) { this.assunto = a; }
        public String getDificuldade() { return dificuldade; }
        public void setDificuldade(String d) { this.dificuldade = d; }
        public String getEnunciado() { return enunciado; }
        public void setEnunciado(String e) { this.enunciado = e; }
        public String getGabarito() { return gabarito; }
        public void setGabarito(String g) { this.gabarito = g; }
        public List<String> getAlternativas() { return alternativas; }
        public void setAlternativas(List<String> a) { this.alternativas = a; }
        public List<Integer> getAlternativasCorretas() { return alternativasCorretas; }
        public void setAlternativasCorretas(List<Integer> a) { this.alternativasCorretas = a; }
    }
}
