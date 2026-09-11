package com.minex.backend.service.extraction;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;
import technology.tabula.ObjectExtractor;
import technology.tabula.Page;
import technology.tabula.PageIterator;
import technology.tabula.RectangularTextContainer;
import technology.tabula.Table;
import technology.tabula.extractors.SpreadsheetExtractionAlgorithm;

/**
 * Text-layer PDF extraction via PDFBox; tables via Tabula.
 * Reports whether the PDF looks scanned (no embedded fonts / no text layer).
 */
@Component
public class PdfTextExtractor {

    public record PdfResult(List<PageBlock> blocks, boolean scanned) {}

    public PdfResult extract(byte[] pdfBytes) throws IOException {
        List<PageBlock> blocks = new ArrayList<>();
        boolean anyText = false;
        boolean anyFonts = false;
        try (PDDocument doc = PDDocument.load(new ByteArrayInputStream(pdfBytes))) {
            int pages = doc.getNumberOfPages();
            for (int i = 1; i <= pages; i++) {
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                String text = stripper.getText(doc);
                if (text != null && text.strip().length() > 20) anyText = true;
                try {
                    var fonts = doc.getPage(i - 1).getResources().getFontNames();
                    if (fonts != null && fonts.iterator().hasNext()) anyFonts = true;
                } catch (Exception ignored) {
                    // Unreadable resources: treat as no fonts.
                }
                blocks.add(new PageBlock(i, "text", text == null ? "" : text.strip(), 0.95));
            }
            // Tables (lower confidence — always surface for human review).
            blocks.addAll(extractTables(pdfBytes));
        }
        return new PdfResult(blocks, !anyText && !anyFonts);
    }

    private List<PageBlock> extractTables(byte[] pdfBytes) {
        List<PageBlock> tables = new ArrayList<>();
        try (PDDocument doc = PDDocument.load(new ByteArrayInputStream(pdfBytes));
             ObjectExtractor extractor = new ObjectExtractor(doc)) {
            SpreadsheetExtractionAlgorithm algo = new SpreadsheetExtractionAlgorithm();
            PageIterator pages = extractor.extract();
            int pageNo = 0;
            while (pages.hasNext()) {
                pageNo++;
                Page page = pages.next();
                @SuppressWarnings("unchecked")
                List<Table> found = (List<Table>) algo.extract(page);
                for (Table table : found) {
                    StringBuilder sb = new StringBuilder();
                    for (List<RectangularTextContainer> row : table.getRows()) {
                        List<String> cells = new ArrayList<>();
                        for (RectangularTextContainer cell : row) {
                            cells.add(cell.getText().replaceAll("\\s+", " ").strip());
                        }
                        sb.append(String.join(" | ", cells)).append('\n');
                    }
                    String text = sb.toString().strip();
                    if (!text.isEmpty()) {
                        tables.add(new PageBlock(pageNo, "table", text, 0.7));
                    }
                }
            }
        } catch (Exception ignored) {
            // Table extraction is best-effort; text blocks still stand.
        }
        return tables;
    }
}
