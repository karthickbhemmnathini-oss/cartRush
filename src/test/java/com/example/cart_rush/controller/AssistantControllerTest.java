package com.example.cart_rush.controller;

import com.example.cart_rush.service.assistant.AssistantAnalysis;
import com.example.cart_rush.service.assistant.AssistantChatRequest;
import com.example.cart_rush.service.assistant.AssistantChatResponse;
import com.example.cart_rush.service.assistant.AssistantIntent;
import com.example.cart_rush.service.assistant.AssistantStreamEvent;
import com.example.cart_rush.service.assistant.GeminiClient;
import com.example.cart_rush.service.assistant.ShoppingAssistantService;
import com.example.cart_rush.service.assistant.ShoppingIntent;
import com.example.cart_rush.repository.ProductRepository;
import com.example.cart_rush.repository.ReviewRepository;
import com.example.cart_rush.service.assistant.ShoppingIntentFallbackParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AssistantControllerTest {

    @Test
    void streamEndpointReturnsTypedSseEventsForGreeting() throws Exception {
        ProductRepository products = Mockito.mock(ProductRepository.class);
        ReviewRepository reviews = Mockito.mock(ReviewRepository.class);
        com.example.cart_rush.service.assistant.ShoppingIntentProvider intentProvider = Mockito.mock(
                com.example.cart_rush.service.assistant.ShoppingIntentProvider.class);
        GeminiClient geminiClient = Mockito.mock(GeminiClient.class);
        ShoppingAssistantService service = new ShoppingAssistantService(products, reviews,
                Mockito.mock(com.example.cart_rush.repository.CartRepository.class),
                Mockito.mock(com.example.cart_rush.repository.CartItemRepository.class),
                Mockito.mock(com.example.cart_rush.repository.OrderRepository.class),
                Mockito.mock(com.example.cart_rush.repository.OrderItemRepository.class),
                intentProvider, geminiClient, new ShoppingIntentFallbackParser());
        when(intentProvider.analyze("hi", List.of()))
                .thenReturn(new AssistantAnalysis(AssistantIntent.GREETING, ShoppingIntent.empty(), null));
        Mockito.doAnswer(invocation -> {
            java.util.function.Consumer<String> onToken = invocation.getArgument(3);
            onToken.accept("Hi! What are you shopping for?");
            return null;
        }).when(geminiClient).streamText(Mockito.anyString(), Mockito.anyList(), Mockito.eq("hi"), Mockito.any());

        Executor directExecutor = Runnable::run;
        AssistantController controller = new AssistantController(service, directExecutor);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();
        String body = mvc.perform(post("/api/assistant/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("{\"message\":\"hi\",\"history\":[]}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertTrue(body.contains("event:status"));
        assertTrue(body.contains("event:token"));
        assertTrue(body.contains("event:final"));
        assertTrue(body.contains("GREETING"));
        assertTrue(body.contains("Hi! What are you shopping for?"));
    }

    @Test
    void synchronousEndpointRetainsLegacyResponseFields() throws Exception {
        ProductRepository products = Mockito.mock(ProductRepository.class);
        ReviewRepository reviews = Mockito.mock(ReviewRepository.class);
        com.example.cart_rush.service.assistant.ShoppingIntentProvider intentProvider = Mockito.mock(
                com.example.cart_rush.service.assistant.ShoppingIntentProvider.class);
        GeminiClient geminiClient = Mockito.mock(GeminiClient.class);
        ShoppingAssistantService service = new ShoppingAssistantService(products, reviews,
                Mockito.mock(com.example.cart_rush.repository.CartRepository.class),
                Mockito.mock(com.example.cart_rush.repository.CartItemRepository.class),
                Mockito.mock(com.example.cart_rush.repository.OrderRepository.class),
                Mockito.mock(com.example.cart_rush.repository.OrderItemRepository.class),
                intentProvider, geminiClient, new ShoppingIntentFallbackParser());
        when(intentProvider.analyze("hi", List.of()))
                .thenReturn(new AssistantAnalysis(AssistantIntent.GREETING, ShoppingIntent.empty(), null));
        Mockito.doAnswer(invocation -> {
            java.util.function.Consumer<String> onToken = invocation.getArgument(3);
            onToken.accept("Hello!");
            return null;
        }).when(geminiClient).streamText(Mockito.anyString(), Mockito.anyList(), Mockito.eq("hi"), Mockito.any());

        AssistantController controller = new AssistantController(service, Runnable::run);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();
        String body = mvc.perform(post("/api/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hi\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNodeCheck.assertLegacyFields(body);
    }

    private static class JsonNodeCheck {
        static void assertLegacyFields(String body) throws Exception {
            var json = new ObjectMapper().readTree(body);
            assertTrue(json.has("reply"));
            assertTrue(json.has("products"));
            assertTrue(json.has("filters"));
            assertTrue(json.has("source"));
        }
    }
}
