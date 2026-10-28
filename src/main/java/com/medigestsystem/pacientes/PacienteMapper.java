package com.medigestsystem.pacientes;

import java.time.LocalDate;
import java.time.Period;

import com.medigestsystem.pacientes.dto.PacienteResponse;
import com.medigestsystem.shared.util.FechaHoraUtil;

public final class PacienteMapper {

    private PacienteMapper() {
    }

    /** @param completo si es false se omiten dirección, correo y contacto de emergencia. */
    public static PacienteResponse aResponse(Paciente p, boolean completo, FechaHoraUtil fechaHora) {
        LocalDate hoy = fechaHora.hoyEnClinica();
        return new PacienteResponse(
                p.getId(),
                p.getNombres(),
                p.getApellidos(),
                p.getTipoDocumento(),
                p.getNumeroDocumento(),
                p.getFechaNacimiento(),
                Period.between(p.getFechaNacimiento(), hoy).getYears(),
                p.getSexo(),
                completo ? p.getEmail() : null,
                p.getTelefono(),
                completo ? p.getDireccion() : null,
                completo ? p.getContactoEmergenciaNombre() : null,
                completo ? p.getContactoEmergenciaTelefono() : null,
                p.isActivo(),
                p.getUsuario() != null,
                p.getCreadoEn() == null ? null : fechaHora.aZonaClinica(p.getCreadoEn()).toOffsetDateTime());
    }
}
