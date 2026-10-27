package com.example.cart_rush.service.assistant;

import java.math.BigDecimal;
import java.util.List;

public record ShoppingIntent(
        String category,
        String brand,
        List<String> keywords,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        Integer minRating,
        String sort,
        Boolean waterproof) {

    public ShoppingIntent {
        keywords = keywords == null ? List.of() : List.copyOf(keywords);
    }

    public static ShoppingIntent empty() {
        return new ShoppingIntent(null, null, List.of(), null, null, null, null, null);
    }
}