package com.medigestsystem.listaespera.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import com.medigestsystem.listaespera.EstadoListaEspera;
import com.medigestsystem.listaespera.FranjaPreferida;

public record ListaEsperaResponse(
        Long id,
        Long pacienteId,
        String paciente,
        Integer especialidadId,
        String especialidad,
        Long medicoId,
        String medico,
        LocalDate fechaDesde,
        LocalDate fechaHasta,
        FranjaPreferida franja,
        EstadoListaEspera estado,
        /** Lugar en la cola de la especialidad (1 = primero). Solo para solicitudes ACTIVAS. */
        Long posicion,
        OfertaResponse oferta,
        Long citaId,
        OffsetDateTime creadoEn
) {
}
