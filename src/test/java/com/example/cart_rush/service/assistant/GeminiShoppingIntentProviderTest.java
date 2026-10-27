package com.example.cart_rush.service.assistant;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GeminiShoppingIntentProviderTest {

    @Test
    void sendsGeminiRequestAndParsesStructuredIntent() {
        AssistantProperties properties = properties();
        GeminiClient geminiClient = Mockito.mock(GeminiClient.class);
        when(geminiClient.generateIntentJson(Mockito.contains("Latest user message:\ntrail shoes under 150"), Mockito.anyString()))
            .thenReturn("""
            {"intent":"SHOPPING_CLEAR","clarification":null,"filters":{"category":null,"brand":"NorthPeak","keywords":["trail"],"minPrice":null,"maxPrice":150,"minRating":4,"sort":"PRICE_ASC","waterproof":true}}
                """);

        GeminiShoppingIntentProvider provider = new GeminiShoppingIntentProvider(
            geminiClient, properties, new ObjectMapper());
        AssistantAnalysis analysis = provider.analyze("trail shoes under 150", List.of());
        ShoppingIntent intent = analysis.filters();

        assertEquals(AssistantIntent.SHOPPING_CLEAR, analysis.intent());
        assertEquals("NorthPeak", intent.brand());
        assertEquals(new BigDecimal("150"), intent.maxPrice());
        assertEquals("trail", intent.keywords().get(0));
        verify(geminiClient).generateIntentJson(Mockito.contains("Latest user message:\ntrail shoes under 150"), Mockito.anyString());
    }

    @Test
    void classifiesGreetingWithoutFilters() {
        AssistantProperties properties = properties();
        GeminiClient geminiClient = Mockito.mock(GeminiClient.class);
        when(geminiClient.generateIntentJson(Mockito.contains("Latest user message:\nhi"), Mockito.anyString()))
            .thenReturn("{\"intent\":\"GREETING\",\"clarification\":null,\"filters\":{}}");
        GeminiShoppingIntentProvider provider = new GeminiShoppingIntentProvider(geminiClient, properties, new ObjectMapper());

        AssistantAnalysis analysis = provider.analyze("hi", List.of());

        assertEquals(AssistantIntent.GREETING, analysis.intent());
        assertEquals(List.of(), analysis.filters().keywords());
    }

    @Test
    void sdkSeamForwardsStreamingTextChunks() {
        GeminiClient geminiClient = Mockito.mock(GeminiClient.class);
        Mockito.doAnswer(invocation -> {
            java.util.function.Consumer<String> consumer = invocation.getArgument(3);
            consumer.accept("Hello ");
            consumer.accept("there!");
            return null;
        }).when(geminiClient).streamText(Mockito.anyString(), Mockito.anyList(), Mockito.anyString(), Mockito.any());
        List<String> received = new ArrayList<>();
        geminiClient.streamText("system", List.of(), "prompt", received::add);

        assertEquals(List.of("Hello ", "there!"), received);
    }

    @Test
    void rejectsMissingApiKeyWithoutMakingARequest() {
        AssistantProperties properties = properties();
        properties.setGeminiApiKey(" ");
        GeminiClient geminiClient = Mockito.mock(GeminiClient.class);
        GeminiShoppingIntentProvider provider = new GeminiShoppingIntentProvider(
            geminiClient, properties, new ObjectMapper());

        assertThrows(AssistantProviderException.class, () -> provider.extractIntent("shoes"));
        Mockito.verifyNoInteractions(geminiClient);
    }

    @Test
    void rejectsEmptyGeminiResponse() {
        AssistantProperties properties = properties();
        GeminiClient geminiClient = Mockito.mock(GeminiClient.class);
        when(geminiClient.generateIntentJson(Mockito.contains("Latest user message:\nshoes"), Mockito.anyString())).thenReturn(" ");
        GeminiShoppingIntentProvider provider = new GeminiShoppingIntentProvider(
            geminiClient, properties, new ObjectMapper());

        assertThrows(AssistantProviderException.class, () -> provider.extractIntent("shoes"));
        verify(geminiClient).generateIntentJson(Mockito.contains("Latest user message:\nshoes"), Mockito.anyString());
    }

    @Test
    void rejectsMalformedIntentJson() {
        AssistantProperties properties = properties();
        GeminiClient geminiClient = Mockito.mock(GeminiClient.class);
        when(geminiClient.generateIntentJson(Mockito.contains("Latest user message:\nshoes"), Mockito.anyString())).thenReturn("not-json");
        GeminiShoppingIntentProvider provider = new GeminiShoppingIntentProvider(
            geminiClient, properties, new ObjectMapper());

        assertThrows(AssistantProviderException.class, () -> provider.extractIntent("shoes"));
        verify(geminiClient).generateIntentJson(Mockito.contains("Latest user message:\nshoes"), Mockito.anyString());
    }

    private static AssistantProperties properties() {
        AssistantProperties properties = new AssistantProperties();
        properties.setGeminiModel("gemini-test");
        properties.setGeminiApiKey("test-key");
        return properties;
    }
}