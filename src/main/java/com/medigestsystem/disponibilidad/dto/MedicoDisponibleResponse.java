package com.medigestsystem.disponibilidad.dto;

/** Médico con horarios libres en el rango consultado. */
public record MedicoDisponibleResponse(
        Long medicoId,
        String medico,
        String consultorio,
        int duracionConsultaMin,
        int totalHorarios,
        SlotResponse proximoHorario
) {
}
