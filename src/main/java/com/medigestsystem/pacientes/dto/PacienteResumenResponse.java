package com.medigestsystem.pacientes.dto;

import com.medigestsystem.citas.dto.CitaResumenResponse;

/** Resumen de actividad del paciente: contadores de citas y próxima cita. */
public record PacienteResumenResponse(
        long citasAtendidas,
        long citasCanceladas,
        long citasNoAsistio,
        long citasPendientes,
        CitaResumenResponse proximaCita
) {
}
