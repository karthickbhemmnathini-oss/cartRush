package com.example.cart_rush.service.assistant;

import com.example.cart_rush.entity.Category;
import com.example.cart_rush.entity.Product;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShoppingIntentFallbackParserTest {

    private final ShoppingIntentFallbackParser parser = new ShoppingIntentFallbackParser();

    @Test
    void extractsCatalogueFiltersAndSort() {
        Product product = product("NorthPeak", "Footwear");

        ShoppingIntent intent = parser.parse(
                "waterproof NorthPeak footwear under ₹150 with 4 stars cheapest", List.of(product));

        assertEquals("Footwear", intent.category());
        assertEquals("NorthPeak", intent.brand());
        assertEquals(new BigDecimal("150"), intent.maxPrice());
        assertEquals(4, intent.minRating());
        assertEquals("PRICE_ASC", intent.sort());
        assertEquals(Boolean.TRUE, intent.waterproof());
        assertTrue(intent.keywords().isEmpty());
    }

    @Test
    void recognizesExplicitNegativeWaterproofFilter() {
        ShoppingIntent intent = parser.parse("show shoes that are not waterproof", List.of());

        assertEquals(Boolean.FALSE, intent.waterproof());
        assertTrue(intent.keywords().contains("shoes"));
        assertFalse(intent.keywords().contains("waterproof"));
    }

    @Test
    void extractsPriceRangeAndTextKeyword() {
        ShoppingIntent intent = parser.parse("find hiking gear between 100 and 250", List.of());

        assertEquals(new BigDecimal("100"), intent.minPrice());
        assertEquals(new BigDecimal("250"), intent.maxPrice());
        assertTrue(intent.keywords().contains("hiking"));
        assertTrue(intent.keywords().contains("gear"));
    }

    @Test
    void treatsGenericProductStockWordsAsFiltersNotSearchKeywords() {
        ShoppingIntent intent = parser.parse("in stock products under 1000", List.of());

        assertEquals(new BigDecimal("1000"), intent.maxPrice());
        assertTrue(intent.keywords().isEmpty());
    }

    @Test
    void giftPurposeAndRecipientWordsAreNotSearchKeywords() {
        ShoppingIntent intent = parser.parse("i want to see product to gift my girl friend", List.of());

        assertTrue(intent.keywords().isEmpty());
    }

    private static Product product(String brand, String categoryName) {
        Product product = new Product();
        product.setBrand(brand);
        product.setCategory(new Category(categoryName));
        return product;
    }
}