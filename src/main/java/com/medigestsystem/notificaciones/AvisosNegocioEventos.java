package com.medigestsystem.notificaciones;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class AvisosNegocioEventos implements AvisosNegocio {

    private final ApplicationEventPublisher eventos;

    public AvisosNegocioEventos(ApplicationEventPublisher eventos) {
        this.eventos = eventos;
    }

    @Override
    public void cita(Long citaId, TipoNotificacion tipo) {
        eventos.publishEvent(new AvisoNegocioEvent(tipo, citaId));
    }

    @Override
    public void ofertaEspera(Long esperaId) {
        eventos.publishEvent(new AvisoNegocioEvent(TipoNotificacion.OFERTA_LISTA_ESPERA, esperaId));
    }
}
