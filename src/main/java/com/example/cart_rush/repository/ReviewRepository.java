package com.example.cart_rush.repository;

import com.example.cart_rush.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    List<Review> findByProduct_Supplier_Id(Long supplierId);

    List<Review> findByProductId(Long productId);

    List<Review> findByUserId(Long userId);

    boolean existsByUserIdAndProductId(Long userId, Long productId);


    long countDistinctProductByUserIdAndProduct_Supplier_Id(
            Long userId, Long supplierId);
}