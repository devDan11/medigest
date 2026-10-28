package com.medigestsystem.shared.dto;

import java.util.List;

import org.springframework.data.domain.Page;

/** Envoltorio estable para respuestas paginadas (no se expone PageImpl directamente). */
public record PaginaResponse<T>(
        List<T> contenido,
        int pagina,
        int tamano,
        long totalElementos,
        int totalPaginas
) {

    public static <T> PaginaResponse<T> de(Page<T> page) {
        return new PaginaResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
