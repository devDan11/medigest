package com.medigestsystem.medicos.dto;

import java.util.Set;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record MedicoRequest(
        @NotBlank(message = "Los nombres son obligatorios")
        @Size(max = 100, message = "Los nombres admiten hasta 100 caracteres")
        String nombres,

        @NotBlank(message = "Los apellidos son obligatorios")
        @Size(max = 100, message = "Los apellidos admiten hasta 100 caracteres")
        String apellidos,

        @NotBlank(message = "El número de colegiatura es obligatorio")
        @Pattern(regexp = "^[A-Za-z0-9-]{3,20}$", message = "La colegiatura admite 3 a 20 letras, números o guiones")
        String numeroColegiatura,

        @Email(message = "El correo no tiene un formato válido")
        @Size(max = 150, message = "El correo admite hasta 150 caracteres")
        String email,

        @Pattern(regexp = "^$|^[0-9+\\-\\s]{6,20}$", message = "El teléfono solo admite dígitos, +, - y espacios (6 a 20)")
        String telefono,

        Integer consultorioId,

        @NotNull(message = "La duración de la consulta es obligatoria")
        @Min(value = 5, message = "La duración mínima es de 5 minutos")
        @Max(value = 240, message = "La duración máxima es de 240 minutos")
        Integer duracionConsultaMin,

        @NotEmpty(message = "Debe indicar al menos una especialidad")
        Set<@NotNull Integer> especialidadIds,

        /** Solo al crear. Si se omite, el médico queda registrado sin acceso al sistema. */
        @Valid
        CuentaMedicoRequest cuenta
) {
}
