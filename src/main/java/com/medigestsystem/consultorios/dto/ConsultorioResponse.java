package com.medigestsystem.consultorios.dto;

import com.medigestsystem.consultorios.Consultorio;

public record ConsultorioResponse(Integer id, String codigo, String ubicacion, boolean activo) {

    public static ConsultorioResponse de(Consultorio c) {
        return new ConsultorioResponse(c.getId(), c.getCodigo(), c.getUbicacion(), c.isActivo());
    }
}
