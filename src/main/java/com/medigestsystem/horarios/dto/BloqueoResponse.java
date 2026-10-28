package com.medigestsystem.horarios.dto;

import java.time.OffsetDateTime;

import com.medigestsystem.horarios.EstadoBloqueo;
import com.medigestsystem.horarios.TipoBloqueo;

public record BloqueoResponse(
        Long id,
        Long medicoId,
        String medico,
        Integer consultorioId,
        String consultorio,
        OffsetDateTime inicio,
        OffsetDateTime fin,
        TipoBloqueo tipo,
        String motivo,
        EstadoBloqueo estado,
        String creadoPor,
        OffsetDateTime creadoEn
) {
}
