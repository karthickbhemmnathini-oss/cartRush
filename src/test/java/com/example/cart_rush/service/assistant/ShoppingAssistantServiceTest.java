package com.example.cart_rush.service.assistant;

import com.example.cart_rush.entity.Cart;
import com.example.cart_rush.entity.CartItem;
import com.example.cart_rush.entity.Category;
import com.example.cart_rush.entity.Order;
import com.example.cart_rush.entity.OrderItem;
import com.example.cart_rush.entity.Product;
import com.example.cart_rush.entity.User;
import com.example.cart_rush.repository.ProductRepository;
import com.example.cart_rush.repository.ReviewRepository;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class ShoppingAssistantServiceTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private ReviewRepository reviewRepository;
    @Mock
    private com.example.cart_rush.repository.CartRepository cartRepository;
    @Mock
    private com.example.cart_rush.repository.CartItemRepository cartItemRepository;
    @Mock
    private com.example.cart_rush.repository.OrderRepository orderRepository;
    @Mock
    private com.example.cart_rush.repository.OrderItemRepository orderItemRepository;
    @Mock
    private ShoppingIntentProvider intentProvider;
    @Mock
    private GeminiClient geminiClient;

    private ShoppingAssistantService service;
    private Product trailShoes;

    @BeforeEach
    void setUp() {
        service = new ShoppingAssistantService(productRepository, reviewRepository,
            cartRepository, cartItemRepository, orderRepository, orderItemRepository,
            intentProvider, geminiClient, new ShoppingIntentFallbackParser());
        trailShoes = product(1L, "Trail Shoe", "NorthPeak", "Footwear", "Trail-ready hiking footwear", "120.00", true);
    }

    @Test
    void usesGeminiExtractedIntentAndReturnsCatalogueProductContract() throws Exception {
        when(productRepository.findAll()).thenReturn(List.of(trailShoes));
        when(reviewRepository.findAll()).thenReturn(List.of());
        when(intentProvider.analyze("waterproof trail shoes under 150", List.of()))
            .thenReturn(new AssistantAnalysis(AssistantIntent.SHOPPING_CLEAR,
                new ShoppingIntent(null, "NorthPeak", List.of("trail"), null,
                    new BigDecimal("150"), null, "PRICE_ASC", true), null));
        org.mockito.Mockito.doAnswer(invocation -> {
            java.util.function.Consumer<String> consumer = invocation.getArgument(3);
            consumer.accept("Found a trail shoe.");
            return null;
        }).when(geminiClient).streamText(org.mockito.Mockito.anyString(), org.mockito.Mockito.anyList(),
            org.mockito.Mockito.contains("Catalogue matches"), org.mockito.Mockito.any());

        AssistantChatResponse response = service.chat("waterproof trail shoes under 150");

        assertEquals("GEMINI", response.source());
        assertTrue(response.aiUsedForIntent());
        assertTrue(response.replyGeneratedByAi());
        assertEquals(1, response.products().size());
        assertEquals("Trail Shoe", response.products().get(0).name());
        assertFalse(response.reply().isBlank());
        assertEquals("GEMINI", response.source());
        assertEquals("NorthPeak", response.filters().brand());
        var json = new ObjectMapper().readTree(new ObjectMapper().writeValueAsString(response));
        assertTrue(json.has("reply"));
        assertTrue(json.has("products"));
        assertTrue(json.has("filters"));
        assertTrue(json.has("source"));
    }

    @Test
    void fallsBackToLocalParserWhenGeminiFails() {
        when(productRepository.findAll()).thenReturn(List.of(trailShoes));
        when(reviewRepository.findAll()).thenReturn(List.of());
        when(intentProvider.analyze("waterproof NorthPeak footwear under 150", List.of()))
            .thenThrow(new IllegalStateException("provider unavailable"));

        AssistantChatResponse response = service.chat("waterproof NorthPeak footwear under 150");

        assertEquals("FALLBACK", response.source());
        assertFalse(response.aiUsedForIntent());
        assertFalse(response.replyGeneratedByAi());
        assertEquals(1, response.products().size());
        assertEquals(Boolean.TRUE, response.filters().waterproof());
    }

    @Test
    void excludesOutOfStockProductsAndOnlyReturnsCatalogueRecords() {
        Product unavailable = product(2L, "Other Trail Shoe", "NorthPeak", "Footwear", "Hiking footwear", "90.00", true);
        unavailable.setStock(0);
        when(productRepository.findAll()).thenReturn(List.of(trailShoes, unavailable));
        when(reviewRepository.findAll()).thenReturn(List.of());
        when(intentProvider.analyze("hiking shoes", List.of()))
            .thenReturn(new AssistantAnalysis(AssistantIntent.SHOPPING_CLEAR,
                new ShoppingIntent(null, null, List.of("hiking"), null, null, null, null, null), null));

        AssistantChatResponse response = service.chat("hiking shoes");

        assertEquals(List.of(1L), response.products().stream().map(AssistantChatResponse.ProductMatch::id).toList());
    }

    @Test
    void greetingDoesNotQueryCatalogueAndStreamsConversationalReply() {
        when(intentProvider.analyze("hi", List.of()))
                .thenReturn(new AssistantAnalysis(AssistantIntent.GREETING, ShoppingIntent.empty(), null));
        List<AssistantStreamEvent> events = new ArrayList<>();
        org.mockito.Mockito.doAnswer(invocation -> {
            java.util.function.Consumer<String> tokenConsumer = invocation.getArgument(3);
            tokenConsumer.accept("Hi! What are you shopping for?");
            return null;
        }).when(geminiClient).streamText(org.mockito.Mockito.anyString(), org.mockito.Mockito.anyList(),
                org.mockito.Mockito.eq("hi"), org.mockito.Mockito.any());

        service.streamChat(new AssistantChatRequest("hi", List.of()), events::add);

        verify(productRepository, never()).findAll();
        assertEquals("GREETING", events.get(0).intent());
        assertEquals("token", events.get(1).type());
        assertEquals("final", events.get(2).type());
        assertEquals("GEMINI", events.get(2).response().source());
    }

    @Test
    void generalQuestionSkipsCatalogueLookupAndStreamsAnswer() {
        when(intentProvider.analyze("what is waterproofing?", List.of()))
                .thenReturn(new AssistantAnalysis(AssistantIntent.GENERAL_QUESTION, ShoppingIntent.empty(), null));
        List<AssistantStreamEvent> events = new ArrayList<>();
        org.mockito.Mockito.doAnswer(invocation -> {
            java.util.function.Consumer<String> consumer = invocation.getArgument(3);
            consumer.accept("Waterproofing helps resist water exposure.");
            return null;
        }).when(geminiClient).streamText(org.mockito.Mockito.anyString(), org.mockito.Mockito.anyList(),
                org.mockito.Mockito.eq("what is waterproofing?"), org.mockito.Mockito.any());

        service.streamChat(new AssistantChatRequest("what is waterproofing?", List.of()), events::add);

        verify(productRepository, never()).findAll();
        verify(reviewRepository, never()).findAll();
        assertEquals(AssistantIntent.GENERAL_QUESTION.name(), events.get(0).intent());
        assertEquals("final", events.get(events.size() - 1).type());
        assertTrue(events.get(events.size() - 1).response().replyGeneratedByAi());
        assertTrue(events.get(events.size() - 1).response().products().isEmpty());
    }

    @Test
    void vagueShoppingRequestAsksClarifyingQuestionWithoutSearching() {
        when(intentProvider.analyze("something for a trip", List.of()))
                .thenReturn(new AssistantAnalysis(AssistantIntent.SHOPPING_VAGUE, ShoppingIntent.empty(),
                        "What type of product do you need for your trip?"));
        List<AssistantStreamEvent> events = new ArrayList<>();

        service.streamChat(new AssistantChatRequest("something for a trip", List.of()), events::add);

        verify(productRepository, never()).findAll();
        verify(geminiClient, never()).streamText(org.mockito.Mockito.anyString(), org.mockito.Mockito.anyList(),
                org.mockito.Mockito.anyString(), org.mockito.Mockito.any());
        assertEquals(AssistantIntent.SHOPPING_VAGUE.name(), events.get(0).intent());
        assertEquals("final", events.get(events.size() - 1).type());
        assertTrue(events.get(events.size() - 1).response().reply().contains("What type"));
        assertTrue(events.get(events.size() - 1).response().products().isEmpty());
    }

    @Test
    void budgetAndInStockRequestSearchesBroadCatalogueInsteadOfClarifying() {
        Product watch = product(10L, "Waterproof Watch", "NorthPeak", "Watches", "Waterproof everyday watch", "950.00", true);
        Product unavailable = product(11L, "Out of Stock Bag", "OtherBrand", "Bags", "Travel bag", "850.00", false);
        unavailable.setStock(0);
        when(productRepository.findAll()).thenReturn(List.of(watch, unavailable));
        when(reviewRepository.findAll()).thenReturn(List.of());
        when(intentProvider.analyze("in stock products under 1000", List.of()))
                .thenReturn(new AssistantAnalysis(AssistantIntent.SHOPPING_VAGUE,
                        new ShoppingIntent(null, null, List.of(), null, null, null, null, null),
                        "What type of product are you looking for?"));
        org.mockito.Mockito.doAnswer(invocation -> {
            java.util.function.Consumer<String> consumer = invocation.getArgument(3);
            consumer.accept("I found one available item under your budget.");
            return null;
        }).when(geminiClient).streamText(org.mockito.Mockito.anyString(), org.mockito.Mockito.anyList(),
                org.mockito.Mockito.contains("Catalogue matches"), org.mockito.Mockito.any());
        List<AssistantStreamEvent> events = new ArrayList<>();

        service.streamChat(new AssistantChatRequest("in stock products under 1000", List.of()), events::add);

        AssistantChatResponse response = events.get(events.size() - 1).response();
        assertEquals(AssistantIntent.SHOPPING_CLEAR.name(), events.get(0).intent());
        assertEquals(1, response.products().size());
        assertEquals("Waterproof Watch", response.products().get(0).name());
        assertEquals(new BigDecimal("1000"), response.filters().maxPrice());
    }

    @Test
    void clearShoppingStreamsGeminiReplyWithCatalogueProductsInFinalEvent() {
        when(productRepository.findAll()).thenReturn(List.of(trailShoes));
        when(reviewRepository.findAll()).thenReturn(List.of());
        when(intentProvider.analyze("trail shoes under 150", List.of()))
                .thenReturn(new AssistantAnalysis(AssistantIntent.SHOPPING_CLEAR,
                        new ShoppingIntent(null, null, List.of("trail"), null, new BigDecimal("150"), null, null, null), null));
        List<AssistantStreamEvent> events = new ArrayList<>();
        org.mockito.Mockito.doAnswer(invocation -> {
            java.util.function.Consumer<String> tokenConsumer = invocation.getArgument(3);
            tokenConsumer.accept("I found a trail shoe.");
            return null;
        }).when(geminiClient).streamText(org.mockito.Mockito.anyString(), org.mockito.Mockito.anyList(),
                org.mockito.Mockito.contains("Catalogue matches"), org.mockito.Mockito.any());

        service.streamChat(new AssistantChatRequest("trail shoes under 150", List.of()), events::add);

        assertEquals(AssistantIntent.SHOPPING_CLEAR.name(), events.get(0).intent());
        AssistantStreamEvent finalEvent = events.get(events.size() - 1);
        assertEquals("final", finalEvent.type());
        assertEquals("GEMINI", finalEvent.response().source());
        assertTrue(finalEvent.response().aiUsedForIntent());
        assertTrue(finalEvent.response().replyGeneratedByAi());
        assertEquals(1, finalEvent.response().products().size());
        assertEquals("Trail Shoe", finalEvent.response().products().get(0).name());
    }

    @Test
    void closestAlternativesRetainHardBudgetAndWaterproofFiltersAndDiscloseRelaxedSoftFilters() {
        Product alternative = product(3L, "Waterproof Day Pack", "OtherBrand", "Bags", "Lightweight pack", "90.00", true);
        when(productRepository.findAll()).thenReturn(List.of(alternative));
        when(reviewRepository.findAll()).thenReturn(List.of());
        when(intentProvider.analyze("waterproof trail footwear under 100", List.of()))
                .thenReturn(new AssistantAnalysis(AssistantIntent.SHOPPING_CLEAR,
                        new ShoppingIntent("Footwear", null, List.of("trail"), null,
                                new BigDecimal("100"), null, null, true), null));
        org.mockito.Mockito.doAnswer(invocation -> {
            java.util.function.Consumer<String> consumer = invocation.getArgument(3);
            consumer.accept("No exact footwear matches; this waterproof day pack is a nearby alternative.");
            return null;
        }).when(geminiClient).streamText(org.mockito.Mockito.anyString(), org.mockito.Mockito.anyList(),
                org.mockito.Mockito.contains("Relaxed filters"), org.mockito.Mockito.any());

        List<AssistantStreamEvent> events = new ArrayList<>();
        service.streamChat(new AssistantChatRequest("waterproof trail footwear under 100", List.of()), events::add);
        AssistantChatResponse response = events.get(events.size() - 1).response();

        assertEquals(1, response.products().size());
        assertEquals(new BigDecimal("90.00"), response.products().get(0).price());
        assertEquals(Boolean.TRUE, response.products().get(0).waterproof());
        assertEquals(List.of("category", "keywords"), response.relaxedFilters());
    }

    @Test
    void fallbackClarifiesGiftRequestInsteadOfShowingUnrelatedAlternatives() {
        when(productRepository.findAll()).thenReturn(List.of(trailShoes));
        when(intentProvider.analyze("i want to see product to gift my girl friend", List.of()))
            .thenThrow(new IllegalStateException("provider unavailable"));

        List<AssistantStreamEvent> events = new ArrayList<>();
        service.streamChat(new AssistantChatRequest("i want to see product to gift my girl friend", List.of()), events::add);

        AssistantChatResponse response = events.get(events.size() - 1).response();
        assertEquals(AssistantIntent.SHOPPING_VAGUE.name(), events.get(0).intent());
        assertEquals("FALLBACK", response.source());
        assertTrue(response.products().isEmpty());
        assertTrue(response.reply().contains("What type of product"));
    }

    @Test
    void reportsHttpStatusCodeWhenGeminiRejectsRequest() {
        when(intentProvider.analyze("hi", List.of()))
            .thenThrow(new com.google.genai.errors.ClientException(400, "INVALID_ARGUMENT", "API key not valid"));

        List<AssistantStreamEvent> events = new ArrayList<>();
        service.streamChat(new AssistantChatRequest("hi", List.of()), events::add);

        AssistantChatResponse response = events.get(events.size() - 1).response();
        assertEquals("FALLBACK", response.source());
        assertEquals("HTTP_400", response.aiFallbackReason());
    }

    @Test
    void fallbackClarifiesWhenOnlyUnmatchedKeywordsRemain() {
        when(productRepository.findAll()).thenReturn(List.of(trailShoes));
        when(reviewRepository.findAll()).thenReturn(List.of());
        when(intentProvider.analyze("show me elephant gear", List.of()))
            .thenThrow(new IllegalStateException("provider unavailable"));

        List<AssistantStreamEvent> events = new ArrayList<>();
        service.streamChat(new AssistantChatRequest("show me elephant gear", List.of()), events::add);

        AssistantChatResponse response = events.get(events.size() - 1).response();
        assertEquals(AssistantIntent.SHOPPING_VAGUE.name(), events.get(0).intent());
        assertTrue(response.products().isEmpty());
        assertTrue(response.reply().contains("elephant"));
    }

    @Test
    void cartRequestReturnsItemsAndTotalForLoggedInCustomer() {
        User customer = new User("Asha", "asha@example.com", "pw", "CUSTOMER");
        customer.setId(7L);
        Cart cart = new Cart(customer);
        cart.setId(3L);
        CartItem first = new CartItem(cart, trailShoes, 2);
        CartItem second = new CartItem(cart,
                product(4L, "Day Pack", "Wayfarer", "Bags", "Light pack", "50.00", false), 1);
        when(intentProvider.analyze("show my cart", List.of()))
                .thenReturn(new AssistantAnalysis(AssistantIntent.ACCOUNT_CART, ShoppingIntent.empty(), null));
        when(cartRepository.findByUserId(7L)).thenReturn(java.util.Optional.of(cart));
        when(cartItemRepository.findByCartId(3L)).thenReturn(List.of(first, second));

        List<AssistantStreamEvent> events = new ArrayList<>();
        service.streamChat(new AssistantChatRequest("show my cart", List.of(), 7L), events::add);

        AssistantChatResponse response = events.get(events.size() - 1).response();
        assertEquals(AssistantIntent.ACCOUNT_CART.name(), events.get(0).intent());
        assertNotNull(response.cart());
        assertEquals(2, response.cart().items().size());
        assertEquals(new BigDecimal("290.00"), response.cart().total());
        assertTrue(response.reply().contains("2 items"));
        assertFalse(response.replyGeneratedByAi());
        verify(geminiClient, never()).streamText(org.mockito.Mockito.anyString(), org.mockito.Mockito.anyList(),
                org.mockito.Mockito.anyString(), org.mockito.Mockito.any());
    }

    @Test
    void ordersRequestReturnsOrderSummariesForLoggedInCustomer() {
        User customer = new User("Asha", "asha@example.com", "pw", "CUSTOMER");
        customer.setId(7L);
        Order order = new Order(customer, "DELIVERED");
        order.setId(9L);
        OrderItem item = new OrderItem(order, trailShoes, 2);
        when(intentProvider.analyze("show my previous purchased products", List.of()))
                .thenReturn(new AssistantAnalysis(AssistantIntent.ACCOUNT_ORDERS, ShoppingIntent.empty(), null));
        when(orderRepository.findByUserId(7L)).thenReturn(List.of(order));
        when(orderItemRepository.findByOrderId(9L)).thenReturn(List.of(item));

        List<AssistantStreamEvent> events = new ArrayList<>();
        service.streamChat(new AssistantChatRequest("show my previous purchased products", List.of(), 7L), events::add);

        AssistantChatResponse response = events.get(events.size() - 1).response();
        assertEquals(AssistantIntent.ACCOUNT_ORDERS.name(), events.get(0).intent());
        assertEquals(1, response.orders().size());
        assertEquals("DELIVERED", response.orders().get(0).status());
        assertEquals(new BigDecimal("240.00"), response.orders().get(0).total());
        assertEquals("Trail Shoe", response.orders().get(0).items().get(0).name());
        assertTrue(response.reply().contains("1 order"));
        assertFalse(response.replyGeneratedByAi());
    }

    @Test
    void accountRequestWithoutLoginAsksToLogInWithoutQuerying() {
        when(intentProvider.analyze("show my cart", List.of()))
                .thenReturn(new AssistantAnalysis(AssistantIntent.ACCOUNT_CART, ShoppingIntent.empty(), null));

        List<AssistantStreamEvent> events = new ArrayList<>();
        service.streamChat(new AssistantChatRequest("show my cart", List.of()), events::add);

        AssistantChatResponse response = events.get(events.size() - 1).response();
        assertTrue(response.reply().toLowerCase().contains("log in"));
        assertTrue(response.products().isEmpty());
        verify(cartRepository, never()).findByUserId(org.mockito.Mockito.anyLong());
        verify(orderRepository, never()).findByUserId(org.mockito.Mockito.anyLong());
    }

    @Test
    void localFallbackRecognizesCartAndOrderPhrases() {
        when(intentProvider.analyze(org.mockito.Mockito.anyString(), org.mockito.Mockito.anyList()))
                .thenThrow(new IllegalStateException("provider unavailable"));

        List<AssistantStreamEvent> cartEvents = new ArrayList<>();
        service.streamChat(new AssistantChatRequest("show my cart", List.of()), cartEvents::add);
        assertEquals(AssistantIntent.ACCOUNT_CART.name(), cartEvents.get(0).intent());
        assertTrue(cartEvents.get(cartEvents.size() - 1).response().reply().toLowerCase().contains("log in"));

        List<AssistantStreamEvent> orderEvents = new ArrayList<>();
        service.streamChat(new AssistantChatRequest("show my previous purchased products", List.of()), orderEvents::add);
        assertEquals(AssistantIntent.ACCOUNT_ORDERS.name(), orderEvents.get(0).intent());
    }

    @Test
    void blankHistoryEntriesAreIgnoredInsteadOfRejected() {
        AssistantChatRequest request = new AssistantChatRequest("hi",
                List.of(new AssistantChatRequest.ChatTurn("assistant", ""),
                        new AssistantChatRequest.ChatTurn("user", "hello")));

        assertEquals(1, request.history().size());
        assertEquals("user", request.history().get(0).role());
    }

    private static Product product(Long id, String name, String brand, String category, String description,
                                   String price, boolean waterproof) {
        Product product = new Product();
        product.setId(id);
        product.setName(name);
        product.setBrand(brand);
        product.setCategory(new Category(category));
        product.setDescription(description);
        product.setPrice(new BigDecimal(price));
        product.setStock(12);
        product.setWaterproof(waterproof);
        return product;
    }
}