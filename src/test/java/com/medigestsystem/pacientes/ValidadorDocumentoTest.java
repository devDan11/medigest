package com.medigestsystem.pacientes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.medigestsystem.shared.exception.SolicitudInvalidaException;

class ValidadorDocumentoTest {

    @Test
    void normalizaEspaciosYMayusculas() {
        assertThat(ValidadorDocumento.normalizar("  ab12 ")).isEqualTo("AB12");
    }

    @Test
    void dniValidoTieneOchoDigitos() {
        assertThatCode(() -> ValidadorDocumento.validar(TipoDocumento.DNI, "12345678")).doesNotThrowAnyException();
    }

    @Test
    void dniConLetrasOLongitudIncorrectaSeRechaza() {
        assertThatThrownBy(() -> ValidadorDocumento.validar(TipoDocumento.DNI, "1234567"))
                .isInstanceOf(SolicitudInvalidaException.class);
        assertThatThrownBy(() -> ValidadorDocumento.validar(TipoDocumento.DNI, "1234567A"))
                .isInstanceOf(SolicitudInvalidaException.class);
    }

    @Test
    void carnetDeExtranjeriaYPasaporteAdmitenAlfanumericos() {
        assertThatCode(() -> ValidadorDocumento.validar(TipoDocumento.CE, "ABC123456")).doesNotThrowAnyException();
        assertThatCode(() -> ValidadorDocumento.validar(TipoDocumento.PASAPORTE, "P1234567")).doesNotThrowAnyException();
        assertThatThrownBy(() -> ValidadorDocumento.validar(TipoDocumento.PASAPORTE, "AB"))
                .isInstanceOf(SolicitudInvalidaException.class);
    }
}
