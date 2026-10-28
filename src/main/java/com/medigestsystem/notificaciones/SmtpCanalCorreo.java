package com.medigestsystem.notificaciones;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** Envío real por SMTP. Solo existe con {@code medigest.correo.habilitado=true} y {@code spring.mail.host}. */
@Component
@ConditionalOnProperty(name = "medigest.correo.habilitado", havingValue = "true")
public class SmtpCanalCorreo implements CanalCorreo {

    private final JavaMailSender sender;
    private final CorreoProperties propiedades;

    public SmtpCanalCorreo(JavaMailSender sender, CorreoProperties propiedades) {
        this.sender = sender;
        this.propiedades = propiedades;
    }

    @Override
    public void enviar(String destinatario, String asunto, String cuerpo) {
        SimpleMailMessage mensaje = new SimpleMailMessage();
        mensaje.setFrom(propiedades.remitente());
        mensaje.setTo(destinatario);
        mensaje.setSubject(asunto);
        mensaje.setText(cuerpo);
        sender.send(mensaje);
    }
}
