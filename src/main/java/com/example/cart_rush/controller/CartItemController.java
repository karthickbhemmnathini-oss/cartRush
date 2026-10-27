package com.example.cart_rush.controller;

import com.example.cart_rush.entity.CartItem;
import com.example.cart_rush.service.CartItemService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cart-items")
public class CartItemController {

    private final CartItemService cartItemService;

    public CartItemController(CartItemService cartItemService) {
        this.cartItemService = cartItemService;
    }

    @PostMapping("/cart/{cartId}/product/{productId}")
    public CartItem addToCart(
            @PathVariable Long cartId,
            @PathVariable Long productId,
            @RequestParam Integer quantity) {

        return cartItemService.addToCart(cartId, productId, quantity);
    }

    @GetMapping("/cart/{cartId}")
    public List<CartItem> getItemsByCartId(@PathVariable Long cartId) {
        return cartItemService.getItemsByCartId(cartId);
    }

    @PutMapping("/{cartItemId}")
    public CartItem updateQuantity(
            @PathVariable Long cartItemId,
            @RequestParam Integer quantity) {

        return cartItemService.updateQuantity(cartItemId, quantity);
    }

    @DeleteMapping("/{cartItemId}")
    public String removeFromCart(@PathVariable Long cartItemId) {

        cartItemService.removeFromCart(cartItemId);

        return "Item removed from cart successfully";
    }
}
