package com.medigestsystem.reportes.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.medigestsystem.citas.EstadoCita;
import com.medigestsystem.dashboard.DashboardResponse.DiaConteo;

/**
 * Reporte de citas de un período (fechas en hora de la clínica, ambas inclusivas).
 *
 * @param tasaAsistencia  % de atendidas sobre (atendidas + no asistió); {@code null} si aún no hay citas cerradas
 * @param tasaCancelacion % de canceladas sobre el total; {@code null} si no hay citas
 * @param porDia          citas no canceladas por día, con los días sin citas en cero
 */
public record ReporteCitasResponse(
        LocalDate desde,
        LocalDate hasta,
        long total,
        Map<EstadoCita, Long> porEstado,
        Double tasaAsistencia,
        Double tasaCancelacion,
        List<DiaConteo> porDia,
        List<FilaResumen> porMedico
) {
}
