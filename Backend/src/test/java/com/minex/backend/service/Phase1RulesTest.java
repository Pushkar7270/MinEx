package com.minex.backend.service;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class Phase1RulesTest {

    @Test
    void mimeAndExtensionMapping() {
        assertEquals("application/pdf", DocumentService.mimeFor("report.PDF"));
        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                DocumentService.mimeFor("data.xlsx"));
        assertEquals("application/zip", DocumentService.mimeFor("archive.zip"));
        assertEquals("application/octet-stream", DocumentService.mimeFor("notes.txt"));
        assertEquals("bin", DocumentService.extensionOf("README"));
        assertEquals("pdf", DocumentService.extensionOf("a.PDF"));
    }

    @Test
    void yoyDeviationMath() {
        assertEquals(0.0, ValidationService.deviation(100, 0));
        assertEquals(0.5, ValidationService.deviation(150, 100), 1e-9);
        assertTrue(ValidationService.deviation(200, 100) > ValidationService.YOY_THRESHOLD);
        assertTrue(ValidationService.deviation(105, 100) < ValidationService.YOY_THRESHOLD);
    }
}
