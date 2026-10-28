package com.medigestsystem.citas.dto;

import java.time.OffsetDateTime;
import java.util.List;

import com.medigestsystem.citas.AccionCita;
import com.medigestsystem.citas.EstadoCita;

/** Detalle de una cita, con las acciones que el usuario actual puede ejecutar sobre ella. */
public record CitaResponse(
        Long id,
        String codigoReserva,
        Long pacienteId,
        String paciente,
        Long medicoId,
        String medico,
        Integer especialidadId,
        String especialidad,
        Integer consultorioId,
        String consultorio,
        OffsetDateTime inicio,
        OffsetDateTime fin,
        EstadoCita estado,
        String motivo,
        String motivoCancelacion,
        OffsetDateTime canceladaEn,
        OffsetDateTime creadoEn,
        OffsetDateTime actualizadoEn,
        List<AccionCita> accionesPermitidas
) {
}
