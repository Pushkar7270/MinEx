package com.minex.backend.service.extraction;

import com.minex.backend.domain.Category;
import com.minex.backend.repo.CategoryRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * §4.3 first pass: deterministic rule-based keyword/regex classifier against
 * the configurable {@code categories} table. LLM fallback (local Llama via
 * Ollama, or hosted Kimi via an OpenAI-compatible endpoint) is invoked only
 * when rule confidence is low — and only accepted above min-confidence.
 */
@Service
public class CategorizerService {

    private static final Map<String, List<String>> RULES = Map.of(
            "Annual Yield", List.of("production", "output", "yield", "dispatch", "offtake",
                    "coal production", "raw coal", "overburden", "productivity"),
            "Annual Expense", List.of("expenditure", "expense", "opex", "revenue expenditure",
                    "cost of production", "operating cost", "wage bill", "salary", "stores"),
            "Budget", List.of("budget", "outlay", "allocation", "capex", "capital expenditure",
                    "plan outlay", "budgetary", "provision"),
            "Safety Incidents", List.of("safety", "accident", "fatality", "fatal", "injury",
                    "injured", "incident", "mine rescue", "disaster", "compensation"),
            "Geological Survey", List.of("survey", "exploration", "drilling", "borehole",
                    "geological", "reserve", "gsi", "cmpdi", "seam", "stratigraphic"));

    private final Map<String, Category> byName = new HashMap<>();
    private final LlmClassifier llm;

    @Autowired
    public CategorizerService(CategoryRepository categories, LlmClassifier llm) {
        categories.findAll().forEach(c -> byName.put(c.getName(), c));
        this.llm = llm;
    }

    /** Test/seeding constructor. */
    CategorizerService(Map<String, Category> byName, LlmClassifier llm) {
        this.byName.putAll(byName);
        this.llm = llm;
    }

    public Optional<Category> categorize(String text) {
        if (text == null || text.isBlank()) return Optional.empty();
        String lower = text.toLowerCase(Locale.ROOT);
        String best = null;
        int bestScore = 0;
        for (var entry : RULES.entrySet()) {
            int score = 0;
            for (String keyword : entry.getValue()) {
                if (lower.contains(keyword)) score += keyword.contains(" ") ? 3 : 1;
            }
            if (score > bestScore) {
                bestScore = score;
                best = entry.getKey();
            }
        }
        // Confident rule hit (a multi-word keyword, or 2+ keyword hits):
        // trust it, no LLM call.
        if (best != null && bestScore >= 2) {
            return Optional.ofNullable(byName.get(best));
        }
        // Ambiguous: ask the LLM (no-op when provider=none), accept only known
        // categories at min-confidence; otherwise a human reviews.
        return llm.classify(text, List.copyOf(byName.keySet()))
                .map(LlmClassifier.LlmResult::category)
                .flatMap(name -> Optional.ofNullable(byName.get(name)));
    }

    /** Score only, for tests. */
    int score(String category, String text) {
        int score = 0;
        String lower = text.toLowerCase(Locale.ROOT);
        for (String keyword : RULES.getOrDefault(category, List.of())) {
            if (lower.contains(keyword)) score += keyword.contains(" ") ? 3 : 1;
        }
        return score;
    }
}
