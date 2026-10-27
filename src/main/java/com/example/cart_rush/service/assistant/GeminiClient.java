package com.example.cart_rush.service.assistant;

import java.util.List;
import java.util.function.Consumer;

public interface GeminiClient {
    String generateIntentJson(String message, String systemInstruction);

    void streamText(String systemInstruction, List<AssistantChatRequest.ChatTurn> history,
                    String prompt, Consumer<String> onToken);
}