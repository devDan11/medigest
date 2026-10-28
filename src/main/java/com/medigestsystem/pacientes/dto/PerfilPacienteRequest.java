package com.medigestsystem.pacientes.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Datos que el propio paciente puede modificar. Nombres, documento y fecha de nacimiento no se editan aquí. */
public record PerfilPacienteRequest(
        @Email(message = "El correo no tiene un formato válido")
        @Size(max = 150, message = "El correo admite hasta 150 caracteres")
        String email,

        @Pattern(regexp = "^$|^[0-9+\\-\\s]{6,20}$", message = "El teléfono solo admite dígitos, +, - y espacios (6 a 20)")
        String telefono,

        @Size(max = 200, message = "La dirección admite hasta 200 caracteres")
        String direccion,

        @Size(max = 100, message = "El contacto de emergencia admite hasta 100 caracteres")
        String contactoEmergenciaNombre,

        @Pattern(regexp = "^$|^[0-9+\\-\\s]{6,20}$", message = "El teléfono de emergencia solo admite dígitos, +, - y espacios (6 a 20)")
        String contactoEmergenciaTelefono
) {
}
