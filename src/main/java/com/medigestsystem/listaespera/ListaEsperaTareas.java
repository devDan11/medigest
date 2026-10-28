package com.medigestsystem.listaespera;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Vence periódicamente las ofertas no aceptadas para que el horario siga su curso. */
@Component
@ConditionalOnProperty(name = "medigest.tareas.habilitadas", havingValue = "true", matchIfMissing = true)
public class ListaEsperaTareas {

    private static final Logger log = LoggerFactory.getLogger(ListaEsperaTareas.class);

    private final ListaEsperaService listaEsperaService;

    public ListaEsperaTareas(ListaEsperaService listaEsperaService) {
        this.listaEsperaService = listaEsperaService;
    }

    @Scheduled(fixedDelayString = "${medigest.tareas.lista-espera-ms:60000}", initialDelayString = "${medigest.tareas.lista-espera-ms:60000}")
    public void vencerPendientes() {
        try {
            int n = listaEsperaService.vencerPendientes();
            if (n > 0) {
                log.info("Lista de espera: {} solicitud(es) vencida(s)", n);
            }
        } catch (RuntimeException e) {
            log.error("Error al vencer ofertas de la lista de espera", e);
        }
    }
}
