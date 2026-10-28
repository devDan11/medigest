package com.medigestsystem.pacientes.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import com.medigestsystem.pacientes.Sexo;
import com.medigestsystem.pacientes.TipoDocumento;

/**
 * Datos del paciente. Para un médico se omiten dirección, correo y contacto de emergencia
 * (principio de mínimo privilegio): solo recibe lo necesario para la atención.
 */
public record PacienteResponse(
        Long id,
        String nombres,
        String apellidos,
        TipoDocumento tipoDocumento,
        String numeroDocumento,
        LocalDate fechaNacimiento,
        Integer edad,
        Sexo sexo,
        String email,
        String telefono,
        String direccion,
        String contactoEmergenciaNombre,
        String contactoEmergenciaTelefono,
        boolean activo,
        boolean tieneCuenta,
        OffsetDateTime registradoEn
) {
}
