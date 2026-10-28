package com.medigestsystem.horarios.dto;

import java.time.LocalTime;

import com.medigestsystem.horarios.HorarioMedico;

public record HorarioResponse(
        Long id,
        int diaSemana,
        LocalTime horaInicio,
        LocalTime horaFin,
        LocalTime descansoInicio,
        LocalTime descansoFin
) {

    public static HorarioResponse de(HorarioMedico h) {
        return new HorarioResponse(h.getId(), h.getDiaSemana(), h.getHoraInicio(), h.getHoraFin(),
                h.getDescansoInicio(), h.getDescansoFin());
    }
}
