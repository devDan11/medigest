package com.medigestsystem.shared.exception;

import org.springframework.http.HttpStatus;

public class ReglaNegocioException extends ApiException {

    public ReglaNegocioException(String mensaje) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, "REGLA_NEGOCIO", mensaje);
    }

    public ReglaNegocioException(String codigo, String mensaje) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, codigo, mensaje);
    }
}
