package com.medigestsystem.listaespera;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.medigestsystem.citas.HorarioLiberadoEvent;

/** Escucha los horarios liberados, una vez confirmada la transacción que los liberó. */
@Component
public class ListaEsperaListener {

    private static final Logger log = LoggerFactory.getLogger(ListaEsperaListener.class);

    private final ListaEsperaService listaEsperaService;

    public ListaEsperaListener(ListaEsperaService listaEsperaService) {
        this.listaEsperaService = listaEsperaService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void alLiberarseHorario(HorarioLiberadoEvent evento) {
        try {
            listaEsperaService.ofrecer(evento);
        } catch (RuntimeException e) {
            // Un fallo al ofrecer el horario no debe afectar a la operación que ya se confirmó.
            log.error("No se pudo ofrecer el horario liberado del médico {}", evento.medicoId(), e);
        }
    }
}
