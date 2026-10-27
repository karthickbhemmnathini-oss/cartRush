package com.example.cart_rush.controller;

import com.example.cart_rush.entity.Order;
import com.example.cart_rush.entity.OrderItem;
import com.example.cart_rush.entity.Supplier;
import com.example.cart_rush.entity.User;
import com.example.cart_rush.repository.OrderItemRepository;
import com.example.cart_rush.repository.OrderRepository;
import com.example.cart_rush.repository.SupplierRepository;
import com.example.cart_rush.repository.UserRepository;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/** Supplier order view, limited to orders containing that supplier's products. */
@RestController
@RequestMapping("/api/supplier-portal/{userId}/orders")
public class SupplierOrderController {
    private final OrderItemRepository orderItems;
    private final OrderRepository orders;
    private final SupplierRepository suppliers;
    private final UserRepository users;

    public SupplierOrderController(OrderItemRepository orderItems, OrderRepository orders,
                                   SupplierRepository suppliers, UserRepository users) {
        this.orderItems = orderItems;
        this.orders = orders;
        this.suppliers = suppliers;
        this.users = users;
    }

    private Supplier requireSupplier(Long userId) {
        User user = users.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
        if (!"SUPPLIER".equalsIgnoreCase(user.getRole())) throw new RuntimeException("Supplier account required");
        return suppliers.findByUserId(userId).orElseThrow(() -> new RuntimeException("Supplier profile not found"));
    }

    @GetMapping
    public List<Map<String, Object>> list(@PathVariable Long userId) {
        Supplier supplier = requireSupplier(userId);
        Map<Long, Map<String, Object>> grouped = new LinkedHashMap<>();
        orderItems.findAll().stream()
                .filter(item -> item.getProduct() != null && item.getProduct().getSupplier() != null
                        && Objects.equals(item.getProduct().getSupplier().getId(), supplier.getId()))
                .sorted(Comparator.comparing((OrderItem i) -> i.getOrder().getCreatedAt(),
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .forEach(item -> {
                    Order order = item.getOrder();
                    Map<String, Object> row = grouped.computeIfAbsent(order.getId(), id -> {
                        Map<String, Object> data = new LinkedHashMap<>();
                        data.put("id", order.getId());
                        data.put("status", order.getStatus());
                        data.put("createdAt", order.getCreatedAt());
                        data.put("customer", order.getUser() == null ? "Customer" : order.getUser().getName());
                        data.put("items", new ArrayList<Map<String, Object>>());
                        return data;
                    });
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> items = (List<Map<String, Object>>) row.get("items");
                    Map<String, Object> product = new LinkedHashMap<>();
                    product.put("id", item.getProduct().getId());
                    product.put("name", item.getProduct().getName());
                    product.put("price", item.getProduct().getPrice());
                    product.put("quantity", item.getQuantity());
                    items.add(product);
                });
        return new ArrayList<>(grouped.values());
    }

    @PutMapping("/{orderId}/status")
    public Map<String, Object> updateStatus(@PathVariable Long userId, @PathVariable Long orderId,
                                            @RequestParam String status) {
        Supplier supplier = requireSupplier(userId);
        Set<String> allowed = Set.of("PROCESSING", "SHIPPED", "DELIVERED", "CANCELLED");
        String normalized = status == null ? "" : status.toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) throw new RuntimeException("Unsupported order status");
        boolean ownsItem = orderItems.findByOrderId(orderId).stream().anyMatch(item -> item.getProduct() != null
                && item.getProduct().getSupplier() != null
                && Objects.equals(item.getProduct().getSupplier().getId(), supplier.getId()));
        if (!ownsItem) throw new RuntimeException("This order does not contain your products");
        Order order = orders.findById(orderId).orElseThrow(() -> new RuntimeException("Order not found"));
        order.setStatus(normalized);
        orders.save(order);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", order.getId());
        result.put("status", order.getStatus());
        return result;
    }
}
