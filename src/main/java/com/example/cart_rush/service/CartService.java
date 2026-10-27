package com.example.cart_rush.service;

import com.example.cart_rush.entity.Cart;
import com.example.cart_rush.repository.CartRepository;
import org.springframework.stereotype.Service;
import com.example.cart_rush.entity.User;
import com.example.cart_rush.repository.UserRepository;

@Service
public class CartService {

    private final CartRepository cartRepository;
    private final UserRepository userRepository;

    public CartService(
            CartRepository cartRepository,
            UserRepository userRepository
    ) {
        this.cartRepository = cartRepository;
        this.userRepository = userRepository;
    }

    public Cart createCart(Long userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (cartRepository.findByUserId(userId).isPresent()) {
            throw new RuntimeException("Cart already exists for this user");
        }

        Cart cart = new Cart();
        cart.setUser(user);

        return cartRepository.save(cart);
    }

    public Cart getCartByUserId(Long userId) {
        return cartRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Cart not found"));
    }
}
