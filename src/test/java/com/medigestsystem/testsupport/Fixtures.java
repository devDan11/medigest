package com.medigestsystem.testsupport;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.medigestsystem.config.MediGestProperties;
import com.medigestsystem.security.UsuarioPrincipal;
import com.medigestsystem.shared.util.FechaHoraUtil;
import com.medigestsystem.usuarios.NombreRol;
import com.medigestsystem.usuarios.Rol;
import com.medigestsystem.usuarios.Usuario;

/** Datos de apoyo compartidos por las pruebas unitarias. */
public final class Fixtures {

    public static final String SECRETO_PRUEBAS = "clave-de-pruebas-con-mas-de-32-caracteres!";
    public static final ZoneId LIMA = ZoneId.of("America/Lima");
    /** Instante fijo de las pruebas: 2030-01-07 (lunes) 15:00 UTC = 10:00 en Lima. */
    public static final Instant AHORA = Instant.parse("2030-01-07T15:00:00Z");

    private Fixtures() {
    }

    public static MediGestProperties propiedades(String secreto) {
        return new MediGestProperties(
                ZoneId.of("America/Lima"),
                new MediGestProperties.Seguridad(
                        new MediGestProperties.Jwt(secreto, "MediGest", 15, 10080),
                        new MediGestProperties.Bloqueo(5, 15),
                        30,
                        false),
                new MediGestProperties.Aplicacion("http://localhost:8080"));
    }

    public static Usuario usuario(Long id, String email, String hash, NombreRol... roles) {
        Usuario u = new Usuario();
        u.setId(id);
        u.setEmail(email);
        u.setPasswordHash(hash);
        u.setActivo(true);
        Set<Rol> conjunto = new HashSet<>();
        for (NombreRol nombre : roles) {
            conjunto.add(rol(nombre));
        }
        u.setRoles(conjunto);
        return u;
    }

    public static Rol rol(NombreRol nombre) {
        Rol rol = new Rol();
        rol.setNombre(nombre);
        return rol;
    }

    /** Reloj fijo en {@link #AHORA} con zona de la clínica. */
    public static FechaHoraUtil fechaHora() {
        return new FechaHoraUtil(Clock.fixed(AHORA, ZoneOffset.UTC), LIMA);
    }

    /** Simula una sesión autenticada con los roles dados. Limpiar con {@link #cerrarSesion()}. */
    public static void autenticarComo(Long usuarioId, NombreRol... roles) {
        List<String> nombres = Arrays.stream(roles).map(Enum::name).toList();
        UsuarioPrincipal principal = new UsuarioPrincipal(usuarioId, "u" + usuarioId + "@test.com", "x", true, true, nombres);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    public static void cerrarSesion() {
        SecurityContextHolder.clearContext();
    }
}
