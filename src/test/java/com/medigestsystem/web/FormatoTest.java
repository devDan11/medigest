package com.medigestsystem.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.medigestsystem.citas.AccionCita;
import com.medigestsystem.citas.EstadoCita;
import com.medigestsystem.citas.TipoEventoCita;
import com.medigestsystem.listaespera.EstadoListaEspera;

class FormatoTest {

    private final Formato fmt = new Formato();

    @Test
    void formateaFechasEnLaHoraRecibida() {
        OffsetDateTime valor = OffsetDateTime.of(2030, 1, 9, 9, 5, 0, 0, ZoneOffset.ofHours(-5));

        assertThat(fmt.fechaHora(valor)).isEqualTo("09/01/2030 09:05");
        assertThat(fmt.fecha(valor)).isEqualTo("09/01/2030");
        assertThat(fmt.fecha(LocalDate.of(2030, 1, 9))).isEqualTo("09/01/2030");
        assertThat(fmt.hora(valor)).isEqualTo("09:05");
    }

    @Test
    void losValoresNulosSeMuestranComoGuion() {
        assertThat(fmt.fechaHora(null)).isEqualTo("—");
        assertThat(fmt.hora(null)).isEqualTo("—");
        assertThat(fmt.estado(null)).isEqualTo("—");
        assertThat(fmt.texto(null)).isEqualTo("—");
    }

    @Test
    void todosLosEstadosTienenEtiquetaYClase() {
        for (EstadoCita estado : EstadoCita.values()) {
            assertThat(fmt.estado(estado)).isNotBlank();
            assertThat(fmt.claseEstado(estado)).startsWith("text-bg-");
        }
        for (EstadoListaEspera estado : EstadoListaEspera.values()) {
            assertThat(fmt.estadoEspera(estado)).isNotBlank();
            assertThat(fmt.claseEspera(estado)).startsWith("text-bg-");
        }
        assertThat(fmt.estado(EstadoCita.NO_ASISTIO)).isEqualTo("No asistió");
    }

    @Test
    void convierteNombresDeEnumeracionEnTexto() {
        assertThat(fmt.texto(TipoEventoCita.values()[0])).doesNotContain("_").matches("^[A-ZÁÉÍÓÚÑ].*");
        assertThat(fmt.texto(EstadoCita.EN_ATENCION)).isEqualTo("En atencion");
    }

    @Test
    void consultaLasAccionesPermitidasPorNombre() {
        List<AccionCita> acciones = List.of(AccionCita.CONFIRMAR, AccionCita.CANCELAR);

        assertThat(fmt.puede(acciones, "CONFIRMAR")).isTrue();
        assertThat(fmt.puede(acciones, "REPROGRAMAR")).isFalse();
        assertThat(fmt.puede(null, "CONFIRMAR")).isFalse();
    }
}
