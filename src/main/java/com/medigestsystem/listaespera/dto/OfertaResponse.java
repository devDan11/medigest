package com.medigestsystem.listaespera.dto;

import java.time.OffsetDateTime;

/** Horario retenido temporalmente para el paciente hasta {@code expiraEn}. */
public record OfertaResponse(
        Long medicoId,
        String medico,
        OffsetDateTime inicio,
        OffsetDateTime fin,
        OffsetDateTime expiraEn
) {
}
