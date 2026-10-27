package com.example.cart_rush.controller;

import com.example.cart_rush.entity.Review;
import com.example.cart_rush.service.ReviewService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping("/user/{userId}/product/{productId}")
    public Review addReview(
            @PathVariable Long userId,
            @PathVariable Long productId,
            @RequestParam Integer rating,
            @RequestParam String comment) {

        return reviewService.addReview(
                userId,
                productId,
                rating,
                comment
        );
    }

    @GetMapping("/{id}")
    public Review getReviewById(@PathVariable Long id) {
        return reviewService.getReviewById(id);
    }

    @GetMapping("/product/{productId}")
    public List<Review> getReviewsByProductId(
            @PathVariable Long productId) {

        return reviewService.getReviewsByProductId(productId);
    }

    @GetMapping("/user/{userId}")
    public List<Review> getReviewsByUserId(
            @PathVariable Long userId) {

        return reviewService.getReviewsByUserId(userId);
    }

    @GetMapping
    public List<Review> getAllReviews() {
        return reviewService.getAllReviews();
    }

    @DeleteMapping("/{id}")
    public String deleteReview(
            @PathVariable Long id,
            @RequestParam Long userId) {

        reviewService.deleteReview(id, userId);

        return "Review deleted successfully";
    }

    @PutMapping("/{id}")
    public Review updateReview(
            @PathVariable Long id,
            @RequestParam Integer rating,
            @RequestParam String comment) {

        return reviewService.updateReview(id, rating, comment);
    }

}
