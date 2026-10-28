package com.medigestsystem.listaespera.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;

import com.medigestsystem.listaespera.FranjaPreferida;

/**
 * Solicitud de espera. {@code pacienteId} lo indica el personal; un paciente se anota a sí mismo.
 * {@code medicoId} es opcional: sin él se acepta cualquier médico de la especialidad.
 */
public record ListaEsperaRequest(
        Long pacienteId,

        @NotNull(message = "La especialidad es obligatoria")
        Integer especialidadId,

        Long medicoId,

        @NotNull(message = "La fecha inicial es obligatoria")
        LocalDate fechaDesde,

        @NotNull(message = "La fecha final es obligatoria")
        LocalDate fechaHasta,

        FranjaPreferida franja
) {
}
