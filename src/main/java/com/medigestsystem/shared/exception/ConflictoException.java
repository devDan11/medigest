package com.medigestsystem.shared.exception;

import org.springframework.http.HttpStatus;

public class ConflictoException extends ApiException {

    public ConflictoException(String mensaje) {
        super(HttpStatus.CONFLICT, "CONFLICTO", mensaje);
    }

    public ConflictoException(String codigo, String mensaje) {
        super(HttpStatus.CONFLICT, codigo, mensaje);
    }
}
