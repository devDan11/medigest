package com.medigestsystem.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medigestsystem.auditoria.AuditoriaService;
import com.medigestsystem.auditoria.ResultadoAuditoria;
import com.medigestsystem.config.MediGestProperties;
import com.medigestsystem.usuarios.Usuario;
import com.medigestsystem.usuarios.UsuarioRepository;

/**
 * Control de intentos de inicio de sesión: bloqueo temporal tras varios fallos consecutivos
 * y auditoría de cada resultado. Se invoca desde los eventos de autenticación de Spring Security,
 * por lo que protege por igual el login web y el login de la API.
 */
@Service
public class IntentosLoginService {

    static final String ENTIDAD = "usuarios";

    private final UsuarioRepository usuarioRepository;
    private final AuditoriaService auditoriaService;
    private final Clock clock;
    private final int maxIntentos;
    private final Duration duracionBloqueo;

    public IntentosLoginService(UsuarioRepository usuarioRepository, AuditoriaService auditoriaService,
                                Clock clock, MediGestProperties properties) {
        this.usuarioRepository = usuarioRepository;
        this.auditoriaService = auditoriaService;
        this.clock = clock;
        this.maxIntentos = properties.seguridad().bloqueo().maxIntentos();
        this.duracionBloqueo = Duration.ofMinutes(properties.seguridad().bloqueo().minutos());
    }

    @Transactional
    public void registrarFallo(String email) {
        Optional<Usuario> encontrado = buscar(email);
        if (encontrado.isEmpty()) {
            auditoriaService.registrarComo(null, normalizar(email), "LOGIN_FALLIDO", ENTIDAD, null,
                    ResultadoAuditoria.FALLO, "Credenciales inválidas");
            return;
        }
        Usuario usuario = encontrado.get();
        Instant ahora = clock.instant();

        if (usuario.getBloqueadoHasta() != null) {
            if (usuario.getBloqueadoHasta().isAfter(ahora)) {
                // Ya bloqueada: el intento no suma más fallos.
                auditoriaService.registrarComo(usuario.getId(), usuario.getEmail(), "LOGIN_BLOQUEADO", ENTIDAD,
                        String.valueOf(usuario.getId()), ResultadoAuditoria.DENEGADO, "Cuenta bloqueada temporalmente");
                return;
            }
            // El bloqueo ya venció: se empieza de cero.
            usuario.setBloqueadoHasta(null);
            usuario.setIntentosFallidos(0);
        }

        int intentos = usuario.getIntentosFallidos() + 1;
        if (intentos >= maxIntentos) {
            usuario.setBloqueadoHasta(ahora.plus(duracionBloqueo));
            usuario.setIntentosFallidos(0);
            usuarioRepository.save(usuario);
            auditoriaService.registrarComo(usuario.getId(), usuario.getEmail(), "CUENTA_BLOQUEADA", ENTIDAD,
                    String.valueOf(usuario.getId()), ResultadoAuditoria.DENEGADO,
                    "Bloqueo por " + maxIntentos + " intentos fallidos consecutivos");
        } else {
            usuario.setIntentosFallidos(intentos);
            usuarioRepository.save(usuario);
            auditoriaService.registrarComo(usuario.getId(), usuario.getEmail(), "LOGIN_FALLIDO", ENTIDAD,
                    String.valueOf(usuario.getId()), ResultadoAuditoria.FALLO,
                    "Intento " + intentos + " de " + maxIntentos);
        }
    }

    @Transactional
    public void registrarExito(String email) {
        buscar(email).ifPresent(usuario -> {
            usuario.setIntentosFallidos(0);
            usuario.setBloqueadoHasta(null);
            usuario.setUltimoAcceso(clock.instant());
            usuarioRepository.save(usuario);
            auditoriaService.registrarComo(usuario.getId(), usuario.getEmail(), "LOGIN_EXITO", ENTIDAD,
                    String.valueOf(usuario.getId()), ResultadoAuditoria.EXITO, null);
        });
    }

    /** Intento sobre una cuenta que ya estaba bloqueada o deshabilitada (rechazado antes de validar la clave). */
    @Transactional
    public void registrarRechazo(String email, String motivo) {
        Optional<Usuario> usuario = buscar(email);
        auditoriaService.registrarComo(usuario.map(Usuario::getId).orElse(null), normalizar(email),
                "LOGIN_RECHAZADO", ENTIDAD, usuario.map(u -> String.valueOf(u.getId())).orElse(null),
                ResultadoAuditoria.DENEGADO, motivo);
    }

    private Optional<Usuario> buscar(String email) {
        String limpio = normalizar(email);
        if (limpio == null || limpio.isEmpty()) {
            return Optional.empty();
        }
        return usuarioRepository.findByEmailIgnoreCase(limpio);
    }

    private static String normalizar(String email) {
        return email == null ? null : email.trim();
    }
}
