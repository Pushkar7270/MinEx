package com.minex.backend.service.extraction;

import java.util.ArrayList;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.springframework.stereotype.Component;

/** Spreadsheet (XLSX/XLS/CSV) and Word (DOCX) extraction via Apache POI. */
@Component
public class OfficeExtractor {

    public List<PageBlock> extractSpreadsheet(byte[] bytes, String filename) {
        List<PageBlock> blocks = new ArrayList<>();
        try (Workbook wb = WorkbookFactory.create(new java.io.ByteArrayInputStream(bytes))) {
            for (Sheet sheet : wb) {
                StringBuilder text = new StringBuilder();
                StringBuilder tables = new StringBuilder();
                for (Row row : sheet) {
                    List<String> cells = new ArrayList<>();
                    for (Cell cell : row) {
                        cells.add(cellText(cell));
                    }
                    while (!cells.isEmpty() && cells.get(cells.size() - 1).isEmpty()) {
                        cells.remove(cells.size() - 1);
                    }
                    if (cells.isEmpty()) continue;
                    String line = String.join(" | ", cells);
                    text.append(line).append('\n');
                    if (cells.size() >= 2) tables.append(line).append('\n');
                }
                String sheetText = text.toString().strip();
                if (!sheetText.isEmpty()) {
                    blocks.add(new PageBlock(null, "text",
                            "Sheet: " + sheet.getSheetName() + "\n" + sheetText, 0.9));
                }
                String tableText = tables.toString().strip();
                if (!tableText.isEmpty()) {
                    blocks.add(new PageBlock(null, "table",
                            "Sheet: " + sheet.getSheetName() + "\n" + tableText, 0.85));
                }
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Spreadsheet parsing failed for " + filename, ex);
        }
        return blocks;
    }

    public List<PageBlock> extractDocx(byte[] bytes, String filename) {
        List<PageBlock> blocks = new ArrayList<>();
        try (XWPFDocument doc = new XWPFDocument(new java.io.ByteArrayInputStream(bytes))) {
            StringBuilder text = new StringBuilder();
            for (XWPFParagraph p : doc.getParagraphs()) {
                String line = p.getText();
                if (line != null && !line.isBlank()) text.append(line.strip()).append('\n');
            }
            if (!text.toString().isBlank()) {
                blocks.add(new PageBlock(null, "text", text.toString().strip(), 0.9));
            }
            for (XWPFTable table : doc.getTables()) {
                StringBuilder sb = new StringBuilder();
                table.getRows().forEach(row -> {
                    List<String> cells = new ArrayList<>();
                    for (XWPFTableCell cell : row.getTableCells()) {
                        cells.add(cell.getText().replaceAll("\\s+", " ").strip());
                    }
                    sb.append(String.join(" | ", cells)).append('\n');
                });
                if (!sb.toString().isBlank()) {
                    blocks.add(new PageBlock(null, "table", sb.toString().strip(), 0.85));
                }
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Word document parsing failed for " + filename, ex);
        }
        return blocks;
    }

    private static String cellText(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().strip();
            case NUMERIC -> {
                double v = cell.getNumericCellValue();
                yield v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> switch (cell.getCachedFormulaResultType()) {
                case STRING -> cell.getStringCellValue().strip();
                case NUMERIC -> {
                    double v = cell.getNumericCellValue();
                    yield v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
                }
                case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
                default -> "";
            };
            default -> "";
        } + (cell.getCellType() == CellType.BLANK ? "" : "");
    }
}
