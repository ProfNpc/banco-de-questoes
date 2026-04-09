package com.questoes.service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import org.springframework.stereotype.Service;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PdfService {

    public byte[] gerarPdfProva(Prova prova, boolean incluirGabarito) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        Document doc = new Document(PageSize.A4, 60, 60, 80, 80);
        PdfWriter writer = PdfWriter.getInstance(doc, baos);

        // Header e footer
        writer.setPageEvent(new HeaderFooterEvent(prova.getTitulo(), prova.getDisciplina()));

        doc.open();

        // Título da prova
        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, Color.decode("#1a1a2e"));
        Font subtitleFont = FontFactory.getFont(FontFactory.HELVETICA, 11, Color.decode("#444444"));
        Font questaoNumFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Color.decode("#0f3460"));
        Font questaoFont = FontFactory.getFont(FontFactory.HELVETICA, 11, Color.BLACK);
        Font altFont = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.BLACK);
        Font altCorretaFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.decode("#16213e"));
        Font gabaritoFont = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 10, Color.decode("#555555"));

        Paragraph titulo = new Paragraph(prova.getTitulo(), titleFont);
        titulo.setAlignment(Element.ALIGN_CENTER);
        titulo.setSpacingAfter(6);
        doc.add(titulo);

        if (prova.getDisciplina() != null && !prova.getDisciplina().isBlank()) {
            Paragraph disc = new Paragraph("Disciplina: " + prova.getDisciplina(), subtitleFont);
            disc.setAlignment(Element.ALIGN_CENTER);
            disc.setSpacingAfter(4);
            doc.add(disc);
        }

        if (prova.getDescricao() != null && !prova.getDescricao().isBlank()) {
            Paragraph desc = new Paragraph(prova.getDescricao(), subtitleFont);
            desc.setAlignment(Element.ALIGN_CENTER);
            desc.setSpacingAfter(4);
            doc.add(desc);
        }

        // Linha separadora
        LineSeparator separator = new LineSeparator(1, 100, Color.decode("#cccccc"), Element.ALIGN_CENTER, -5);
        doc.add(new Chunk(separator));
        doc.add(Chunk.NEWLINE);

        // Questões
        List<ProvaQuestao> questoes = prova.getProvaQuestoes();
        for (int i = 0; i < questoes.size(); i++) {
            ProvaQuestao pq = questoes.get(i);
            Questao q = pq.getQuestao();

            // Número e pontos
            String pontoStr = pq.getPontos() != null ? String.format(" (%.1f pt%s)",
                    pq.getPontos(), pq.getPontos() == 1.0 ? "" : "s") : "";
            Paragraph numParagraph = new Paragraph(
                    "Questão " + (i + 1) + pontoStr, questaoNumFont);
            numParagraph.setSpacingBefore(14);
            numParagraph.setSpacingAfter(4);
            doc.add(numParagraph);

            // Enunciado (texto simples - strip markdown para PDF)
            String enunciado = stripMarkdown(q.getEnunciado());
            Paragraph enunciadoParagraph = new Paragraph(enunciado, questaoFont);
            enunciadoParagraph.setSpacingAfter(6);
            enunciadoParagraph.setLeading(16);
            doc.add(enunciadoParagraph);

            // Alternativas (objetiva)
            if (q.getTipo() == Questao.TipoQuestao.OBJETIVA && !q.getAlternativas().isEmpty()) {
                for (Alternativa alt : q.getAlternativas()) {
                    boolean correta = Boolean.TRUE.equals(alt.getCorreta());
                    Font fAlt = (incluirGabarito && correta) ? altCorretaFont : altFont;
                    String prefixo = alt.getLetra() + ") ";
                    String textoAlt = stripMarkdown(alt.getTexto());
                    if (incluirGabarito && correta) textoAlt += " ✓";

                    Paragraph altParagraph = new Paragraph(prefixo + textoAlt, fAlt);
                    altParagraph.setIndentationLeft(20);
                    altParagraph.setSpacingAfter(3);
                    altParagraph.setLeading(14);
                    doc.add(altParagraph);
                }
            }

            // Gabarito discursiva
            if (q.getTipo() == Questao.TipoQuestao.DISCURSIVA) {
                if (incluirGabarito && q.getGabarito() != null && !q.getGabarito().isBlank()) {
                    Paragraph gabParagraph = new Paragraph(
                            "Gabarito: " + stripMarkdown(q.getGabarito()), gabaritoFont);
                    gabParagraph.setIndentationLeft(10);
                    gabParagraph.setSpacingBefore(4);
                    doc.add(gabParagraph);
                } else if (!incluirGabarito) {
                    // Linhas para resposta
                    doc.add(Chunk.NEWLINE);
                    for (int l = 0; l < 5; l++) {
                        LineSeparator ls = new LineSeparator(0.5f, 100, Color.decode("#aaaaaa"), Element.ALIGN_CENTER, -2);
                        doc.add(new Chunk(ls));
                        doc.add(new Paragraph(" ", new Font(Font.HELVETICA, 8)));
                    }
                }
            }
        }

        // Gabarito resumido ao final (objetivas)
        if (incluirGabarito) {
            doc.newPage();
            Paragraph gabTitulo = new Paragraph("GABARITO", titleFont);
            gabTitulo.setAlignment(Element.ALIGN_CENTER);
            gabTitulo.setSpacingAfter(12);
            doc.add(gabTitulo);

            PdfPTable tabela = new PdfPTable(2);
            tabela.setWidthPercentage(60);
            tabela.setHorizontalAlignment(Element.ALIGN_CENTER);
            tabela.setWidths(new float[]{1f, 2f});

            addCabecalhoTabela(tabela, "Nº", questaoNumFont);
            addCabecalhoTabela(tabela, "Resposta", questaoNumFont);

            for (int i = 0; i < questoes.size(); i++) {
                Questao q = questoes.get(i).getQuestao();
                addCelulaTabela(tabela, String.valueOf(i + 1), questaoFont);
                if (q.getTipo() == Questao.TipoQuestao.OBJETIVA) {
                    String resp = q.getAlternativas().stream()
                            .filter(a -> Boolean.TRUE.equals(a.getCorreta()))
                            .map(Alternativa::getLetra)
                            .reduce("", (a, b) -> a.isEmpty() ? b : a + ", " + b);
                    addCelulaTabela(tabela, resp.isEmpty() ? "-" : resp, questaoFont);
                } else {
                    addCelulaTabela(tabela, "Discursiva", gabaritoFont);
                }
            }
            doc.add(tabela);
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

        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, Color.decode("#1a1a2e"));
        Font questaoNumFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Color.decode("#0f3460"));
        Font questaoFont = FontFactory.getFont(FontFactory.HELVETICA, 11, Color.BLACK);
        Font altFont = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.BLACK);

        Paragraph tituloParagraph = new Paragraph(titulo, titleFont);
        tituloParagraph.setAlignment(Element.ALIGN_CENTER);
        tituloParagraph.setSpacingAfter(10);
        doc.add(tituloParagraph);

        LineSeparator sep = new LineSeparator(1, 100, Color.decode("#cccccc"), Element.ALIGN_CENTER, -5);
        doc.add(new Chunk(sep));
        doc.add(Chunk.NEWLINE);

        for (int i = 0; i < questoes.size(); i++) {
            Questao q = questoes.get(i);

            Paragraph numP = new Paragraph("Questão " + (i + 1), questaoNumFont);
            numP.setSpacingBefore(14);
            numP.setSpacingAfter(4);
            doc.add(numP);

            Paragraph enuncP = new Paragraph(stripMarkdown(q.getEnunciado()), questaoFont);
            enuncP.setLeading(16);
            enuncP.setSpacingAfter(6);
            doc.add(enuncP);

            if (q.getTipo() == Questao.TipoQuestao.OBJETIVA) {
                for (Alternativa alt : q.getAlternativas()) {
                    Paragraph altP = new Paragraph(alt.getLetra() + ") " + stripMarkdown(alt.getTexto()), altFont);
                    altP.setIndentationLeft(20);
                    altP.setSpacingAfter(3);
                    altP.setLeading(14);
                    doc.add(altP);
                }
            } else {
                doc.add(Chunk.NEWLINE);
                for (int l = 0; l < 5; l++) {
                    LineSeparator ls = new LineSeparator(0.5f, 100, Color.decode("#aaaaaa"), Element.ALIGN_CENTER, -2);
                    doc.add(new Chunk(ls));
                    doc.add(new Paragraph(" ", new Font(Font.HELVETICA, 8)));
                }
            }
        }

        doc.close();
        return baos.toByteArray();
    }

    private String stripMarkdown(String text) {
        if (text == null) return "";
        return text
                .replaceAll("!\\[.*?\\]\\(.*?\\)", "[imagem]")
                .replaceAll("\\[([^]]+)\\]\\([^)]+\\)", "$1")
                .replaceAll("\\*\\*(.+?)\\*\\*", "$1")
                .replaceAll("\\*(.+?)\\*", "$1")
                .replaceAll("__(.+?)__", "$1")
                .replaceAll("_(.+?)_", "$1")
                .replaceAll("~~(.+?)~~", "$1")
                .replaceAll("```[\\s\\S]*?```", "")
                .replaceAll("`(.+?)`", "$1")
                .replaceAll("^#{1,6}\\s+", "")
                .replaceAll("(?m)^#{1,6}\\s+", "")
                .replaceAll("^>\\s+", "")
                .replaceAll("(?m)^>\\s+", "")
                .replaceAll("^[-*+]\\s+", "")
                .replaceAll("(?m)^[-*+]\\s+", "")
                .trim();
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

    private static class HeaderFooterEvent extends PdfPageEventHelper {
        private final String titulo;
        private final String subtitulo;

        HeaderFooterEvent(String titulo, String subtitulo) {
            this.titulo = titulo;
            this.subtitulo = subtitulo;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContent();
            Font headerFont = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.decode("#888888"));
            Font footerFont = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.decode("#888888"));

            // Header
            Phrase header = new Phrase(titulo + (subtitulo != null ? " | " + subtitulo : ""), headerFont);
            ColumnText.showTextAligned(cb, Element.ALIGN_LEFT, header,
                    document.left(), document.top() + 15, 0);

            // Footer - página
            Phrase footer = new Phrase("Página " + writer.getPageNumber(), footerFont);
            ColumnText.showTextAligned(cb, Element.ALIGN_RIGHT, footer,
                    document.right(), document.bottom() - 20, 0);

            // Linha header
            cb.setLineWidth(0.5f);
            cb.setColorStroke(Color.decode("#cccccc"));
            cb.moveTo(document.left(), document.top() + 10);
            cb.lineTo(document.right(), document.top() + 10);
            cb.stroke();

            // Linha footer
            cb.moveTo(document.left(), document.bottom() - 10);
            cb.lineTo(document.right(), document.bottom() - 10);
            cb.stroke();
        }
    }
}
