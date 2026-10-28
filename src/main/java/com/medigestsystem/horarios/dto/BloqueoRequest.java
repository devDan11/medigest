package com.medigestsystem.horarios.dto;

import java.time.OffsetDateTime;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.medigestsystem.horarios.TipoBloqueo;

/**
 * Bloqueo de agenda. Debe indicarse un médico o un consultorio (no ambos). El administrador lo crea
 * ya aprobado; el médico solo puede solicitar bloqueos propios, que el administrador debe aprobar.
 */
public record BloqueoRequest(
        Long medicoId,
        Integer consultorioId,

        @NotNull(message = "La fecha de inicio es obligatoria")
        OffsetDateTime inicio,

        @NotNull(message = "La fecha de fin es obligatoria")
        OffsetDateTime fin,

        @NotNull(message = "El tipo es obligatorio")
        TipoBloqueo tipo,

        @Size(max = 200, message = "El motivo admite hasta 200 caracteres")
        String motivo
) {
}
