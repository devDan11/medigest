package com.medigestsystem.citas.dto;

import java.time.OffsetDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReprogramarRequest(
        @NotNull(message = "El nuevo horario es obligatorio")
        OffsetDateTime nuevoInicio,

        @NotBlank(message = "Indique el motivo del cambio")
        @Size(max = 300, message = "El motivo admite hasta 300 caracteres")
        String motivo
) {
}
