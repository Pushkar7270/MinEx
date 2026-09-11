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
 */
public interface LlmClassifier {

    record LlmResult(String category, double confidence) {}

    Optional<LlmResult> classify(String text, List<String> categories);
}
