package com.medigestsystem.shared.dto;

import jakarta.validation.constraints.NotNull;

/** Cuerpo común para activar o desactivar un registro (PATCH .../estado). */
public record EstadoRequest(@NotNull(message = "Indique si el registro queda activo") Boolean activo) {
}
