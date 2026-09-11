package com.minex.backend.service.extraction;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Heuristic figure spotter: finds "<label> <number> <unit>" lines in
 * extracted text. Produces per-field confidence; weak matches are left for
 * the Data Corrector via {@code needs_review}.
 */
@Component
public class FigureExtractor {

    private static final Pattern FIGURE = Pattern.compile(
            "^(.{3,90}?)\\s*[:\\-–]?\\s*([0-9][0-9,]*\\.?[0-9]*)\\s*(MT|Mt|million tonnes?|crore|lakh|%|percent|per cent|Rs\\.?|INR|km|MW)?\\s*$");

    private static final Pattern PERIOD = Pattern.compile(
            "(?:FY\\s?)?(20\\d{2})\\s*[\\-–/]\\s*\\d{2,4}");

    public List<CandidateFigure> extract(List<PageBlock> blocks) {
        List<CandidateFigure> out = new ArrayList<>();
        for (PageBlock block : blocks) {
            if (block.text() == null) continue;
            for (String rawLine : block.text().split("\\R")) {
                String line = rawLine.trim().replaceAll("\\s+", " ");
                if (line.length() < 5 || line.length() > 160) continue;
                if (line.contains("|")) {
                    extractTableRow(line, block.confidence(), out);
                    continue;
                }
                Matcher m = FIGURE.matcher(line);
                if (!m.matches()) continue;
                String label = m.group(1).trim();
                if (label.isEmpty() || label.matches("[0-9.,\\s]+")) continue;
                double value;
                try {
                    value = Double.parseDouble(m.group(2).replace(",", ""));
                } catch (NumberFormatException ex) {
                    continue;
                }
                String unit = m.group(3);
                double confidence = block.confidence()
                        * (unit != null ? 0.9 : 0.65)
                        * (label.length() > 60 ? 0.9 : 1.0);
                out.add(new CandidateFigure(label, value, unit, line, round(confidence)));
            }
        }
        return out;
    }

    /**
     * Table rows from Tabula/POI arrive as "label | value | unit" cells.
     * Header rows (no numeric cell) are skipped naturally.
     */
    private void extractTableRow(String line, double blockConfidence, List<CandidateFigure> out) {
        String[] cells = line.split("\\|");
        if (cells.length < 2) return;
        Double value = null;
        int valueIdx = -1;
        for (int i = 0; i < cells.length; i++) {
            Double v = tryNumber(cells[i].trim());
            if (v != null) {
                value = v;
                valueIdx = i;
                break;
            }
        }
        if (value == null) return; // header row
        String label = null;
        for (int i = 0; i < valueIdx; i++) {
            String cell = cells[i].trim();
            if (!cell.isEmpty() && tryNumber(cell) == null) {
                label = cell;
                break;
            }
        }
        if (label == null || label.length() < 3) return;
        String unit = null;
        if (valueIdx + 1 < cells.length) {
            String maybeUnit = cells[valueIdx + 1].trim();
            if (!maybeUnit.isEmpty() && tryNumber(maybeUnit) == null && maybeUnit.length() <= 20) {
                unit = maybeUnit;
            }
        }
        double confidence = blockConfidence * 0.8 * (unit != null ? 0.9 : 0.6);
        out.add(new CandidateFigure(label, value, unit, line, round(confidence)));
    }

    private static Double tryNumber(String s) {
        try {
            return Double.parseDouble(s.replace(",", ""));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /** First fiscal-year mention in the text, e.g. "2024-25". Null if none. */    public String detectPeriod(List<PageBlock> blocks) {
        for (PageBlock block : blocks) {
            if (block.text() == null) continue;
            Matcher m = PERIOD.matcher(block.text());
            if (m.find()) {
                String start = m.group(1);
                String tail = m.group(0).replaceAll(".*[\\-–/]\\s*", "").replaceAll("\\D", "");
                if (tail.length() > 2) tail = tail.substring(2);
                if (tail.length() == 1) tail = "0" + tail;
                return tail.length() == 2 ? start + "-" + tail : start;
            }
        }
        return null;
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
