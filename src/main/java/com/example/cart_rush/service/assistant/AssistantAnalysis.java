package com.example.cart_rush.service.assistant;

public record AssistantAnalysis(
        AssistantIntent intent,
        ShoppingIntent filters,
        String clarification) {

    public AssistantAnalysis {
        intent = intent == null ? AssistantIntent.SHOPPING_VAGUE : intent;
        filters = filters == null ? ShoppingIntent.empty() : filters;
    }
}