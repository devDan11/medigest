package com.medigestsystem.web.paginas;

import java.util.Set;
import java.util.TreeSet;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

import org.springframework.stereotype.Component;

import com.medigestsystem.shared.exception.SolicitudInvalidaException;

/**
 * Aplica a los formularios web las mismas validaciones de los DTO de la API (que se declaran con anotaciones
 * y solo se ejecutan automáticamente en los controladores REST).
 */
@Component
public class ValidadorWeb {

    private final Validator validator;

    public ValidadorWeb(Validator validator) {
        this.validator = validator;
    }

    /** Devuelve el mismo objeto si es válido; si no, lanza un error de negocio con todos los mensajes. */
    public <T> T validar(T solicitud) {
        Set<ConstraintViolation<T>> violaciones = validator.validate(solicitud);
        if (!violaciones.isEmpty()) {
            Set<String> mensajes = new TreeSet<>();
            violaciones.forEach(v -> mensajes.add(v.getMessage()));
            throw new SolicitudInvalidaException("DATOS_INVALIDOS", String.join(" · ", mensajes));
        }
        return solicitud;
    }
}
