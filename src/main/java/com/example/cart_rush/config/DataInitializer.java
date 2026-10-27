package com.example.cart_rush.config;

import com.example.cart_rush.entity.Order;
import com.example.cart_rush.entity.OrderItem;
import com.example.cart_rush.entity.Product;
import com.example.cart_rush.entity.User;
import com.example.cart_rush.repository.OrderItemRepository;
import com.example.cart_rush.repository.OrderRepository;
import com.example.cart_rush.repository.ProductRepository;
import com.example.cart_rush.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 1) Gives orders that were created before Order.createdAt existed a date,
 *    so they stay put on the charts instead of drifting to "today".
 * 2) Optionally seeds ~90 days of demo orders (app.seed-demo-data=true)
 *    so the charts have something to show. Turn it off again afterwards.
 */
@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    @Value("${app.seed-demo-data:false}")
    private boolean seedDemoData;

    public DataInitializer(OrderRepository orderRepository,
                           OrderItemRepository orderItemRepository,
                           ProductRepository productRepository,
                           UserRepository userRepository) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        backfillOrderDates();
        if (seedDemoData) {
            seedDemoOrders();
        }
    }

    private void backfillOrderDates() {
        List<Order> undated = orderRepository.findAll().stream()
                .filter(o -> o.getCreatedAt() == null)
                .toList();
        if (undated.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        undated.forEach(o -> o.setCreatedAt(now));
        orderRepository.saveAll(undated);
        log.info("Backfilled createdAt for {} existing orders", undated.size());
    }

    private void seedDemoOrders() {
        if (orderRepository.count() > 40) {
            log.info("Demo seeding skipped: {} orders already exist", orderRepository.count());
            return;
        }
        List<Product> products = productRepository.findAll();
        List<User> users = userRepository.findAll();
        if (products.isEmpty() || users.isEmpty()) {
            log.warn("Demo seeding skipped: need at least one product and one user");
            return;
        }

        Random rnd = new Random(42);
        LocalDateTime now = LocalDateTime.now();
        int created = 0;

        for (int daysAgo = 89; daysAgo >= 0; daysAgo--) {
            LocalDateTime base = now.minusDays(daysAgo);
            boolean weekend = base.getDayOfWeek().getValue() >= 6;
            int growth = (90 - daysAgo) / 30;            // store slowly grows
            int count = rnd.nextInt(3) + growth + (weekend ? 1 : 0);

            for (int n = 0; n < count; n++) {
                LocalDateTime when = base.withHour(8 + rnd.nextInt(14)).withMinute(rnd.nextInt(60));
                if (when.isAfter(now)) {
                    when = now.minusMinutes(rnd.nextInt(120) + 1);
                }

                String status;
                int roll = rnd.nextInt(100);
                if (daysAgo > 7) {
                    status = roll < 88 ? "DELIVERED" : (roll < 95 ? "SHIPPED" : "CANCELLED");
                } else {
                    status = roll < 40 ? "PLACED" : (roll < 75 ? "SHIPPED" : (roll < 92 ? "DELIVERED" : "CANCELLED"));
                }

                Order order = new Order(users.get(rnd.nextInt(users.size())), status);
                order.setCreatedAt(when);
                order = orderRepository.save(order);

                List<Product> pool = new ArrayList<>(products);
                Collections.shuffle(pool, rnd);
                int lines = Math.min(pool.size(), 1 + rnd.nextInt(3));
                for (int i = 0; i < lines; i++) {
                    orderItemRepository.save(new OrderItem(order, pool.get(i), 1 + rnd.nextInt(3)));
                }
                created++;
            }
        }
        log.info("Seeded {} demo orders across the last 90 days", created);
    }
}
