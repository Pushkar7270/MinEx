package com.minex.backend.service.extraction;

/** One normalized block of document text (a page, sheet, or table). */
public record PageBlock(
        Integer pageNumber,
        String blockType, // text, table, figure, header, footer
        String text,
        double confidence) {
}
