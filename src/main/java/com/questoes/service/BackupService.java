package com.questoes.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.zip.*;

/**
 * Gera e restaura um backup completo:
 *   - Script SQL do banco H2 (todas as tabelas)
 *   - Diretório de uploads (imagens)
 *   - Repositório Git completo (.git + arquivos .md)
 *
 * Formato do arquivo: questoes-backup-YYYYMMDD-HHmmss.zip
 * Estrutura interna:
 *   backup/
 *     database/
 *       schema.sql       -- CREATE TABLE + INSERT de todos os dados
 *     uploads/           -- cópia dos arquivos de upload
 *     git-questoes/      -- clone completo do repositório git
 */
@Service
@Slf4j
public class BackupService {

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @Value("${spring.datasource.username:sa}")
    private String datasourceUser;

    @Value("${spring.datasource.password:}")
    private String datasourcePassword;

    @Value("${app.upload.dir:./uploads}")
    private String uploadDir;

    @Value("${app.git.repo.dir:./git-questoes}")
    private String gitRepoDir;

    // ── Geração ──────────────────────────────────────────────────────────────

    public byte[] gerarBackup() throws Exception {
        log.info("Iniciando geração de backup...");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.setLevel(Deflater.BEST_COMPRESSION);

            // 1) Banco de dados
            adicionarBancoDeDados(zos);

            // 2) Uploads (imagens)
            adicionarDiretorio(zos, Paths.get(uploadDir).toAbsolutePath(), "backup/uploads/");

            // 3) Repositório Git completo
            adicionarDiretorio(zos, Paths.get(gitRepoDir).toAbsolutePath(), "backup/git-questoes/");
        }

        log.info("Backup gerado: {} bytes", baos.size());
        return baos.toByteArray();
    }

    private void adicionarBancoDeDados(ZipOutputStream zos) throws Exception {
        log.info("Exportando banco de dados H2...");

        // Extrair o path do arquivo H2 a partir da URL JDBC
        // jdbc:h2:file:./data/questoesdb;AUTO_SERVER=TRUE  →  ./data/questoesdb
        String h2FilePath = extrairH2FilePath(datasourceUrl);

        try (Connection conn = DriverManager.getConnection(datasourceUrl, datasourceUser, datasourcePassword)) {
            StringBuilder sql = new StringBuilder();
            sql.append("-- QuestionBase Backup\n");
            sql.append("-- Gerado em: ").append(LocalDateTime.now()).append("\n\n");
            sql.append("SET REFERENTIAL_INTEGRITY FALSE;\n\n");

            DatabaseMetaData meta = conn.getMetaData();
            // Exportar tabelas na ordem correta para respeitar FK
            String[] tabelas = {"QUESTAO", "ALTERNATIVA", "PROVA", "PROVA_QUESTAO"};

            for (String tabela : tabelas) {
                exportarTabela(conn, meta, tabela, sql);
            }

            sql.append("\nSET REFERENTIAL_INTEGRITY TRUE;\n");

            zos.putNextEntry(new ZipEntry("backup/database/schema.sql"));
            zos.write(sql.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zos.closeEntry();
            log.info("Banco exportado para backup/database/schema.sql");
        }
    }

    private void exportarTabela(Connection conn, DatabaseMetaData meta,
                                 String tabela, StringBuilder sql) throws SQLException {
        // DDL da tabela (H2 suporta SCRIPT para extrair DDL)
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COLUMN_NAME, DATA_TYPE, CHARACTER_MAXIMUM_LENGTH, IS_NULLABLE " +
                "FROM INFORMATION_SCHEMA.COLUMNS WHERE UPPER(TABLE_NAME) = ? ORDER BY ORDINAL_POSITION")) {
            ps.setString(1, tabela);
            ResultSet rs = ps.executeQuery();
            if (!rs.isBeforeFirst()) return; // tabela não existe
        }

        // Dados
        sql.append("-- Tabela: ").append(tabela).append("\n");
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM " + tabela)) {

            ResultSetMetaData rsMeta = rs.getMetaData();
            int cols = rsMeta.getColumnCount();

            // Construir lista de colunas
            StringBuilder colNames = new StringBuilder();
            for (int i = 1; i <= cols; i++) {
                if (i > 1) colNames.append(", ");
                colNames.append(rsMeta.getColumnName(i));
            }

            int rowCount = 0;
            while (rs.next()) {
                sql.append("INSERT INTO ").append(tabela)
                   .append(" (").append(colNames).append(") VALUES (");
                for (int i = 1; i <= cols; i++) {
                    if (i > 1) sql.append(", ");
                    Object val = rs.getObject(i);
                    if (val == null) {
                        sql.append("NULL");
                    } else if (val instanceof String) {
                        sql.append("'").append(((String) val)
                                .replace("'", "''")
                                .replace("\\", "\\\\")).append("'");
                    } else if (val instanceof java.sql.Timestamp) {
                        sql.append("TIMESTAMP '").append(val).append("'");
                    } else if (val instanceof Boolean) {
                        sql.append((Boolean) val ? "TRUE" : "FALSE");
                    } else {
                        sql.append(val);
                    }
                }
                sql.append(");\n");
                rowCount++;
            }
            sql.append("-- ").append(rowCount).append(" registro(s) em ").append(tabela).append("\n\n");
            log.info("Tabela {}: {} registros exportados", tabela, rowCount);
        } catch (Exception e) {
            log.warn("Erro ao exportar tabela {}: {}", tabela, e.getMessage());
            sql.append("-- ERRO ao exportar tabela ").append(tabela).append(": ")
               .append(e.getMessage()).append("\n\n");
        }
    }

    private void adicionarDiretorio(ZipOutputStream zos, Path baseDir, String prefixoZip) throws IOException {
        if (!Files.exists(baseDir)) {
            log.warn("Diretório não encontrado, pulando: {}", baseDir);
            return;
        }

        Files.walkFileTree(baseDir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String relativePath = baseDir.relativize(file).toString().replace("\\", "/");
                String entryName = prefixoZip + relativePath;
                try {
                    zos.putNextEntry(new ZipEntry(entryName));
                    Files.copy(file, zos);
                    zos.closeEntry();
                } catch (Exception e) {
                    log.warn("Erro ao adicionar arquivo ao backup: {}", file, e);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                String relativePath = baseDir.relativize(dir).toString().replace("\\", "/");
                if (!relativePath.isEmpty()) {
                    zos.putNextEntry(new ZipEntry(prefixoZip + relativePath + "/"));
                    zos.closeEntry();
                }
                return FileVisitResult.CONTINUE;
            }
        });
        log.info("Diretório adicionado ao backup: {} → {}", baseDir, prefixoZip);
    }

    // ── Restauração ──────────────────────────────────────────────────────────

    public void restaurarBackup(MultipartFile arquivo) throws Exception {
        log.info("Iniciando restauração de backup: {}", arquivo.getOriginalFilename());

        try (ZipInputStream zis = new ZipInputStream(arquivo.getInputStream())) {
            ZipEntry entry;
            String sqlBackup = null;

            // Primeira passagem: extrair o SQL
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().equals("backup/database/schema.sql")) {
                    sqlBackup = new String(zis.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    log.info("SQL de backup lido: {} chars", sqlBackup.length());
                    break;
                }
                zis.closeEntry();
            }

            if (sqlBackup == null) {
                throw new IllegalArgumentException("Arquivo de backup inválido: não contém backup/database/schema.sql");
            }
        }

        // Segunda passagem com novo stream: restaurar tudo
        try (ZipInputStream zis = new ZipInputStream(arquivo.getInputStream())) {
            ZipEntry entry;
            boolean sqlRestaurado = false;

            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();

                if (name.equals("backup/database/schema.sql")) {
                    // Restaurar banco
                    String sql = new String(zis.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    restaurarBanco(sql);
                    sqlRestaurado = true;

                } else if (name.startsWith("backup/uploads/") && !entry.isDirectory()) {
                    // Restaurar arquivo de upload
                    String relativePath = name.substring("backup/uploads/".length());
                    Path destino = Paths.get(uploadDir).resolve(relativePath).toAbsolutePath();
                    Files.createDirectories(destino.getParent());
                    Files.write(destino, zis.readAllBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

                } else if (name.startsWith("backup/git-questoes/") && !entry.isDirectory()) {
                    // Restaurar repositório Git
                    String relativePath = name.substring("backup/git-questoes/".length());
                    Path destino = Paths.get(gitRepoDir).resolve(relativePath).toAbsolutePath();
                    Files.createDirectories(destino.getParent());
                    Files.write(destino, zis.readAllBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                }

                zis.closeEntry();
            }

            if (!sqlRestaurado) {
                throw new IllegalStateException("SQL não foi restaurado");
            }
        }

        log.info("Backup restaurado com sucesso!");
    }

    private void restaurarBanco(String sql) throws Exception {
        log.info("Restaurando banco de dados...");
        try (Connection conn = DriverManager.getConnection(datasourceUrl, datasourceUser, datasourcePassword)) {
            conn.setAutoCommit(false);
            try {
                // Limpar dados existentes (na ordem inversa das FKs)
                try (Statement st = conn.createStatement()) {
                    st.execute("SET REFERENTIAL_INTEGRITY FALSE");
                    for (String tabela : new String[]{"PROVA_QUESTAO", "ALTERNATIVA", "QUESTAO", "PROVA"}) {
                        try {
                            st.execute("DELETE FROM " + tabela);
                            log.info("Tabela {} limpa", tabela);
                        } catch (Exception e) {
                            log.warn("Tabela {} não existe ainda: {}", tabela, e.getMessage());
                        }
                    }
                }

                // Executar cada linha SQL
                String[] statements = sql.split(";\n");
                try (Statement st = conn.createStatement()) {
                    for (String stmt : statements) {
                        String s = stmt.trim();
                        if (s.isEmpty() || s.startsWith("--")) continue;
                        try {
                            st.execute(s);
                        } catch (Exception e) {
                            log.warn("Erro ao executar: {} — {}", s.substring(0, Math.min(80, s.length())), e.getMessage());
                        }
                    }
                }

                conn.commit();
                log.info("Banco restaurado com sucesso");
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                try (Statement st = conn.createStatement()) {
                    st.execute("SET REFERENTIAL_INTEGRITY TRUE");
                }
            }
        }
    }

    private String extrairH2FilePath(String url) {
        // jdbc:h2:file:./data/questoesdb;... → ./data/questoesdb
        int start = url.indexOf("file:");
        if (start < 0) return null;
        String path = url.substring(start + 5);
        int end = path.indexOf(';');
        return end > 0 ? path.substring(0, end) : path;
    }

    public String gerarNomeArquivoBackup() {
        return "questoes-backup-" +
               LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) +
               ".zip";
    }
}
