package com.medigestsystem.shared.exception;

import org.springframework.http.HttpStatus;

/** Excepción de negocio con código HTTP y código funcional estable para el cliente. */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus estado;
    private final String codigo;

    protected ApiException(HttpStatus estado, String codigo, String mensaje) {
        super(mensaje);
        this.estado = estado;
        this.codigo = codigo;
    }

    public HttpStatus getEstado() {
        return estado;
    }

    public String getCodigo() {
        return codigo;
    }
}
