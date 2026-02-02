package com.example.edps.global.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

public record Paged<T>(
        List<T> items,
        int page,
        int size,
        long totalCount
) {
    public static <S, T> Paged<T> from(Page<S> p, Function<S, T> mapper) {
        return new Paged<>(
                p.getContent().stream().map(mapper).toList(),
                p.getNumber(),
                p.getSize(),
                p.getTotalElements()
        );
    }
}
