package com.medigestsystem.notificaciones;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuración del envío de correo ({@code medigest.correo.*}). Con {@code habilitado=false} (valor por defecto)
 * las notificaciones se registran pero no se envían: quedan como OMITIDA y pueden reintentarse luego.
 *
 * @param habilitado          activa el envío SMTP real (requiere {@code spring.mail.*})
 * @param remitente           dirección (o "Nombre &lt;dirección&gt;") que figura como remitente
 * @param maxIntentos         intentos de envío antes de marcar la notificación como FALLIDA
 * @param backoffMinutos      minutos base de espera entre reintentos (se multiplica por el nº de intento)
 * @param tamanoLote          notificaciones procesadas por cada ejecución del despachador
 * @param recordatorioHoras   horas de anticipación del recordatorio de cita
 */
@ConfigurationProperties(prefix = "medigest.correo")
public record CorreoProperties(
        @DefaultValue("false") boolean habilitado,
        @DefaultValue("MediGest <no-reply@medigest.local>") String remitente,
        @DefaultValue("5") int maxIntentos,
        @DefaultValue("2") int backoffMinutos,
        @DefaultValue("20") int tamanoLote,
        @DefaultValue("24") int recordatorioHoras
) {
}
