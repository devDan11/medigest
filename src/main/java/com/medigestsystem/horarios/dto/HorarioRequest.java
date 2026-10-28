package com.medigestsystem.horarios.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Horario semanal completo del médico: reemplaza al vigente. Una lista vacía deja al médico sin atención. */
public record HorarioRequest(
        @NotNull(message = "Debe indicar la lista de franjas")
        @Size(max = 28, message = "Se admiten hasta 28 franjas semanales")
        List<@Valid @NotNull FranjaRequest> franjas
) {
}
