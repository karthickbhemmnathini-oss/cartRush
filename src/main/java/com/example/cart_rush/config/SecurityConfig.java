package com.example.cart_rush.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/auth/register/customer",
                                "/api/auth/register/supplier",
                                "/api/auth/login",
                                "/api/auth/users/**",
                                "/api/suppliers/**",
                                "/api/carts/**",
                                "/api/cart-items/**",
                                "/api/products/**",
                                "/api/supplier-portal/**",
                                "/api/orders/**",
                                "/api/order-items/**",
                                "/api/reviews/**",
                                "/api/admin/**",
                                "/api/categories/**",
                                "/api/assistant/**",
                                "/", "/index.html", "/css/**", "/js/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                );

        return http.build();
    }
}