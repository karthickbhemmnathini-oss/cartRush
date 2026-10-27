package com.example.cart_rush.service.assistant;

import com.google.genai.Client;
import com.google.genai.ResponseStream;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.Part;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Component
public class GoogleGenAiSdkClient implements GeminiClient {

    private final AssistantProperties properties;

    public GoogleGenAiSdkClient(AssistantProperties properties) {
        this.properties = properties;
    }

    @Override
    public String generateIntentJson(String message, String systemInstruction) {
        try (Client client = Client.builder()
                .apiKey(properties.getGeminiApiKey())
                .httpOptions(HttpOptions.builder()
                        .timeout(Math.toIntExact(properties.getReadTimeout().toMillis()))
                        .build())
                .build()) {
            GenerateContentConfig config = GenerateContentConfig.builder()
                    .systemInstruction(Content.fromParts(Part.fromText(systemInstruction)))
                    .responseMimeType("application/json")
                    .candidateCount(1)
                    .temperature(0.0f)
                    .build();
            GenerateContentResponse response = client.models.generateContent(
                    properties.getGeminiModel(), message, config);
            return response.text();
        }
    }

    @Override
    public void streamText(String systemInstruction, List<AssistantChatRequest.ChatTurn> history,
                           String prompt, Consumer<String> onToken) {
        try (Client client = newClient()) {
            List<Content> contents = new ArrayList<>();
            if (history != null) {
                history.stream().limit(AssistantChatRequest.MAX_HISTORY_MESSAGES).forEach(turn -> {
                    String role = "assistant".equalsIgnoreCase(turn.role()) ? "model" : "user";
                    contents.add(Content.fromParts(Part.fromText(turn.content())).toBuilder().role(role).build());
                });
            }
            contents.add(Content.fromParts(Part.fromText(prompt)));
            GenerateContentConfig config = GenerateContentConfig.builder()
                    .systemInstruction(Content.fromParts(Part.fromText(systemInstruction)))
                    .candidateCount(1)
                    .temperature(0.4f)
                    .build();
            try (ResponseStream<GenerateContentResponse> responseStream =
                         client.models.generateContentStream(properties.getGeminiModel(), contents, config)) {
                for (GenerateContentResponse response : responseStream) {
                    String text = response.text();
                    if (text != null && !text.isEmpty()) {
                        onToken.accept(text);
                    }
                }
            }
        }
    }

    private Client newClient() {
        return Client.builder()
                .apiKey(properties.getGeminiApiKey())
                .httpOptions(HttpOptions.builder()
                        .timeout(Math.toIntExact(properties.getReadTimeout().toMillis()))
                        .build())
                .build();
    }
}