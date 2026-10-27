package com.example.cart_rush.controller;

import com.example.cart_rush.entity.Category;
import com.example.cart_rush.entity.Product;
import com.example.cart_rush.entity.Supplier;
import com.example.cart_rush.entity.User;
import com.example.cart_rush.repository.CategoryRepository;
import com.example.cart_rush.repository.ProductRepository;
import com.example.cart_rush.repository.SupplierRepository;
import com.example.cart_rush.repository.UserRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Supplier-specific product operations, scoped to the logged-in supplier's user id. */
@RestController
@RequestMapping("/api/supplier-portal/{userId}/products")
public class SupplierPortalController {
    private final ProductRepository products;
    private final SupplierRepository suppliers;
    private final UserRepository users;
    private final CategoryRepository categories;

    public SupplierPortalController(ProductRepository products, SupplierRepository suppliers,
                                    UserRepository users, CategoryRepository categories) {
        this.products = products;
        this.suppliers = suppliers;
        this.users = users;
        this.categories = categories;
    }

    private Supplier requireSupplier(Long userId) {
        User user = users.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
        if (!"SUPPLIER".equalsIgnoreCase(user.getRole())) {
            throw new RuntimeException("Only supplier accounts can manage products");
        }
        return suppliers.findByUserId(userId).orElseThrow(() -> new RuntimeException("Supplier profile not found"));
    }

    @GetMapping
    public List<Product> list(@PathVariable Long userId) {
        Supplier supplier = requireSupplier(userId);
        return products.findBySupplierId(supplier.getId());
    }

    @PostMapping
    public Product create(@PathVariable Long userId, @RequestBody Product product) {
        Supplier supplier = requireSupplier(userId);
        product.setId(null);
        product.setSupplier(supplier);
        if (product.getCategory() != null && product.getCategory().getId() != null) {
            Category category = categories.findById(product.getCategory().getId())
                    .orElseThrow(() -> new RuntimeException("Category not found"));
            product.setCategory(category);
        } else {
            product.setCategory(null);
        }
        return products.save(product);
    }

    @PutMapping("/{productId}")
    public Product update(@PathVariable Long userId, @PathVariable Long productId, @RequestBody Product incoming) {
        Supplier supplier = requireSupplier(userId);
        Product existing = products.findById(productId).orElseThrow(() -> new RuntimeException("Product not found"));
        if (existing.getSupplier() == null || !existing.getSupplier().getId().equals(supplier.getId())) {
            throw new RuntimeException("You can only edit your own products");
        }
        existing.setName(incoming.getName());
        existing.setBrand(incoming.getBrand());
        existing.setPrice(incoming.getPrice());
        existing.setStock(incoming.getStock());
        existing.setDescription(incoming.getDescription());
        existing.setImageUrl(incoming.getImageUrl());
        existing.setWaterproof(incoming.getWaterproof());
        if (incoming.getCategory() != null && incoming.getCategory().getId() != null) {
            existing.setCategory(categories.findById(incoming.getCategory().getId())
                    .orElseThrow(() -> new RuntimeException("Category not found")));
        } else existing.setCategory(null);
        return products.save(existing);
    }

    @DeleteMapping("/{productId}")
    public String delete(@PathVariable Long userId, @PathVariable Long productId) {
        Supplier supplier = requireSupplier(userId);
        Product existing = products.findById(productId).orElseThrow(() -> new RuntimeException("Product not found"));
        if (existing.getSupplier() == null || !existing.getSupplier().getId().equals(supplier.getId())) {
            throw new RuntimeException("You can only delete your own products");
        }
        products.delete(existing);
        return "Product deleted successfully";
    }
}
