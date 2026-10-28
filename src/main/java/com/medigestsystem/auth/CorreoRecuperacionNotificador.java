package com.medigestsystem.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.medigestsystem.notificaciones.CanalCorreo;
import com.medigestsystem.usuarios.Usuario;

/**
 * Envía el enlace de recuperación por correo. El enlace contiene un secreto, por eso se envía directamente por
 * el canal y NO se guarda en la tabla de notificaciones ni se escribe en el log. Un fallo de envío no se revela
 * a quien solicita la recuperación (para no permitir descubrir qué correos existen).
 */
@Component
@Primary
@ConditionalOnProperty(name = "medigest.correo.habilitado", havingValue = "true")
public class CorreoRecuperacionNotificador implements RecuperacionNotificador {

    private static final Logger log = LoggerFactory.getLogger(CorreoRecuperacionNotificador.class);

    private final CanalCorreo canal;

    public CorreoRecuperacionNotificador(CanalCorreo canal) {
        this.canal = canal;
    }

    @Override
    public void enviarEnlace(Usuario usuario, String enlace, int minutosVigencia) {
        String cuerpo = "Hola,\n\nRecibimos una solicitud para restablecer tu contraseña de MediGest.\n\n"
                + "Usa este enlace (vence en " + minutosVigencia + " minutos y sirve una sola vez):\n" + enlace + "\n\n"
                + "Si no fuiste tú, ignora este mensaje: tu contraseña no cambiará.\n\n"
                + "MediGest · Este mensaje es automático, no respondas a este correo.\n";
        try {
            canal.enviar(usuario.getEmail(), "Restablece tu contraseña", cuerpo);
        } catch (RuntimeException e) {
            log.error("No se pudo enviar el correo de recuperación al usuario {}: {}", usuario.getId(), e.getMessage());
        }
    }
}
