package com.medigestsystem.citas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;

import com.medigestsystem.notificaciones.AvisosNegocio;
import com.medigestsystem.auditoria.AuditoriaService;
import com.medigestsystem.auditoria.ResultadoAuditoria;
import com.medigestsystem.citas.dto.CancelarRequest;
import com.medigestsystem.citas.dto.CitaRequest;
import com.medigestsystem.citas.dto.CitaResponse;
import com.medigestsystem.citas.dto.ReprogramarRequest;
import com.medigestsystem.config.PoliticaCitasProperties;
import com.medigestsystem.consultorios.Consultorio;
import com.medigestsystem.consultorios.ConsultorioRepository;
import com.medigestsystem.disponibilidad.DisponibilidadService;
import com.medigestsystem.especialidades.Especialidad;
import com.medigestsystem.medicos.Medico;
import com.medigestsystem.medicos.MedicoRepository;
import com.medigestsystem.pacientes.Paciente;
import com.medigestsystem.pacientes.PacienteRepository;
import com.medigestsystem.shared.exception.ApiException;
import com.medigestsystem.shared.exception.ConflictoException;
import com.medigestsystem.shared.exception.ReglaNegocioException;
import com.medigestsystem.shared.exception.SolicitudInvalidaException;
import com.medigestsystem.testsupport.Fixtures;
import com.medigestsystem.usuarios.NombreRol;
import com.medigestsystem.usuarios.UsuarioRepository;

/**
 * "Ahora" es el lunes 2030-01-07 10:00 en Lima. Cita lejana: miércoles 09:00 (47 h); cita cercana: martes 09:00 (23 h,
 * dentro de las 24 h que la política exige a los pacientes).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CitaServiceTest {

    static final ZoneOffset LIMA = ZoneOffset.ofHours(-5);
    static final OffsetDateTime MIERCOLES_9 = OffsetDateTime.of(2030, 1, 9, 9, 0, 0, 0, LIMA);
    static final OffsetDateTime MIERCOLES_10 = OffsetDateTime.of(2030, 1, 9, 10, 0, 0, 0, LIMA);
    static final OffsetDateTime MARTES_9 = OffsetDateTime.of(2030, 1, 8, 9, 0, 0, 0, LIMA);
    static final OffsetDateTime HOY_12 = OffsetDateTime.of(2030, 1, 7, 12, 0, 0, 0, LIMA);
    static final OffsetDateTime HOY_08 = OffsetDateTime.of(2030, 1, 7, 8, 0, 0, 0, LIMA);

    @Mock CitaRepository citaRepository;
    @Mock HistorialCitaRepository historialRepository;
    @Mock MedicoRepository medicoRepository;
    @Mock PacienteRepository pacienteRepository;
    @Mock ConsultorioRepository consultorioRepository;
    @Mock UsuarioRepository usuarioRepository;
    @Mock DisponibilidadService disponibilidad;
    @Mock AuditoriaService auditoriaService;
    @Mock ApplicationEventPublisher eventos;
    @Mock AvisosNegocio avisos;
    @Mock CodigoReservaGenerator codigos;

    CitaService servicio;
    Paciente paciente;
    Medico medico;
    Especialidad especialidad;
    Consultorio consultorio;

    @BeforeEach
    void preparar() {
        servicio = new CitaService(citaRepository, historialRepository, medicoRepository, pacienteRepository,
                consultorioRepository, usuarioRepository, disponibilidad, auditoriaService,
                new PoliticaCitasProperties(60, 90, 24, 3, 30), Fixtures.fechaHora(), eventos, codigos, avisos);

        consultorio = new Consultorio();
        consultorio.setId(3);
        consultorio.setCodigo("C-101");
        consultorio.setActivo(true);
        especialidad = new Especialidad();
        especialidad.setId(1);
        especialidad.setNombre("Cardiología");
        especialidad.setActiva(true);
        medico = new Medico();
        medico.setId(5L);
        medico.setNombres("Carlos");
        medico.setApellidos("Paredes");
        medico.setActivo(true);
        medico.setDuracionConsultaMin(30);
        medico.setConsultorio(consultorio);
        medico.setEspecialidades(new HashSet<>(Set.of(especialidad)));
        paciente = new Paciente();
        paciente.setId(9L);
        paciente.setNombres("Ana");
        paciente.setApellidos("Quispe");
        paciente.setActivo(true);

        when(medicoRepository.obtenerConBloqueo(5L)).thenReturn(Optional.of(medico));
        when(consultorioRepository.obtenerConBloqueo(3)).thenReturn(Optional.of(consultorio));
        when(pacienteRepository.obtenerConBloqueo(9L)).thenReturn(Optional.of(paciente));
        when(pacienteRepository.buscarIdPorUsuario(50L)).thenReturn(Optional.of(9L));
        when(pacienteRepository.buscarIdPorUsuario(51L)).thenReturn(Optional.of(10L));
        when(medicoRepository.buscarIdPorUsuario(70L)).thenReturn(Optional.of(5L));
        when(medicoRepository.buscarIdPorUsuario(71L)).thenReturn(Optional.of(6L));
        when(codigos.nuevo()).thenReturn("MG-TEST0001");
        when(citaRepository.saveAndFlush(any())).thenAnswer(i -> {
            Cita c = i.getArgument(0);
            if (c.getId() == null) {
                c.setId(100L);
            }
            return c;
        });
        when(historialRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(historialRepository.countByCitaIdAndTipoEvento(any(), any())).thenReturn(0L);
    }

    @AfterEach
    void limpiar() {
        Fixtures.cerrarSesion();
    }

    private Cita cita(EstadoCita estado, OffsetDateTime inicio) {
        Cita c = new Cita();
        c.setId(100L);
        c.setCodigoReserva("MG-EXISTE01");
        c.setPaciente(paciente);
        c.setMedico(medico);
        c.setEspecialidad(especialidad);
        c.setConsultorio(consultorio);
        c.setInicioUtc(inicio.toInstant());
        c.setFinUtc(inicio.toInstant().plusSeconds(1800));
        c.setEstado(estado);
        when(citaRepository.obtenerConBloqueo(100L)).thenReturn(Optional.of(c));
        when(citaRepository.findById(100L)).thenReturn(Optional.of(c));
        return c;
    }

    private static void assertCodigo(Runnable accion, String codigo) {
        assertThatThrownBy(accion::run).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getCodigo()).isEqualTo(codigo));
    }

    private HistorialCita ultimoHistorial() {
        ArgumentCaptor<HistorialCita> captor = ArgumentCaptor.forClass(HistorialCita.class);
        verify(historialRepository).save(captor.capture());
        return captor.getValue();
    }

    // ================================================================== reserva

    @Test
    void recepcionReservaUnaCitaPendienteConLaDuracionDelMedico() {
        Fixtures.autenticarComo(2L, NombreRol.RECEPCIONISTA);

        CitaResponse r = servicio.reservar(new CitaRequest(9L, 5L, 1, MIERCOLES_9, "Dolor de pecho"));

        assertThat(r.estado()).isEqualTo(EstadoCita.PENDIENTE);
        assertThat(r.codigoReserva()).isEqualTo("MG-TEST0001");
        assertThat(r.fin().toInstant()).isEqualTo(MIERCOLES_9.toInstant().plusSeconds(30 * 60)); // regla 12
        assertThat(r.consultorio()).isEqualTo("C-101");
        verify(disponibilidad).validarReserva(eq(medico), eq(consultorio), eq(9L), eq(MIERCOLES_9.toInstant()),
                eq(MIERCOLES_9.toInstant().plusSeconds(1800)), isNull(), isNull(), eq(false));
        HistorialCita h = ultimoHistorial();
        assertThat(h.getTipoEvento()).isEqualTo(TipoEventoCita.CREADA);
        assertThat(h.getEstadoNuevo()).isEqualTo(EstadoCita.PENDIENTE);
        verify(auditoriaService).registrar(eq("CITA_CREADA"), eq("citas"), any(), eq(ResultadoAuditoria.EXITO), any()); // regla 14
    }

    @Test
    void elPacienteSoloReservaParaSiMismoYSeLeExigeLaAnticipacion() {
        Fixtures.autenticarComo(50L, NombreRol.PACIENTE);

        servicio.reservar(new CitaRequest(99L, 5L, 1, MIERCOLES_9, null)); // el 99 se ignora

        verify(pacienteRepository).obtenerConBloqueo(9L);
        verify(pacienteRepository, never()).obtenerConBloqueo(99L);
        verify(disponibilidad).validarReserva(any(), any(), eq(9L), any(), any(), isNull(), isNull(), eq(true));
    }

    @Test
    void noSePuedeReservarConUnMedicoInactivo() { // regla 6
        medico.setActivo(false);
        Fixtures.autenticarComo(2L, NombreRol.RECEPCIONISTA);

        assertCodigo(() -> servicio.reservar(new CitaRequest(9L, 5L, 1, MIERCOLES_9, null)), "MEDICO_INACTIVO");
        verify(citaRepository, never()).saveAndFlush(any());
    }

    @Test
    void noSePuedeReservarParaUnPacienteInactivo() {
        paciente.setActivo(false);
        Fixtures.autenticarComo(2L, NombreRol.RECEPCIONISTA);

        assertCodigo(() -> servicio.reservar(new CitaRequest(9L, 5L, 1, MIERCOLES_9, null)), "PACIENTE_INACTIVO");
    }

    @Test
    void elMedicoDebeAtenderLaEspecialidadSolicitada() {
        Fixtures.autenticarComo(2L, NombreRol.RECEPCIONISTA);

        assertCodigo(() -> servicio.reservar(new CitaRequest(9L, 5L, 2, MIERCOLES_9, null)), "ESPECIALIDAD_NO_ATENDIDA");
    }

    @Test
    void unMedicoNoPuedeReservarCitas() {
        Fixtures.autenticarComo(70L, NombreRol.MEDICO);

        assertThatThrownBy(() -> servicio.reservar(new CitaRequest(9L, 5L, 1, MIERCOLES_9, null)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void unaSuperposicionDetectadaPorLaDisponibilidadImpideGuardar() {
        Fixtures.autenticarComo(2L, NombreRol.RECEPCIONISTA);
        doThrow(new ConflictoException("MEDICO_OCUPADO", "ocupado")).when(disponibilidad)
                .validarReserva(any(), any(), any(), any(), any(), any(), any(), ArgumentMatchers.anyBoolean());

        assertThatThrownBy(() -> servicio.reservar(new CitaRequest(9L, 5L, 1, MIERCOLES_9, null)))
                .isInstanceOf(ConflictoException.class);
        verify(citaRepository, never()).saveAndFlush(any());
    }

    @Test
    void siElIndiceUnicoRechazaElHorarioSeResponde409() { // regla 11 (red de seguridad en la base de datos)
        Fixtures.autenticarComo(2L, NombreRol.RECEPCIONISTA);
        org.mockito.Mockito.doThrow(new DataIntegrityViolationException("ux_citas_medico_inicio_activa")).when(citaRepository).saveAndFlush(any());

        assertThatThrownBy(() -> servicio.reservar(new CitaRequest(9L, 5L, 1, MIERCOLES_9, null)))
                .isInstanceOf(ConflictoException.class);
        assertCodigo(() -> servicio.reservar(new CitaRequest(9L, 5L, 1, MIERCOLES_9, null)), "HORARIO_NO_DISPONIBLE");
    }

    // ================================================================== reprogramación

    @Test
    void reprogramarGuardaElHistorialYLiberaElHorarioAnterior() { // regla 9
        Cita c = cita(EstadoCita.CONFIRMADA, MIERCOLES_9);
        Fixtures.autenticarComo(2L, NombreRol.RECEPCIONISTA);

        CitaResponse r = servicio.reprogramar(100L, new ReprogramarRequest(MIERCOLES_10, "A pedido del paciente"));

        assertThat(r.inicio().toInstant()).isEqualTo(MIERCOLES_10.toInstant());
        assertThat(r.estado()).isEqualTo(EstadoCita.CONFIRMADA);
        verify(disponibilidad).validarReserva(eq(medico), eq(consultorio), eq(9L), eq(MIERCOLES_10.toInstant()),
                eq(MIERCOLES_10.toInstant().plusSeconds(1800)), eq(100L), isNull(), eq(false));
        HistorialCita h = ultimoHistorial();
        assertThat(h.getTipoEvento()).isEqualTo(TipoEventoCita.REPROGRAMADA);
        assertThat(h.getInicioAnteriorUtc()).isEqualTo(MIERCOLES_9.toInstant());
        assertThat(h.getInicioNuevoUtc()).isEqualTo(MIERCOLES_10.toInstant());
        assertThat(h.getMotivo()).isEqualTo("A pedido del paciente");
        ArgumentCaptor<HorarioLiberadoEvent> evento = ArgumentCaptor.forClass(HorarioLiberadoEvent.class);
        verify(eventos).publishEvent(evento.capture());
        assertThat(evento.getValue().inicio()).isEqualTo(MIERCOLES_9.toInstant());
        assertThat(evento.getValue().medicoId()).isEqualTo(5L);
        verify(auditoriaService).registrar(eq("CITA_REPROGRAMADA"), eq("citas"), any(), eq(ResultadoAuditoria.EXITO), any());
    }

    @Test
    void elPacienteNoReprogramaConMenosDeLasHorasQueExigeLaPolitica() {
        cita(EstadoCita.PENDIENTE, MARTES_9);
        Fixtures.autenticarComo(50L, NombreRol.PACIENTE);

        assertCodigo(() -> servicio.reprogramar(100L, new ReprogramarRequest(MIERCOLES_10, "Motivo")),
                "REPROGRAMACION_TARDIA");
    }

    @Test
    void elPacienteTieneUnLimiteDeReprogramaciones() {
        cita(EstadoCita.PENDIENTE, MIERCOLES_9);
        when(historialRepository.countByCitaIdAndTipoEvento(100L, TipoEventoCita.REPROGRAMADA)).thenReturn(3L);
        Fixtures.autenticarComo(50L, NombreRol.PACIENTE);

        assertCodigo(() -> servicio.reprogramar(100L, new ReprogramarRequest(MIERCOLES_10, "Motivo")),
                "LIMITE_REPROGRAMACIONES");
    }

    @Test
    void soloSeReprogramanCitasPendientesOConfirmadas() {
        cita(EstadoCita.ATENDIDA, MIERCOLES_9);
        Fixtures.autenticarComo(2L, NombreRol.RECEPCIONISTA);

        assertCodigo(() -> servicio.reprogramar(100L, new ReprogramarRequest(MIERCOLES_10, "Motivo")),
                "CITA_NO_REPROGRAMABLE");
    }

    @Test
    void reprogramarAlMismoHorarioSeRechaza() {
        cita(EstadoCita.PENDIENTE, MIERCOLES_9);
        Fixtures.autenticarComo(2L, NombreRol.RECEPCIONISTA);

        assertThatThrownBy(() -> servicio.reprogramar(100L, new ReprogramarRequest(MIERCOLES_9, "Motivo")))
                .isInstanceOf(SolicitudInvalidaException.class);
    }

    @Test
    void unPacienteNoReprogramaLaCitaDeOtro() { // regla 10
        cita(EstadoCita.PENDIENTE, MIERCOLES_9);
        Fixtures.autenticarComo(51L, NombreRol.PACIENTE);

        assertThatThrownBy(() -> servicio.reprogramar(100L, new ReprogramarRequest(MIERCOLES_10, "Motivo")))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ================================================================== cancelación

    @Test
    void cancelarConservaLaCitaYRegistraMotivoFechaHistorialYAuditoria() { // reglas 14 y 15
        Cita c = cita(EstadoCita.PENDIENTE, MIERCOLES_9);
        Fixtures.autenticarComo(2L, NombreRol.RECEPCIONISTA);

        CitaResponse r = servicio.cancelar(100L, new CancelarRequest("El paciente viaja"));

        assertThat(r.estado()).isEqualTo(EstadoCita.CANCELADA);
        assertThat(c.getMotivoCancelacion()).isEqualTo("El paciente viaja");
        assertThat(c.getCanceladaEn()).isEqualTo(Fixtures.AHORA);
        verify(citaRepository, never()).delete(any(Cita.class));
        assertThat(ultimoHistorial().getTipoEvento()).isEqualTo(TipoEventoCita.CANCELADA);
        verify(auditoriaService).registrar(eq("CITA_CANCELADA"), eq("citas"), any(), eq(ResultadoAuditoria.EXITO), any());
        verify(eventos).publishEvent(ArgumentMatchers.<HorarioLiberadoEvent>any());
    }

    @Test
    void unaCitaAtendidaOCanceladaNoSePuedeCancelar() {
        cita(EstadoCita.ATENDIDA, MIERCOLES_9);
        Fixtures.autenticarComo(2L, NombreRol.RECEPCIONISTA);
        assertCodigo(() -> servicio.cancelar(100L, new CancelarRequest("Motivo")), "TRANSICION_INVALIDA");

        cita(EstadoCita.CANCELADA, MIERCOLES_9);
        assertCodigo(() -> servicio.cancelar(100L, new CancelarRequest("Motivo")), "TRANSICION_INVALIDA");
    }

    @Test
    void elPacienteNoCancelaFueraDelPlazoDeLaPolitica() {
        cita(EstadoCita.CONFIRMADA, MARTES_9);
        Fixtures.autenticarComo(50L, NombreRol.PACIENTE);

        assertCodigo(() -> servicio.cancelar(100L, new CancelarRequest("Motivo")), "CANCELACION_TARDIA");
    }

    @Test
    void recepcionPuedeCancelarAunFueraDelPlazoDelPaciente() {
        cita(EstadoCita.CONFIRMADA, MARTES_9);
        Fixtures.autenticarComo(2L, NombreRol.RECEPCIONISTA);

        assertThat(servicio.cancelar(100L, new CancelarRequest("Médico ausente")).estado())
                .isEqualTo(EstadoCita.CANCELADA);
    }

    // ================================================================== estados y asistencia

    @Test
    void unaCitaCanceladaNoPuedeMarcarseComoAtendida() { // regla 7
        cita(EstadoCita.CANCELADA, HOY_12);
        Fixtures.autenticarComo(70L, NombreRol.MEDICO);

        assertCodigo(() -> servicio.registrarAsistencia(100L, AccionAsistencia.FINALIZAR), "TRANSICION_INVALIDA");
        assertCodigo(() -> servicio.registrarAsistencia(100L, AccionAsistencia.INICIAR), "TRANSICION_INVALIDA");
    }

    @Test
    void unaCitaAtendidaNoPuedeVolverAEstadosAnteriores() { // regla 8
        cita(EstadoCita.ATENDIDA, HOY_08);
        Fixtures.autenticarComo(2L, NombreRol.RECEPCIONISTA);

        assertCodigo(() -> servicio.registrarAsistencia(100L, AccionAsistencia.LLEGADA), "TRANSICION_INVALIDA");
        assertCodigo(() -> servicio.registrarAsistencia(100L, AccionAsistencia.NO_ASISTIO), "TRANSICION_INVALIDA");
        assertCodigo(() -> servicio.confirmar(100L), "TRANSICION_INVALIDA");
    }

    @Test
    void flujoCompletoDeAtencionDelDia() {
        Cita c = cita(EstadoCita.CONFIRMADA, HOY_12);

        Fixtures.autenticarComo(2L, NombreRol.RECEPCIONISTA);
        assertThat(servicio.registrarAsistencia(100L, AccionAsistencia.LLEGADA).estado()).isEqualTo(EstadoCita.EN_ESPERA);

        Fixtures.autenticarComo(70L, NombreRol.MEDICO);
        assertThat(servicio.registrarAsistencia(100L, AccionAsistencia.INICIAR).estado()).isEqualTo(EstadoCita.EN_ATENCION);
        assertThat(servicio.registrarAsistencia(100L, AccionAsistencia.FINALIZAR).estado()).isEqualTo(EstadoCita.ATENDIDA);

        assertThat(c.getEstado()).isEqualTo(EstadoCita.ATENDIDA);
        verify(historialRepository, times(3)).save(any());
    }

    @Test
    void laLlegadaSoloSeRegistraElDiaDeLaCita() {
        cita(EstadoCita.PENDIENTE, MIERCOLES_9);
        Fixtures.autenticarComo(2L, NombreRol.RECEPCIONISTA);

        assertCodigo(() -> servicio.registrarAsistencia(100L, AccionAsistencia.LLEGADA), "FUERA_DE_FECHA");
    }

    @Test
    void noSeIniciaLaAtencionSinHaberRegistradoLaLlegada() {
        cita(EstadoCita.PENDIENTE, HOY_12);
        Fixtures.autenticarComo(70L, NombreRol.MEDICO);

        assertCodigo(() -> servicio.registrarAsistencia(100L, AccionAsistencia.INICIAR), "TRANSICION_INVALIDA");
    }

    @Test
    void soloElMedicoDeLaCitaIniciaLaAtencion() {
        cita(EstadoCita.EN_ESPERA, HOY_12);
        Fixtures.autenticarComo(71L, NombreRol.MEDICO); // otro médico (id 6)

        assertThatThrownBy(() -> servicio.registrarAsistencia(100L, AccionAsistencia.INICIAR))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void recepcionNoPuedeIniciarNiFinalizarAtenciones() {
        cita(EstadoCita.EN_ESPERA, HOY_12);
        Fixtures.autenticarComo(2L, NombreRol.RECEPCIONISTA);

        assertThatThrownBy(() -> servicio.registrarAsistencia(100L, AccionAsistencia.INICIAR))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void laInasistenciaSoloSeMarcaCuandoLaCitaYaDebioComenzar() {
        Cita c = cita(EstadoCita.PENDIENTE, MIERCOLES_9);
        Fixtures.autenticarComo(2L, NombreRol.RECEPCIONISTA);
        assertCodigo(() -> servicio.registrarAsistencia(100L, AccionAsistencia.NO_ASISTIO), "CITA_NO_INICIADA");

        c.setInicioUtc(HOY_08.toInstant());
        c.setFinUtc(HOY_08.toInstant().plusSeconds(1800));
        assertThat(servicio.registrarAsistencia(100L, AccionAsistencia.NO_ASISTIO).estado())
                .isEqualTo(EstadoCita.NO_ASISTIO);
    }

    @Test
    void soloElPersonalConfirmaCitas() {
        cita(EstadoCita.PENDIENTE, MIERCOLES_9);

        Fixtures.autenticarComo(50L, NombreRol.PACIENTE);
        assertThatThrownBy(() -> servicio.confirmar(100L)).isInstanceOf(AccessDeniedException.class);

        Fixtures.autenticarComo(2L, NombreRol.RECEPCIONISTA);
        assertThat(servicio.confirmar(100L).estado()).isEqualTo(EstadoCita.CONFIRMADA);
        assertCodigo(() -> servicio.confirmar(100L), "TRANSICION_INVALIDA"); // ya confirmada
    }

    // ================================================================== consulta y autorización

    @Test
    void unPacienteSoloConsultaSusPropiasCitas() { // regla 10
        cita(EstadoCita.PENDIENTE, MIERCOLES_9);

        Fixtures.autenticarComo(50L, NombreRol.PACIENTE);
        assertThat(servicio.obtener(100L).id()).isEqualTo(100L);
        assertThat(servicio.historial(100L)).isNotNull();

        Fixtures.autenticarComo(51L, NombreRol.PACIENTE);
        assertThatThrownBy(() -> servicio.obtener(100L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> servicio.historial(100L)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void unMedicoNoConsultaCitasDeOtroMedico() {
        cita(EstadoCita.PENDIENTE, MIERCOLES_9);

        Fixtures.autenticarComo(71L, NombreRol.MEDICO);
        assertThatThrownBy(() -> servicio.obtener(100L)).isInstanceOf(AccessDeniedException.class);

        Fixtures.autenticarComo(70L, NombreRol.MEDICO);
        assertThat(servicio.obtener(100L).medicoId()).isEqualTo(5L);
    }

    @Test
    void elDetalleIncluyeSoloLasAccionesPermitidasAlUsuario() {
        cita(EstadoCita.PENDIENTE, MIERCOLES_9);

        Fixtures.autenticarComo(2L, NombreRol.RECEPCIONISTA);
        assertThat(servicio.obtener(100L).accionesPermitidas())
                .contains(AccionCita.CONFIRMAR, AccionCita.REPROGRAMAR, AccionCita.CANCELAR)
                .doesNotContain(AccionCita.INICIAR_ATENCION, AccionCita.FINALIZAR_ATENCION);

        Fixtures.autenticarComo(50L, NombreRol.PACIENTE);
        assertThat(servicio.obtener(100L).accionesPermitidas())
                .containsExactlyInAnyOrder(AccionCita.REPROGRAMAR, AccionCita.CANCELAR);
    }

    @Test
    void elListadoDeUnPacienteSinFichaEstaVacio() {
        when(pacienteRepository.buscarIdPorUsuario(52L)).thenReturn(Optional.empty());
        Fixtures.autenticarComo(52L, NombreRol.PACIENTE);

        assertThat(servicio.listar(null, null, null, null, null, null, null, 0, 20).contenido()).isEmpty();
        verify(citaRepository, never()).findAll(ArgumentMatchers.<Specification<Cita>>any(), any(Pageable.class));
    }

    @Test
    void elListadoSeFiltraEnElBackendSegunElRol() {
        when(citaRepository.findAll(ArgumentMatchers.<Specification<Cita>>any(), any(Pageable.class)))
                .thenReturn(Page.empty());

        Fixtures.autenticarComo(50L, NombreRol.PACIENTE);
        assertThat(servicio.listar(77L, null, null, null, null, null, null, 0, 20).contenido()).isEmpty();
        Fixtures.autenticarComo(70L, NombreRol.MEDICO);
        assertThat(servicio.listar(null, 99L, null, null, null, null, null, 0, 20).contenido()).isEmpty();
        Fixtures.autenticarComo(2L, NombreRol.RECEPCIONISTA);
        assertThat(servicio.listar(null, null, null, null, null, null, null, 0, 20).contenido()).isEmpty();
        verify(citaRepository, times(3)).findAll(ArgumentMatchers.<Specification<Cita>>any(), any(Pageable.class));
    }

    @Test
    void sinSesionNoSePuedeOperar() {
        Fixtures.cerrarSesion();
        assertThatThrownBy(() -> servicio.obtener(100L)).isInstanceOf(AccessDeniedException.class);
    }
}
