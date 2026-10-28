package com.medigestsystem.notificaciones;

/**
 * Punto de entrada para que los servicios de negocio avisen de hechos que pueden generar una notificación.
 * La notificación se crea después de confirmada la transacción de origen, nunca dentro de ella.
 */
public interface AvisosNegocio {

    /** Una cita cambió (reservada, confirmada, reprogramada o cancelada). */
    void cita(Long citaId, TipoNotificacion tipo);

    /** Se ofreció un horario liberado a una solicitud de la lista de espera. */
    void ofertaEspera(Long esperaId);
}
