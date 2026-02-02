package com.example.edps.domain.product.controller;

import com.example.edps.domain.product.dto.ProductResponse;
import com.example.edps.domain.product.entity.Product;
import com.example.edps.domain.product.service.ProductService;
import com.example.edps.global.dto.Paged;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/products")
public class ProductController {
    private final ProductService productService;

    @GetMapping
    public Paged<ProductResponse> list(
            @RequestParam(required = false) String name,
            Pageable pageable
    ) {
        Page<Product> page = productService.search(name, pageable);
        return Paged.from(page, ProductResponse::from);
    }
}
