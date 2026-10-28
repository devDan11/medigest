package com.medigestsystem.notificaciones;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Convierte los avisos de negocio en notificaciones pendientes, una vez confirmada la transacción de origen. */
@Component
public class NotificacionListener {

    private static final Logger log = LoggerFactory.getLogger(NotificacionListener.class);

    private final NotificacionService notificacionService;

    public NotificacionListener(NotificacionService notificacionService) {
        this.notificacionService = notificacionService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void alOcurrirAviso(AvisoNegocioEvent evento) {
        try {
            if (evento.tipo() == TipoNotificacion.OFERTA_LISTA_ESPERA) {
                notificacionService.encolarOferta(evento.id());
            } else {
                notificacionService.encolarParaCita(evento.id(), evento.tipo());
            }
        } catch (RuntimeException e) {
            // Un fallo al notificar nunca debe afectar a la operación ya confirmada.
            log.error("No se pudo registrar la notificación {} ({})", evento.tipo(), evento.id(), e);
        }
    }
}
