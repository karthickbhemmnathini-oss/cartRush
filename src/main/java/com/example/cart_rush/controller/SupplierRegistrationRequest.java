package com.example.cart_rush.controller;

import com.example.cart_rush.entity.User;

public class SupplierRegistrationRequest {

    private User user;
    private String businessName;
    private String phone;
    private String address;

    public SupplierRegistrationRequest() {
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getBusinessName() {
        return businessName;
    }

    public void setBusinessName(String businessName) {
        this.businessName = businessName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }
}
