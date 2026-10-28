package com.medigestsystem.security;

import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import com.medigestsystem.usuarios.NombreRol;

/** Acceso al usuario autenticado, ya sea por sesión (web) o por JWT (API). */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static Optional<Long> usuarioIdActual() {
        Object principal = principal();
        if (principal instanceof UsuarioPrincipal p) {
            return Optional.of(p.getId());
        }
        if (principal instanceof Jwt jwt) {
            try {
                return Optional.of(Long.valueOf(jwt.getSubject()));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    public static Optional<String> emailActual() {
        Object principal = principal();
        if (principal instanceof UsuarioPrincipal p) {
            return Optional.of(p.getUsername());
        }
        if (principal instanceof Jwt jwt) {
            return Optional.ofNullable(jwt.getClaimAsString(JwtService.CLAIM_EMAIL));
        }
        return Optional.empty();
    }

    public static boolean tieneRol(NombreRol rol) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return false;
        }
        String buscado = "ROLE_" + rol.name();
        for (GrantedAuthority ga : auth.getAuthorities()) {
            if (buscado.equals(ga.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    private static Object principal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        return auth.getPrincipal();
    }
}
