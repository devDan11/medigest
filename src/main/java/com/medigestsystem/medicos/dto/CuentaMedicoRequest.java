package com.medigestsystem.medicos.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.medigestsystem.auth.PoliticaPassword;

/** Credenciales de acceso del médico, que se crean junto con su ficha. */
public record CuentaMedicoRequest(
        @NotBlank(message = "El correo de la cuenta es obligatorio")
        @Email(message = "El correo de la cuenta no tiene un formato válido")
        @Size(max = 150, message = "El correo admite hasta 150 caracteres")
        String email,

        @NotBlank(message = "La contraseña es obligatoria")
        @Pattern(regexp = PoliticaPassword.REGEX, message = PoliticaPassword.MENSAJE)
        String password
) {
}
