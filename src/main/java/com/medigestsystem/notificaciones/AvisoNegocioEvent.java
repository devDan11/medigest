package com.medigestsystem.notificaciones;

/** Hecho de negocio que debe notificarse; {@code id} es la cita o, para OFERTA_LISTA_ESPERA, la solicitud de espera. */
public record AvisoNegocioEvent(TipoNotificacion tipo, Long id) {
}
