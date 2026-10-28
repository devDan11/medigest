package com.medigestsystem.citas.dto;

import java.time.OffsetDateTime;

import com.medigestsystem.citas.EstadoCita;

/** Vista resumida de una cita. Las fechas se muestran en la hora local de la clínica (America/Lima). */
public record CitaResumenResponse(
        Long id,
        String codigoReserva,
        Long pacienteId,
        String paciente,
        Long medicoId,
        String medico,
        Integer especialidadId,
        String especialidad,
        String consultorio,
        OffsetDateTime inicio,
        OffsetDateTime fin,
        EstadoCita estado,
        String motivo
) {
}
