package com.example.cart_rush.service;

import com.example.cart_rush.entity.CartItem;
import com.example.cart_rush.repository.CartItemRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import com.example.cart_rush.entity.Cart;
import com.example.cart_rush.entity.Product;
import com.example.cart_rush.repository.CartRepository;
import com.example.cart_rush.repository.ProductRepository;

@Service
public class CartItemService {

    private final CartItemRepository cartItemRepository;
    private final CartRepository cartRepository;
    private final ProductRepository productRepository;

    public CartItemService(
            CartItemRepository cartItemRepository,
            CartRepository cartRepository,
            ProductRepository productRepository
    ) {
        this.cartItemRepository = cartItemRepository;
        this.cartRepository = cartRepository;
        this.productRepository = productRepository;
    }

    public CartItem addToCart(Long cartId, Long productId, Integer quantity) {

        Cart cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new RuntimeException("Cart not found"));

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        if (quantity <= 0) {
            throw new RuntimeException("Quantity must be greater than zero");
        }

        CartItem cartItem = new CartItem();
        cartItem.setCart(cart);
        cartItem.setProduct(product);
        cartItem.setQuantity(quantity);

        return cartItemRepository.save(cartItem);
    }

    public List<CartItem> getItemsByCartId(Long cartId) {
        return cartItemRepository.findByCartId(cartId);
    }

    public CartItem updateQuantity(Long cartItemId, Integer quantity) {

        CartItem cartItem = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new RuntimeException("Cart item not found"));

        if (quantity <= 0) {
            throw new RuntimeException("Quantity must be greater than zero");
        }

        cartItem.setQuantity(quantity);

        return cartItemRepository.save(cartItem);
    }

    public void removeFromCart(Long cartItemId) {

        if (!cartItemRepository.existsById(cartItemId)) {
            throw new RuntimeException("Cart item not found");
        }

        cartItemRepository.deleteById(cartItemId);
    }
}