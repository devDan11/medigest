package com.medigestsystem.especialidades.dto;

import com.medigestsystem.especialidades.Especialidad;

public record EspecialidadResponse(
        Integer id,
        String nombre,
        String descripcion,
        int duracionSugeridaMin,
        boolean activa
) {

    public static EspecialidadResponse de(Especialidad e) {
        return new EspecialidadResponse(e.getId(), e.getNombre(), e.getDescripcion(),
                e.getDuracionSugeridaMin(), e.isActiva());
    }
}
