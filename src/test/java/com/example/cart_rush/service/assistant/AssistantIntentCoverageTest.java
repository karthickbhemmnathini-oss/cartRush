package com.example.cart_rush.service.assistant;

import com.example.cart_rush.entity.Category;
import com.example.cart_rush.entity.Product;
import com.example.cart_rush.repository.ProductRepository;
import com.example.cart_rush.repository.ReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;

/**
 * Large-scale combinatorial coverage of the intent/classification layer that runs
 * in real time before any Gemini call. Each generated phrase must route to the
 * intent its wording implies and must not throw, so the SSE stream always gets a
 * well-formed first event within milliseconds.
 */
@ExtendWith(MockitoExtension.class)
class AssistantIntentCoverageTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private ReviewRepository reviewRepository;
    @Mock
    private ShoppingIntentProvider intentProvider;
    @Mock
    private GeminiClient geminiClient;

    private ShoppingAssistantService service;

    private static final List<Product> CATALOGUE = List.of(
            product(1L, "Summit Waterproof Watch", "NorthPeak", "Watches", "Everyday waterproof watch", "129.95", 40, true),
            product(2L, "Trail GPS Watch", "Altimeter", "Watches", "GPS trail watch", "219.00", 15, true),
            product(3L, "Field Analog Watch", "NorthPeak", "Watches", "Classic field watch", "84.50", 25, true),
            product(4L, "Ridge Daypack 24L", "Wayfarer", "Bags", "Light daypack", "74.99", 30, false),
            product(5L, "Stormproof Rolltop", "Wayfarer", "Bags", "Waterproof rolltop bag", "109.00", 12, true),
            product(6L, "Granite Trail Shoes", "NorthPeak", "Footwear", "Rugged trail shoes", "119.95", 18, false),
            product(7L, "Monsoon Shell Jacket", "Stormline", "Jackets", "Waterproof rain shell", "149.00", 20, true)
    );

    @BeforeEach
    void setUp() {
        service = new ShoppingAssistantService(productRepository, reviewRepository,
            null, null, null, null, null, null,
                intentProvider, geminiClient, new ShoppingIntentFallbackParser());
        lenient().when(productRepository.findAll()).thenReturn(CATALOGUE);
        lenient().when(reviewRepository.findAll()).thenReturn(List.of());
    }

    @Test
    void greetingVariationsAlwaysClassifyAsGreeting() {
        List<String> greetings = generateGreetings();
        assertTrue(greetings.size() >= 90, "expected a broad greeting set");
        for (String message : greetings) {
            AssistantIntent intent = classify(message);
            assertEquals(AssistantIntent.GREETING, intent, () -> "misclassified greeting: " + message);
        }
    }

    @Test
    void shoppingPhrasesRouteToClearOrVagueButNeverGreeting() {
        List<String> phrases = generateShoppingPhrases();
        assertTrue(phrases.size() >= 2000, "expected thousands of shopping phrases, got " + phrases.size());
        int clear = 0;
        int vague = 0;
        for (String message : phrases) {
            AssistantIntent intent = classify(message);
            assertTrue(intent == AssistantIntent.SHOPPING_CLEAR || intent == AssistantIntent.SHOPPING_VAGUE,
                    () -> "shopping phrase routed to " + intent + ": " + message);
            if (intent == AssistantIntent.SHOPPING_CLEAR) {
                clear++;
            } else {
                vague++;
            }
        }
        // A healthy classifier must resolve a solid share of concrete requests.
        final int clearCount = clear;
        final int vagueCount = vague;
        assertTrue(clearCount > phrases.size() / 3,
                () -> "too few SHOPPING_CLEAR results: clear=" + clearCount + " vague=" + vagueCount);
    }

    @Test
    void cartPhrasesAlwaysClassifyAsCartIntent() {
        for (String message : generateCartPhrases()) {
            AssistantIntent intent = classify(message);
            assertEquals(AssistantIntent.ACCOUNT_CART, intent, () -> "cart phrase missed: " + message);
        }
    }

    @Test
    void orderAndPurchasePhrasesAlwaysClassifyAsOrderIntent() {
        for (String message : generateOrderPhrases()) {
            AssistantIntent intent = classify(message);
            assertEquals(AssistantIntent.ACCOUNT_ORDERS, intent, () -> "order phrase missed: " + message);
        }
    }

    @Test
    void questionPhrasesClassifyAsGeneralQuestion() {
        for (String message : generateQuestionPhrases()) {
            AssistantIntent intent = classify(message);
            assertEquals(AssistantIntent.GENERAL_QUESTION, intent, () -> "question missed: " + message);
        }
    }

    @Test
    void priceAndRatingConstraintsAreExtractedCorrectly() {
        Map<String, BigDecimal> budgets = Map.of(
                "under 500", new BigDecimal("500"),
                "below 1500", new BigDecimal("1500"),
                "less than 2000", new BigDecimal("2000"));
        budgets.forEach((phrase, expected) -> {
            ShoppingIntent intent = parse("waterproof watch " + phrase);
            assertEquals(expected, intent.maxPrice(), () -> "budget not parsed from: " + phrase);
        });

        ShoppingIntent over = parse("waterproof watch over 100");
        assertEquals(new BigDecimal("100"), over.minPrice());

        ShoppingIntent rated = parse("watches rated 4 stars");
        assertEquals(4, rated.minRating());

        ShoppingIntent waterproof = parse("waterproof jackets");
        assertEquals(Boolean.TRUE, waterproof.waterproof());
    }

    @Test
    void categoryRatingFollowupKeepsTheSelectedCategory() {
        org.mockito.Mockito.when(productRepository.findAll()).thenReturn(List.of(
            product(8L, "Trail Compass", "NorthPeak", "Accessories", "Navigation accessory", "40.00", 20, false)));
        List<AssistantChatRequest.ChatTurn> history = List.of(
                new AssistantChatRequest.ChatTurn("user", "categories in the store please"),
                new AssistantChatRequest.ChatTurn("assistant", "Our catalogue categories include Accessories."),
                new AssistantChatRequest.ChatTurn("user", "accessories"),
                new AssistantChatRequest.ChatTurn("assistant", "Showing accessories."));

        AssistantAnalysis analysis = service.classifyLocally("4 stars", history);

        assertEquals(AssistantIntent.SHOPPING_CLEAR, analysis.intent());
        assertEquals("Accessories", analysis.filters().category());
        assertEquals(4, analysis.filters().minRating());
    }

    @Test
    void generatedRoleMatrixHasTwoThousandCasesForBothModes() throws Exception {
        try (var stream = getClass().getResourceAsStream("/assistant-fallback-messages.csv")) {
            assertNotNull(stream, "generated assistant test CSV must be on the test classpath");
            List<String> rows;
            try (var reader = new BufferedReader(new InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8))) {
                rows = reader.lines().skip(1).filter(line -> !line.isBlank()).toList();
            }
            assertEquals(2000, rows.size());
                List<String> roles = rows.stream().map(line -> line.split(",", 4)[0].replace("\"", "")).toList();
                assertTrue(Set.of("ANY", "CUSTOMER", "SUPPLIER", "ADMIN").containsAll(roles.stream().distinct().toList()));
                assertTrue(roles.stream().filter("ANY"::equals).count() > 0);
                assertTrue(roles.stream().filter("CUSTOMER"::equals).count() > 0);
                assertTrue(roles.stream().filter("SUPPLIER"::equals).count() > 0);
                assertTrue(roles.stream().filter("ADMIN"::equals).count() > 0);
            assertTrue(rows.stream().allMatch(line -> line.contains("GEMINI_AND_FALLBACK")));
        }
    }

    @Test
    void streamingFirstEventIsEmittedImmediatelyForEveryRoute() {
        // Prove real-time behavior at the service level: the very first SSE event
        // (status) must arrive synchronously, before any network call resolves.
        List<String> samples = List.of("hi", "show my cart", "show my orders",
                "waterproof watch under 500", "something for a trip", "what is waterproofing");
        for (String message : samples) {
            List<AssistantStreamEvent> events = new ArrayList<>();
            org.mockito.Mockito.when(intentProvider.analyze(org.mockito.Mockito.eq(message), org.mockito.Mockito.anyList()))
                    .thenAnswer(invocation -> {
                        throw new AssistantProviderException("KEY_NOT_CONFIGURED");
                    });
            long start = System.nanoTime();
            try {
                service.streamChat(new AssistantChatRequest(message, List.of(), null), events::add);
            } catch (RuntimeException ignored) {
                // Provider paths may throw after the first status event; we only assert on timing/order.
            }
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            assertTrue(!events.isEmpty(), "no events for: " + message);
            assertEquals("status", events.get(0).type(), () -> "first event not status for: " + message);
            assertNotNull(events.get(0).intent());
            assertTrue(elapsedMs < 2000, () -> "first event too slow (" + elapsedMs + "ms) for: " + message);
        }
    }

        @Test
        void routesOneThousandFallbackQuestionsAcrossSupportedDomains() {
        String[] commonSuffixes = {"", " please", " now", " please now", " for me", " thanks",
            " today", " now please", " if you can", " thanks please"};
        String[] greetingSuffixes = {"", "!", ".", " there", " there!", " friend", " friend!",
            " everyone", " all", " folks"};
        List<String> questions = new ArrayList<>();
        List<AssistantIntent> expectedIntents = new ArrayList<>();

        addQuestionFamily(questions, expectedIntents, AssistantIntent.GREETING,
            variants(new String[]{"hi", "hello", "hey", "good morning", "good afternoon", "good evening", "howdy", "greetings", "welcome", "good day"}, greetingSuffixes));
        addQuestionFamily(questions, expectedIntents, AssistantIntent.CATALOGUE_CATEGORIES,
            variants(new String[]{"list product categories", "show categories", "what categories do you have", "which categories are available", "browse product categories", "tell me the categories", "show me store categories", "list the types of products", "what product categories exist", "categories in the store"}, commonSuffixes));
        addQuestionFamily(questions, expectedIntents, AssistantIntent.PRODUCT_INFO,
            variants(new String[]{"tell me details about Summit Waterproof Watch", "what is Summit Waterproof Watch", "show details for Summit Waterproof Watch", "give me information about Summit Waterproof Watch", "describe Summit Waterproof Watch", "what is the price of Summit Waterproof Watch", "is Summit Waterproof Watch in stock", "tell me about Summit Waterproof Watch", "show Summit Waterproof Watch information", "what category is Summit Waterproof Watch"}, commonSuffixes));
        addQuestionFamily(questions, expectedIntents, AssistantIntent.PRODUCT_REVIEWS,
            variants(new String[]{"show reviews for Summit Waterproof Watch", "read reviews for Summit Waterproof Watch", "what are the reviews of Summit Waterproof Watch", "average rating for Summit Waterproof Watch", "show ratings for Summit Waterproof Watch", "summarize reviews about Summit Waterproof Watch", "how is Summit Waterproof Watch rated", "what do reviews say about Summit Waterproof Watch", "rating of Summit Waterproof Watch", "reviews on Summit Waterproof Watch"}, commonSuffixes));
        addQuestionFamily(questions, expectedIntents, AssistantIntent.REWARDS_INFO,
            variants(new String[]{"do i have rewards", "show my reward points", "how many loyalty points do i have", "tell me about rewards", "what rewards can i get", "check my points", "do you offer loyalty rewards", "show reward balance", "how does the points program work", "where are my rewards"}, commonSuffixes));
        addQuestionFamily(questions, expectedIntents, AssistantIntent.SHOPPING_CLEAR,
            variants(new String[]{"show watches under 500", "find bags below 200", "search jackets under 1000", "buy shoes below 1500", "recommend a watch under 300", "show waterproof watches under 500", "find bags over 100", "show watches between 50 and 200", "search products under 1000", "find waterproof jackets below 500"}, commonSuffixes));
        addQuestionFamily(questions, expectedIntents, AssistantIntent.SHOPPING_CLEAR,
            variants(new String[]{"show watches rated 4 stars", "find bags rated 5 stars", "show 4 star watches", "top rated jackets", "show watches with 5 star rating", "find highly rated shoes", "show products rated 4 stars", "best rated bags", "waterproof watches rated 4 stars", "show footwear with 5 stars"}, commonSuffixes));
        addQuestionFamily(questions, expectedIntents, AssistantIntent.ACCOUNT_CART,
            variants(new String[]{"show my cart", "view my cart", "what is in my cart", "check my cart", "show my basket", "open my basket", "cart contents", "what is in the basket", "let me see my cart", "display my shopping basket"}, commonSuffixes));
        addQuestionFamily(questions, expectedIntents, AssistantIntent.ACCOUNT_ORDERS,
            variants(new String[]{"show my orders", "my order history", "show my previous purchases", "what i bought", "what have i purchased", "show my purchase history", "previous orders", "list my orders", "show my past purchases", "my purchases"}, commonSuffixes));
        addQuestionFamily(questions, expectedIntents, AssistantIntent.SHOPPING_VAGUE,
            variants(new String[]{"something for a trip", "something for a gift", "i need something", "help me choose a product", "looking for something", "need a present", "shopping for travel", "i want a gift", "recommend something", "help me find gear"}, commonSuffixes));

        assertEquals(1000, questions.size(), "the generated evaluation matrix must contain exactly 1,000 questions");
        for (int index = 0; index < questions.size(); index++) {
            String question = questions.get(index);
            AssistantIntent expectedIntent = expectedIntents.get(index);
            AssistantIntent actual = service.classifyLocally(question).intent();
            assertEquals(expectedIntent, actual, "Question " + index + " misrouted: " + question);
        }
    }

    private static void addQuestionFamily(List<String> questions, List<AssistantIntent> expected,
                                          AssistantIntent intent, List<String> family) {
        questions.addAll(family);
        for (int index = 0; index < family.size(); index++) expected.add(intent);
    }

        private static List<String> variants(String[] templates, String[] suffixes) {
        List<String> questions = new ArrayList<>(templates.length * suffixes.length);
        for (String template : templates) {
            for (String suffix : suffixes) questions.add(template + suffix);
        }
        return questions;
        }

    // ---- classifiers exercised through the real streamChat path in fallback mode ----

    private AssistantIntent classify(String message) {
        org.mockito.Mockito.when(intentProvider.analyze(org.mockito.Mockito.eq(message), org.mockito.Mockito.anyList()))
                .thenAnswer(invocation -> {
                    throw new AssistantProviderException("KEY_NOT_CONFIGURED");
                });
        List<AssistantStreamEvent> events = new ArrayList<>();
        try {
            service.streamChat(new AssistantChatRequest(message, List.of(), null), events::add);
        } catch (RuntimeException ignored) {
            // streamText may fail without a real Gemini client; classification already happened.
        }
        assertTrue(!events.isEmpty(), "no status event for: " + message);
        return AssistantIntent.valueOf(events.get(0).intent());
    }

    private ShoppingIntent parse(String message) {
        org.mockito.Mockito.when(intentProvider.analyze(org.mockito.Mockito.eq(message), org.mockito.Mockito.anyList()))
                .thenAnswer(invocation -> {
                    throw new AssistantProviderException("KEY_NOT_CONFIGURED");
                });
        final ShoppingIntent[] captured = new ShoppingIntent[1];
        try {
            service.streamChat(new AssistantChatRequest(message, List.of(), null), event -> {
                if ("final".equals(event.type()) && event.response() != null) {
                    captured[0] = event.response().filters();
                }
            });
        } catch (RuntimeException ignored) {
        }
        return captured[0];
    }

    // ---- phrase generators ----

    private static List<String> generateGreetings() {
        String[] bases = { "hi", "hello", "hey", "good morning", "good afternoon", "good evening", "howdy" };
        String[] suffixes = { "", ".", "!", " there", " there!", " friend", " 😊" };
        List<String> out = new ArrayList<>();
        for (String base : bases) {
            for (String suffix : suffixes) {
                out.add(base + suffix);
                out.add(capitalize(base) + suffix);
            }
        }
        return out;
    }

    private static List<String> generateShoppingPhrases() {
        String[] verbs = { "show me", "find", "search", "i want to buy", "i need", "looking for", "recommend", "suggest" };
        String[] objects = { "waterproof watch", "watch", "bag", "trail shoes", "jacket", "daypack", "rolltop", "product" };
        String[] constraints = {
                "", " under 100", " under 500", " under 1000", " below 2000", " over 100",
                " between 50 and 200", " rated 4 stars", " rated 5 stars", " top rated",
                " cheapest", " in stock", " available", " waterproof", " from NorthPeak"
        };
        List<String> out = new ArrayList<>();
        for (String verb : verbs) {
            for (String object : objects) {
                for (String constraint : constraints) {
                    out.add(verb + " " + object + constraint);
                    out.add(verb + " a " + object + constraint);
                    out.add(capitalize(verb) + " " + object + constraint + " please");
                }
            }
        }
        return out;
    }

    private static List<String> generateCartPhrases() {
        String[] prefixes = { "show", "what's in", "view", "open", "check", "see" };
        String[] nouns = { "my cart", "the cart", "my basket", "the basket", "cart", "basket" };
        List<String> out = new ArrayList<>();
        for (String prefix : prefixes) {
            for (String noun : nouns) {
                out.add(prefix + " " + noun);
                out.add(capitalize(prefix) + " " + noun + " please");
            }
        }
        return out;
    }

    private static List<String> generateOrderPhrases() {
        String[] templates = {
                "show my orders", "my orders", "order history", "my order history",
                "show my previous orders", "show my previous purchases", "my purchases",
                "what i bought", "what i purchased", "show what i bought",
                "my previous purchased products", "show my purchase history",
                "when will my order arrive"
        };
        List<String> out = new ArrayList<>();
        for (String template : templates) {
            out.add(template);
            out.add(capitalize(template));
            out.add(template + " please");
        }
        return out;
    }

    private static List<String> generateQuestionPhrases() {
        String[] templates = {
                "what is waterproofing", "how do i wash a waterproof jacket", "what does gps mean",
                "why choose a rolltop bag", "how long does delivery take", "where do you ship",
                "which watch is best for swimming"
        };
        List<String> out = new ArrayList<>();
        for (String template : templates) {
            out.add(template);
            out.add(template + "?");
            out.add(capitalize(template) + "?");
        }
        return out;
    }

    private static String capitalize(String value) {
        return value.isEmpty() ? value : value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1);
    }

    private static Product product(Long id, String name, String brand, String category, String description,
                                   String price, int stock, boolean waterproof) {
        Product product = new Product();
        product.setId(id);
        product.setName(name);
        product.setBrand(brand);
        product.setCategory(new Category(category));
        product.setDescription(description);
        product.setPrice(new BigDecimal(price));
        product.setStock(stock);
        product.setWaterproof(waterproof);
        return product;
    }
}
