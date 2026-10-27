package com.example.cart_rush.service.assistant;

import com.example.cart_rush.entity.Category;
import com.example.cart_rush.entity.Product;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ShoppingIntentFallbackParser {

    private static final Pattern PRICE = Pattern.compile("(?:\\$|₹|rs\\.?\\s*)?([0-9]+(?:\\.[0-9]{1,2})?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern RATING = Pattern.compile("([1-5])\\s*(?:star|stars|rating)", Pattern.CASE_INSENSITIVE);
    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "and", "any", "are", "below", "best", "cheap", "cheapest", "find", "for", "good",
            "have", "i", "in", "is", "it", "me", "need", "of", "on", "please", "show", "some", "the",
            "to", "under", "want", "with", "without", "not", "waterproof", "rating", "rated", "star", "stars",
            "product", "products", "item", "items", "stock", "available", "availability",
                "price", "sort", "by", "ascending", "descending", "high", "low", "at", "least", "above", "over",
                "something", "anything", "thing", "things", "trip", "travel", "stuff",
                "gift", "gifts", "present", "presents", "occasion", "birthday", "anniversary",
                "suggest", "suggestion", "recommend", "recommendation", "give", "see", "get", "my",
                "girl", "girls", "boy", "boys", "girlfriend", "boyfriend", "friend", "friends",
                "wife", "husband", "mom", "mother", "dad", "father", "sister", "brother", "her", "him"
    );

    public ShoppingIntent parse(String message, List<Product> catalogue) {
        String normalized = message.toLowerCase(Locale.ROOT);
        String category = catalogue.stream()
                .map(Product::getCategory)
                .filter(java.util.Objects::nonNull)
                .map(Category::getName)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .filter(name -> containsPhrase(normalized, name))
                .findFirst().orElse(null);
        String brand = catalogue.stream()
                .map(Product::getBrand)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .filter(value -> containsPhrase(normalized, value))
                .findFirst().orElse(null);

        BigDecimal minPrice = null;
        BigDecimal maxPrice = null;
        if (normalized.matches(".*\\b(under|below|less than|at most)\\b.*")) {
            maxPrice = firstPrice(normalized);
        } else if (normalized.matches(".*\\b(over|above|more than|at least)\\b.*")) {
            minPrice = firstPrice(normalized);
        } else {
            Matcher matcher = Pattern.compile("(?:between|from)\\s+" + PRICE.pattern() + "\\s+(?:and|to)\\s+" + PRICE.pattern(), Pattern.CASE_INSENSITIVE).matcher(normalized);
            if (matcher.find()) {
                minPrice = new BigDecimal(matcher.group(1));
                maxPrice = new BigDecimal(matcher.group(2));
            }
        }

        Matcher ratingMatcher = RATING.matcher(normalized);
        Integer rating = ratingMatcher.find() ? Integer.valueOf(ratingMatcher.group(1)) : null;
        String sort = normalized.contains("cheapest") || normalized.contains("lowest price") || normalized.contains("price low")
                ? "PRICE_ASC"
                : normalized.contains("most expensive") || normalized.contains("highest price") || normalized.contains("price high")
                ? "PRICE_DESC"
                : normalized.contains("top rated") || normalized.contains("highest rated") ? "RATING_DESC" : null;
        Boolean waterproof = normalized.contains("not waterproof") ? Boolean.FALSE
            : normalized.contains("waterproof") ? Boolean.TRUE : null;

        List<String> keywords = new ArrayList<>();
        for (String token : normalized.replaceAll("[^\\p{L}\\p{N} ]", " ").split("\\s+")) {
                boolean categoryOrBrand = (category != null && containsPhrase(token, category))
                    || (brand != null && containsPhrase(token, brand));
            if (token.length() > 2 && !STOP_WORDS.contains(token) && !token.matches("\\d+") && !categoryOrBrand) {
                keywords.add(token);
            }
        }
        return new ShoppingIntent(category, brand, keywords, minPrice, maxPrice, rating, sort, waterproof);
    }

    private static BigDecimal firstPrice(String message) {
        Matcher matcher = PRICE.matcher(message);
        return matcher.find() ? new BigDecimal(matcher.group(1)) : null;
    }

    private static boolean containsPhrase(String message, String phrase) {
        return message.contains(phrase.toLowerCase(Locale.ROOT));
    }
}