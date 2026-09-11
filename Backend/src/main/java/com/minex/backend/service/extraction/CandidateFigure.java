package com.minex.backend.service.extraction;

import java.util.List;
import java.util.Locale;

/** A numeric figure spotted in running text, before categorization. */
public record CandidateFigure(
        String fieldName,
        Double value,
        String unit,
        String context,
        double confidence) {

    public String normalizedUnit() {
        if (unit == null) return null;
        return switch (unit.toLowerCase(Locale.ROOT)) {
            case "mt", "million tonnes", "million tonne" -> "MT";
            case "crore", "rs crore", "inr crore" -> "INR crore";
            case "lakh", "rs lakh", "inr lakh" -> "INR lakh";
            case "%", "percent", "per cent" -> "%";
            case "rs", "rs.", "inr" -> "INR";
            default -> unit;
        };
    }
}
