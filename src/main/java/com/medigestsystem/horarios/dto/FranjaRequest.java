package com.medigestsystem.horarios.dto;

import java.time.LocalTime;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Franja de atención semanal en hora local de la clínica. diaSemana: 1 = lunes … 7 = domingo. */
public record FranjaRequest(
        @NotNull(message = "El día de la semana es obligatorio")
        @Min(value = 1, message = "El día debe estar entre 1 (lunes) y 7 (domingo)")
        @Max(value = 7, message = "El día debe estar entre 1 (lunes) y 7 (domingo)")
        Integer diaSemana,

        @NotNull(message = "La hora de inicio es obligatoria")
        LocalTime horaInicio,

        @NotNull(message = "La hora de fin es obligatoria")
        LocalTime horaFin,

        LocalTime descansoInicio,

        LocalTime descansoFin
) {
}
