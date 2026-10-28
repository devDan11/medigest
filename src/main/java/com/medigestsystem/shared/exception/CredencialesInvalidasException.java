package com.medigestsystem.shared.exception;

import org.springframework.http.HttpStatus;

public class CredencialesInvalidasException extends ApiException {

    public CredencialesInvalidasException(String mensaje) {
        super(HttpStatus.UNAUTHORIZED, "CREDENCIALES_INVALIDAS", mensaje);
    }

    public CredencialesInvalidasException(String codigo, String mensaje) {
        super(HttpStatus.UNAUTHORIZED, codigo, mensaje);
    }
}
