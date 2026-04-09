package com.questoes.service;

import com.questoes.entity.Questao;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LogCommand;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
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
}
