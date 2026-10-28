package com.medigestsystem.system;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;

public record SystemStatusResponse(
        String aplicacion,
        List<String> perfilesActivos,
        String zonaHoraria,
        Instant ahoraUtc,
        ZonedDateTime ahoraClinica,
        boolean baseDatosDisponible,
        String baseDatos
) {
}
