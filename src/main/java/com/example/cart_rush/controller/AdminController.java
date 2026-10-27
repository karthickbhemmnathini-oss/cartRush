package com.example.cart_rush.controller;

import com.example.cart_rush.entity.*;
import com.example.cart_rush.service.*;
import org.springframework.web.bind.annotation.*;
import com.example.cart_rush.service.OrderItemService;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final UserService userService;
    private final SupplierService supplierService;
    private final ProductService productService;
    private final CategoryService categoryService;
    private final OrderService orderService;
    private final ReviewService reviewService;
    private final OrderItemService orderItemService;
    private final FraudDetectionService fraudDetectionService;
    private final AnalyticsService analyticsService;

    public AdminController(
            UserService userService,
            SupplierService supplierService,
            ProductService productService,
            CategoryService categoryService,
            OrderService orderService,
            ReviewService reviewService,
            OrderItemService orderItemService,
            FraudDetectionService fraudDetectionService,
            AnalyticsService analyticsService) {

        this.userService = userService;
        this.supplierService = supplierService;
        this.productService = productService;
        this.categoryService = categoryService;
        this.orderService = orderService;
        this.reviewService = reviewService;
        this.orderItemService = orderItemService;
        this.fraudDetectionService = fraudDetectionService;
        this.analyticsService = analyticsService;
    }

    // USERS

    @GetMapping("/users")
    public List<User> getAllUsers() {
        return userService.getAllUsers();
    }

    @GetMapping("/users/{id}")
    public User getUser(@PathVariable Long id) {
        return userService.getUserById(id);
    }

    @DeleteMapping("/users/{id}")
    public String deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return "User deleted successfully";
    }


    // SUPPLIERS

    @GetMapping("/suppliers")
    public List<Supplier> getAllSuppliers() {
        return supplierService.getAllSuppliers();
    }

    @GetMapping("/suppliers/{id}")
    public Supplier getSupplier(@PathVariable Long id) {
        return supplierService.getSupplierById(id);
    }

    @DeleteMapping("/suppliers/{id}")
    public String deleteSupplier(@PathVariable Long id) {
        supplierService.deleteSupplier(id);
        return "Supplier deleted successfully";
    }


    // PRODUCTS

    @GetMapping("/products")
    public List<Product> getAllProducts() {
        return productService.getAllProducts();
    }

    @PostMapping("/products")
    public Product createProduct(@RequestBody Product product) {
        return productService.createProduct(product);
    }

    @GetMapping("/products/{id}")
    public Product getProduct(@PathVariable Long id) {
        return productService.getProductById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));
    }

    @PutMapping("/products/{id}")
    public Product updateProduct(
            @PathVariable Long id,
            @RequestBody Product product) {

        return productService.updateProduct(id, product);
    }

    @DeleteMapping("/products/{id}")
    public String deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return "Product deleted successfully";
    }


    // CATEGORIES

    @GetMapping("/categories")
    public List<Category> getAllCategories() {
        return categoryService.getAllCategories();
    }

    @PostMapping("/categories")
    public Category createCategory(@RequestBody Category category) {
        return categoryService.createCategory(category);
    }


    // ORDERS

    @GetMapping("/orders")
    public List<Order> getAllOrders() {
        return orderService.getAllOrders();
    }

    @GetMapping("/orders/{id}")
    public Order getOrder(@PathVariable Long id) {
        return orderService.getOrderById(id);
    }

    @PutMapping("/orders/{id}/status")
    public Order updateOrderStatus(
            @PathVariable Long id,
            @RequestParam String status) {

        return orderService.updateOrderStatus(id, status);
    }


    // REVIEWS

    @GetMapping("/reviews")
    public List<Review> getAllReviews() {
        return reviewService.getAllReviews();
    }

    @GetMapping("/reviews/{id}")
    public Review getReview(@PathVariable Long id) {
        return reviewService.getReviewById(id);
    }

    @DeleteMapping("/reviews/{id}")
    public String deleteReview(@PathVariable Long id) {
        reviewService.deleteReview(id);
        return "Review deleted successfully";
    }
    // DASHBOARD

    @GetMapping("/dashboard")
    public Map<String, Object> getDashboard() {

        Map<String, Object> dashboard = new HashMap<>();

        dashboard.put("users", (long) userService.getAllUsers().size());
        dashboard.put("suppliers", (long) supplierService.getAllSuppliers().size());
        dashboard.put("products", (long) productService.getAllProducts().size());
        dashboard.put("orders", (long) orderService.getAllOrders().size());
        dashboard.put("reviews", (long) reviewService.getAllReviews().size());

        java.math.BigDecimal revenue = java.math.BigDecimal.ZERO;

        for (var order : orderService.getAllOrders()) {

            if ("CANCELLED".equalsIgnoreCase(order.getStatus())) {
                continue;
            }

            var items = orderItemService.getItemsByOrderId(order.getId());

            for (var item : items) {
                revenue = revenue.add(
                        item.getProduct().getPrice()
                                .multiply(
                                        java.math.BigDecimal.valueOf(item.getQuantity())
                                )
                );
            }
        }

        dashboard.put("revenue", revenue);
        long lowStock = productService.getAllProducts().stream()
                .filter(p -> p.getStock() <= 10)
                .count();

        dashboard.put("lowStock", lowStock);
        return dashboard;
    }

    // ANALYTICS (charts)

    @GetMapping("/analytics")
    public AnalyticsService.Analytics getAnalytics() {
        return analyticsService.getAnalytics();
    }

    // FRAUD DETECTION

    @GetMapping("/fraud/suppliers/{id}")
    public Map<String, Object> detectSupplierFraud(
            @PathVariable Long id) {

        return fraudDetectionService.detectSupplierFraud(id);
    }

    @GetMapping("/fraud/suppliers")
    public List<Map<String, Object>> detectAllSuppliersFraud() {
        return fraudDetectionService.detectAllSuppliersFraud();
    }

}