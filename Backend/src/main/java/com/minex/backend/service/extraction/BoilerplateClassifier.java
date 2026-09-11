package com.minex.backend.service.extraction;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * §4.6 ads/clutter removal: classifies page-level blocks as content vs
 * boilerplate/promotional. Boilerplate is hidden from the clean view but the
 * original document stays intact and viewable on demand.
 */
@Component
public class BoilerplateClassifier {

    private static final Set<String> DENYLIST = Set.of(
            "advertisement", "advertorial", "sponsored", "subscribe now",
            "follow us", "page intentionally left blank", "for private circulation");

    public boolean isBoilerplate(String text, Map<String, Integer> firstLineFreq,
                                 Map<String, Integer> lastLineFreq) {
        if (text == null || text.isBlank()) return true;
        String trimmed = text.strip();
        if (trimmed.length() < 10) return true;
        String lower = trimmed.toLowerCase(Locale.ROOT);
        for (String banned : DENYLIST) {
            if (lower.contains(banned)) return true;
        }
        // Repeated header/footer across ≥3 pages (e.g. running titles, page numbers).
        String[] lines = trimmed.split("\\R");
        if (lines.length > 1) {
            String first = lines[0].strip();
            String last = lines[lines.length - 1].strip();
            if (first.length() > 3 && firstLineFreq.getOrDefault(first, 0) >= 3) return true;
            if (last.length() > 3 && lastLineFreq.getOrDefault(last, 0) >= 3) return true;
        }
        return false;
    }

    /** Counts first/last lines across blocks so repeats can be detected. */
    public void accumulateFrequencies(List<PageBlock> blocks, Map<String, Integer> firstLineFreq,
                                      Map<String, Integer> lastLineFreq) {
        for (PageBlock block : blocks) {
            if (!"text".equals(block.blockType()) || block.text() == null || block.text().isBlank()) continue;
            String[] lines = block.text().strip().split("\\R");
            if (lines.length > 1) {
                firstLineFreq.merge(lines[0].strip(), 1, Integer::sum);
                lastLineFreq.merge(lines[lines.length - 1].strip(), 1, Integer::sum);
            }
        }
    }
}
