package com.minex.backend.service.extraction;

import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Default: LLM segregation off (rule-based only). Active unless a provider is set. */
@Component
@ConditionalOnProperty(name = "app.llm.provider", havingValue = "none", matchIfMissing = true)
public class NoOpLlmClassifier implements LlmClassifier {
    @Override
    public Optional<LlmResult> classify(String text, List<String> categories) {
        return Optional.empty();
    }
}
