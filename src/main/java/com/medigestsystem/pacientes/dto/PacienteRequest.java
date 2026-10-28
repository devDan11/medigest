package com.medigestsystem.pacientes.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.medigestsystem.pacientes.Sexo;
import com.medigestsystem.pacientes.TipoDocumento;

/** Datos completos del paciente, para registro y edición por parte del personal. */
public record PacienteRequest(
        @NotBlank(message = "Los nombres son obligatorios")
        @Size(max = 100, message = "Los nombres admiten hasta 100 caracteres")
        String nombres,

        @NotBlank(message = "Los apellidos son obligatorios")
        @Size(max = 100, message = "Los apellidos admiten hasta 100 caracteres")
        String apellidos,

        @NotNull(message = "El tipo de documento es obligatorio")
        TipoDocumento tipoDocumento,

        @NotBlank(message = "El número de documento es obligatorio")
        @Size(max = 20, message = "El número de documento admite hasta 20 caracteres")
        String numeroDocumento,

        @NotNull(message = "La fecha de nacimiento es obligatoria")
        @Past(message = "La fecha de nacimiento debe ser anterior a hoy")
        LocalDate fechaNacimiento,

        Sexo sexo,

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
