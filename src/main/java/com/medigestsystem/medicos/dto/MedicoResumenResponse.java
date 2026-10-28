package com.medigestsystem.medicos.dto;

public record MedicoResumenResponse(
        long citasAtendidas,
        long citasCanceladas,
        long citasNoAsistio,
        long citasFuturas
) {
}
