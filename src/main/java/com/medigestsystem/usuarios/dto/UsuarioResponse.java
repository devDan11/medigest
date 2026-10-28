package com.medigestsystem.usuarios.dto;

import java.time.Instant;
import java.util.List;

import com.medigestsystem.usuarios.Usuario;

public record UsuarioResponse(
        Long id,
        String email,
        boolean activo,
        List<String> roles,
        Instant bloqueadoHasta,
        Instant ultimoAcceso
) {

    public static UsuarioResponse de(Usuario usuario) {
        return new UsuarioResponse(
                usuario.getId(),
                usuario.getEmail(),
                usuario.isActivo(),
                usuario.getRoles().stream().map(r -> r.getNombre().name()).sorted().toList(),
                usuario.getBloqueadoHasta(),
                usuario.getUltimoAcceso());
    }
}
