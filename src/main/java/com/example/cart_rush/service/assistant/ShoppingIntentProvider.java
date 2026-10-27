package com.example.cart_rush.service.assistant;

import java.util.List;

public interface ShoppingIntentProvider {
    AssistantAnalysis analyze(String message, List<AssistantChatRequest.ChatTurn> history);

    default ShoppingIntent extractIntent(String message) {
        return analyze(message, List.of()).filters();
    }
}