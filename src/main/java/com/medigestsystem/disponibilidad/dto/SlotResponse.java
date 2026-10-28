package com.medigestsystem.disponibilidad.dto;

import java.time.OffsetDateTime;

/** Horario libre: inicio y fin en hora local de la clínica (America/Lima). */
public record SlotResponse(OffsetDateTime inicio, OffsetDateTime fin) {
}
