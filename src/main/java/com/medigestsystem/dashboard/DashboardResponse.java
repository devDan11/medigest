package com.medigestsystem.dashboard;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.medigestsystem.citas.EstadoCita;
import com.medigestsystem.citas.dto.CitaResumenResponse;

/** Indicadores del panel. Para médicos y pacientes cubren solo sus propias citas. */
public record DashboardResponse(
        boolean globales,
        long citasHoy,
        long pendientesDeConfirmar,
        Map<EstadoCita, Long> porEstadoHoy,
        List<DiaConteo> proximosDias,
        Long pacientesActivos,
        Long medicosActivos,
        Long esperaActiva,
        List<CitaResumenResponse> proximas
) {
    public record DiaConteo(LocalDate fecha, long total) {
    }
}
