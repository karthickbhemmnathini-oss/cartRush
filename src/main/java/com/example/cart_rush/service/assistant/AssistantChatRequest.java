package com.example.cart_rush.service.assistant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;

import java.util.List;

public record AssistantChatRequest(
        @NotBlank @Size(max = 1000) String message,
        @Size(max = MAX_HISTORY_MESSAGES) List<@Valid ChatTurn> history,
        Long userId) {

    public static final int MAX_HISTORY_MESSAGES = 20;

    public AssistantChatRequest(String message, List<ChatTurn> history) {
        this(message, history, null);
    }

    public AssistantChatRequest {
        history = history == null ? List.of() : List.copyOf(history.stream()
                .filter(turn -> turn != null
                        && turn.role() != null && !turn.role().isBlank()
                        && turn.content() != null && !turn.content().isBlank())
                .toList());
    }

    public record ChatTurn(
            @NotBlank @Size(max = 1000) String role,
            @NotBlank @Size(max = 2000) String content) {
    }
}