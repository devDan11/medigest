package com.medigestsystem.medicos;

import java.util.Comparator;

import com.medigestsystem.consultorios.dto.ConsultorioResponse;
import com.medigestsystem.especialidades.Especialidad;
import com.medigestsystem.especialidades.dto.EspecialidadResponse;
import com.medigestsystem.medicos.dto.MedicoResponse;

public final class MedicoMapper {

    private MedicoMapper() {
    }

    /** Debe invocarse dentro de una transacción (especialidades y consultorio son LAZY). */
    public static MedicoResponse aResponse(Medico m, boolean completo) {
        return new MedicoResponse(
                m.getId(),
                m.getNombres(),
                m.getApellidos(),
                m.getNumeroColegiatura(),
                completo ? m.getEmail() : null,
                completo ? m.getTelefono() : null,
                completo && m.getConsultorio() != null ? ConsultorioResponse.de(m.getConsultorio()) : null,
                m.getDuracionConsultaMin(),
                m.getEspecialidades().stream()
                        .sorted(Comparator.comparing(Especialidad::getNombre))
                        .map(EspecialidadResponse::de)
                        .toList(),
                m.isActivo(),
                m.getUsuario() != null);
    }
}
