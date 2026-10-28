package com.medigestsystem.citas;

import java.time.Instant;

/**
 * Se publica cuando un horario queda libre (cancelación, reprogramación, oferta rechazada o vencida).
 * La lista de espera lo escucha, después de confirmada la transacción, para ofrecerlo al siguiente paciente.
 *
 * @param excluirEsperaId solicitud de espera que no debe recibir este horario (la que acaba de rechazarlo), o null
 */
public record HorarioLiberadoEvent(Long medicoId, Integer especialidadId, Instant inicio, Instant fin,
                                   Long excluirEsperaId) {

    public HorarioLiberadoEvent(Long medicoId, Integer especialidadId, Instant inicio, Instant fin) {
        this(medicoId, especialidadId, inicio, fin, null);
    }
}
