package com.medigestsystem.pacientes;

import java.util.Locale;
import java.util.regex.Pattern;

import com.medigestsystem.shared.exception.SolicitudInvalidaException;

/** Normalización y validación del documento de identidad según su tipo. */
public final class ValidadorDocumento {

    private static final Pattern PATRON_DNI = Pattern.compile("^\\d{8}$");
    private static final Pattern PATRON_CE = Pattern.compile("^[A-Z0-9]{9,12}$");
    private static final Pattern PATRON_PASAPORTE = Pattern.compile("^[A-Z0-9]{6,12}$");
    private static final Pattern PATRON_OTRO = Pattern.compile("^[A-Z0-9-]{5,20}$");

    private ValidadorDocumento() {
    }

    public static String normalizar(String documento) {
        return documento == null ? "" : documento.trim().toUpperCase(Locale.ROOT);
    }

    /** Lanza {@link SolicitudInvalidaException} si el número no cumple el formato del tipo indicado. */
    public static void validar(TipoDocumento tipo, String documentoNormalizado) {
        Pattern patron = switch (tipo) {
            case DNI -> PATRON_DNI;
            case CE -> PATRON_CE;
            case PASAPORTE -> PATRON_PASAPORTE;
            case OTRO -> PATRON_OTRO;
        };
        if (!patron.matcher(documentoNormalizado).matches()) {
            String esperado = switch (tipo) {
                case DNI -> "El DNI debe tener exactamente 8 dígitos.";
                case CE -> "El carné de extranjería debe tener entre 9 y 12 caracteres alfanuméricos.";
                case PASAPORTE -> "El pasaporte debe tener entre 6 y 12 caracteres alfanuméricos.";
                case OTRO -> "El documento debe tener entre 5 y 20 caracteres alfanuméricos o guiones.";
            };
            throw new SolicitudInvalidaException("DOCUMENTO_INVALIDO", esperado);
        }
    }
}
