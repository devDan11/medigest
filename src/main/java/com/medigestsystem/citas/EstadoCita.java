package com.medigestsystem.citas;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Estados de una cita y sus transiciones válidas.
 *
 * <pre>
 * PENDIENTE   -> CONFIRMADA | EN_ESPERA | CANCELADA | NO_ASISTIO
 * CONFIRMADA  -> EN_ESPERA | CANCELADA | NO_ASISTIO
 * EN_ESPERA   -> EN_ATENCION | CANCELADA | NO_ASISTIO
 * EN_ATENCION -> ATENDIDA
 * ATENDIDA, CANCELADA, NO_ASISTIO: estados terminales
 * </pre>
 */
public enum EstadoCita {
    PENDIENTE,
    CONFIRMADA,
    EN_ESPERA,
    EN_ATENCION,
    ATENDIDA,
    CANCELADA,
    NO_ASISTIO;

    private static final Map<EstadoCita, Set<EstadoCita>> TRANSICIONES = new EnumMap<>(EstadoCita.class);

    static {
        TRANSICIONES.put(PENDIENTE, EnumSet.of(CONFIRMADA, EN_ESPERA, CANCELADA, NO_ASISTIO));
        TRANSICIONES.put(CONFIRMADA, EnumSet.of(EN_ESPERA, CANCELADA, NO_ASISTIO));
        TRANSICIONES.put(EN_ESPERA, EnumSet.of(EN_ATENCION, CANCELADA, NO_ASISTIO));
        TRANSICIONES.put(EN_ATENCION, EnumSet.of(ATENDIDA));
        TRANSICIONES.put(ATENDIDA, EnumSet.noneOf(EstadoCita.class));
        TRANSICIONES.put(CANCELADA, EnumSet.noneOf(EstadoCita.class));
        TRANSICIONES.put(NO_ASISTIO, EnumSet.noneOf(EstadoCita.class));
    }

    public boolean puedeTransicionarA(EstadoCita destino) {
        return TRANSICIONES.get(this).contains(destino);
    }

    public Set<EstadoCita> transicionesPermitidas() {
        return Collections.unmodifiableSet(TRANSICIONES.get(this));
    }

    public boolean esTerminal() {
        return TRANSICIONES.get(this).isEmpty();
    }

    /** Una cita ocupa el horario del médico, paciente y consultorio salvo que esté cancelada o sin asistencia. */
    public boolean ocupaHorario() {
        return this != CANCELADA && this != NO_ASISTIO;
    }

    /** Solo se puede reprogramar antes de que el paciente llegue. */
    public boolean permiteReprogramar() {
        return this == PENDIENTE || this == CONFIRMADA;
    }
}
