package com.medigestsystem.disponibilidad;

import java.time.Instant;

/** Intervalo libre calculado: [inicio, fin). */
public record Slot(Instant inicio, Instant fin) {
}
