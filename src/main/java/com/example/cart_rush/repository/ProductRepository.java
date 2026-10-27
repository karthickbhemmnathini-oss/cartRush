package com.example.cart_rush.repository;

import com.example.cart_rush.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import org.springframework.data.jpa.repository.Query;
import java.util.List;


public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findByNameContainingIgnoreCase(String name);

    List<Product> findBySupplierId(Long supplierId);

    List<Product> findByBrandContainingIgnoreCase(String brand);

    List<Product> findByPriceBetween(BigDecimal minPrice,
                                     BigDecimal maxPrice);

    @Query("""
    SELECT p FROM Product p
    WHERE (:name IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :name, '%')))
    AND (:brand IS NULL OR LOWER(p.brand) LIKE LOWER(CONCAT('%', :brand, '%')))
    AND (:minPrice IS NULL OR p.price >= :minPrice)
    AND (:maxPrice IS NULL OR p.price <= :maxPrice)
""")
    List<Product> filterProducts(
            @Param("name") String name,
            @Param("brand") String brand,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice
    );
}
