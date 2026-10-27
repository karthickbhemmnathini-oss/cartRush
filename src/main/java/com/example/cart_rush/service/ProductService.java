package com.example.cart_rush.service;

import com.example.cart_rush.entity.Category;
import com.example.cart_rush.entity.Product;
import com.example.cart_rush.repository.ProductRepository;
import org.springframework.stereotype.Service;
import java.util.Optional;
import com.example.cart_rush.repository.CategoryRepository;
import java.util.List;
import java.math.BigDecimal;
import com.example.cart_rush.entity.Supplier;
import com.example.cart_rush.repository.SupplierRepository;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final SupplierRepository supplierRepository;

    public ProductService(ProductRepository productRepository,
                          CategoryRepository categoryRepository,
                          SupplierRepository supplierRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.supplierRepository = supplierRepository;
    }

    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }
    public Product createProduct(Product product) {
        product.setId(null);

        if (product.getCategory() != null
                && product.getCategory().getId() != null) {

            Category category = categoryRepository.findById(
                    product.getCategory().getId()
            ).orElseThrow(() ->
                    new RuntimeException("Category not found"));

            product.setCategory(category);
        }
        if (product.getSupplier() != null
                && product.getSupplier().getId() != null) {

            Supplier supplier = supplierRepository.findById(
                    product.getSupplier().getId()
            ).orElseThrow(() ->
                    new RuntimeException("Supplier not found"));

            product.setSupplier(supplier);
        }
        return productRepository.save(product);
    }
    public Optional<Product> getProductById(Long id) {
        return productRepository.findById(id);
    }

    public void deleteProduct(Long id) {
        productRepository.deleteById(id);
    }

    public Product updateProduct(Long id, Product updatedProduct) {
        Product existingProduct = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        existingProduct.setName(updatedProduct.getName());
        existingProduct.setDescription(updatedProduct.getDescription());
        existingProduct.setPrice(updatedProduct.getPrice());
        existingProduct.setStock(updatedProduct.getStock());
        existingProduct.setBrand(updatedProduct.getBrand());
        existingProduct.setWaterproof(updatedProduct.getWaterproof());
        existingProduct.setImageUrl(updatedProduct.getImageUrl());
        existingProduct.setCategory(updatedProduct.getCategory());

        return productRepository.save(existingProduct);
    }

    public List<Product> searchByName(String name) {
        return productRepository.findByNameContainingIgnoreCase(name);
    }

    public List<Product> searchByBrand(String brand) {
        return productRepository.findByBrandContainingIgnoreCase(brand);
    }

    public List<Product> searchByPriceRange(BigDecimal minPrice,
                                            BigDecimal maxPrice) {
        return productRepository.findByPriceBetween(minPrice, maxPrice);
    }

    public List<Product> filterProducts(
            String name,
            String brand,
            BigDecimal minPrice,
            BigDecimal maxPrice
    ) {
        return productRepository.filterProducts(
                name,
                brand,
                minPrice,
                maxPrice
        );
    }

}
