package com.medigestsystem.especialidades.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record EspecialidadRequest(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 80, message = "El nombre admite hasta 80 caracteres")
        String nombre,

        @Size(max = 300, message = "La descripción admite hasta 300 caracteres")
        String descripcion,

        @NotNull(message = "La duración sugerida es obligatoria")
        @Min(value = 5, message = "La duración mínima es 5 minutos")
        @Max(value = 240, message = "La duración máxima es 240 minutos")
        Integer duracionSugeridaMin
) {
}
