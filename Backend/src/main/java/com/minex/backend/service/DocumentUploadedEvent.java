package com.minex.backend.service;

import java.util.UUID;

/** Published when a raw file lands in storage; consumed by the extraction worker. */
public record DocumentUploadedEvent(UUID documentId) {
}
