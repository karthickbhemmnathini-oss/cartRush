package com.example.cart_rush.service.assistant;

public record AssistantStreamEvent(
        String type,
        String text,
        String intent,
        AssistantChatResponse response) {

    public static AssistantStreamEvent status(String text, String intent) {
        return new AssistantStreamEvent("status", text, intent, null);
    }

    public static AssistantStreamEvent token(String text, String intent) {
        return new AssistantStreamEvent("token", text, intent, null);
    }

    public static AssistantStreamEvent finalResponse(AssistantChatResponse response, String intent) {
        return new AssistantStreamEvent("final", null, intent, response);
    }

    public static AssistantStreamEvent error(String text, String intent) {
        return new AssistantStreamEvent("error", text, intent, null);
    }
}
