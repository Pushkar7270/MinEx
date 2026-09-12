package com.minex.backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Binds the `app.*` keys from application.yml (PRD §4/§9 tunables). */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app")
public class AppProps {
    private final Jwt jwt = new Jwt();
    private final Files files = new Files();
    private final Extraction extraction = new Extraction();
    private final Minio minio = new Minio();
    private final Llm llm = new Llm();
    private final RbacRules rbac = new RbacRules();

    @Getter
    @Setter
    public static class Jwt {
        private String secret;
        private long accessTokenTtlMinutes = 480; // 8h: survives a full demo day
        private long refreshTokenTtlDays = 7;
    }

    @Getter
    @Setter
    public static class Files {
        private long maxSizeBytes = 209715200L;
        private String allowedMimeTypes = "application/pdf";
    }

    @Getter
    @Setter
    public static class Extraction {
        private double confidenceThreshold = 0.75;
        /** Below this, human verification is mandatory (red priority). */
        private double lowConfidenceThreshold = 0.5;
    }

    @Getter
    @Setter
    public static class Minio {
        private String endpoint;
        private String accessKey;
        private String secretKey;
        private String bucket;
        private boolean secure;
    }

    @Getter
    @Setter
    public static class Llm {
        /** none | ollama (local Llama) | openai-compatible (hosted, e.g. Kimi). */
        private String provider = "none";
        private String endpoint = "http://localhost:11434";
        private String model = "llama3.1:8b";
        private String apiKey = "";
        private double minConfidence = 0.7;
        private long timeoutSeconds = 60;
    }

    /**
     * Role privileges are derived from a role's numeric rank (data, not names)
     * against these thresholds. Change the taxonomy in the DB and tune these —
     * no code edits needed.
     */
    @Getter
    @Setter
    public static class RbacRules {
        /** May submit corrections / upload documents. */
        private int correctRank = 10;
        /** May approve or reject submitted figures. */
        private int reviewRank = 20;
        /** May publish approved figures to dashboards. */
        private int publishRank = 30;
        /** May change other users' roles. */
        private int adminRank = 100;
    }
}
