package com.iulianlounge.backend.dto;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

// Formato de paginación de architecture.md §3. Propio en vez de serializar Page de Spring,
// cuyo JSON no es estable entre versiones
public record PageResponse<T>(List<T> content, int page, int size, long totalElements) {

    public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements());
    }
}
