package com.medigestsystem.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;

import com.medigestsystem.citas.CitaRepository;
import com.medigestsystem.citas.CitaService;
import com.medigestsystem.citas.EstadoCita;
import com.medigestsystem.citas.dto.CitaResumenResponse;
import com.medigestsystem.listaespera.EstadoListaEspera;
import com.medigestsystem.listaespera.ListaEsperaService;
import com.medigestsystem.listaespera.dto.ListaEsperaResponse;
import com.medigestsystem.medicos.MedicoRepository;
import com.medigestsystem.pacientes.PacienteRepository;
import com.medigestsystem.shared.dto.PaginaResponse;
import com.medigestsystem.testsupport.Fixtures;
import com.medigestsystem.usuarios.NombreRol;

/** "Ahora" es el lunes 2030-01-07 10:00 en Lima (15:00 UTC). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DashboardServiceTest {

    static final Instant HOY_INI = Instant.parse("2030-01-07T05:00:00Z");
    static final Instant MANANA_INI = Instant.parse("2030-01-08T05:00:00Z");
    static final Instant PENDIENTES_FIN = Fixtures.AHORA.plusSeconds(30L * 24 * 3600);

    @Mock CitaRepository citaRepository;
    @Mock CitaService citaService;
    @Mock ListaEsperaService listaEsperaService;
    @Mock MedicoRepository medicoRepository;
    @Mock PacienteRepository pacienteRepository;

    DashboardService servicio;

    @BeforeEach
    void preparar() {
        servicio = new DashboardService(citaRepository, citaService, listaEsperaService, medicoRepository,
                pacienteRepository, Fixtures.fechaHora(), Fixtures.LIMA);
        when(citaService.listar(any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new PaginaResponse<>(List.of(), 0, 20, 0, 0));
        when(listaEsperaService.listar(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new PaginaResponse<ListaEsperaResponse>(List.of(), 0, 1, 4, 4));
    }

    @AfterEach
    void limpiar() {
        Fixtures.cerrarSesion();
    }

    private static CitaResumenResponse cita(long id, EstadoCita estado) {
        OffsetDateTime inicio = OffsetDateTime.of(2030, 1, 9, 9, 0, 0, 0, ZoneOffset.ofHours(-5));
        return new CitaResumenResponse(id, "MG-" + id, 9L, "Ana", 5L, "Dr. Pérez", 1, "Cardiología", "C-1",
                inicio, inicio.plusMinutes(30), estado, null);
    }

    @Test
    void elPersonalVeLosIndicadoresGlobales() {
        Fixtures.autenticarComo(2L, NombreRol.RECEPCIONISTA);
        when(citaRepository.contarPorEstado(eq(HOY_INI), eq(MANANA_INI), eq(-1L), eq(-1L)))
                .thenReturn(List.<Object[]>of(new Object[]{EstadoCita.CONFIRMADA, 2L}, new Object[]{EstadoCita.PENDIENTE, 1L}));
        when(citaRepository.contarPorEstado(eq(Fixtures.AHORA), eq(PENDIENTES_FIN), eq(-1L), eq(-1L)))
                .thenReturn(List.<Object[]>of(new Object[]{EstadoCita.PENDIENTE, 6L}));
        when(citaRepository.iniciosEntre(any(), any(), eq(-1L), eq(-1L))).thenReturn(List.of(
                Instant.parse("2030-01-07T14:00:00Z"), Instant.parse("2030-01-07T16:00:00Z"),
                Instant.parse("2030-01-09T14:00:00Z"), Instant.parse("2030-02-20T14:00:00Z")));
        when(citaService.listar(any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new PaginaResponse<>(List.of(cita(1, EstadoCita.CONFIRMADA), cita(2, EstadoCita.CANCELADA),
                        cita(3, EstadoCita.ATENDIDA), cita(4, EstadoCita.PENDIENTE)), 0, 20, 4, 1));
        when(pacienteRepository.countByActivo(true)).thenReturn(10L);
        when(medicoRepository.countByActivo(true)).thenReturn(3L);

        DashboardResponse r = servicio.resumen();

        assertThat(r.globales()).isTrue();
        assertThat(r.citasHoy()).isEqualTo(3);
        assertThat(r.porEstadoHoy().get(EstadoCita.CONFIRMADA)).isEqualTo(2);
        assertThat(r.porEstadoHoy().get(EstadoCita.CANCELADA)).isZero();
        assertThat(r.pendientesDeConfirmar()).isEqualTo(6);
        assertThat(r.proximosDias()).hasSize(7);
        assertThat(r.proximosDias().get(0).total()).isEqualTo(2);
        assertThat(r.proximosDias().get(1).total()).isZero();
        assertThat(r.proximosDias().get(2).total()).isEqualTo(1);
        assertThat(r.proximosDias().stream().mapToLong(d -> d.total()).sum()).isEqualTo(3); // el de febrero queda fuera
        assertThat(r.proximas()).extracting(CitaResumenResponse::id).containsExactly(1L, 4L); // sin canceladas ni atendidas
        assertThat(r.pacientesActivos()).isEqualTo(10);
        assertThat(r.medicosActivos()).isEqualTo(3);
        assertThat(r.esperaActiva()).isEqualTo(4);
    }

    @Test
    void elMedicoSoloVeSuPropiaAgendaYNoLosTotalesDeLaClinica() {
        Fixtures.autenticarComo(70L, NombreRol.MEDICO);
        when(medicoRepository.buscarIdPorUsuario(70L)).thenReturn(Optional.of(5L));
        when(citaRepository.contarPorEstado(any(), any(), anyLong(), anyLong())).thenReturn(List.of());
        when(citaRepository.iniciosEntre(any(), any(), anyLong(), anyLong())).thenReturn(List.of());

        DashboardResponse r = servicio.resumen();

        assertThat(r.globales()).isFalse();
        assertThat(r.pacientesActivos()).isNull();
        assertThat(r.medicosActivos()).isNull();
        assertThat(r.esperaActiva()).isNull();
        verify(citaRepository).contarPorEstado(eq(HOY_INI), eq(MANANA_INI), eq(5L), eq(-1L));
        verify(listaEsperaService, never()).listar(any(), any(), any(), anyInt(), anyInt());
        verify(pacienteRepository, never()).countByActivo(true);
    }

    @Test
    void elPacienteSoloVeSusCitasYUnaCuentaSinFichaNoVeNada() {
        Fixtures.autenticarComo(50L, NombreRol.PACIENTE);
        when(pacienteRepository.buscarIdPorUsuario(50L)).thenReturn(Optional.empty());
        when(citaRepository.contarPorEstado(any(), any(), anyLong(), anyLong())).thenReturn(List.of());
        when(citaRepository.iniciosEntre(any(), any(), anyLong(), anyLong())).thenReturn(List.of());

        DashboardResponse r = servicio.resumen();

        assertThat(r.citasHoy()).isZero();
        verify(citaRepository).contarPorEstado(eq(HOY_INI), eq(MANANA_INI), eq(-1L), eq(-2L)); // -2: ninguna cita coincide
        assertThat(r.esperaActiva()).isEqualTo(4); // el servicio de espera ya acota al paciente
    }

    @Test
    void sinSesionOSinRolSeDeniega() {
        assertThatThrownBy(() -> servicio.resumen()).isInstanceOf(AccessDeniedException.class);

        Fixtures.autenticarComo(99L);
        assertThatThrownBy(() -> servicio.resumen()).isInstanceOf(AccessDeniedException.class);
    }
}
