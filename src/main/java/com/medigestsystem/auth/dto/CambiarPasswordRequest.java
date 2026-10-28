package com.medigestsystem.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import com.medigestsystem.auth.PoliticaPassword;

public record CambiarPasswordRequest(
        @NotBlank(message = "La contraseña actual es obligatoria") String passwordActual,

        @NotBlank(message = "La nueva contraseña es obligatoria")
        @Pattern(regexp = PoliticaPassword.REGEX, message = PoliticaPassword.MENSAJE)
        String nuevaPassword
) {
}
