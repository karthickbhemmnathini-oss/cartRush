package com.example.cart_rush.service.assistant;

import com.example.cart_rush.entity.Cart;
import com.example.cart_rush.entity.CartItem;
import com.example.cart_rush.entity.Order;
import com.example.cart_rush.entity.OrderItem;
import com.example.cart_rush.entity.Product;
import com.example.cart_rush.entity.Review;
import com.example.cart_rush.repository.CartItemRepository;
import com.example.cart_rush.repository.CartRepository;
import com.example.cart_rush.repository.OrderItemRepository;
import com.example.cart_rush.repository.OrderRepository;
import com.example.cart_rush.repository.ProductRepository;
import com.example.cart_rush.repository.ReviewRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

@Service
public class ShoppingAssistantService {

    private static final Logger log = LoggerFactory.getLogger(ShoppingAssistantService.class);
    private static final int MAX_RESULTS = 6;

    private final ProductRepository productRepository;
    private final ReviewRepository reviewRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ShoppingIntentProvider intentProvider;
    private final GeminiClient geminiClient;
    private final ShoppingIntentFallbackParser fallbackParser;

    public ShoppingAssistantService(ProductRepository productRepository,
                                    ReviewRepository reviewRepository,
                                    CartRepository cartRepository,
                                    CartItemRepository cartItemRepository,
                                    OrderRepository orderRepository,
                                    OrderItemRepository orderItemRepository,
                                    ShoppingIntentProvider intentProvider,
                                    GeminiClient geminiClient,
                                    ShoppingIntentFallbackParser fallbackParser) {
        this.productRepository = productRepository;
        this.reviewRepository = reviewRepository;
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.intentProvider = intentProvider;
        this.geminiClient = geminiClient;
        this.fallbackParser = fallbackParser;
    }

    @Transactional(readOnly = true)
    public AssistantChatResponse chat(String message) {
        AssistantChatResponse[] result = new AssistantChatResponse[1];
        StringBuilder accumulatedReply = new StringBuilder();
        streamChat(new AssistantChatRequest(message, List.of()), event -> {
            if ("token".equals(event.type()) && event.text() != null) {
                accumulatedReply.append(event.text());
            } else if ("final".equals(event.type())) {
                result[0] = event.response();
            }
        });
        if (result[0] == null) {
            return new AssistantChatResponse(accumulatedReply.toString(), List.of(), ShoppingIntent.empty(),
                    "FALLBACK", false, false, "NO_FINAL_RESPONSE");
        }
        return result[0];
    }

    public void streamChat(AssistantChatRequest request, Consumer<AssistantStreamEvent> events) {
        String message = request.message().trim();
        AssistantAnalysis analysis;
        String source;
        String fallbackReason = null;
        try {
            analysis = normalizeAnalysis(intentProvider.analyze(message, request.history()), message);
            source = "GEMINI";
        } catch (RuntimeException exception) {
            fallbackReason = classifyProviderFailure(exception);
            log.warn("Gemini intent classification failed ({}: {}); selecting local response path",
                    fallbackReason, exception.getMessage());
            analysis = classifyLocally(message);
            source = "FALLBACK";
        }

        AssistantIntent intent = analysis.intent();
        events.accept(AssistantStreamEvent.status(statusFor(intent, source), intent.name()));

        if (intent == AssistantIntent.GREETING) {
            streamGeneralReply(message, request.history(), intent, source, fallbackReason,
                    "Greet the user briefly and invite them to ask for shopping help. Do not search the catalogue.",
                    events);
            return;
        }
        if (intent == AssistantIntent.GENERAL_QUESTION) {
            streamGeneralReply(message, request.history(), intent, source, fallbackReason,
                    "Answer the general question helpfully. Do not claim Cart Rush inventory or product facts; those were not provided.",
                    events);
            return;
        }
        if (intent == AssistantIntent.UNSUPPORTED) {
            String reply = "I’m here to help with shopping and products in Cart Rush. What kind of gear are you looking for?";
            events.accept(AssistantStreamEvent.token(reply, intent.name()));
            events.accept(AssistantStreamEvent.finalResponse(
                    new AssistantChatResponse(reply, List.of(), ShoppingIntent.empty(), source,
                            "GEMINI".equals(source), false, fallbackReason), intent.name()));
            return;
        }
        if (intent == AssistantIntent.ACCOUNT_CART) {
            streamCartReply(request.userId(), source, fallbackReason, events);
            return;
        }
        if (intent == AssistantIntent.ACCOUNT_ORDERS) {
            streamOrdersReply(request.userId(), source, fallbackReason, events);
            return;
        }
        if (intent == AssistantIntent.SHOPPING_VAGUE) {
            String reply = analysis.clarification() == null || analysis.clarification().isBlank()
                    ? "What type of product are you looking for, and do you have a budget or must-have feature?"
                    : analysis.clarification();
            events.accept(AssistantStreamEvent.token(reply, intent.name()));
            events.accept(AssistantStreamEvent.finalResponse(
                    new AssistantChatResponse(reply, List.of(), analysis.filters(), source,
                            "GEMINI".equals(source), false, fallbackReason), intent.name()));
            return;
        }

        List<Product> catalogue = productRepository.findAll();
        ShoppingIntent supplementalFilters = fallbackParser.parse(message, catalogue);
        ShoppingIntent filters = mergeFilters(analysis.filters(), supplementalFilters);
        if ("FALLBACK".equals(source)) {
            filters = normalize(supplementalFilters);
        }
        ShoppingIntent resolvedFilters = filters;
        Map<Long, Rating> ratings = readRatings();
        List<AssistantChatResponse.ProductMatch> matches = catalogue.stream()
            .filter(product -> matches(product, resolvedFilters, ratings))
            .sorted(comparator(resolvedFilters, ratings))
                .limit(MAX_RESULTS)
                .map(product -> toMatch(product, ratings.get(product.getId())))
                .toList();
        List<String> relaxedFilters = List.of();
        if (matches.isEmpty()) {
            List<Product> alternatives = closestAlternatives(catalogue, resolvedFilters, ratings);
            if (!alternatives.isEmpty()) {
            matches = alternatives.stream().map(product -> toMatch(product, ratings.get(product.getId()))).toList();
            relaxedFilters = relaxedFilterNames(resolvedFilters);
            }
        }

        if ("FALLBACK".equals(source)) {
            String reply = matches.isEmpty()
                    ? "I couldn’t find matching in-stock items. Try another product type or budget."
                : relaxedFilters.isEmpty()
                ? "I found " + matches.size() + " in-stock catalogue item" + (matches.size() == 1 ? "" : "s") + " for your request."
                : "I couldn’t find exact matches. Here are closest in-stock alternatives; relaxed filters: " + String.join(", ", relaxedFilters) + ".";
            events.accept(AssistantStreamEvent.token(reply, intent.name()));
            events.accept(AssistantStreamEvent.finalResponse(new AssistantChatResponse(reply, matches, resolvedFilters,
                source, false, false, fallbackReason, relaxedFilters), intent.name()));
            return;
        }

        String prompt = shoppingReplyPrompt(message, resolvedFilters, matches, relaxedFilters);
        StringBuilder streamedReply = new StringBuilder();
        try {
            geminiClient.streamText(
                    "You are the Cart Rush shopping assistant. Explain recommendations using only the supplied catalogue facts. "
                            + "Do not invent products, prices, stock, ratings, brands, or features. If no products are supplied, say there are no exact matches and ask which requirement the user can relax.",
                    request.history(), prompt,
                    token -> {
                        if (token != null && !token.isEmpty()) {
                            streamedReply.append(token);
                            events.accept(AssistantStreamEvent.token(token, intent.name()));
                        }
                    });
            String reply = streamedReply.isEmpty()
                    ? "I found catalogue matches for your request."
                    : streamedReply.toString();
            events.accept(AssistantStreamEvent.finalResponse(new AssistantChatResponse(reply, matches, resolvedFilters,
                    source, true, true, null, relaxedFilters), intent.name()));
        } catch (RuntimeException exception) {
            String reason = classifyProviderFailure(exception);
            log.warn("Gemini answer streaming failed ({}); returning catalogue fallback", reason);
            String reply = matches.isEmpty()
                    ? "I couldn’t find matching in-stock items. Try another product type or budget."
                    : "I found " + matches.size() + " catalogue item" + (matches.size() == 1 ? "" : "s") + ". Gemini is unavailable, so this is a local catalogue result.";
            events.accept(AssistantStreamEvent.token(reply, intent.name()));
            events.accept(AssistantStreamEvent.finalResponse(new AssistantChatResponse(reply, matches, resolvedFilters,
                    "FALLBACK", false, false, reason, relaxedFilters), intent.name()));
        }
    }

    private void streamGeneralReply(String message, List<AssistantChatRequest.ChatTurn> history,
                                    AssistantIntent intent, String source, String fallbackReason,
                                    String systemInstruction, Consumer<AssistantStreamEvent> events) {
        if ("FALLBACK".equals(source)) {
            String reply = intent == AssistantIntent.GREETING
                    ? "Hi! What kind of product or gear are you shopping for today?"
                    : "I can help find products in the Cart Rush catalogue. What would you like to know or shop for?";
            events.accept(AssistantStreamEvent.token(reply, intent.name()));
            events.accept(AssistantStreamEvent.finalResponse(new AssistantChatResponse(reply, List.of(),
                    ShoppingIntent.empty(), source, false, false, fallbackReason), intent.name()));
            return;
        }

        StringBuilder reply = new StringBuilder();
        try {
            geminiClient.streamText(systemInstruction, history, message, token -> {
                if (token != null && !token.isEmpty()) {
                    reply.append(token);
                    events.accept(AssistantStreamEvent.token(token, intent.name()));
                }
            });
            String finalReply = reply.isEmpty() ? "How can I help with your shopping today?" : reply.toString();
            events.accept(AssistantStreamEvent.finalResponse(new AssistantChatResponse(finalReply, List.of(),
                    ShoppingIntent.empty(), source, true, true, null), intent.name()));
        } catch (RuntimeException exception) {
            String reason = classifyProviderFailure(exception);
            log.warn("Gemini answer streaming failed ({}); using local response", reason);
            String localReply = intent == AssistantIntent.GREETING
                    ? "Hi! What kind of product or gear are you shopping for today?"
                    : "I can help find products in the Cart Rush catalogue. What would you like to shop for?";
            events.accept(AssistantStreamEvent.token(localReply, intent.name()));
            events.accept(AssistantStreamEvent.finalResponse(new AssistantChatResponse(localReply, List.of(),
                    ShoppingIntent.empty(), "FALLBACK", false, false, reason), intent.name()));
        }
    }

    private static String statusFor(AssistantIntent intent, String source) {
        if ("FALLBACK".equals(source)) return "Using local fallback";
        return switch (intent) {
            case GREETING, GENERAL_QUESTION, UNSUPPORTED -> "Thinking";
            case SHOPPING_CLEAR -> "Checking the catalogue";
            case SHOPPING_VAGUE -> "Understanding your request";
            case ACCOUNT_CART -> "Checking your cart";
            case ACCOUNT_ORDERS -> "Checking your orders";
        };
    }

    private void streamCartReply(Long userId, String source, String fallbackReason,
                                 Consumer<AssistantStreamEvent> events) {
        AssistantIntent intent = AssistantIntent.ACCOUNT_CART;
        if (userId == null) {
            String reply = "Please log in to your customer account so I can show your cart.";
            events.accept(AssistantStreamEvent.token(reply, intent.name()));
            events.accept(AssistantStreamEvent.finalResponse(new AssistantChatResponse(reply, List.of(),
                    ShoppingIntent.empty(), source, "GEMINI".equals(source), false, fallbackReason), intent.name()));
            return;
        }
        Cart cart = cartRepository.findByUserId(userId).orElse(null);
        List<CartItem> items = cart == null ? List.of() : cartItemRepository.findByCartId(cart.getId());
        List<AssistantChatResponse.CartEntry> entries = items.stream()
                .filter(item -> item.getProduct() != null)
                .map(item -> {
                    Product product = item.getProduct();
                    int quantity = item.getQuantity() == null ? 1 : item.getQuantity();
                    BigDecimal price = product.getPrice() == null ? BigDecimal.ZERO : product.getPrice();
                    return new AssistantChatResponse.CartEntry(product.getId(), product.getName(),
                            product.getBrand(), product.getImageUrl(), price, quantity,
                            price.multiply(BigDecimal.valueOf(quantity)));
                })
                .toList();
        BigDecimal total = entries.stream().map(AssistantChatResponse.CartEntry::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        AssistantChatResponse.CartView cartView = new AssistantChatResponse.CartView(entries, total);
        String reply = entries.isEmpty()
                ? "Your cart is empty. Tell me what kind of gear you need and I’ll find something from our catalogue."
                : "You have " + entries.size() + " item" + (entries.size() == 1 ? "" : "s")
                + " in your cart, total " + total + ".";
        events.accept(AssistantStreamEvent.token(reply, intent.name()));
        events.accept(AssistantStreamEvent.finalResponse(new AssistantChatResponse(reply, List.of(),
                ShoppingIntent.empty(), source, "GEMINI".equals(source), false, fallbackReason,
                List.of(), cartView, List.of()), intent.name()));
    }

    private void streamOrdersReply(Long userId, String source, String fallbackReason,
                                   Consumer<AssistantStreamEvent> events) {
        AssistantIntent intent = AssistantIntent.ACCOUNT_ORDERS;
        if (userId == null) {
            String reply = "Please log in to your customer account so I can show your previous orders.";
            events.accept(AssistantStreamEvent.token(reply, intent.name()));
            events.accept(AssistantStreamEvent.finalResponse(new AssistantChatResponse(reply, List.of(),
                    ShoppingIntent.empty(), source, "GEMINI".equals(source), false, fallbackReason), intent.name()));
            return;
        }
        List<Order> orders = orderRepository.findByUserId(userId);
        List<AssistantChatResponse.OrderView> views = orders.stream().map(order -> {
            List<AssistantChatResponse.OrderEntry> entries = orderItemRepository.findByOrderId(order.getId()).stream()
                    .filter(item -> item.getProduct() != null)
                    .map(this::toOrderEntry)
                    .toList();
            BigDecimal total = entries.stream()
                    .map(entry -> entry.unitPrice().multiply(BigDecimal.valueOf(entry.quantity())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            return new AssistantChatResponse.OrderView(order.getId(), order.getStatus(), order.getCreatedAt(),
                    entries, total);
        }).toList();
        String reply = views.isEmpty()
                ? "You haven’t placed any orders yet. When you do, I can show them here."
                : "You have " + views.size() + " order" + (views.size() == 1 ? "" : "s") + ".";
        events.accept(AssistantStreamEvent.token(reply, intent.name()));
        events.accept(AssistantStreamEvent.finalResponse(new AssistantChatResponse(reply, List.of(),
                ShoppingIntent.empty(), source, "GEMINI".equals(source), false, fallbackReason,
                List.of(), null, views), intent.name()));
    }

    private AssistantChatResponse.OrderEntry toOrderEntry(OrderItem item) {
        Product product = item.getProduct();
        int quantity = item.getQuantity() == null ? 1 : item.getQuantity();
        BigDecimal unitPrice = item.getUnitPrice() != null ? item.getUnitPrice()
                : product.getPrice() != null ? product.getPrice() : BigDecimal.ZERO;
        return new AssistantChatResponse.OrderEntry(product.getId(), product.getName(), quantity, unitPrice);
    }

    private AssistantAnalysis normalizeAnalysis(AssistantAnalysis analysis, String message) {
        if (analysis == null || analysis.intent() == null) return classifyLocally(message);
        ShoppingIntent extracted = mergeFilters(analysis.filters(), fallbackParser.parse(message, List.of()));
        AssistantIntent intent = analysis.intent();
        if (intent == AssistantIntent.SHOPPING_VAGUE && hasSearchConstraint(extracted)) {
            intent = AssistantIntent.SHOPPING_CLEAR;
        }
        return new AssistantAnalysis(intent, extracted, analysis.clarification());
    }

    private static boolean hasSearchConstraint(ShoppingIntent intent) {
        return intent.category() != null || intent.brand() != null || intent.minPrice() != null
                || intent.maxPrice() != null || intent.minRating() != null || intent.waterproof() != null
                || !intent.keywords().isEmpty();
    }

    private static ShoppingIntent mergeFilters(ShoppingIntent preferred, ShoppingIntent supplemental) {
        ShoppingIntent first = normalize(preferred);
        ShoppingIntent second = normalize(supplemental);
        java.util.LinkedHashSet<String> keywords = new java.util.LinkedHashSet<>(first.keywords());
        keywords.addAll(second.keywords());
        return normalize(new ShoppingIntent(
                first.category() != null ? first.category() : second.category(),
                first.brand() != null ? first.brand() : second.brand(),
                List.copyOf(keywords),
                first.minPrice() != null ? first.minPrice() : second.minPrice(),
                first.maxPrice() != null ? first.maxPrice() : second.maxPrice(),
                first.minRating() != null ? first.minRating() : second.minRating(),
                first.sort() != null ? first.sort() : second.sort(),
                first.waterproof() != null ? first.waterproof() : second.waterproof()));
    }

    private AssistantAnalysis classifyLocally(String message) {
        String normalized = message == null ? "" : message.trim().toLowerCase(Locale.ROOT);
        if (normalized.matches("^(hi|hello|hey|good morning|good afternoon|good evening|howdy)[.!\\s]*(there|friend|everyone|all|folks)?[.!\\s\\p{So}]*$")) {
            return new AssistantAnalysis(AssistantIntent.GREETING, ShoppingIntent.empty(), null);
        }
        if (normalized.matches(".*\\b(my cart|my basket|the cart|the basket|cart|basket)\\b.*")) {
            return new AssistantAnalysis(AssistantIntent.ACCOUNT_CART, ShoppingIntent.empty(), null);
        }
        // Explicit purchase-history lookups are account queries even when phrased as "what i bought".
        // Note: bare "my order" (delivery/status questions) stays a question; only history/past-purchase
        // wording, or an explicit view/show request for "my orders", routes to account orders.
        boolean isQuestion = normalized.matches(".*\\b(what|how|why|when|where|who|which)\\b.*");
        if (normalized.matches(".*\\b(my orders|order history|previous(ly)? (orders?|purchases?|purchased|bought)|my (purchases?|bought)|my order history|what (have|had) i (bought|purchased)|what i (bought|purchased))\\b.*")
                || (!isQuestion && normalized.matches(".*\\b(show|view|see|list|get|open|check)\\b.*\\bmy orders\\b.*"))) {
            return new AssistantAnalysis(AssistantIntent.ACCOUNT_ORDERS, ShoppingIntent.empty(), null);
        }
        // Genuine questions (how/what/why/when...) are answered, not searched — even when they
        // name a product attribute such as "waterproof". Check this before shopping cues.
        if (isQuestion) {
            return new AssistantAnalysis(AssistantIntent.GENERAL_QUESTION, ShoppingIntent.empty(), null);
        }
        boolean shoppingCue = normalized.matches(".*\\b(find|show|search|buy|purchase|recommend|suggest|suggestion|looking for|shop|product|products|under|below|between|price|in stock|available|waterproof|rated|cheapest|budget)\\b.*");
        if (shoppingCue) {
            List<Product> catalogue = productRepository.findAll();
            ShoppingIntent parsed = normalize(fallbackParser.parse(normalized, catalogue));
            boolean hasStructuredConstraint = parsed.category() != null || parsed.brand() != null
                    || parsed.minPrice() != null || parsed.maxPrice() != null
                    || parsed.waterproof() != null || parsed.minRating() != null;
            if (hasStructuredConstraint) {
                return new AssistantAnalysis(AssistantIntent.SHOPPING_CLEAR, parsed, null);
            }
            if (!parsed.keywords().isEmpty()) {
                Map<Long, Rating> ratings = readRatings();
                boolean anyMatch = catalogue.stream().anyMatch(product -> matches(product, parsed, ratings));
                if (anyMatch) {
                    return new AssistantAnalysis(AssistantIntent.SHOPPING_CLEAR, parsed, null);
                }
                return new AssistantAnalysis(AssistantIntent.SHOPPING_VAGUE, ShoppingIntent.empty(),
                        "I couldn't find anything in our catalogue matching \"" + String.join(" ", parsed.keywords())
                                + "\". What type of product are you looking for, and do you have a budget or must-have feature?");
            }
            return new AssistantAnalysis(AssistantIntent.SHOPPING_VAGUE, parsed,
                    "What type of product are you looking for, and do you have a budget or must-have feature?");
        }
        if (normalized.matches(".*\\b(need|want|looking)\\b.*")) {
            return new AssistantAnalysis(AssistantIntent.SHOPPING_VAGUE, ShoppingIntent.empty(),
                    "What type of product are you looking for, and do you have a budget or must-have feature?");
        }
        return new AssistantAnalysis(AssistantIntent.UNSUPPORTED, ShoppingIntent.empty(), null);
    }

    private static String shoppingReplyPrompt(String message, ShoppingIntent filters,
                                              List<AssistantChatResponse.ProductMatch> matches,
                                              List<String> relaxedFilters) {
        StringBuilder prompt = new StringBuilder("User request: ").append(message)
                .append("\nApplied filters: ").append(filters)
                .append("\nRelaxed filters for closest alternatives: ").append(relaxedFilters)
                .append("\nCatalogue matches (authoritative; do not add other items):");
        if (matches.isEmpty()) {
            prompt.append(" none");
        } else {
            for (AssistantChatResponse.ProductMatch product : matches) {
                prompt.append("\n- id=").append(product.id())
                        .append("; name=").append(product.name())
                        .append("; description=").append(product.description())
                        .append("; price=").append(product.price())
                        .append("; stock=").append(product.stock())
                        .append("; brand=").append(product.brand())
                        .append("; waterproof=").append(product.waterproof())
                        .append("; category=").append(product.category() == null ? null : product.category().name())
                        .append("; averageRating=").append(product.averageRating())
                        .append("; ratingCount=").append(product.ratingCount());
            }
        }
        return prompt.toString();
    }

    private static List<Product> closestAlternatives(List<Product> catalogue, ShoppingIntent filters,
                                                       Map<Long, Rating> ratings) {
        return catalogue.stream()
                .filter(product -> satisfiesHardConstraints(product, filters))
                .sorted(Comparator.<Product>comparingInt(product -> alternativeScore(product, filters, ratings)).reversed()
                        .thenComparing(comparator(filters, ratings)))
                .limit(3)
                .toList();
    }

    private static boolean satisfiesHardConstraints(Product product, ShoppingIntent filters) {
        if (product.getStock() == null || product.getStock() <= 0 || product.getPrice() == null) return false;
        if (filters.minPrice() != null && product.getPrice().compareTo(filters.minPrice()) < 0) return false;
        if (filters.maxPrice() != null && product.getPrice().compareTo(filters.maxPrice()) > 0) return false;
        return filters.waterproof() == null
                || filters.waterproof().equals(Boolean.TRUE.equals(product.getWaterproof()));
    }

    private static int alternativeScore(Product product, ShoppingIntent filters, Map<Long, Rating> ratings) {
        int score = 0;
        if (filters.category() != null && contains(product.getCategory() == null ? null : product.getCategory().getName(), filters.category())) score += 5;
        if (filters.brand() != null && contains(product.getBrand(), filters.brand())) score += 4;
        if (filters.minRating() != null && average(ratings.get(product.getId())) >= filters.minRating()) score += 2;
        for (String keyword : filters.keywords()) {
            if (contains(product.getName(), keyword) || contains(product.getDescription(), keyword)) score++;
        }
        return score;
    }

    private static List<String> relaxedFilterNames(ShoppingIntent filters) {
        java.util.ArrayList<String> relaxed = new java.util.ArrayList<>();
        if (filters.category() != null) relaxed.add("category");
        if (filters.brand() != null) relaxed.add("brand");
        if (!filters.keywords().isEmpty()) relaxed.add("keywords");
        if (filters.minRating() != null) relaxed.add("minimum rating");
        return List.copyOf(relaxed);
    }

    private static String classifyProviderFailure(RuntimeException exception) {
        if (exception instanceof AssistantProviderException providerException) {
            return providerException.getReasonCode();
        }
        if (exception instanceof com.google.genai.errors.ApiException apiException) {
            return "HTTP_" + apiException.code();
        }
        if (exception instanceof RestClientResponseException responseException) {
            return "HTTP_" + responseException.getStatusCode().value();
        }
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof SocketTimeoutException) return "NETWORK_TIMEOUT";
            if (cause instanceof UnknownHostException) return "DNS_FAILURE";
            if (cause instanceof ConnectException) return "CONNECTION_FAILURE";
            if (cause instanceof javax.net.ssl.SSLException) return "TLS_FAILURE";
            cause = cause.getCause();
        }
        if (exception instanceof ResourceAccessException) return "NETWORK_ERROR";
        return exception.getClass().getSimpleName();
    }

    private Map<Long, Rating> readRatings() {
        Map<Long, Rating> ratings = new HashMap<>();
        for (Review review : reviewRepository.findAll()) {
            if (review.getProduct() == null || review.getProduct().getId() == null || review.getRating() == null) {
                continue;
            }
            ratings.computeIfAbsent(review.getProduct().getId(), ignored -> new Rating())
                    .add(review.getRating());
        }
        return ratings;
    }

    private static ShoppingIntent normalize(ShoppingIntent intent) {
        if (intent == null) {
            return ShoppingIntent.empty();
        }
        Integer rating = intent.minRating() != null && intent.minRating() >= 1 && intent.minRating() <= 5
                ? intent.minRating() : null;
        String sort = intent.sort() != null && List.of("PRICE_ASC", "PRICE_DESC", "RATING_DESC").contains(intent.sort().toUpperCase(Locale.ROOT))
                ? intent.sort().toUpperCase(Locale.ROOT) : null;
        BigDecimal min = nonNegative(intent.minPrice());
        BigDecimal max = nonNegative(intent.maxPrice());
        if (min != null && max != null && min.compareTo(max) > 0) {
            BigDecimal temp = min;
            min = max;
            max = temp;
        }
        List<String> keywords = intent.keywords() == null ? List.of() : intent.keywords().stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .limit(8)
                .toList();
        return new ShoppingIntent(clean(intent.category()), clean(intent.brand()), keywords, min, max, rating, sort, intent.waterproof());
    }

    private static BigDecimal nonNegative(BigDecimal value) {
        return value != null && value.signum() >= 0 ? value : null;
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static boolean matches(Product product, ShoppingIntent intent, Map<Long, Rating> ratings) {
        if (product.getStock() == null || product.getStock() <= 0) {
            return false;
        }
        if (intent.category() != null && (product.getCategory() == null || product.getCategory().getName() == null
                || !contains(product.getCategory().getName(), intent.category()))) {
            return false;
        }
        if (intent.brand() != null && !contains(product.getBrand(), intent.brand())) {
            return false;
        }
        if (intent.minPrice() != null && (product.getPrice() == null || product.getPrice().compareTo(intent.minPrice()) < 0)) {
            return false;
        }
        if (intent.maxPrice() != null && (product.getPrice() == null || product.getPrice().compareTo(intent.maxPrice()) > 0)) {
            return false;
        }
        if (intent.waterproof() != null && !intent.waterproof().equals(Boolean.TRUE.equals(product.getWaterproof()))) {
            return false;
        }
        if (intent.minRating() != null && average(ratings.get(product.getId())) < intent.minRating()) {
            return false;
        }
        return intent.keywords().isEmpty() || intent.keywords().stream().anyMatch(keyword ->
                contains(product.getName(), keyword) || contains(product.getDescription(), keyword));
    }

    private static Comparator<Product> comparator(ShoppingIntent intent, Map<Long, Rating> ratings) {
        Comparator<Product> comparator = Comparator.comparing(Product::getId, Comparator.nullsLast(Long::compareTo));
        if ("PRICE_ASC".equals(intent.sort())) {
            comparator = Comparator.comparing(Product::getPrice, Comparator.nullsLast(BigDecimal::compareTo));
        } else if ("PRICE_DESC".equals(intent.sort())) {
            comparator = Comparator.comparing(Product::getPrice, Comparator.nullsLast(BigDecimal::compareTo)).reversed();
        } else if ("RATING_DESC".equals(intent.sort())) {
            comparator = Comparator.comparingDouble((Product product) -> average(ratings.get(product.getId()))).reversed();
        }
        return comparator;
    }

    private static AssistantChatResponse.ProductMatch toMatch(Product product, Rating rating) {
        AssistantChatResponse.CategoryMatch category = product.getCategory() == null ? null
                : new AssistantChatResponse.CategoryMatch(product.getCategory().getId(), product.getCategory().getName());
        return new AssistantChatResponse.ProductMatch(product.getId(), product.getName(), product.getDescription(),
                product.getPrice(), product.getStock(), product.getBrand(), product.getWaterproof(), category,
                product.getImageUrl(), average(rating), rating == null ? 0 : rating.count);
    }

    private static boolean contains(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT));
    }

    private static double average(Rating rating) {
        return rating == null || rating.count == 0 ? 0 : (double) rating.total / rating.count;
    }

    private static class Rating {
        private int total;
        private long count;

        private void add(int value) {
            total += value;
            count++;
        }
    }
}