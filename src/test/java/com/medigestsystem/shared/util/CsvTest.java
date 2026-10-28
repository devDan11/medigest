package com.medigestsystem.shared.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class CsvTest {

    @Test
    void empiezaConBomUtf8YUsaCrlf() {
        byte[] datos = Csv.generar(List.of("Médico", "Citas"), List.of(Arrays.asList("Ana", 3)));

        assertThat(datos[0]).isEqualTo((byte) 0xEF);
        assertThat(datos[1]).isEqualTo((byte) 0xBB);
        assertThat(datos[2]).isEqualTo((byte) 0xBF);
        assertThat(new String(datos, 3, datos.length - 3, StandardCharsets.UTF_8)).isEqualTo("Médico;Citas\r\nAna;3\r\n");
    }

    @Test
    void entrecomillaLosValoresConComaComillasOSaltos() {
        assertThat(Csv.celda("Paz, Luis")).isEqualTo("\"Paz, Luis\"");
        assertThat(Csv.celda("dijo \"hola\"")).isEqualTo("\"dijo \"\"hola\"\"\"");
        assertThat(Csv.celda("a;b")).isEqualTo("\"a;b\"");
        assertThat(Csv.celda("a\nb")).isEqualTo("\"a\nb\"");
    }

    @Test
    void neutralizaLasFormulasDeHojaDeCalculo() {
        assertThat(Csv.celda("=HYPERLINK(\"http://x\")")).startsWith("\"'=");
        assertThat(Csv.celda("+cmd")).isEqualTo("'+cmd");
        assertThat(Csv.celda("@SUM(A1)")).isEqualTo("'@SUM(A1)");
    }

    @Test
    void losNumerosNegativosNoSeAlteran() {
        assertThat(Csv.celda(-5)).isEqualTo("-5");
        assertThat(Csv.celda(null)).isEmpty();
    }
}
