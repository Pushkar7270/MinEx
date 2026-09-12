package com.minex.backend.service.extraction;

import static org.junit.jupiter.api.Assertions.*;

import com.minex.backend.domain.Category;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ExtractionLogicTest {

    private final FigureExtractor figures = new FigureExtractor();
    private final BoilerplateClassifier boilerplate = new BoilerplateClassifier();

    @Test
    void extractsLabeledFiguresWithUnits() {
        var blocks = List.of(new PageBlock(1, "text",
                "Coal production 773.8 MT\nRevenue expenditure: 12,450 crore\nRandom words here", 0.95));
        var found = figures.extract(blocks);
        assertEquals(2, found.size());
        assertEquals("Coal production", found.get(0).fieldName());
        assertEquals(773.8, found.get(0).value());
        assertEquals("MT", found.get(0).normalizedUnit());
        assertEquals("INR crore", found.get(1).normalizedUnit());
    }

    @Test
    void skipsNumberOnlyLines() {
        var blocks = List.of(new PageBlock(1, "text", "12345\n2024", 0.95));
        assertTrue(figures.extract(blocks).isEmpty());
    }

    @Test
    void extractsTableRowsAndSkipsHeaders() {
        var blocks = List.of(new PageBlock(null, "table",
                "Metric | Value | Unit\nCoal production | 773.8 | MT\nDispatch | 700.5 | MT", 0.85));
        var found = figures.extract(blocks);
        assertEquals(2, found.size());
        assertEquals("Coal production", found.get(0).fieldName());
        assertEquals(773.8, found.get(0).value());
        assertEquals("MT", found.get(0).normalizedUnit());
    }

    @Test
    void detectsFiscalPeriod() {
        var blocks = List.of(new PageBlock(1, "text", "Annual Report FY 2024-25 highlights", 0.95));
        assertEquals("2024-25", figures.detectPeriod(blocks));
    }

    @Test
    void flagsDenylistAndRepeatedHeaders() {
        assertTrue(boilerplate.isBoilerplate("This is an advertisement page", Map.of(), Map.of()));
        assertTrue(boilerplate.isBoilerplate("x", Map.of(), Map.of())); // too short
        var freq = Map.of("CIL Annual Report", 5);
        assertTrue(boilerplate.isBoilerplate("CIL Annual Report\nSome body text here", freq, Map.of()));
        assertFalse(boilerplate.isBoilerplate("Coal production rose to 773.8 MT in 2024-25", Map.of(), Map.of()));
    }

    @Test
    void categorizesIntoSeededCategories() {
        var cats = Map.of(
                "Annual Yield", cat("Annual Yield"),
                "Budget", cat("Budget"),
                "Annual Expense", cat("Annual Expense"),
                "Safety Incidents", cat("Safety Incidents"),
                "Geological Survey", cat("Geological Survey"));
        var categorizer = new CategorizerService(cats, (text, categories) -> java.util.Optional.empty());
        assertEquals("Annual Yield",
                categorizer.categorize("Coal production and offtake dispatch rose").orElseThrow().getName());
        assertEquals("Budget",
                categorizer.categorize("Capital expenditure budget outlay provision").orElseThrow().getName());
        assertEquals("Safety Incidents",
                categorizer.categorize("Mine safety accident fatality compensation").orElseThrow().getName());
        assertTrue(categorizer.categorize("Completely unrelated lorem ipsum dolor").isEmpty());
    }

    @Test
    void llmFallbackCoversTiedCategories() {
        var cats = Map.of("Annual Yield", cat("Annual Yield"), "Budget", cat("Budget"));
        // "production" (Annual Yield) and "budget" (Budget) tie -> rule unsure -> LLM decides.
        LlmClassifier stub = (text, categories) ->
                java.util.Optional.of(new LlmClassifier.LlmResult("Budget", 0.9));
        var withLlm = new CategorizerService(cats, stub);
        assertEquals("Budget", withLlm.categorize("Production budget").orElseThrow().getName());
        // Low-confidence LLM answer is rejected by the impl -> human review.
        LlmClassifier unsure = (text, categories) -> java.util.Optional.empty();
        var withUnsureLlm = new CategorizerService(cats, unsure);
        assertTrue(withUnsureLlm.categorize("Production budget").isEmpty());
    }

    @Test
    void singleStrongKeywordIsTrustedWithoutLlm() {
        var cats = Map.of(
                "Annual Yield", cat("Annual Yield"),
                "Budget", cat("Budget"),
                "Annual Expense", cat("Annual Expense"),
                "Safety Incidents", cat("Safety Incidents"),
                "Geological Survey", cat("Geological Survey"));
        LlmClassifier noop = (text, categories) -> java.util.Optional.empty();
        var c = new CategorizerService(cats, noop);
        // A single decisive Annual Yield keyword ("output") is now trusted directly.
        assertEquals("Annual Yield", c.categorize("Output value 42").orElseThrow().getName());
    }

    @Test
    void taxonomyExtendsItselfOnConfidentNewProposal() {
        var cats = new java.util.HashMap<>(Map.of("Annual Yield", cat("Annual Yield")));
        LlmClassifier proposer = (text, categories) -> java.util.Optional.of(
                new LlmClassifier.LlmResult("Community Welfare", 0.9, true));
        var ext = new CategorizerService(cats, proposer);
        var created = ext.categorize("Scholarships awarded 120").orElseThrow();
        assertEquals("Community Welfare", created.getName());
        assertTrue(cats.containsKey("Community Welfare"));
    }

    @Test
    void unknownNameWithoutNewFlagIsRejected() {
        var cats = Map.of("Annual Yield", cat("Annual Yield"));
        LlmClassifier liar = (text, categories) -> java.util.Optional.of(
                new LlmClassifier.LlmResult("Nonsense Bucket", 0.9, false));
        var ext = new CategorizerService(cats, liar);
        assertTrue(ext.categorize("Scholarships awarded 120").isEmpty());
    }

    @Test
    void categoryNamesAreNormalized() {
        assertEquals("Community Welfare", LlmClassifier.normalize("  community   WELFARE "));
    }

    @Test
    void confidenceFollowsSpecFormula() {
        // Single PDF-text figure with a unit: C_block(0.95) × W_unit(1) × W_label(1).
        var one = figures.extract(List.of(new PageBlock(1, "text", "Coal production 773.8 MT", 0.95)));
        assertEquals(1, one.size());
        assertEquals(0.95, one.get(0).confidence(), 1e-9);

        // Second figure has no unit while the first did → r_unit = 0.5 → W_unit = 0.5.
        var two = figures.extract(List.of(new PageBlock(1, "text",
                "Coal production 773.8 MT\nDispatch 700.5", 0.95)));
        assertEquals(2, two.size());
        assertEquals(0.95, two.get(0).confidence(), 1e-9);
        assertEquals(0.48, two.get(1).confidence(), 1e-9); // 0.95 * 0.5 * 1.0
    }

    @Test
    void confidencePenalisesLongLabels() {
        var blocks = List.of(new PageBlock(1, "text",
                "Coal output 100 MT\n"
                        + "Very long descriptive production of raw coal from all subsidiaries during year 200 MT", 0.95));
        var found = figures.extract(blocks);
        assertEquals(2, found.size());
        assertEquals(0.95, found.get(0).confidence(), 1e-9);
        // avg label length ≈ 44.5 / this 78 → W_label ≈ 0.5705 → 0.95 * 1 * 0.5705 ≈ 0.54
        assertEquals(0.54, found.get(1).confidence(), 0.01);
    }

    @Test
    void tableFigureUsesBlockConfidence() {
        var found = figures.extract(List.of(new PageBlock(null, "table", "CIL | 100 | MT", 0.85)));
        assertEquals(1, found.size());
        assertEquals(0.85, found.get(0).confidence(), 1e-9);
    }

    @Test
    void entropyFallsBackWhenCategoriesTie() {
        var cats = Map.of(
                "Annual Yield", cat("Annual Yield"),
                "Budget", cat("Budget"),
                "Annual Expense", cat("Annual Expense"),
                "Safety Incidents", cat("Safety Incidents"),
                "Geological Survey", cat("Geological Survey"));
        // "production" → Annual Yield, "budget" → Budget: a 1–1 tie → high entropy →
        // the answer must come from the LLM (Safety is unreachable via the rules).
        LlmClassifier stub = (text, categories) ->
                java.util.Optional.of(new LlmClassifier.LlmResult("Safety Incidents", 0.9));
        var c = new CategorizerService(cats, stub);
        assertEquals("Safety Incidents", c.categorize("production budget").orElseThrow().getName());
    }

    private static Category cat(String name) {
        Category c = new Category();
        c.setName(name);
        return c;
    }
}
