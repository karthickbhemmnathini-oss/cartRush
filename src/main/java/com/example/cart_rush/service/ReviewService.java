package com.example.cart_rush.service;

import com.example.cart_rush.entity.Product;
import com.example.cart_rush.entity.Review;
import com.example.cart_rush.entity.User;
import com.example.cart_rush.repository.ProductRepository;
import com.example.cart_rush.repository.ReviewRepository;
import com.example.cart_rush.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    public ReviewService(
            ReviewRepository reviewRepository,
            UserRepository userRepository,
            ProductRepository productRepository) {

        this.reviewRepository = reviewRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
    }

    public Review addReview(
            Long userId,
            Long productId,
            Integer rating,
            String comment) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        if (reviewRepository.existsByUserIdAndProductId(userId, productId)) {
            throw new RuntimeException("You have already reviewed this product");
        }

        if (rating == null || rating < 1 || rating > 5) {
            throw new RuntimeException("Rating must be between 1 and 5");
        }

        Review review = new Review(
                user,
                product,
                rating,
                comment
        );

        return reviewRepository.save(review);
    }

    public Review getReviewById(Long id) {
        return reviewRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Review not found"));
    }

    public List<Review> getReviewsByProductId(Long productId) {
        return reviewRepository.findByProductId(productId);
    }

    public List<Review> getReviewsByUserId(Long userId) {
        return reviewRepository.findByUserId(userId);
    }

    public List<Review> getAllReviews() {
        return reviewRepository.findAll();
    }

    public void deleteReview(Long id) {
        if (!reviewRepository.existsById(id)) {
            throw new RuntimeException("Review not found");
        }

        reviewRepository.deleteById(id);
    }

    public Review updateReview(
            Long id,
            Integer rating,
            String comment) {

        Review review = reviewRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Review not found"));

        if (rating == null || rating < 1 || rating > 5) {
            throw new RuntimeException("Rating must be between 1 and 5");
        }

        review.setRating(rating);
        review.setComment(comment);

        return reviewRepository.save(review);
    }

    public void deleteReview(Long id, Long userId) {

        Review review = reviewRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Review not found"));

        if (!review.getUser().getId().equals(userId)) {
            throw new RuntimeException("You can only delete your own review");
        }

        reviewRepository.delete(review);
    }

}
