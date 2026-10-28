package com.medigestsystem.disponibilidad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.medigestsystem.citas.Cita;
import com.medigestsystem.citas.CitaRepository;
import com.medigestsystem.config.PoliticaCitasProperties;
import com.medigestsystem.consultorios.Consultorio;
import com.medigestsystem.horarios.BloqueoHorario;
import com.medigestsystem.horarios.BloqueoHorarioRepository;
import com.medigestsystem.horarios.EstadoBloqueo;
import com.medigestsystem.horarios.HorarioMedico;
import com.medigestsystem.horarios.HorarioMedicoRepository;
import com.medigestsystem.listaespera.ListaEspera;
import com.medigestsystem.listaespera.ListaEsperaRepository;
import com.medigestsystem.medicos.Medico;
import com.medigestsystem.medicos.MedicoRepository;
import com.medigestsystem.shared.exception.ApiException;
import com.medigestsystem.shared.exception.ConflictoException;
import com.medigestsystem.testsupport.Fixtures;

/**
 * "Ahora" es el lunes 2030-01-07 10:00 en Lima. La jornada de prueba es martes 08:00-12:00 con descanso
 * 10:00-10:30 y consultas de 30 minutos: 08:00, 08:30, 09:00, 09:30, 10:30, 11:00 y 11:30 (7 horarios).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DisponibilidadServiceTest {

    static final LocalDate MARTES = LocalDate.of(2030, 1, 8);

    @Mock HorarioMedicoRepository horarioRepository;
    @Mock CitaRepository citaRepository;
    @Mock BloqueoHorarioRepository bloqueoRepository;
    @Mock ListaEsperaRepository listaEsperaRepository;
    @Mock MedicoRepository medicoRepository;

    DisponibilidadService servicio;
    Medico medico;
    Consultorio consultorio;

    @BeforeEach
    void preparar() {
        servicio = new DisponibilidadService(horarioRepository, citaRepository, bloqueoRepository,
                listaEsperaRepository, medicoRepository, new PoliticaCitasProperties(60, 90, 24, 3, 30),
                Fixtures.fechaHora(), Fixtures.LIMA);
        consultorio = new Consultorio();
        consultorio.setId(3);
        consultorio.setCodigo("C-101");
        medico = new Medico();
        medico.setId(5L);
        medico.setActivo(true);
        medico.setDuracionConsultaMin(30);
        medico.setConsultorio(consultorio);

        HorarioMedico martes = franja(2, "08:00", "12:00", "10:00", "10:30");
        when(horarioRepository.findByMedicoIdAndActivoTrueOrderByDiaSemanaAscHoraInicioAsc(5L)).thenReturn(List.of(martes));
        when(horarioRepository.findByMedicoIdAndDiaSemanaAndActivoTrue(5L, 2)).thenReturn(List.of(martes));
        when(horarioRepository.findByMedicoIdAndDiaSemanaAndActivoTrue(5L, 1)).thenReturn(List.of());
        when(horarioRepository.findByMedicoIdAndDiaSemanaAndActivoTrue(5L, 7)).thenReturn(List.of());
        when(citaRepository.findByMedicoIdAndInicioUtcLessThanAndFinUtcGreaterThanAndEstadoNotIn(
                anyLong(), any(), any(), anyCollection())).thenReturn(List.of());
        when(citaRepository.findByConsultorioIdAndInicioUtcLessThanAndFinUtcGreaterThanAndEstadoNotIn(
                anyInt(), any(), any(), anyCollection())).thenReturn(List.of());
        when(bloqueoRepository.findByMedicoIdAndEstadoAndInicioUtcLessThanAndFinUtcGreaterThan(
                anyLong(), any(), any(), any())).thenReturn(List.of());
        when(bloqueoRepository.findByConsultorioIdAndEstadoAndInicioUtcLessThanAndFinUtcGreaterThan(
                anyInt(), any(), any(), any())).thenReturn(List.of());
        when(listaEsperaRepository.findOfertasVigentesDelMedico(anyLong(), any())).thenReturn(List.of());
        when(citaRepository.contarSolapadasMedicoSin(anyLong(), any(), any(), anyCollection(), anyLong())).thenReturn(0L);
        when(citaRepository.contarSolapadasConsultorioSin(anyInt(), any(), any(), anyCollection(), anyLong())).thenReturn(0L);
        when(citaRepository.contarSolapadasPacienteSin(anyLong(), any(), any(), anyCollection(), anyLong())).thenReturn(0L);
        when(listaEsperaRepository.contarOfertasVigentesSolapadas(anyLong(), any(), any(), any(), anyLong())).thenReturn(0L);
    }

    private static HorarioMedico franja(int dia, String ini, String fin, String dIni, String dFin) {
        HorarioMedico h = new HorarioMedico();
        h.setDiaSemana(dia);
        h.setHoraInicio(LocalTime.parse(ini));
        h.setHoraFin(LocalTime.parse(fin));
        if (dIni != null) {
            h.setDescansoInicio(LocalTime.parse(dIni));
            h.setDescansoFin(LocalTime.parse(dFin));
        }
        h.setActivo(true);
        return h;
    }

    /** Instante correspondiente a una hora local de Lima. */
    private static Instant lima(LocalDate dia, String hora) {
        return dia.atTime(LocalTime.parse(hora)).atZone(Fixtures.LIMA).toInstant();
    }

    private static void assertCodigo(Runnable accion, String codigo) {
        assertThatThrownBy(accion::run).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getCodigo()).isEqualTo(codigo));
    }

    // ------------------------------------------------------------------ generación de horarios

    @Test
    void generaHorariosRespetandoElDescanso() {
        List<LocalDateTime> inicios = DisponibilidadService.generarInicios(MARTES,
                List.of(franja(2, "08:00", "12:00", "10:00", "10:30")), 30);
        assertThat(inicios).extracting(LocalDateTime::toLocalTime).containsExactly(
                LocalTime.of(8, 0), LocalTime.of(8, 30), LocalTime.of(9, 0), LocalTime.of(9, 30),
                LocalTime.of(10, 30), LocalTime.of(11, 0), LocalTime.of(11, 30));
    }

    @Test
    void laUltimaConsultaDebeTerminarDentroDeLaJornada() {
        // 45 minutos en 08:00-10:00: 08:00-08:45 y 08:45-09:30; 09:30-10:15 no cabe.
        List<LocalDateTime> inicios = DisponibilidadService.generarInicios(MARTES,
                List.of(franja(2, "08:00", "10:00", null, null)), 45);
        assertThat(inicios).extracting(LocalDateTime::toLocalTime)
                .containsExactly(LocalTime.of(8, 0), LocalTime.of(8, 45));
    }

    @Test
    void despuesDelDescansoElTramoReiniciaDesdeSuHora() {
        // 08:00-12:00, descanso 10:00-10:20, consultas de 40 min: 08:00, 08:40, 09:20 | 10:20, 11:00 (11:40-12:20 no cabe)
        List<LocalDateTime> inicios = DisponibilidadService.generarInicios(MARTES,
                List.of(franja(2, "08:00", "12:00", "10:00", "10:20")), 40);
        assertThat(inicios).extracting(LocalDateTime::toLocalTime).containsExactly(
                LocalTime.of(8, 0), LocalTime.of(8, 40), LocalTime.of(9, 20), LocalTime.of(10, 20), LocalTime.of(11, 0));
    }

    // ------------------------------------------------------------------ cálculo de disponibilidad

    @Test
    void calculaLosSieteHorariosLibresDelMartes() {
        List<Slot> slots = servicio.calcular(medico, MARTES, MARTES);
        assertThat(slots).hasSize(7);
        assertThat(slots.get(0).inicio()).isEqualTo(lima(MARTES, "08:00"));
        assertThat(slots.get(0).fin()).isEqualTo(lima(MARTES, "08:30"));
    }

    @Test
    void noMuestraHorariosOcupadosPorUnaCita() {
        Cita c = new Cita();
        c.setInicioUtc(lima(MARTES, "09:00"));
        c.setFinUtc(lima(MARTES, "09:30"));
        when(citaRepository.findByMedicoIdAndInicioUtcLessThanAndFinUtcGreaterThanAndEstadoNotIn(
                eq(5L), any(), any(), anyCollection())).thenReturn(List.of(c));

        List<Slot> slots = servicio.calcular(medico, MARTES, MARTES);
        assertThat(slots).hasSize(6);
        assertThat(slots).extracting(Slot::inicio).doesNotContain(lima(MARTES, "09:00"));
    }

    @Test
    void noMuestraHorariosBloqueadosParaElMedico() {
        BloqueoHorario b = new BloqueoHorario();
        b.setInicioUtc(lima(MARTES, "08:00"));
        b.setFinUtc(lima(MARTES, "10:00"));
        b.setEstado(EstadoBloqueo.APROBADO);
        when(bloqueoRepository.findByMedicoIdAndEstadoAndInicioUtcLessThanAndFinUtcGreaterThan(
                eq(5L), eq(EstadoBloqueo.APROBADO), any(), any())).thenReturn(List.of(b));

        List<Slot> slots = servicio.calcular(medico, MARTES, MARTES);
        assertThat(slots).extracting(Slot::inicio).containsExactly(
                lima(MARTES, "10:30"), lima(MARTES, "11:00"), lima(MARTES, "11:30"));
    }

    @Test
    void noMuestraHorariosCuandoElConsultorioEstaOcupadoPorOtroMedico() {
        Cita deOtroMedico = new Cita();
        deOtroMedico.setInicioUtc(lima(MARTES, "11:00"));
        deOtroMedico.setFinUtc(lima(MARTES, "11:30"));
        when(citaRepository.findByConsultorioIdAndInicioUtcLessThanAndFinUtcGreaterThanAndEstadoNotIn(
                eq(3), any(), any(), anyCollection())).thenReturn(List.of(deOtroMedico));

        assertThat(servicio.calcular(medico, MARTES, MARTES)).extracting(Slot::inicio)
                .doesNotContain(lima(MARTES, "11:00")).hasSize(6);
    }

    @Test
    void noMuestraHorariosBloqueadosParaElConsultorio() {
        BloqueoHorario b = new BloqueoHorario();
        b.setInicioUtc(lima(MARTES, "00:00"));
        b.setFinUtc(lima(MARTES.plusDays(1), "00:00"));
        when(bloqueoRepository.findByConsultorioIdAndEstadoAndInicioUtcLessThanAndFinUtcGreaterThan(
                eq(3), eq(EstadoBloqueo.APROBADO), any(), any())).thenReturn(List.of(b));

        assertThat(servicio.calcular(medico, MARTES, MARTES)).isEmpty();
    }

    @Test
    void unaOfertaVigenteDeLaListaDeEsperaRetieneElHorario() {
        ListaEspera oferta = new ListaEspera();
        oferta.setOfertaInicioUtc(lima(MARTES, "08:30"));
        oferta.setOfertaFinUtc(lima(MARTES, "09:00"));
        when(listaEsperaRepository.findOfertasVigentesDelMedico(eq(5L), any())).thenReturn(List.of(oferta));

        assertThat(servicio.calcular(medico, MARTES, MARTES)).extracting(Slot::inicio)
                .doesNotContain(lima(MARTES, "08:30")).hasSize(6);
    }

    @Test
    void elDiaDeHoySoloMuestraHorariosFuturos() {
        // Ahora = lunes 10:00; se agrega jornada de lunes 08:00-12:00 sin descanso.
        HorarioMedico lunes = franja(1, "08:00", "12:00", null, null);
        when(horarioRepository.findByMedicoIdAndActivoTrueOrderByDiaSemanaAscHoraInicioAsc(5L)).thenReturn(List.of(lunes));

        LocalDate hoy = LocalDate.of(2030, 1, 7);
        assertThat(servicio.calcular(medico, hoy, hoy)).extracting(Slot::inicio).containsExactly(
                lima(hoy, "10:30"), lima(hoy, "11:00"), lima(hoy, "11:30"));
    }

    @Test
    void medicoInactivoOSinHorarioNoTieneDisponibilidad() {
        medico.setActivo(false);
        assertThat(servicio.calcular(medico, MARTES, MARTES)).isEmpty();

        medico.setActivo(true);
        when(horarioRepository.findByMedicoIdAndActivoTrueOrderByDiaSemanaAscHoraInicioAsc(5L)).thenReturn(List.of());
        assertThat(servicio.calcular(medico, MARTES, MARTES)).isEmpty();
    }

    @Test
    void noOfreceHorariosMasAllaDelHorizonte() {
        LocalDate lejano = LocalDate.of(2030, 6, 4); // un martes, a más de 90 días
        assertThat(lejano.getDayOfWeek().getValue()).isEqualTo(2);
        assertThat(servicio.calcular(medico, lejano, lejano)).isEmpty();
    }

    // ------------------------------------------------------------------ validación de una reserva

    private void validar(String hora, boolean paciente) {
        Instant ini = lima(MARTES, hora);
        servicio.validarReserva(medico, consultorio, 9L, ini, ini.plusSeconds(1800), null, null, paciente);
    }

    @Test
    void aceptaUnHorarioValido() {
        assertThatCode(() -> validar("09:00", true)).doesNotThrowAnyException();
    }

    @Test
    void rechazaFechasPasadas() {
        Instant ayer = lima(LocalDate.of(2030, 1, 1), "09:00"); // martes pasado
        assertCodigo(() -> servicio.validarReserva(medico, consultorio, 9L, ayer, ayer.plusSeconds(1800),
                null, null, false), "FECHA_PASADA");
    }

    @Test
    void elPacienteDebeReservarConAnticipacionMinima() {
        // Hoy lunes 10:00; el médico atiende lunes 10:30 (30 min después): menos de los 60 requeridos.
        HorarioMedico lunes = franja(1, "08:00", "12:00", null, null);
        when(horarioRepository.findByMedicoIdAndDiaSemanaAndActivoTrue(5L, 1)).thenReturn(List.of(lunes));
        Instant ini = lima(LocalDate.of(2030, 1, 7), "10:30");

        assertCodigo(() -> servicio.validarReserva(medico, consultorio, 9L, ini, ini.plusSeconds(1800),
                null, null, true), "ANTICIPACION_INSUFICIENTE");
        assertThatCode(() -> servicio.validarReserva(medico, consultorio, 9L, ini, ini.plusSeconds(1800),
                null, null, false)).doesNotThrowAnyException(); // el personal no tiene esa restricción
    }

    @Test
    void rechazaHorariosFueraDeLaJornadaDescansoODesalineados() {
        assertCodigo(() -> validar("07:30", false), "FUERA_DE_HORARIO");   // antes de abrir
        assertCodigo(() -> validar("12:00", false), "FUERA_DE_HORARIO");   // al cierre
        assertCodigo(() -> validar("10:00", false), "FUERA_DE_HORARIO");   // durante el descanso
        assertCodigo(() -> validar("09:15", false), "FUERA_DE_HORARIO");   // no alineado a la duración
        Instant domingo = lima(LocalDate.of(2030, 1, 13), "09:00");
        assertCodigo(() -> servicio.validarReserva(medico, consultorio, 9L, domingo, domingo.plusSeconds(1800),
                null, null, false), "FUERA_DE_HORARIO");                   // día sin atención
    }

    @Test
    void rechazaReservasMasAllaDelHorizonte() {
        Instant lejano = lima(LocalDate.of(2030, 6, 4), "09:00");
        assertCodigo(() -> servicio.validarReserva(medico, consultorio, 9L, lejano, lejano.plusSeconds(1800),
                null, null, false), "FUERA_DE_HORIZONTE");
    }

    @Test
    void rechazaHorariosBloqueados() {
        when(bloqueoRepository.existsByMedicoIdAndEstadoAndInicioUtcLessThanAndFinUtcGreaterThan(
                eq(5L), eq(EstadoBloqueo.APROBADO), any(), any())).thenReturn(true);
        assertCodigo(() -> validar("09:00", false), "MEDICO_BLOQUEADO");

        when(bloqueoRepository.existsByMedicoIdAndEstadoAndInicioUtcLessThanAndFinUtcGreaterThan(
                eq(5L), eq(EstadoBloqueo.APROBADO), any(), any())).thenReturn(false);
        when(bloqueoRepository.existsByConsultorioIdAndEstadoAndInicioUtcLessThanAndFinUtcGreaterThan(
                eq(3), eq(EstadoBloqueo.APROBADO), any(), any())).thenReturn(true);
        assertCodigo(() -> validar("09:00", false), "CONSULTORIO_BLOQUEADO");
    }

    @Test
    void impideQueUnMedicoAtiendaDosCitasSimultaneas() { // regla 1
        when(citaRepository.contarSolapadasMedicoSin(eq(5L), any(), any(), anyCollection(), anyLong())).thenReturn(1L);
        assertThatThrownBy(() -> validar("09:00", false)).isInstanceOf(ConflictoException.class);
        assertCodigo(() -> validar("09:00", false), "MEDICO_OCUPADO");
    }

    @Test
    void impideDosReservasIncompatiblesEnUnConsultorio() { // regla 2
        when(citaRepository.contarSolapadasConsultorioSin(eq(3), any(), any(), anyCollection(), anyLong())).thenReturn(1L);
        assertCodigo(() -> validar("09:00", false), "CONSULTORIO_OCUPADO");
    }

    @Test
    void impideQueUnPacienteReserveCitasSuperpuestas() { // regla 3
        when(citaRepository.contarSolapadasPacienteSin(eq(9L), any(), any(), anyCollection(), anyLong())).thenReturn(1L);
        assertCodigo(() -> validar("09:00", false), "PACIENTE_OCUPADO");
    }

    @Test
    void respetaLaRetencionTemporalDeLaListaDeEspera() {
        when(listaEsperaRepository.contarOfertasVigentesSolapadas(eq(5L), any(), any(), any(), anyLong())).thenReturn(1L);
        assertCodigo(() -> validar("09:00", false), "HORARIO_RESERVADO_TEMPORALMENTE");
    }

    @Test
    void alReprogramarSeIgnoraLaPropiaCita() {
        Instant ini = lima(MARTES, "09:00");
        // La consulta recibe el id de la cita a ignorar (77): con otro id devolvería 1.
        when(citaRepository.contarSolapadasMedicoSin(eq(5L), any(), any(), anyCollection(), eq(77L))).thenReturn(0L);
        when(citaRepository.contarSolapadasMedicoSin(eq(5L), any(), any(), anyCollection(), eq(-1L))).thenReturn(1L);
        assertThatCode(() -> servicio.validarReserva(medico, consultorio, 9L, ini, ini.plusSeconds(1800), 77L, null, false))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> servicio.validarReserva(medico, consultorio, 9L, ini, ini.plusSeconds(1800), null, null, false))
                .isInstanceOf(ConflictoException.class);
    }
}
