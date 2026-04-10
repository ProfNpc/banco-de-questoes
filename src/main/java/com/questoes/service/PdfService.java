package com.questoes.service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.BulletList;
import org.commonmark.node.Code;
import org.commonmark.node.Emphasis;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Link;
import org.commonmark.node.OrderedList;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.node.Text;
import org.commonmark.node.ThematicBreak;
import org.commonmark.parser.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.ListItem;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.draw.LineSeparator;
import com.questoes.entity.Alternativa;
import com.questoes.entity.Prova;
import com.questoes.entity.ProvaQuestao;
import com.questoes.entity.Questao;

@Service
public class PdfService {

    private static final Logger log = LoggerFactory.getLogger(PdfService.class);

    @Value("${app.upload.dir:./uploads}")
    private String uploadDir;

    // ── Fontes reutilizadas ──────────────────────────────────────────────────
    private static final Font F_TITLE       = FontFactory.getFont(FontFactory.HELVETICA_BOLD,  16, Color.decode("#1a1a2e"));
    private static final Font F_SUBTITLE    = FontFactory.getFont(FontFactory.HELVETICA,        11, Color.decode("#444444"));
    private static final Font F_QNUM        = FontFactory.getFont(FontFactory.HELVETICA_BOLD,  11, Color.decode("#0f3460"));
    private static final Font F_BODY        = FontFactory.getFont(FontFactory.HELVETICA,        11, Color.BLACK);
    private static final Font F_BOLD        = FontFactory.getFont(FontFactory.HELVETICA_BOLD,  11, Color.BLACK);
    private static final Font F_ITALIC      = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE,11, Color.BLACK);
    private static final Font F_BOLDITALIC  = FontFactory.getFont(FontFactory.HELVETICA_BOLDOBLIQUE, 11, Color.BLACK);
    private static final Font F_CODE        = FontFactory.getFont(FontFactory.COURIER,          10, Color.decode("#333333"));
    private static final Font F_ALT         = FontFactory.getFont(FontFactory.HELVETICA,        10, Color.BLACK);
    private static final Font F_ALT_CORRETA = FontFactory.getFont(FontFactory.HELVETICA_BOLD,  10, Color.decode("#166534"));
    private static final Font F_GABARITO    = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE,10, Color.decode("#555555"));

    private final Parser mdParser;

    public PdfService() {
        mdParser = Parser.builder()
                .extensions(List.of(TablesExtension.create()))
                .build();
    }

    // ════════════════════════════════════════════════════════════════════════
    //  API pública
    // ════════════════════════════════════════════════════════════════════════

    public byte[] gerarPdfProva(Prova prova, boolean incluirGabarito) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 60, 60, 80, 80);
        PdfWriter writer = PdfWriter.getInstance(doc, baos);
        writer.setPageEvent(new HeaderFooterEvent(prova.getTitulo(), prova.getDisciplina()));
        doc.open();

        // Cabeçalho da prova
        addTitulo(doc, prova.getTitulo(), prova.getDisciplina(), prova.getDescricao());

        // Questões
        List<ProvaQuestao> questoes = prova.getProvaQuestoes();
        for (int i = 0; i < questoes.size(); i++) {
            ProvaQuestao pq = questoes.get(i);
            Questao q = pq.getQuestao();
            renderizarQuestao(doc, q, i + 1, pq.getPontos(), incluirGabarito);
        }

        // Tabela de gabarito ao final
        if (incluirGabarito) {
            doc.newPage();
            adicionarTabelaGabarito(doc, questoes);
        }

        doc.close();
        return baos.toByteArray();
    }

    public byte[] gerarPdfQuestoesSelecionadas(List<Questao> questoes, String titulo) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 60, 60, 80, 80);
        PdfWriter writer = PdfWriter.getInstance(doc, baos);
        writer.setPageEvent(new HeaderFooterEvent(titulo, null));
        doc.open();

        Paragraph tituloPar = new Paragraph(titulo, F_TITLE);
        tituloPar.setAlignment(Element.ALIGN_CENTER);
        tituloPar.setSpacingAfter(10);
        doc.add(tituloPar);
        doc.add(new Chunk(new LineSeparator(1, 100, Color.decode("#cccccc"), Element.ALIGN_CENTER, -5)));
        doc.add(Chunk.NEWLINE);

        for (int i = 0; i < questoes.size(); i++) {
            renderizarQuestao(doc, questoes.get(i), i + 1, null, false);
        }

        doc.close();
        return baos.toByteArray();
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Renderização de questão
    // ════════════════════════════════════════════════════════════════════════

    private void renderizarQuestao(Document doc, Questao q, int numero,
                                    Double pontos, boolean incluirGabarito) throws DocumentException, IOException {

        // Número da questão
        String pontoStr = (pontos != null)
                ? String.format(" (%.1f pt%s)", pontos, pontos == 1.0 ? "" : "s") : "";
        Paragraph numPar = new Paragraph("Questão " + numero + pontoStr, F_QNUM);
        numPar.setSpacingBefore(16);
        numPar.setSpacingAfter(4);
        doc.add(numPar);

        // Enunciado (Markdown → PDF)
        renderizarMarkdown(doc, q.getEnunciado(), false);

        // Alternativas (objetiva)
        if (q.getTipo() == Questao.TipoQuestao.OBJETIVA && !q.getAlternativas().isEmpty()) {
            doc.add(new Paragraph(" ", new Font(Font.HELVETICA, 4)));
            for (Alternativa alt : q.getAlternativas()) {
                boolean correta = Boolean.TRUE.equals(alt.getCorreta());
                renderizarAlternativa(doc, alt, incluirGabarito && correta);
            }
        }

        // Gabarito discursiva
        if (q.getTipo() == Questao.TipoQuestao.DISCURSIVA) {
            if (incluirGabarito && q.getGabarito() != null && !q.getGabarito().isBlank()) {
                Paragraph gabLabel = new Paragraph("Gabarito:", F_GABARITO);
                gabLabel.setSpacingBefore(6);
                doc.add(gabLabel);
                renderizarMarkdown(doc, q.getGabarito(), true);
            } else if (!incluirGabarito) {
                adicionarLinhasResposta(doc, 5);
            }
        }
    }

    private void renderizarAlternativa(Document doc, Alternativa alt,
                                        boolean destacarCorreta) throws DocumentException, IOException {
        // A letra fica como prefixo; o texto da alternativa também suporta Markdown
        Font fLetra = destacarCorreta ? F_ALT_CORRETA : F_ALT;

        // Usa um parágrafo com chunk da letra + conteúdo inline do Markdown da alternativa
        String prefixo = alt.getLetra() + ")  ";
        Phrase prefixoPhrase = new Phrase(prefixo, fLetra);

        // Parsear o Markdown da alternativa para pegar o texto inline
        org.commonmark.node.Node raiz = mdParser.parse(alt.getTexto() != null ? alt.getTexto() : "");
        Paragraph par = new Paragraph();
        par.setIndentationLeft(20);
        par.setSpacingAfter(3);
        par.setLeading(14);
        par.add(prefixoPhrase);

        // Adicionar conteúdo inline ao mesmo parágrafo
        adicionarInlineAoParagrafo(par, raiz, destacarCorreta ? F_ALT_CORRETA : F_ALT);

        if (destacarCorreta) {
            par.add(new Chunk("  ✓", F_ALT_CORRETA));
        }
        doc.add(par);

        // Se a alternativa tiver imagens, inserir abaixo
        adicionarImagensDoMarkdown(doc, alt.getTexto(), 250, 20);
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Motor de renderização Markdown → PDF (nós da AST CommonMark)
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Percorre a AST do Markdown e adiciona elementos ao Document.
     *
     * @param indentado  true quando dentro de um bloco de gabarito (identação extra)
     */
    private void renderizarMarkdown(Document doc, String markdown,
                                     boolean indentado) throws DocumentException, IOException {
        if (markdown == null || markdown.isBlank()) return;

        org.commonmark.node.Node raiz = mdParser.parse(markdown);
        org.commonmark.node.Node node = raiz.getFirstChild();

        while (node != null) {
            renderizarBloco(doc, node, indentado);
            node = node.getNext();
        }
    }

    private void renderizarBloco(Document doc, org.commonmark.node.Node node,
                                  boolean indentado) throws DocumentException, IOException {

        float indent = indentado ? 16f : 0f;

        if (node instanceof org.commonmark.node.Paragraph) {
            Paragraph par = new Paragraph();
            par.setFont(F_BODY);
            par.setLeading(16);
            par.setSpacingAfter(6);
            par.setIndentationLeft(indent);

            // Verificar se o parágrafo contém APENAS uma imagem
            if (isApenasSoftBreakOuImagem(node)) {
                // Renderizar imagem centralizada
                adicionarImagensDoMarkdown(doc, extrairTextoMarkdownDoNo(node), 400, indent);
            } else {
                adicionarInlineAoParagrafo(par, node, F_BODY);
                doc.add(par);
                // Imagens inline dentro do parágrafo ficam em linha separada abaixo
                adicionarImagensDoMarkdown(doc, extrairTextoMarkdownDoNo(node), 360, indent);
            }

        } else if (node instanceof Heading heading) {
            int level = heading.getLevel();
            float size = level == 1 ? 14 : level == 2 ? 12 : 11;
            Font fH = FontFactory.getFont(FontFactory.HELVETICA_BOLD, size, Color.decode("#0f3460"));
            Paragraph par = new Paragraph();
            par.setFont(fH);
            par.setSpacingBefore(10);
            par.setSpacingAfter(4);
            par.setIndentationLeft(indent);
            adicionarInlineAoParagrafo(par, node, fH);
            doc.add(par);

        } else if (node instanceof BulletList || node instanceof OrderedList) {
            renderizarLista(doc, node, indent, 0);

        } else if (node instanceof BlockQuote) {
            renderizarBlockquote(doc, node);

        } else if (node instanceof FencedCodeBlock code) {
            renderizarBlocoCode(doc, code.getLiteral(), indent);

        } else if (node instanceof IndentedCodeBlock code) {
            renderizarBlocoCode(doc, code.getLiteral(), indent);

        } else if (node instanceof ThematicBreak) {
            doc.add(new Chunk(new LineSeparator(0.5f, 100, Color.decode("#aaaaaa"),
                    Element.ALIGN_CENTER, -3)));
            doc.add(Chunk.NEWLINE);

        } else if (node instanceof HtmlBlock) {
            // Ignorar HTML blocks
        }
        // Outros nós (LinkReferenceDefinition etc.) são ignorados silenciosamente
    }

    private void adicionarInlineAoParagrafo(Paragraph par, org.commonmark.node.Node container,
                                             Font fontePadrao) {
        org.commonmark.node.Node child = container.getFirstChild();
        while (child != null) {
            adicionarInlineChunk(par, child, fontePadrao, false, false);
            child = child.getNext();
        }
    }

    private void adicionarInlineChunk(Paragraph par, org.commonmark.node.Node node,
                                       Font fonte, boolean bold, boolean italic) {
        if (node instanceof Text text) {
            par.add(new Chunk(text.getLiteral(), resolverFonte(fonte, bold, italic)));

        } else if (node instanceof StrongEmphasis) {
            org.commonmark.node.Node child = node.getFirstChild();
            while (child != null) {
                adicionarInlineChunk(par, child, fonte, true, italic);
                child = child.getNext();
            }

        } else if (node instanceof Emphasis) {
            org.commonmark.node.Node child = node.getFirstChild();
            while (child != null) {
                adicionarInlineChunk(par, child, fonte, bold, true);
                child = child.getNext();
            }

        } else if (node instanceof Code code) {
            par.add(new Chunk(code.getLiteral(), F_CODE));

        } else if (node instanceof Link link) {
            // Exibir o texto do link (sem a URL)
            org.commonmark.node.Node child = link.getFirstChild();
            while (child != null) {
                adicionarInlineChunk(par, child, fonte, bold, italic);
                child = child.getNext();
            }

        } else if (node instanceof SoftLineBreak || node instanceof HardLineBreak) {
            par.add(Chunk.NEWLINE);

        } else if (node instanceof org.commonmark.node.Image) {
            // Imagens não cabem inline num Chunk; serão tratadas por adicionarImagensDoMarkdown
            par.add(new Chunk(" [imagem] ", F_ITALIC));

        } else if (node instanceof HtmlInline) {
            // Ignorar HTML inline

        } else {
            // Processar filhos recursivamente para nós desconhecidos
            org.commonmark.node.Node child = node.getFirstChild();
            while (child != null) {
                adicionarInlineChunk(par, child, fonte, bold, italic);
                child = child.getNext();
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Imagens: buscar no filesystem e inserir no PDF
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Varre o Markdown procurando tags ![alt](url) e insere cada imagem
     * encontrada como elemento no Document, carregando do disco local.
     *
     * @param maxWidth     largura máxima em pontos
     * @param leftIndent   indentação esquerda em pontos
     */
    private void adicionarImagensDoMarkdown(Document doc, String markdown,
                                             float maxWidth, float leftIndent)
            throws DocumentException, IOException {
        if (markdown == null || markdown.isBlank()) return;

        org.commonmark.node.Node raiz = mdParser.parse(markdown);
        adicionarImagensDoNo(doc, raiz, maxWidth, leftIndent);
    }

    private void adicionarImagensDoNo(Document doc, org.commonmark.node.Node node,
                                       float maxWidth, float leftIndent)
            throws DocumentException, IOException {
        if (node == null) return;

        if (node instanceof org.commonmark.node.Image imgNode) {
            String destination = imgNode.getDestination();
            Image img = carregarImagem(destination);
            if (img != null) {
                // Redimensionar proporcionalmente se maior que maxWidth
                if (img.getWidth() > maxWidth) {
                    img.scaleToFit(maxWidth, 600);
                }
                img.setIndentationLeft(leftIndent);
                img.setSpacingBefore(6);
                img.setSpacingAfter(6);
                doc.add(img);
            }
        }

        // Percorrer filhos e irmãos
        adicionarImagensDoNo(doc, node.getFirstChild(), maxWidth, leftIndent);
        adicionarImagensDoNo(doc, node.getNext(), maxWidth, leftIndent);
    }

    /**
     * Tenta carregar a imagem pelo path:
     *  - Se começa com /uploads/ → busca em uploadDir no disco
     *  - Se começa com http(s):// → tenta carregar via URL (requer rede)
     *  - Caso contrário → trata como path relativo ao uploadDir
     */
    private Image carregarImagem(String destination) {
        if (destination == null || destination.isBlank()) return null;
        try {
            if (destination.startsWith("/uploads/")) {
                // Caminho local: mapear para o diretório de uploads
                String fileName = destination.substring("/uploads/".length());
                Path filePath = Paths.get(uploadDir).resolve(fileName).toAbsolutePath();
                if (Files.exists(filePath)) {
                    byte[] bytes = Files.readAllBytes(filePath);
                    return Image.getInstance(bytes);
                } else {
                    log.warn("Imagem não encontrada no disco: {}", filePath);
                    return null;
                }
            } else if (destination.startsWith("http://") || destination.startsWith("https://")) {
                // URL externa — carrega diretamente (requer conectividade)
                return Image.getInstance(destination);
            } else {
                // Path relativo — tentar relativo ao uploadDir
                Path filePath = Paths.get(uploadDir).resolve(destination).toAbsolutePath();
                if (Files.exists(filePath)) {
                    return Image.getInstance(filePath.toUri().toURL());
                }
                return null;
            }
        } catch (Exception e) {
            log.warn("Não foi possível carregar imagem '{}': {}", destination, e.getMessage());
            return null;
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Listas, blockquote, code block
    // ════════════════════════════════════════════════════════════════════════

    private void renderizarLista(Document doc, org.commonmark.node.Node lista,
                                  float indent, int nivel) throws DocumentException, IOException {
        boolean ordered = lista instanceof OrderedList;
        int contador = ordered ? ((OrderedList) lista).getStartNumber() : 0;

        org.commonmark.node.Node item = lista.getFirstChild();
        while (item instanceof org.commonmark.node.ListItem) {
            String bullet = ordered ? (contador++) + "." : "•";

            // Pegar o conteúdo do item
            org.commonmark.node.Node conteudo = item.getFirstChild();
            while (conteudo != null) {
                if (conteudo instanceof org.commonmark.node.Paragraph) {
                    Paragraph par = new Paragraph();
                    par.setFont(F_BODY);
                    par.setLeading(15);
                    par.setSpacingAfter(2);
                    par.setIndentationLeft(indent + 12 + nivel * 12f);
                    par.setFirstLineIndent(-(12f));
                    par.add(new Chunk(bullet + "  ", F_BOLD));
                    adicionarInlineAoParagrafo(par, conteudo, F_BODY);
                    doc.add(par);
                    adicionarImagensDoMarkdown(doc, extrairTextoMarkdownDoNo(conteudo),
                            360, indent + 24);
                    bullet = ""; // Bullet só na primeira linha do item
                } else if (conteudo instanceof BulletList || conteudo instanceof OrderedList) {
                    renderizarLista(doc, conteudo, indent + 12, nivel + 1);
                }
                conteudo = conteudo.getNext();
            }
            item = item.getNext();
        }
    }

    private void renderizarBlockquote(Document doc, org.commonmark.node.Node bq)
            throws DocumentException, IOException {
        org.commonmark.node.Node child = bq.getFirstChild();
        while (child != null) {
            if (child instanceof org.commonmark.node.Paragraph) {
                Paragraph par = new Paragraph();
                par.setFont(F_ITALIC);
                par.setLeading(15);
                par.setSpacingAfter(4);
                par.setIndentationLeft(20);
                par.add(new Chunk("| ", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11,
                        Color.decode("#999999"))));
                adicionarInlineAoParagrafo(par, child, F_ITALIC);
                doc.add(par);
            }
            child = child.getNext();
        }
    }

    private void renderizarBlocoCode(Document doc, String literal,
                                      float indent) throws DocumentException {
        if (literal == null) return;
        Paragraph par = new Paragraph(literal.trim(), F_CODE);
        par.setLeading(14);
        par.setSpacingBefore(4);
        par.setSpacingAfter(6);
        par.setIndentationLeft(indent + 8);
        //par.setBackgroundColor(Color.decode("#f3f3f8"));
        doc.add(par);
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Helpers de cabeçalho / gabarito / linhas
    // ════════════════════════════════════════════════════════════════════════

    private void addTitulo(Document doc, String titulo, String disciplina,
                            String descricao) throws DocumentException {
        Paragraph t = new Paragraph(titulo, F_TITLE);
        t.setAlignment(Element.ALIGN_CENTER);
        t.setSpacingAfter(6);
        doc.add(t);

        if (disciplina != null && !disciplina.isBlank()) {
            Paragraph d = new Paragraph("Disciplina: " + disciplina, F_SUBTITLE);
            d.setAlignment(Element.ALIGN_CENTER);
            d.setSpacingAfter(4);
            doc.add(d);
        }

        if (descricao != null && !descricao.isBlank()) {
            Paragraph desc = new Paragraph(descricao, F_SUBTITLE);
            desc.setAlignment(Element.ALIGN_CENTER);
            desc.setSpacingAfter(4);
            doc.add(desc);
        }

        doc.add(new Chunk(new LineSeparator(1, 100, Color.decode("#cccccc"), Element.ALIGN_CENTER, -5)));
        doc.add(Chunk.NEWLINE);
    }

    private void adicionarLinhasResposta(Document doc, int quantidade) throws DocumentException {
        doc.add(Chunk.NEWLINE);
        for (int l = 0; l < quantidade; l++) {
            doc.add(new Chunk(new LineSeparator(0.5f, 100, Color.decode("#aaaaaa"),
                    Element.ALIGN_CENTER, -2)));
            doc.add(new Paragraph(" ", new Font(Font.HELVETICA, 7)));
        }
    }

    private void adicionarTabelaGabarito(Document doc, List<ProvaQuestao> questoes)
            throws DocumentException {
        Paragraph gabTitulo = new Paragraph("GABARITO", F_TITLE);
        gabTitulo.setAlignment(Element.ALIGN_CENTER);
        gabTitulo.setSpacingAfter(12);
        doc.add(gabTitulo);

        PdfPTable tabela = new PdfPTable(2);
        tabela.setWidthPercentage(60);
        tabela.setHorizontalAlignment(Element.ALIGN_CENTER);
        tabela.setWidths(new float[]{1f, 2f});

        addCabecalhoTabela(tabela, "Nº",       F_QNUM);
        addCabecalhoTabela(tabela, "Resposta", F_QNUM);

        for (int i = 0; i < questoes.size(); i++) {
            Questao q = questoes.get(i).getQuestao();
            addCelulaTabela(tabela, String.valueOf(i + 1), F_BODY);
            if (q.getTipo() == Questao.TipoQuestao.OBJETIVA) {
                String resp = q.getAlternativas().stream()
                        .filter(a -> Boolean.TRUE.equals(a.getCorreta()))
                        .map(Alternativa::getLetra)
                        .reduce("", (a, b) -> a.isEmpty() ? b : a + ", " + b);
                addCelulaTabela(tabela, resp.isEmpty() ? "-" : resp, F_BODY);
            } else {
                addCelulaTabela(tabela, "Discursiva", F_GABARITO);
            }
        }
        doc.add(tabela);
    }

    private void addCabecalhoTabela(PdfPTable tabela, String texto, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(texto, font));
        cell.setBackgroundColor(Color.decode("#e8eaf6"));
        cell.setPadding(6);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        tabela.addCell(cell);
    }

    private void addCelulaTabela(PdfPTable tabela, String texto, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(texto, font));
        cell.setPadding(5);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        tabela.addCell(cell);
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Utilitários de fonte e AST
    // ════════════════════════════════════════════════════════════════════════

    /** Resolve a fonte correta dada a combinação bold/italic. */
    private Font resolverFonte(Font base, boolean bold, boolean italic) {
        if (bold && italic) return adaptarTamanho(F_BOLDITALIC, base);
        if (bold)           return adaptarTamanho(F_BOLD,       base);
        if (italic)         return adaptarTamanho(F_ITALIC,     base);
        return base;
    }

    private Font adaptarTamanho(Font target, Font referencia) {
        if (Math.abs(target.getSize() - referencia.getSize()) < 0.1f) return target;
        Font f = new Font(target);
        f.setSize(referencia.getSize());
        return f;
    }

    /**
     * Retorna true se o parágrafo contiver exclusivamente uma imagem
     * (possivelmente precedida/seguida de SoftLineBreak/HardLineBreak).
     */
    private boolean isApenasSoftBreakOuImagem(org.commonmark.node.Node par) {
        org.commonmark.node.Node child = par.getFirstChild();
        boolean temImagem = false;
        while (child != null) {
            if (child instanceof org.commonmark.node.Image) {
                temImagem = true;
            } else if (!(child instanceof SoftLineBreak) && !(child instanceof HardLineBreak)) {
                return false;
            }
            child = child.getNext();
        }
        return temImagem;
    }

    /**
     * Reconstrói uma representação Markdown simplificada de um nó,
     * usada apenas para extrair as URLs de imagens embutidas.
     */
    private String extrairTextoMarkdownDoNo(org.commonmark.node.Node no) {
        StringBuilder sb = new StringBuilder();
        extrairTextoRec(no, sb);
        return sb.toString();
    }

    private void extrairTextoRec(org.commonmark.node.Node node, StringBuilder sb) {
        if (node == null) return;
        if (node instanceof org.commonmark.node.Image img) {
            sb.append("![").append(img.getTitle() != null ? img.getTitle() : "")
              .append("](").append(img.getDestination()).append(") ");
        }
        extrairTextoRec(node.getFirstChild(), sb);
        extrairTextoRec(node.getNext(), sb);
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Header/Footer de página
    // ════════════════════════════════════════════════════════════════════════

    private static class HeaderFooterEvent extends PdfPageEventHelper {
        private final String titulo;
        private final String subtitulo;

        HeaderFooterEvent(String titulo, String subtitulo) {
            this.titulo    = titulo;
            this.subtitulo = subtitulo;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContent();
            Font fH = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.decode("#888888"));

            String cabecalho = titulo + (subtitulo != null ? " | " + subtitulo : "");
            ColumnText.showTextAligned(cb, Element.ALIGN_LEFT,
                    new Phrase(cabecalho, fH),
                    document.left(), document.top() + 15, 0);

            ColumnText.showTextAligned(cb, Element.ALIGN_RIGHT,
                    new Phrase("Página " + writer.getPageNumber(), fH),
                    document.right(), document.bottom() - 20, 0);

            cb.setLineWidth(0.5f);
            cb.setColorStroke(Color.decode("#cccccc"));
            cb.moveTo(document.left(),  document.top()    + 10);
            cb.lineTo(document.right(), document.top()    + 10);
            cb.stroke();
            cb.moveTo(document.left(),  document.bottom() - 10);
            cb.lineTo(document.right(), document.bottom() - 10);
            cb.stroke();
        }
    }
}
