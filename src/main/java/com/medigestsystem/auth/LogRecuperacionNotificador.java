package com.medigestsystem.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import com.medigestsystem.usuarios.Usuario;

/**
 * Entrega provisional del enlace de recuperación: solo en el perfil {@code dev} lo escribe en el log
 * para poder probar el flujo sin servidor de correo. En cualquier otro perfil NO escribe el token.
 * La fase de notificaciones (Fase 7) reemplaza este componente por el envío real de correo.
 */
@Component
public class LogRecuperacionNotificador implements RecuperacionNotificador {

    private static final Logger log = LoggerFactory.getLogger(LogRecuperacionNotificador.class);

    private final boolean modoDesarrollo;

    public LogRecuperacionNotificador(Environment environment) {
        this.modoDesarrollo = environment.acceptsProfiles(Profiles.of("dev"));
    }

    @Override
    public void enviarEnlace(Usuario usuario, String enlace, int minutosVigencia) {
        if (modoDesarrollo) {
            log.info("[DEV] Enlace de recuperación para {} (vence en {} min): {}",
                    usuario.getEmail(), minutosVigencia, enlace);
        } else {
            log.warn("Se generó un enlace de recuperación para el usuario {}, pero no hay un canal de correo configurado.",
                    usuario.getId());
        }
    }
}
