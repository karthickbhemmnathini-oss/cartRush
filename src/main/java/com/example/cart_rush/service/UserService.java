package com.example.cart_rush.service;

import com.example.cart_rush.entity.User;
import com.example.cart_rush.repository.UserRepository;
import org.springframework.stereotype.Service;
import com.example.cart_rush.entity.Supplier;
import com.example.cart_rush.repository.SupplierRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.example.cart_rush.controller.LoginRequest;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;

    private final SupplierRepository supplierRepository;

    private final PasswordEncoder passwordEncoder;

    public UserService(
            UserRepository userRepository,
            SupplierRepository supplierRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.supplierRepository = supplierRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public Supplier registerSupplier(
            User user,
            String businessName,
            String phone,
            String address
    ) {

        if (userRepository.existsByEmail(user.getEmail())) {
            throw new RuntimeException("Email already registered");
        }

        user.setId(null);
        user.setRole("SUPPLIER");

        user.setPassword(passwordEncoder.encode(user.getPassword()));

        User savedUser = userRepository.save(user);

        Supplier supplier = new Supplier();
        supplier.setBusinessName(businessName);
        supplier.setPhone(phone);
        supplier.setAddress(address);
        supplier.setUser(savedUser);

        return supplierRepository.save(supplier);
    }

    public User registerCustomer(User user) {

        if (userRepository.existsByEmail(user.getEmail())) {
            throw new RuntimeException("Email already registered");
        }

        user.setId(null);
        user.setRole("CUSTOMER");
        user.setPassword(passwordEncoder.encode(user.getPassword()));

        return userRepository.save(user);
    }

    public User login(LoginRequest request) {

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Invalid email or password"));

        if (!passwordEncoder.matches(
                request.getPassword(),
                user.getPassword()
        )) {
            throw new RuntimeException("Invalid email or password");
        }

        return user;
    }

    public User getUserById(Long id) {

        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public User updateUser(Long id, User userDetails) {

        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setName(userDetails.getName());
        user.setEmail(userDetails.getEmail());

        return userRepository.save(user);
    }

    public void deleteUser(Long id) {

        if (!userRepository.existsById(id)) {
            throw new RuntimeException("User not found");
        }

        userRepository.deleteById(id);
    }
}
