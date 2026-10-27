package com.example.cart_rush.controller;

import com.example.cart_rush.entity.User;
import com.example.cart_rush.service.UserService;
import org.springframework.web.bind.annotation.*;
import com.example.cart_rush.entity.Supplier;
import com.example.cart_rush.controller.LoginRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import java.util.List;

@RestController
@RequestMapping("/api/auth")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register/customer")
    public User registerCustomer(@RequestBody User user) {
        return userService.registerCustomer(user);
    }

    @PostMapping("/register/supplier")
    public Supplier registerSupplier(
            @RequestBody SupplierRegistrationRequest request
    ) {
        return userService.registerSupplier(
                request.getUser(),
                request.getBusinessName(),
                request.getPhone(),
                request.getAddress()
        );
    }

    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest request) {

        User user = userService.login(request);

        return new LoginResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole()
        );
    }

    @GetMapping("/users/{id}")
    public User getUserById(@PathVariable Long id) {
        return userService.getUserById(id);
    }

    @GetMapping("/users")
    public List<User> getAllUsers() {
        return userService.getAllUsers();
    }

    @PutMapping("/users/{id}")
    public User updateUser(
            @PathVariable Long id,
            @RequestBody User userDetails
    ) {
        return userService.updateUser(id, userDetails);
    }

    @DeleteMapping("/users/{id}")
    public String deleteUser(@PathVariable Long id) {

        userService.deleteUser(id);

        return "User deleted successfully";
    }
}
