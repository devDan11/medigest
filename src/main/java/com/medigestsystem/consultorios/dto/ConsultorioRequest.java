package com.medigestsystem.consultorios.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ConsultorioRequest(
        @NotBlank(message = "El código es obligatorio")
        @Size(max = 20, message = "El código admite hasta 20 caracteres")
        @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9 .\\-]*$",
                message = "El código solo admite letras, números, espacios, puntos y guiones")
        String codigo,

        @Size(max = 100, message = "La ubicación admite hasta 100 caracteres")
        String ubicacion
) {
}
