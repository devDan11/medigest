package com.medigestsystem.horarios;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalTime;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.medigestsystem.auditoria.AuditoriaService;
import com.medigestsystem.citas.Cita;
import com.medigestsystem.citas.CitaRepository;
import com.medigestsystem.horarios.dto.FranjaRequest;
import com.medigestsystem.horarios.dto.HorarioRequest;
import com.medigestsystem.medicos.Medico;
import com.medigestsystem.medicos.MedicoRepository;
import com.medigestsystem.shared.exception.ReglaNegocioException;
import com.medigestsystem.shared.exception.SolicitudInvalidaException;
import com.medigestsystem.testsupport.Fixtures;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HorarioServiceTest {

    @Mock HorarioMedicoRepository horarioRepository;
    @Mock MedicoRepository medicoRepository;
    @Mock CitaRepository citaRepository;
    @Mock AuditoriaService auditoriaService;

    HorarioService servicio;
    Medico medico;

    @BeforeEach
    void preparar() {
        servicio = new HorarioService(horarioRepository, medicoRepository, citaRepository,
                auditoriaService, Fixtures.fechaHora());
        medico = new Medico();
        medico.setId(7L);
        medico.setDuracionConsultaMin(30);
        when(medicoRepository.findById(7L)).thenReturn(Optional.of(medico));
        when(horarioRepository.findByMedicoId(7L)).thenReturn(List.of());
        when(horarioRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));
        when(citaRepository.findByMedicoIdAndInicioUtcAfterAndEstadoNotIn(anyLong(), any(), anyCollection()))
                .thenReturn(List.of());
    }

    private static FranjaRequest franja(int dia, String ini, String fin) {
        return new FranjaRequest(dia, LocalTime.parse(ini), LocalTime.parse(fin), null, null);
    }

    @Test
    void franjaValidaSeAcepta() {
        assertThatCode(() -> HorarioService.validarFranjas(List.of(franja(1, "08:00", "13:00")), 30))
                .doesNotThrowAnyException();
    }

    @Test
    void finAnteriorAlInicioSeRechaza() {
        assertThatThrownBy(() -> HorarioService.validarFranjas(List.of(franja(1, "13:00", "08:00")), 30))
                .isInstanceOf(SolicitudInvalidaException.class);
    }

    @Test
    void franjasSolapadasElMismoDiaSeRechazan() {
        assertThatThrownBy(() -> HorarioService.validarFranjas(
                List.of(franja(2, "08:00", "12:00"), franja(2, "11:00", "15:00")), 30))
                .isInstanceOf(SolicitudInvalidaException.class);
    }

    @Test
    void franjasContiguasOMismaHoraEnOtroDiaSeAdmiten() {
        assertThatCode(() -> HorarioService.validarFranjas(
                List.of(franja(2, "08:00", "12:00"), franja(2, "12:00", "15:00"), franja(3, "08:00", "12:00")), 30))
                .doesNotThrowAnyException();
    }

    @Test
    void descansoIncompletoOFueraDeRangoSeRechaza() {
        FranjaRequest sinFin = new FranjaRequest(1, LocalTime.of(8, 0), LocalTime.of(16, 0), LocalTime.of(12, 0), null);
        FranjaRequest fuera = new FranjaRequest(1, LocalTime.of(8, 0), LocalTime.of(16, 0), LocalTime.of(15, 0), LocalTime.of(17, 0));
        assertThatThrownBy(() -> HorarioService.validarFranjas(List.of(sinFin), 30))
                .isInstanceOf(SolicitudInvalidaException.class);
        assertThatThrownBy(() -> HorarioService.validarFranjas(List.of(fuera), 30))
                .isInstanceOf(SolicitudInvalidaException.class);
    }

    @Test
    void franjaMasCortaQueUnaConsultaSeRechaza() {
        assertThatThrownBy(() -> HorarioService.validarFranjas(List.of(franja(1, "08:00", "08:20")), 30))
                .isInstanceOf(SolicitudInvalidaException.class);
    }

    @Test
    void reemplazarBorraLoAnteriorYGuardaLoNuevo() {
        var resultado = servicio.reemplazar(7L, new HorarioRequest(List.of(franja(1, "08:00", "12:00"))));
        assertThat(resultado).hasSize(1);
        verify(horarioRepository).deleteAll(List.of());
        verify(horarioRepository).flush();
    }

    @Test
    void noSePermiteDejarFueraUnaCitaFuturaYaReservada() {
        // Lunes 2030-01-14 14:00 UTC = 09:00 Lima; el nuevo horario solo cubre los martes.
        Cita cita = new Cita();
        cita.setInicioUtc(Instant.parse("2030-01-14T14:00:00Z"));
        cita.setFinUtc(Instant.parse("2030-01-14T14:30:00Z"));
        when(citaRepository.findByMedicoIdAndInicioUtcAfterAndEstadoNotIn(anyLong(), any(), anyCollection()))
                .thenReturn(List.of(cita));

        assertThatThrownBy(() -> servicio.reemplazar(7L, new HorarioRequest(List.of(franja(2, "08:00", "12:00")))))
                .isInstanceOf(ReglaNegocioException.class);
        verify(horarioRepository, never()).deleteAll(any());
    }

    @Test
    void unaCitaDentroDelNuevoHorarioNoBloquea() {
        Cita cita = new Cita();
        cita.setInicioUtc(Instant.parse("2030-01-14T14:00:00Z"));
        cita.setFinUtc(Instant.parse("2030-01-14T14:30:00Z"));
        when(citaRepository.findByMedicoIdAndInicioUtcAfterAndEstadoNotIn(anyLong(), any(), anyCollection()))
                .thenReturn(List.of(cita));

        assertThatCode(() -> servicio.reemplazar(7L, new HorarioRequest(List.of(franja(1, "08:00", "12:00")))))
                .doesNotThrowAnyException();
    }
}
