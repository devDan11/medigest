package com.medigestsystem.auth.dto;

import com.medigestsystem.usuarios.dto.UsuarioResponse;

public record TokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        UsuarioResponse usuario
) {
}
