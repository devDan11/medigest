package com.medigestsystem.reportes.dto;

/** Totales de citas de un médico o de una especialidad en el período consultado. */
public record FilaResumen(Long id, String nombre, long total, long atendidas, long canceladas, long noAsistio) {
}
