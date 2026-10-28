package com.medigestsystem.config;

import java.time.ZoneId;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Propiedades propias de la aplicación (prefijo {@code medigest}).
 * Los secretos (clave JWT) se leen del entorno, nunca del repositorio.
 */
@ConfigurationProperties(prefix = "medigest")
public record MediGestProperties(
        @DefaultValue("America/Lima") ZoneId timezone,
        @DefaultValue Seguridad seguridad,
        @DefaultValue Aplicacion aplicacion
) {

    public record Seguridad(
            @DefaultValue Jwt jwt,
            @DefaultValue Bloqueo bloqueo,
            /** Vigencia del enlace de recuperación de contraseña. */
            @DefaultValue("30") int recuperacionMinutos,
            /** Si es false, Swagger UI y /v3/api-docs exigen rol ADMINISTRADOR. */
            @DefaultValue("false") boolean swaggerPublico
    ) {
    }

    public record Jwt(
            /** Clave HMAC (mínimo 32 caracteres). Si está vacía, fuera de prod se genera una efímera. */
            String secreto,
            @DefaultValue("MediGest") String emisor,
            @DefaultValue("15") long accesoMinutos,
            @DefaultValue("10080") long refrescoMinutos
    ) {
    }

    public record Bloqueo(
            @DefaultValue("5") int maxIntentos,
            @DefaultValue("15") int minutos
    ) {
    }

    public record Aplicacion(
            @DefaultValue("http://localhost:8080") String urlBase
    ) {
    }
}
