package com.minex.backend.service.extraction;

import java.util.List;
import java.util.Optional;

/**
 * Pluggable LLM segregation (PRD §4.3 fallback). Invoked only when the
 * rule-based classifier is unsure.
 *
 * Contract: implementations return a result only when confidence meets the
 * configured {@code app.llm.min-confidence}; anything weaker comes back as
 * {@link Optional#empty()} so the figure stays in human review.
 * A result may name an existing category or propose a new one
 * ({@code newlyProposed=true}); callers decide whether new names are adopted.
 */
public interface LlmClassifier {

    record LlmResult(String category, double confidence, boolean newlyProposed) {
        LlmResult(String category, double confidence) {
            this(category, confidence, false);
        }
    }

    Optional<LlmResult> classify(String text, List<String> categories);

    /** Shared acceptance: known or plausibly-new name, at min-confidence. */
    static Optional<LlmResult> accept(com.fasterxml.jackson.databind.JsonNode answer,
                                      List<String> categories, double minConfidence) {
        String raw = answer.path("category").asText(null);
        double confidence = answer.path("confidence").asDouble(0);
        boolean isNew = answer.path("new").asBoolean(false);
        if (raw == null || confidence < minConfidence) return Optional.empty();
        String name = normalize(raw);
        if (name.length() < 3 || name.length() > 60) return Optional.empty();
        if (!isNew && !categories.contains(name)) return Optional.empty();
        return Optional.of(new LlmResult(name, confidence, isNew || !categories.contains(name)));
    }

    static String normalize(String name) {
        String clean = name.strip().replaceAll("\\s+", " ");
        if (clean.isEmpty()) return clean;
        String[] words = clean.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!w.isEmpty()) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(Character.toUpperCase(w.charAt(0)));
                if (w.length() > 1) sb.append(w.substring(1).toLowerCase(java.util.Locale.ROOT));
            }
        }
        return sb.toString();
    }
}
