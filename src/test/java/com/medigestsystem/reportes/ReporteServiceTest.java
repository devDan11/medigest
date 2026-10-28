package com.medigestsystem.reportes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.medigestsystem.citas.CitaRepository;
import com.medigestsystem.citas.EstadoCita;
import com.medigestsystem.reportes.dto.ReporteCitasResponse;
import com.medigestsystem.shared.exception.SolicitudInvalidaException;
import com.medigestsystem.testsupport.Fixtures;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReporteServiceTest {

    @Mock CitaRepository citaRepository;
    ReporteService servicio;

    @BeforeEach
    void preparar() {
        servicio = new ReporteService(citaRepository, Fixtures.fechaHora(), Fixtures.LIMA);
    }

    static Object[] estado(EstadoCita e, long n) {
        return new Object[]{e, n};
    }

    @Test
    void sinFechasUsaLosUltimos30DiasHastaHoyEnLima() {
        ReporteCitasResponse r = servicio.citas(null, null, null, null);

        assertThat(r.hasta()).isEqualTo(LocalDate.of(2030, 1, 7));
        assertThat(r.desde()).isEqualTo(LocalDate.of(2029, 12, 9));
        assertThat(r.porDia()).hasSize(30);

        ArgumentCaptor<Instant> desde = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> hasta = ArgumentCaptor.forClass(Instant.class);
        verify(citaRepository).contarEstadosReporte(desde.capture(), hasta.capture(), anyLong(), anyInt());
        assertThat(desde.getValue()).isEqualTo(Instant.parse("2029-12-09T05:00:00Z"));
        assertThat(hasta.getValue()).isEqualTo(Instant.parse("2030-01-08T05:00:00Z"));
    }

    @Test
    void calculaLasTasasDeAsistenciaYCancelacion() {
        when(citaRepository.contarEstadosReporte(any(), any(), anyLong(), anyInt())).thenReturn(List.of(
                estado(EstadoCita.ATENDIDA, 8), estado(EstadoCita.NO_ASISTIO, 2), estado(EstadoCita.CANCELADA, 5)));

        ReporteCitasResponse r = servicio.citas(null, null, null, null);

        assertThat(r.total()).isEqualTo(15);
        assertThat(r.tasaAsistencia()).isEqualTo(80.0);
        assertThat(r.tasaCancelacion()).isEqualTo(33.3);
        assertThat(r.porEstado().get(EstadoCita.PENDIENTE)).isZero();
    }

    @Test
    void sinCitasLasTasasSonNulasYLosDiasQuedanEnCero() {
        ReporteCitasResponse r = servicio.citas(null, null, null, null);

        assertThat(r.total()).isZero();
        assertThat(r.tasaAsistencia()).isNull();
        assertThat(r.tasaCancelacion()).isNull();
        assertThat(r.porDia()).allMatch(d -> d.total() == 0);
    }

    @Test
    void agrupaPorDiaLocalYNoCuentaLasCanceladas() {
        // 04:30Z del 7-ene = 23:30 del 6-ene en Lima
        when(citaRepository.diasReporte(any(), any(), anyLong(), anyInt())).thenReturn(List.of(
                new Object[]{Instant.parse("2030-01-07T04:30:00Z"), EstadoCita.CONFIRMADA},
                new Object[]{Instant.parse("2030-01-07T15:00:00Z"), EstadoCita.PENDIENTE},
                new Object[]{Instant.parse("2030-01-07T16:00:00Z"), EstadoCita.CANCELADA}));

        ReporteCitasResponse r = servicio.citas(LocalDate.of(2030, 1, 6), LocalDate.of(2030, 1, 7), null, null);

        assertThat(r.porDia()).extracting(d -> d.fecha() + ":" + d.total()).containsExactly("2030-01-06:1", "2030-01-07:1");
    }

    @Test
    void agrupaPorMedicoOrdenadoPorTotal() {
        when(citaRepository.medicosReporte(any(), any(), anyLong(), anyInt())).thenReturn(List.of(
                new Object[]{1L, "Ana", "Paz", EstadoCita.ATENDIDA, 2L},
                new Object[]{1L, "Ana", "Paz", EstadoCita.CANCELADA, 1L},
                new Object[]{2L, "Luis", "Rojas", EstadoCita.ATENDIDA, 5L}));

        var filas = servicio.citas(null, null, null, null).porMedico();

        assertThat(filas).extracting("nombre").containsExactly("Luis Rojas", "Ana Paz");
        assertThat(filas.get(1).total()).isEqualTo(3);
        assertThat(filas.get(1).atendidas()).isEqualTo(2);
        assertThat(filas.get(1).canceladas()).isEqualTo(1);
    }

    @Test
    void agrupaPorEspecialidad() {
        when(citaRepository.especialidadesReporte(any(), any())).thenReturn(List.of(
                new Object[]{3, "Cardiología", EstadoCita.ATENDIDA, 4L},
                new Object[]{3, "Cardiología", EstadoCita.NO_ASISTIO, 1L}));

        var filas = servicio.especialidades(null, null).especialidades();

        assertThat(filas).hasSize(1);
        assertThat(filas.get(0).nombre()).isEqualTo("Cardiología");
        assertThat(filas.get(0).total()).isEqualTo(5);
        assertThat(filas.get(0).noAsistio()).isEqualTo(1);
    }

    @Test
    void rechazaRangosInvertidosOExcesivos() {
        assertThatThrownBy(() -> servicio.citas(LocalDate.of(2030, 1, 7), LocalDate.of(2030, 1, 1), null, null))
                .isInstanceOf(SolicitudInvalidaException.class);
        assertThatThrownBy(() -> servicio.citas(LocalDate.of(2028, 1, 1), LocalDate.of(2030, 1, 1), null, null))
                .isInstanceOf(SolicitudInvalidaException.class);
    }

    @Test
    void elCsvPorMedicoIncluyeCabeceraYFilas() {
        when(citaRepository.medicosReporte(any(), any(), anyLong(), anyInt())).thenReturn(List.<Object[]>of(
                new Object[]{1L, "Ana", "Paz", EstadoCita.ATENDIDA, 2L}));

        String csv = new String(servicio.csvCitas("medico", null, null, null, null), StandardCharsets.UTF_8);

        assertThat(csv).contains("Médico;Total;Atendidas;Canceladas;No asistió\r\nAna Paz;2;2;0;0\r\n");
    }

    @Test
    void rechazaUnaVistaDesconocida() {
        assertThatThrownBy(() -> servicio.csvCitas("otra", null, null, null, null))
                .isInstanceOf(SolicitudInvalidaException.class);
    }
}
