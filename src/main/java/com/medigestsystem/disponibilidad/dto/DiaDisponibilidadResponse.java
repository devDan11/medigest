package com.medigestsystem.disponibilidad.dto;

import java.time.LocalDate;
import java.util.List;

public record DiaDisponibilidadResponse(LocalDate fecha, List<SlotResponse> horarios) {
}
