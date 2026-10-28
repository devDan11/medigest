package com.medigestsystem.shared.exception;

import org.springframework.http.HttpStatus;

public class SolicitudInvalidaException extends ApiException {

    public SolicitudInvalidaException(String mensaje) {
        super(HttpStatus.BAD_REQUEST, "SOLICITUD_INVALIDA", mensaje);
    }

    public SolicitudInvalidaException(String codigo, String mensaje) {
        super(HttpStatus.BAD_REQUEST, codigo, mensaje);
    }
}
