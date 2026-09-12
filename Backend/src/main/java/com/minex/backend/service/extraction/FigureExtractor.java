package com.minex.backend.service.extraction;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Heuristic figure spotter: finds "<label> <number> <unit>" lines in
 * extracted text. Produces per-field confidence; weak matches are left for
 * the Data Corrector via {@code needs_review}.
 *
 * <p>Confidence follows the agreed spec:
 * {@code confidence = C_block × W_unit × W_label}, where
 * <ul>
 *   <li>{@code C_block} is the source-block confidence (PDF text 0.95,
 *       PDF table 0.70, Office text 0.90, Office table 0.85) — already carried
 *       by {@link PageBlock#confidence()};</li>
 *   <li>{@code W_unit} rewards figures that match the document's unit pattern:
 *       {@code r_unit} when the figure has a unit, {@code 1 - r_unit} when it does
 *       not, with {@code r_unit} = share of figures with a unit so far;</li>
 *   <li>{@code W_label = min(1, avgLabelLength / thisLabelLength)}, penalising
 *       labels that are unusually long versus the document so far.</li>
 * </ul>
 */
@Component
public class FigureExtractor {

    private static final Pattern FIGURE = Pattern.compile(
            "^(.{3,90}?)\\s*[:\\-–]?\\s*([0-9][0-9,]*\\.?[0-9]*)\\s*(MT|Mt|million tonnes?|crore|lakh|%|percent|per cent|Rs\\.?|INR|km|MW)?\\s*$");

    private static final Pattern PERIOD = Pattern.compile(
            "(?:FY\\s?)?(20\\d{2})\\s*[\\-–/]\\s*\\d{2,4}");

    public List<CandidateFigure> extract(List<PageBlock> blocks) {
        List<Raw> raw = new ArrayList<>();
        for (PageBlock block : blocks) {
            if (block.text() == null) continue;
            for (String rawLine : block.text().split("\\R")) {
                String line = rawLine.trim().replaceAll("\\s+", " ");
                if (line.length() < 5 || line.length() > 160) continue;
                if (line.contains("|")) {
                    collectTableRow(line, block.confidence(), raw);
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
                raw.add(new Raw(label, value, m.group(3), line, block.confidence()));
            }
        }
        return score(raw);
    }

    /** Applies the document-adaptive confidence over the candidates in document order. */
    private List<CandidateFigure> score(List<Raw> raw) {
        List<CandidateFigure> out = new ArrayList<>();
        int total = 0;
        int withUnit = 0;
        double labelLengthSum = 0;
        for (Raw r : raw) {
            total++;
            boolean hasUnit = r.unit() != null && !r.unit().isBlank();
            if (hasUnit) withUnit++;
            labelLengthSum += r.label().length();

            // r_unit over figures seen so far (including this one), so a lone
            // figure is self-consistent and no division by zero can occur.
            double rUnit = (double) withUnit / total;
            double wUnit = hasUnit ? rUnit : (1.0 - rUnit);

            double avgLabelLength = labelLengthSum / total;
            double wLabel = Math.min(1.0, avgLabelLength / Math.max(1, r.label().length()));

            out.add(new CandidateFigure(r.label(), r.value(), r.unit(), r.context(),
                    round(r.blockConfidence() * wUnit * wLabel)));
        }
        return out;
    }

    /**
     * Table rows from Tabula/POI arrive as "label | value | unit" cells.
     * Header rows (no numeric cell) are skipped naturally.
     */
    private void collectTableRow(String line, double blockConfidence, List<Raw> out) {
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
        out.add(new Raw(label, value, unit, line, blockConfidence));
    }

    private static Double tryNumber(String s) {
        try {
            return Double.parseDouble(s.replace(",", ""));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /** First fiscal-year mention in the text, e.g. "2024-25". Null if none. */
    public String detectPeriod(List<PageBlock> blocks) {
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

    /** Raw candidate before the document-adaptive confidence is applied. */
    private record Raw(String label, Double value, String unit, String context, double blockConfidence) {}
}
