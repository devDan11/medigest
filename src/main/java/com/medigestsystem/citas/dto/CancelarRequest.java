package com.medigestsystem.citas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelarRequest(
        @NotBlank(message = "Indique el motivo de la cancelación")
        @Size(max = 300, message = "El motivo admite hasta 300 caracteres")
        String motivo
) {
}
