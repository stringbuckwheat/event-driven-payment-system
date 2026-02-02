package com.example.edps.domain.cart.repository;

import com.example.edps.domain.cart.model.Cart;
import org.springframework.data.repository.CrudRepository;

public interface CartRepository extends CrudRepository<Cart, String> {
}