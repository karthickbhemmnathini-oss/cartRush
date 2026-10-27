package com.example.cart_rush.service;

import com.example.cart_rush.entity.Order;
import com.example.cart_rush.repository.OrderRepository;
import org.springframework.stereotype.Service;

import java.util.List;

import com.example.cart_rush.entity.Cart;
import com.example.cart_rush.entity.CartItem;
import com.example.cart_rush.entity.OrderItem;
import com.example.cart_rush.entity.Product;
import com.example.cart_rush.repository.CartItemRepository;
import com.example.cart_rush.repository.CartRepository;
import com.example.cart_rush.repository.OrderItemRepository;
import com.example.cart_rush.repository.ProductRepository;

import java.util.ArrayList;
@Service
public class OrderService {

    private final OrderRepository orderRepository;

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    public OrderService(
            OrderRepository orderRepository,
            CartRepository cartRepository,
            CartItemRepository cartItemRepository,
            OrderItemRepository orderItemRepository,
            ProductRepository productRepository) {

        this.orderRepository = orderRepository;
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.orderItemRepository = orderItemRepository;
        this.productRepository = productRepository;
    }

    public Order placeOrder(Long userId) {

        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Cart not found"));

        List<CartItem> cartItems = cartItemRepository.findByCartId(cart.getId());

        if (cartItems.isEmpty()) {
            throw new RuntimeException("Cart is empty");
        }

        Order order = new Order(cart.getUser(), "PLACED");
        order = orderRepository.save(order);

        for (CartItem cartItem : cartItems) {

            if (cartItem.getQuantity() == null || cartItem.getQuantity() <= 0) {
                throw new RuntimeException("Invalid cart item quantity");
            }

            Product product = cartItem.getProduct();

            if (product == null) {
                throw new RuntimeException("Product not found for cart item");
            }

            if (product.getStock() < cartItem.getQuantity()) {
                throw new RuntimeException(
                        "Not enough stock for product: " + product.getName()
                );
            }

            product.setStock(product.getStock() - cartItem.getQuantity());
            productRepository.save(product);

            OrderItem orderItem = new OrderItem(
                    order,
                    product,
                    cartItem.getQuantity()
            );

            orderItemRepository.save(orderItem);
        }

        cartItemRepository.deleteAll(cartItems);

        return order;
    }

    public Order getOrderById(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found"));
    }

    public List<Order> getOrdersByUserId(Long userId) {
        return orderRepository.findByUserId(userId);
    }

    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }

    public Order updateOrderStatus(Long id, String status) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        order.setStatus(status);

        return orderRepository.save(order);
    }
}