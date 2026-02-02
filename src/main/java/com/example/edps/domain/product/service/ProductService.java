package com.example.edps.domain.product.service;

import com.example.edps.domain.product.entity.Product;
import com.example.edps.domain.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ProductService {
    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public Page<Product> search(String name, Pageable pageable) {
        if (!StringUtils.hasText(name)) {
            return productRepository.findAll(pageable);
        }

        return productRepository.findByNameContainingIgnoreCase(name.trim(), pageable);
    }
}
