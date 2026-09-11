package com.minex.backend.service.extraction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minex.backend.config.AppProps;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Llama (or any Ollama-hosted open-weight model) segregation.
 * Needs Ollama running, e.g. {@code ollama pull llama3.1:8b && ollama serve}.
 * Local-only: no document text leaves the machine.
 */
@Component
@ConditionalOnProperty(name = "app.llm.provider", havingValue = "ollama")
public class OllamaLlmClassifier implements LlmClassifier {

    private static final Logger log = LoggerFactory.getLogger(OllamaLlmClassifier.class);
    private static final Pattern JSON = Pattern.compile("\\{[^{}]*\"category\"[^{}]*\\}");

    private final RestClient client;
    private final AppProps props;
    private final ObjectMapper mapper = new ObjectMapper();

    public OllamaLlmClassifier(AppProps props) {
        this.props = props;
        this.client = RestClient.builder()
                .baseUrl(props.getLlm().getEndpoint())
                .build();
    }

    @Override
    public Optional<LlmResult> classify(String text, List<String> categories) {
        String prompt = "Classify this mining-report figure into exactly one category from: "
                + String.join(", ", categories)
                + ". Reply with JSON only: {\"category\": \"<name>\", \"confidence\": 0.0-1.0}. Figure: " + text;
        try {
            String body = client.post().uri("/api/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("model", props.getLlm().getModel(), "stream", false,
                            "messages", List.of(Map.of("role", "user", "content", prompt))))
                    .retrieve().body(String.class);
            JsonNode root = mapper.readTree(body);
            String content = root.path("message").path("content").asText("");
            Matcher m = JSON.matcher(content);
            if (!m.find()) return Optional.empty();
            JsonNode answer = mapper.readTree(m.group());
            String category = answer.path("category").asText(null);
            double confidence = answer.path("confidence").asDouble(0);
            if (category == null || !categories.contains(category)
                    || confidence < props.getLlm().getMinConfidence()) {
                return Optional.empty();
            }
            return Optional.of(new LlmResult(category, confidence));
        } catch (Exception ex) {
            log.warn("Ollama classification failed, leaving figure for human review: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    /** Timeout guard, for tests. */
    RestClient clientWithTimeout() {
        return RestClient.builder().baseUrl(props.getLlm().getEndpoint())
                .requestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory() {
                    {
                        setConnectTimeout(Duration.ofSeconds(5));
                        setReadTimeout(Duration.ofSeconds(props.getLlm().getTimeoutSeconds()));
                    }
                }).build();
    }
}
