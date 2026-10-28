package com.medigestsystem.notificaciones;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Tareas periódicas: genera recordatorios de cita y envía las notificaciones pendientes. */
@Component
public class NotificacionTareas {

    private static final Logger log = LoggerFactory.getLogger(NotificacionTareas.class);

    private final NotificacionService servicio;

    public NotificacionTareas(NotificacionService servicio) {
        this.servicio = servicio;
    }

    @Scheduled(fixedDelayString = "${medigest.tareas.recordatorios-ms:600000}", initialDelayString = "${medigest.tareas.recordatorios-ms:600000}")
    public void generarRecordatorios() {
        try {
            int nuevas = 0;
            for (Long citaId : servicio.citasParaRecordatorio()) {
                try {
                    if (servicio.encolarParaCita(citaId, TipoNotificacion.RECORDATORIO_CITA)) {
                        nuevas++;
                    }
                } catch (RuntimeException e) {
                    log.error("No se pudo crear el recordatorio de la cita {}", citaId, e);
                }
            }
            if (nuevas > 0) {
                log.info("Recordatorios de cita creados: {}", nuevas);
            }
        } catch (RuntimeException e) {
            log.error("Falló la generación de recordatorios", e);
        }
    }

    @Scheduled(fixedDelayString = "${medigest.tareas.notificaciones-ms:30000}", initialDelayString = "${medigest.tareas.notificaciones-ms:30000}")
    public void despachar() {
        try {
            List<Long> ids = servicio.idsPendientes();
            for (Long id : ids) {
                try {
                    servicio.procesar(id);
                } catch (RuntimeException e) {
                    log.error("Error al procesar la notificación {}", id, e);
                }
            }
        } catch (RuntimeException e) {
            log.error("Falló el despacho de notificaciones", e);
        }
    }
}
