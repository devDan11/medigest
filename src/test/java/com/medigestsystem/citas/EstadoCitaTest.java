package com.medigestsystem.citas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class EstadoCitaTest {

    @Test
    void unaCitaCanceladaNoPuedeMarcarseComoAtendida() {
        assertFalse(EstadoCita.CANCELADA.puedeTransicionarA(EstadoCita.ATENDIDA));
    }

    @Test
    void unaCitaAtendidaNoPuedeVolverAPendiente() {
        assertFalse(EstadoCita.ATENDIDA.puedeTransicionarA(EstadoCita.PENDIENTE));
    }

    @Test
    void flujoNormalDeAtencion() {
        assertTrue(EstadoCita.PENDIENTE.puedeTransicionarA(EstadoCita.CONFIRMADA));
        assertTrue(EstadoCita.CONFIRMADA.puedeTransicionarA(EstadoCita.EN_ESPERA));
        assertTrue(EstadoCita.EN_ESPERA.puedeTransicionarA(EstadoCita.EN_ATENCION));
        assertTrue(EstadoCita.EN_ATENCION.puedeTransicionarA(EstadoCita.ATENDIDA));
    }

    @Test
    void enAtencionSoloPuedeTerminarComoAtendida() {
        assertEquals(Set.of(EstadoCita.ATENDIDA), EstadoCita.EN_ATENCION.transicionesPermitidas());
    }

    @Test
    void noSePuedeAtenderSinPasarPorLaSalaDeEspera() {
        assertFalse(EstadoCita.PENDIENTE.puedeTransicionarA(EstadoCita.EN_ATENCION));
        assertFalse(EstadoCita.CONFIRMADA.puedeTransicionarA(EstadoCita.ATENDIDA));
    }

    @Test
    void losEstadosTerminalesNoTienenTransiciones() {
        Set<EstadoCita> terminales = EnumSet.of(EstadoCita.ATENDIDA, EstadoCita.CANCELADA, EstadoCita.NO_ASISTIO);
        for (EstadoCita estado : EstadoCita.values()) {
            assertEquals(terminales.contains(estado), estado.esTerminal(), estado.name());
        }
    }

    @ParameterizedTest
    @EnumSource(EstadoCita.class)
    void ningunEstadoTransicionaASiMismo(EstadoCita estado) {
        assertFalse(estado.puedeTransicionarA(estado));
    }

    @Test
    void soloLasCitasCanceladasOSinAsistenciaLiberanElHorario() {
        for (EstadoCita estado : EstadoCita.values()) {
            boolean libera = estado == EstadoCita.CANCELADA || estado == EstadoCita.NO_ASISTIO;
            assertEquals(!libera, estado.ocupaHorario(), estado.name());
        }
    }

    @Test
    void soloSePuedeReprogramarAntesDeLaLlegadaDelPaciente() {
        assertTrue(EstadoCita.PENDIENTE.permiteReprogramar());
        assertTrue(EstadoCita.CONFIRMADA.permiteReprogramar());
        assertFalse(EstadoCita.EN_ESPERA.permiteReprogramar());
        assertFalse(EstadoCita.ATENDIDA.permiteReprogramar());
        assertFalse(EstadoCita.CANCELADA.permiteReprogramar());
    }

    @Test
    void laSalidaDeTransicionesPermitidasNoSePuedeModificar() {
        Set<EstadoCita> permitidas = EstadoCita.PENDIENTE.transicionesPermitidas();
        try {
            permitidas.add(EstadoCita.ATENDIDA);
        } catch (UnsupportedOperationException esperado) {
            assertFalse(EstadoCita.PENDIENTE.puedeTransicionarA(EstadoCita.ATENDIDA));
            return;
        }
        throw new AssertionError("El conjunto de transiciones debería ser inmutable");
    }
}
