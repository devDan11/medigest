package com.medigestsystem.notificaciones.dto;

import java.time.OffsetDateTime;

import com.medigestsystem.notificaciones.EstadoNotificacion;
import com.medigestsystem.notificaciones.TipoNotificacion;

public record NotificacionResponse(
        Long id,
        TipoNotificacion tipo,
        EstadoNotificacion estado,
        String destinatario,
        String asunto,
        int intentos,
        String ultimoError,
        Long citaId,
        OffsetDateTime programadaPara,
        OffsetDateTime enviadaEn,
        OffsetDateTime creadoEn
) {
}
