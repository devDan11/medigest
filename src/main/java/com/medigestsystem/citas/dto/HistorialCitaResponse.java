package com.medigestsystem.citas.dto;

import java.time.OffsetDateTime;

import com.medigestsystem.citas.EstadoCita;
import com.medigestsystem.citas.TipoEventoCita;

public record HistorialCitaResponse(
        Long id,
        TipoEventoCita tipoEvento,
        EstadoCita estadoAnterior,
        EstadoCita estadoNuevo,
        OffsetDateTime inicioAnterior,
        OffsetDateTime inicioNuevo,
        String motivo,
        String usuario,
        OffsetDateTime fecha
) {
}
