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
 * Segregation: deterministic rules first; LLM fallback for ambiguity.
 * The taxonomy is self-extending — when the LLM confidently proposes a
 * genuinely new category, it is created in the categories table (visible
 * in the dashboard + role hierarchy immediately, admin can rename later).
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
    private final java.util.function.Function<String, Category> creator;

    @Autowired
    public CategorizerService(CategoryRepository categories, LlmClassifier llm) {
        categories.findAll().forEach(c -> byName.put(c.getName(), c));
        this.llm = llm;
        this.creator = name -> {
            Category existing = byName.get(name);
            if (existing != null) return existing;
            Category created = new Category();
            created.setName(name);
            created.setCreatedAt(java.time.OffsetDateTime.now());
            try {
                created = categories.save(created);
            } catch (org.springframework.dao.DataIntegrityViolationException dup) {
                // Raced another worker: reuse the winner.
                created = categories.findByName(name).orElseThrow(() -> dup);
            }
            byName.put(created.getName(), created);
            return created;
        };
    }

    /** Test/seeding constructor (transient categories, no DB). */
    CategorizerService(Map<String, Category> byName, LlmClassifier llm) {
        this.byName.putAll(byName);
        this.llm = llm;
        this.creator = name -> byName.computeIfAbsent(name, n -> {
            Category c = new Category();
            c.setName(n);
            return c;
        });
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
        // Ambiguous: ask the LLM (no-op when provider=none). Known names map
        // directly; genuinely new names are adopted into the taxonomy.
        // Anything else stays with a human reviewer.
        return llm.classify(text, List.copyOf(byName.keySet())).flatMap(result -> {
            Category known = byName.get(result.category());
            if (known != null) return Optional.of(known);
            if (result.newlyProposed()) return Optional.of(creator.apply(result.category()));
            return Optional.empty();
        });
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
