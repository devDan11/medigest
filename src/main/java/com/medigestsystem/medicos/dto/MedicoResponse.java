package com.medigestsystem.medicos.dto;

import java.util.List;

import com.medigestsystem.consultorios.dto.ConsultorioResponse;
import com.medigestsystem.especialidades.dto.EspecialidadResponse;

/** Para quienes no son personal administrativo se omiten correo, teléfono y consultorio. */
public record MedicoResponse(
        Long id,
        String nombres,
        String apellidos,
        String numeroColegiatura,
        String email,
        String telefono,
        ConsultorioResponse consultorio,
        int duracionConsultaMin,
        List<EspecialidadResponse> especialidades,
        boolean activo,
        boolean tieneCuenta
) {
}
