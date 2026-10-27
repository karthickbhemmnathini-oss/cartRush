
package com.example.cart_rush.service;

import com.example.cart_rush.entity.Review;
import com.example.cart_rush.entity.Supplier;
import com.example.cart_rush.repository.ReviewRepository;
import com.example.cart_rush.repository.SupplierRepository;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


@Service
public class FraudDetectionService {

    private final ReviewRepository reviewRepository;
    private final SupplierRepository supplierRepository;

    public FraudDetectionService(
            ReviewRepository reviewRepository,
            SupplierRepository supplierRepository) {
        this.reviewRepository = reviewRepository;
        this.supplierRepository = supplierRepository;
    }

    public List<Review> getReviewsForSupplier(Long supplierId) {
        Supplier supplier = supplierRepository.findById(supplierId)
                .orElseThrow(() ->
                        new RuntimeException("Supplier not found"));

        return reviewRepository.findByProduct_Supplier_Id(
                supplier.getId()
        );
    }

    public java.util.Map<String, Object> getSupplierReviewStats(
            Long supplierId) {

        List<Review> reviews = getReviewsForSupplier(supplierId);

        int totalReviews = reviews.size();

        double averageRating = reviews.stream()
                .filter(review -> review.getRating() != null)
                .mapToInt(Review::getRating)
                .average()
                .orElse(0.0);

        long lowRatingCount = reviews.stream()
                .filter(review -> review.getRating() != null)
                .filter(review -> review.getRating() <= 2)
                .count();

        double lowRatingPercentage = totalReviews == 0
                ? 0.0
                : (lowRatingCount * 100.0 / totalReviews);

        java.util.Map<String, Object> stats =
                new java.util.HashMap<>();

        stats.put("supplierId", supplierId);
        stats.put("totalReviews", totalReviews);
        stats.put("averageRating",
                Math.round(averageRating * 100.0) / 100.0);
        stats.put("lowRatingCount", lowRatingCount);
        stats.put("lowRatingPercentage",
                Math.round(lowRatingPercentage * 100.0) / 100.0);

        return stats;
    }

    public java.util.Map<String, Object> detectSupplierFraud(
            Long supplierId) {

        java.util.Map<String, Object> stats =
                getSupplierReviewStats(supplierId);

        int totalReviews = ((Number) stats.get("totalReviews")).intValue();
        double averageRating =
                ((Number) stats.get("averageRating")).doubleValue();
        double lowRatingPercentage =
                ((Number) stats.get("lowRatingPercentage")).doubleValue();

        int score = 0;
        java.util.List<String> reasons = new java.util.ArrayList<>();

        if (totalReviews >= 5 && averageRating <= 2.0) {
            score += 40;
            reasons.add("Average rating is 2.0 or lower");
        }

        if (totalReviews >= 5 && lowRatingPercentage >= 60.0) {
            score += 30;
            reasons.add("At least 60% of reviews have low ratings");
        }

        if (totalReviews >= 10) {
            score += 10;
            reasons.add("Supplier has at least 10 reviews");
        }



        List<Review> reviews = getReviewsForSupplier(supplierId);

        java.util.Map<Long, java.util.Set<Long>> productsByCustomer =
                new java.util.HashMap<>();

        for (Review review : reviews) {
            if (review.getUser() != null && review.getProduct() != null) {
                Long userId = review.getUser().getId();
                Long productId = review.getProduct().getId();

                productsByCustomer
                        .computeIfAbsent(userId, id -> new java.util.HashSet<>())
                        .add(productId);
            }
        }

        boolean multipleProductsReviewed = productsByCustomer.values()
                .stream()
                .anyMatch(products -> products.size() >= 2);

        if (multipleProductsReviewed) {
            score += 20;
            reasons.add(
                    "A customer reviewed multiple products from this supplier"
            );
        }

        score = Math.min(score, 100);

        String status;
        if (score >= 70) {
            status = "HIGH_RISK";
        } else if (score >= 50) {
            status = "SUSPICIOUS";
        } else {
            status = "NORMAL";
        }

        java.util.List<java.util.Map<String, Object>> productDetails =
                new java.util.ArrayList<>();

        java.util.Map<Long, java.util.List<Review>> reviewsByProduct =
                new java.util.HashMap<>();

        for (Review review : reviews) {
            if (review.getProduct() != null) {
                Long productId = review.getProduct().getId();

                reviewsByProduct
                        .computeIfAbsent(productId, id -> new java.util.ArrayList<>())
                        .add(review);
            }
        }

        for (java.util.Map.Entry<Long, java.util.List<Review>> entry
                : reviewsByProduct.entrySet()) {

            java.util.List<Review> productReviews = entry.getValue();

            java.util.Map<String, Object> productInfo =
                    new java.util.HashMap<>();

            productInfo.put("productId", entry.getKey());
            productInfo.put("productName",
                    productReviews.get(0).getProduct().getName());
            productInfo.put("reviewCount", productReviews.size());

            double productAverage = productReviews.stream()
                    .filter(r -> r.getRating() != null)
                    .mapToInt(Review::getRating)
                    .average()
                    .orElse(0.0);

            productInfo.put("averageRating",
                    Math.round(productAverage * 100.0) / 100.0);

            productDetails.add(productInfo);
        }

        stats.put("productDetails", productDetails);
        stats.put("fraudScore", score);
        stats.put("status", status);
        stats.put("reasons", reasons);

        return stats;
    }

    public List<java.util.Map<String, Object>> detectAllSuppliersFraud() {

        List<java.util.Map<String, Object>> results =
                new java.util.ArrayList<>();

        for (Supplier supplier : supplierRepository.findAll()) {
            results.add(detectSupplierFraud(supplier.getId()));
        }

        return results;
    }
}
