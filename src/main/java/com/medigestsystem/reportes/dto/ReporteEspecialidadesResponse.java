package com.medigestsystem.reportes.dto;

import java.time.LocalDate;
import java.util.List;

public record ReporteEspecialidadesResponse(LocalDate desde, LocalDate hasta, List<FilaResumen> especialidades) {
}
