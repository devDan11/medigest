package com.medigestsystem.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PoliticaPasswordTest {

    @ParameterizedTest
    @ValueSource(strings = {"Clave2026", "abcdefg1", "12345678a", "Con espacios 1", "Dan2118."})
    void aceptaContrasenasConLetraYNumero(String password) {
        assertThat(PoliticaPassword.esValida(password)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"corta1", "soloLetrasSinNumero", "123456789", "", "        "})
    void rechazaContrasenasDebiles(String password) {
        assertThat(PoliticaPassword.esValida(password)).isFalse();
    }

    @Test
    void rechazaNuloYContrasenasMayoresAlLimiteDeBcrypt() {
        assertThat(PoliticaPassword.esValida(null)).isFalse();
        assertThat(PoliticaPassword.esValida("a1".repeat(37))).isFalse(); // 74 caracteres
        assertThat(PoliticaPassword.esValida("a1".repeat(36))).isTrue();  // 72 caracteres
    }
}
