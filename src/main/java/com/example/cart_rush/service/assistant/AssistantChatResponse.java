package com.example.cart_rush.service.assistant;

import java.math.BigDecimal;
import java.util.List;

public record AssistantChatResponse(
        String reply,
        List<ProductMatch> products,
        ShoppingIntent filters,
        String source,
        boolean aiUsedForIntent,
        boolean replyGeneratedByAi,
                String aiFallbackReason,
                List<String> relaxedFilters,
                CartView cart,
                List<OrderView> orders,
                List<String> categories,
                List<ReviewSummary> reviews) {

            public AssistantChatResponse(String reply, List<ProductMatch> products, ShoppingIntent filters,
                             String source, boolean aiUsedForIntent, boolean replyGeneratedByAi,
                             String aiFallbackReason, List<String> relaxedFilters,
                             CartView cart, List<OrderView> orders) {
                this(reply, products, filters, source, aiUsedForIntent, replyGeneratedByAi,
                    aiFallbackReason, relaxedFilters, cart, orders, List.of(), List.of());
            }

        public AssistantChatResponse(String reply, List<ProductMatch> products, ShoppingIntent filters,
                                                                 String source, boolean aiUsedForIntent, boolean replyGeneratedByAi,
                                                                 String aiFallbackReason, List<String> relaxedFilters) {
                this(reply, products, filters, source, aiUsedForIntent, replyGeneratedByAi, aiFallbackReason,
                        relaxedFilters, null, List.of());
        }

        public AssistantChatResponse(String reply, List<ProductMatch> products, ShoppingIntent filters,
                                                                 String source, boolean aiUsedForIntent, boolean replyGeneratedByAi,
                                                                 String aiFallbackReason) {
                this(reply, products, filters, source, aiUsedForIntent, replyGeneratedByAi, aiFallbackReason, List.of());
        }

        public AssistantChatResponse {
                products = products == null ? List.of() : List.copyOf(products);
                relaxedFilters = relaxedFilters == null ? List.of() : List.copyOf(relaxedFilters);
                orders = orders == null ? List.of() : List.copyOf(orders);
                categories = categories == null ? List.of() : List.copyOf(categories);
                reviews = reviews == null ? List.of() : List.copyOf(reviews);
        }

    public record CartView(List<CartEntry> items, BigDecimal total) {
        public CartView {
            items = items == null ? List.of() : List.copyOf(items);
        }
    }

    public record CartEntry(Long productId, String name, String brand, String imageUrl,
                            BigDecimal price, Integer quantity, BigDecimal lineTotal) {
    }

    public record OrderView(Long id, String status, java.time.LocalDateTime createdAt,
                            List<OrderEntry> items, BigDecimal total) {
        public OrderView {
            items = items == null ? List.of() : List.copyOf(items);
        }
    }

    public record OrderEntry(Long productId, String name, Integer quantity, BigDecimal unitPrice) {
    }

    public record ReviewSummary(Long productId, String productName, double averageRating,
                                long ratingCount, List<ReviewEntry> reviews) {
        public ReviewSummary {
            reviews = reviews == null ? List.of() : List.copyOf(reviews);
        }
    }

    public record ReviewEntry(Integer rating, String comment) {
    }

    public record ProductMatch(
            Long id,
            String name,
            String description,
            BigDecimal price,
            Integer stock,
            String brand,
            Boolean waterproof,
            CategoryMatch category,
            String imageUrl,
            double averageRating,
            long ratingCount) {
    }

    public record CategoryMatch(Long id, String name) {
    }
}