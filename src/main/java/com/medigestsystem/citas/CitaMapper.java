package com.medigestsystem.citas;

import com.medigestsystem.citas.dto.CitaResumenResponse;
import com.medigestsystem.shared.util.FechaHoraUtil;

/** Convierte citas a DTO. Debe invocarse dentro de una transacción (las relaciones son LAZY). */
public final class CitaMapper {

    private CitaMapper() {
    }

    public static CitaResumenResponse aResumen(Cita c, FechaHoraUtil fechaHora) {
        return new CitaResumenResponse(
                c.getId(),
                c.getCodigoReserva(),
                c.getPaciente().getId(),
                c.getPaciente().getNombres() + " " + c.getPaciente().getApellidos(),
                c.getMedico().getId(),
                c.getMedico().getNombres() + " " + c.getMedico().getApellidos(),
                c.getEspecialidad().getId(),
                c.getEspecialidad().getNombre(),
                c.getConsultorio() == null ? null : c.getConsultorio().getCodigo(),
                fechaHora.aZonaClinica(c.getInicioUtc()).toOffsetDateTime(),
                fechaHora.aZonaClinica(c.getFinUtc()).toOffsetDateTime(),
                c.getEstado(),
                c.getMotivo());
    }
}
