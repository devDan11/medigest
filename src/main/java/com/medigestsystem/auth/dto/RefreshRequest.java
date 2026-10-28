package com.medigestsystem.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(@NotBlank(message = "El token de refresco es obligatorio") String refreshToken) {
}
