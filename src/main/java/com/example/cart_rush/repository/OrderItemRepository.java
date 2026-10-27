package com.example.cart_rush.repository;

import com.example.cart_rush.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    List<OrderItem> findByOrderId(Long orderId);

    @Query("""
            SELECT oi FROM OrderItem oi
            JOIN FETCH oi.order
            JOIN FETCH oi.product p
            LEFT JOIN FETCH p.category
            """)
    List<OrderItem> findAllForAnalytics();
}
