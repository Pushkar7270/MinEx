package com.minex.backend.service.extraction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minex.backend.config.AppProps;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Hosted-model segregation over any OpenAI-compatible {@code /chat/completions}
 * endpoint — e.g. Moonshot Kimi ({@code https://api.moonshot.ai/v1},
 * model {@code kimi-k2-0711-preview}) via {@code APP_LLM_API_KEY}.
 * Enable only with explicit consent: document text leaves the machine.
 */
@Component
@ConditionalOnProperty(name = "app.llm.provider", havingValue = "openai-compatible")
public class OpenAiCompatibleLlmClassifier implements LlmClassifier {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleLlmClassifier.class);

    private final RestClient client;
    private final AppProps props;
    private final ObjectMapper mapper = new ObjectMapper();

    public OpenAiCompatibleLlmClassifier(AppProps props) {
        this.props = props;
        this.client = RestClient.builder()
                .baseUrl(props.getLlm().getEndpoint())
                .defaultHeader("Authorization", "Bearer " + props.getLlm().getApiKey())
                .build();
    }

    @Override
    public Optional<LlmResult> classify(String text, List<String> categories) {
        String prompt = "You sort mining-report figures into categories. Existing categories: "
                + String.join(", ", categories)
                + ". If one fits, use it with \"new\": false. Otherwise propose a concise new "
                + "category name (2-4 words) with \"new\": true. Reply with JSON only: "
                + "{\"category\": \"<name>\", \"confidence\": 0.0-1.0, \"new\": true/false}. Figure: " + text;
        try {
            String body = client.post().uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("model", props.getLlm().getModel(), "temperature", 0,
                            "messages", List.of(Map.of("role", "user", "content", prompt))))
                    .retrieve().body(String.class);
            JsonNode root = mapper.readTree(body);
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            int start = content.indexOf('{');
            int end = content.lastIndexOf('}');
            if (start < 0 || end <= start) return Optional.empty();
            JsonNode answer = mapper.readTree(content.substring(start, end + 1));
            return LlmClassifier.accept(answer, categories, props.getLlm().getMinConfidence());
        } catch (Exception ex) {
            log.warn("Hosted LLM classification failed, leaving figure for human review: {}", ex.getMessage());
            return Optional.empty();
        }
    }
}
