package com.medigestsystem.citas.dto;

import java.time.OffsetDateTime;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Reserva de una cita. {@code pacienteId} lo indica el personal; un paciente siempre reserva para sí mismo
 * (si lo envía, se ignora). La duración la define el médico: no se envía hora de fin.
 */
public record CitaRequest(
        Long pacienteId,

        @NotNull(message = "El médico es obligatorio")
        Long medicoId,

        @NotNull(message = "La especialidad es obligatoria")
        Integer especialidadId,

        @NotNull(message = "La fecha y hora de inicio son obligatorias")
        OffsetDateTime inicio,

        @Size(max = 300, message = "El motivo admite hasta 300 caracteres")
        String motivo
) {
}
