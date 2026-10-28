package com.medigestsystem.citas.dto;

import jakarta.validation.constraints.NotNull;

import com.medigestsystem.citas.AccionAsistencia;

public record AsistenciaRequest(
        @NotNull(message = "La acción es obligatoria")
        AccionAsistencia accion
) {
}
