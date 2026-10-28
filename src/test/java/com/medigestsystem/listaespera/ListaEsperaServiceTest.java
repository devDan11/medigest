package com.medigestsystem.listaespera;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;

import com.medigestsystem.notificaciones.AvisosNegocio;
import com.medigestsystem.auditoria.AuditoriaService;
import com.medigestsystem.citas.CitaRepository;
import com.medigestsystem.citas.CitaService;
import com.medigestsystem.citas.HorarioLiberadoEvent;
import com.medigestsystem.citas.dto.CitaResponse;
import com.medigestsystem.config.PoliticaCitasProperties;
import com.medigestsystem.consultorios.Consultorio;
import com.medigestsystem.disponibilidad.DisponibilidadService;
import com.medigestsystem.especialidades.Especialidad;
import com.medigestsystem.especialidades.EspecialidadRepository;
import com.medigestsystem.listaespera.dto.ListaEsperaRequest;
import com.medigestsystem.listaespera.dto.ListaEsperaResponse;
import com.medigestsystem.medicos.Medico;
import com.medigestsystem.medicos.MedicoRepository;
import com.medigestsystem.pacientes.Paciente;
import com.medigestsystem.pacientes.PacienteRepository;
import com.medigestsystem.shared.exception.ApiException;
import com.medigestsystem.shared.exception.ConflictoException;
import com.medigestsystem.shared.exception.RecursoNoEncontradoException;
import com.medigestsystem.testsupport.Fixtures;
import com.medigestsystem.usuarios.NombreRol;

/** "Ahora" es el lunes 2030-01-07 10:00 en Lima. El horario liberado es el miércoles 2030-01-09 09:00-09:30. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ListaEsperaServiceTest {

    static final Instant INI = Instant.parse("2030-01-09T14:00:00Z"); // 09:00 Lima
    static final Instant FIN = INI.plusSeconds(1800);

    @Mock ListaEsperaRepository esperaRepository;
    @Mock PacienteRepository pacienteRepository;
    @Mock MedicoRepository medicoRepository;
    @Mock EspecialidadRepository especialidadRepository;
    @Mock CitaRepository citaRepository;
    @Mock CitaService citaService;
    @Mock DisponibilidadService disponibilidad;
    @Mock AuditoriaService auditoriaService;
    @Mock ApplicationEventPublisher eventos;
    @Mock AvisosNegocio avisos;

    ListaEsperaService servicio;
    Paciente ana;
    Paciente luis;
    Medico medico;
    Medico otroMedico;
    Especialidad especialidad;

    @BeforeEach
    void preparar() {
        servicio = new ListaEsperaService(esperaRepository, pacienteRepository, medicoRepository,
                especialidadRepository, citaRepository, citaService, disponibilidad, auditoriaService,
                new PoliticaCitasProperties(60, 90, 24, 3, 30), Fixtures.fechaHora(), eventos, Fixtures.LIMA, avisos);
        especialidad = new Especialidad();
        especialidad.setId(1);
        especialidad.setNombre("Cardiología");
        especialidad.setActiva(true);
        Consultorio consultorio = new Consultorio();
        consultorio.setId(3);
        medico = medico(5L, consultorio);
        otroMedico = medico(6L, consultorio);
        ana = paciente(9L, "Ana");
        luis = paciente(10L, "Luis");

        when(medicoRepository.obtenerConBloqueo(5L)).thenReturn(Optional.of(medico));
        when(medicoRepository.findById(5L)).thenReturn(Optional.of(medico));
        when(pacienteRepository.findById(9L)).thenReturn(Optional.of(ana));
        when(pacienteRepository.buscarIdPorUsuario(50L)).thenReturn(Optional.of(9L));
        when(pacienteRepository.buscarIdPorUsuario(51L)).thenReturn(Optional.of(10L));
        when(especialidadRepository.findById(1)).thenReturn(Optional.of(especialidad));
        when(esperaRepository.saveAndFlush(any())).thenAnswer(i -> {
            ListaEspera e = i.getArgument(0);
            e.setId(30L);
            return e;
        });
        when(esperaRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(esperaRepository.reclamarParaOferta(anyLong(), any(), any(), any(), any(), any())).thenReturn(1);
    }

    @AfterEach
    void limpiar() {
        Fixtures.cerrarSesion();
    }

    private Medico medico(Long id, Consultorio consultorio) {
        Medico m = new Medico();
        m.setId(id);
        m.setNombres("Dr. " + id);
        m.setApellidos("Prueba");
        m.setActivo(true);
        m.setConsultorio(consultorio);
        m.setEspecialidades(new HashSet<>(Set.of(especialidad)));
        return m;
    }

    private Paciente paciente(Long id, String nombre) {
        Paciente p = new Paciente();
        p.setId(id);
        p.setNombres(nombre);
        p.setApellidos("Prueba");
        p.setActivo(true);
        return p;
    }

    private ListaEspera espera(Long id, Paciente p, Medico m, String desde, String hasta, FranjaPreferida franja,
                               EstadoListaEspera estado) {
        ListaEspera e = new ListaEspera();
        e.setId(id);
        e.setPaciente(p);
        e.setMedico(m);
        e.setEspecialidad(especialidad);
        e.setFechaDesde(LocalDate.parse(desde));
        e.setFechaHasta(LocalDate.parse(hasta));
        e.setFranja(franja);
        e.setEstado(estado);
        return e;
    }

    private ListaEspera ofertada(Instant expira) {
        ListaEspera e = espera(20L, ana, null, "2030-01-08", "2030-01-20", FranjaPreferida.CUALQUIERA, EstadoListaEspera.OFERTADA);
        e.setOfertaMedico(medico);
        e.setOfertaInicioUtc(INI);
        e.setOfertaFinUtc(FIN);
        e.setOfertaExpiraEn(expira);
        when(esperaRepository.existsById(20L)).thenReturn(true);
        when(esperaRepository.buscarMedicoDeOferta(20L)).thenReturn(Optional.of(5L));
        when(esperaRepository.obtenerConBloqueo(20L)).thenReturn(Optional.of(e));
        return e;
    }

    private void candidatos(ListaEspera... lista) {
        when(esperaRepository.findByEspecialidadIdAndEstadoOrderByCreadoEnAsc(1, EstadoListaEspera.ACTIVA))
                .thenReturn(List.of(lista));
    }

    private static CitaResponse citaCreada() {
        return new CitaResponse(500L, "MG-OFERTA01", null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, List.of());
    }

    private static void assertCodigo(Runnable accion, String codigo) {
        assertThatThrownBy(accion::run).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getCodigo()).isEqualTo(codigo));
    }

    // ================================================================== franjas

    @Test
    void laFranjaDeLaMananaTerminaAlMediodia() {
        assertThat(ListaEsperaService.franjaCompatible(FranjaPreferida.MANANA, LocalTime.of(11, 30))).isTrue();
        assertThat(ListaEsperaService.franjaCompatible(FranjaPreferida.MANANA, LocalTime.of(12, 0))).isFalse();
        assertThat(ListaEsperaService.franjaCompatible(FranjaPreferida.TARDE, LocalTime.of(12, 0))).isTrue();
        assertThat(ListaEsperaService.franjaCompatible(FranjaPreferida.TARDE, LocalTime.of(9, 0))).isFalse();
        assertThat(ListaEsperaService.franjaCompatible(FranjaPreferida.CUALQUIERA, LocalTime.of(20, 0))).isTrue();
        assertThat(ListaEsperaService.franjaCompatible(null, LocalTime.of(8, 0))).isTrue();
    }

    // ================================================================== registro

    @Test
    void elPacienteSeAnotaEnLaListaYQuedaActivo() {
        Fixtures.autenticarComo(50L, NombreRol.PACIENTE);

        ListaEsperaResponse r = servicio.crear(new ListaEsperaRequest(null, 1, null,
                LocalDate.of(2030, 1, 8), LocalDate.of(2030, 1, 20), null));

        assertThat(r.estado()).isEqualTo(EstadoListaEspera.ACTIVA);
        assertThat(r.franja()).isEqualTo(FranjaPreferida.CUALQUIERA);
        assertThat(r.pacienteId()).isEqualTo(9L);
        assertThat(r.posicion()).isEqualTo(1L);
    }

    @Test
    void noSePermiteUnaSegundaSolicitudVigenteParaLaMismaEspecialidad() {
        Fixtures.autenticarComo(50L, NombreRol.PACIENTE);
        when(esperaRepository.existsByPacienteIdAndEspecialidadIdAndEstadoIn(eq(9L), eq(1), anyCollection())).thenReturn(true);

        assertThatThrownBy(() -> servicio.crear(new ListaEsperaRequest(null, 1, null,
                LocalDate.of(2030, 1, 8), LocalDate.of(2030, 1, 20), null))).isInstanceOf(ConflictoException.class);
        verify(esperaRepository, never()).saveAndFlush(any());
    }

    @Test
    void validaFechasYMedico() {
        Fixtures.autenticarComo(50L, NombreRol.PACIENTE);

        assertCodigo(() -> servicio.crear(new ListaEsperaRequest(null, 1, null,
                LocalDate.of(2030, 1, 1), LocalDate.of(2030, 1, 20), null)), "FECHA_PASADA");
        assertCodigo(() -> servicio.crear(new ListaEsperaRequest(null, 1, null,
                LocalDate.of(2030, 1, 20), LocalDate.of(2030, 1, 10), null)), "RANGO_INVALIDO");
        assertCodigo(() -> servicio.crear(new ListaEsperaRequest(null, 1, null,
                LocalDate.of(2030, 1, 8), LocalDate.of(2030, 12, 31), null)), "FUERA_DE_HORIZONTE");

        medico.setEspecialidades(new HashSet<>());
        assertCodigo(() -> servicio.crear(new ListaEsperaRequest(null, 1, 5L,
                LocalDate.of(2030, 1, 8), LocalDate.of(2030, 1, 20), null)), "ESPECIALIDAD_NO_ATENDIDA");
    }

    @Test
    void unMedicoNoGestionaLaListaDeEspera() {
        Fixtures.autenticarComo(70L, NombreRol.MEDICO);

        assertThatThrownBy(() -> servicio.crear(new ListaEsperaRequest(9L, 1, null,
                LocalDate.of(2030, 1, 8), LocalDate.of(2030, 1, 20), null))).isInstanceOf(AccessDeniedException.class);
    }

    // ================================================================== detección de horarios liberados

    @Test
    void ofreceElHorarioALaPrimeraSolicitudCompatibleEnOrdenDeRegistro() {
        ListaEspera otroMed = espera(1L, ana, otroMedico, "2030-01-08", "2030-01-20", FranjaPreferida.CUALQUIERA, EstadoListaEspera.ACTIVA);
        ListaEspera soloTarde = espera(2L, ana, null, "2030-01-08", "2030-01-20", FranjaPreferida.TARDE, EstadoListaEspera.ACTIVA);
        ListaEspera fueraFechas = espera(3L, ana, null, "2030-01-10", "2030-01-20", FranjaPreferida.CUALQUIERA, EstadoListaEspera.ACTIVA);
        ListaEspera primera = espera(4L, ana, medico, "2030-01-08", "2030-01-20", FranjaPreferida.MANANA, EstadoListaEspera.ACTIVA);
        ListaEspera segunda = espera(5L, luis, null, "2030-01-08", "2030-01-20", FranjaPreferida.CUALQUIERA, EstadoListaEspera.ACTIVA);
        candidatos(otroMed, soloTarde, fueraFechas, primera, segunda);
        Fixtures.autenticarComo(2L, NombreRol.RECEPCIONISTA);

        boolean ofrecido = servicio.ofrecer(new HorarioLiberadoEvent(5L, 1, INI, FIN));

        assertThat(ofrecido).isTrue();
        ArgumentCaptor<Instant> expira = ArgumentCaptor.forClass(Instant.class);
        verify(esperaRepository).reclamarParaOferta(eq(4L), eq(medico), eq(INI), eq(FIN), expira.capture(), any());
        assertThat(expira.getValue()).isEqualTo(Fixtures.AHORA.plus(Duration.ofMinutes(30)));
        verify(esperaRepository, times(1)).reclamarParaOferta(anyLong(), any(), any(), any(), any(), any());
    }

    @Test
    void noOfreceNadaSiElHorarioYaNoEstaLibre() {
        candidatos(espera(4L, ana, null, "2030-01-08", "2030-01-20", FranjaPreferida.CUALQUIERA, EstadoListaEspera.ACTIVA));
        when(disponibilidad.motivoNoDisponible(any(), any(), anyLong(), any(), any(), any()))
                .thenReturn(Optional.of("El médico ya tiene una cita en ese horario."));

        assertThat(servicio.ofrecer(new HorarioLiberadoEvent(5L, 1, INI, FIN))).isFalse();
        verify(esperaRepository, never()).reclamarParaOferta(anyLong(), any(), any(), any(), any(), any());
    }

    @Test
    void noOfreceHorariosQueYaPasaron() {
        Instant pasado = Fixtures.AHORA.minusSeconds(3600);

        assertThat(servicio.ofrecer(new HorarioLiberadoEvent(5L, 1, pasado, pasado.plusSeconds(1800)))).isFalse();
        verify(medicoRepository, never()).obtenerConBloqueo(anyLong());
    }

    @Test
    void omiteAlPacienteQueYaTieneUnaCitaQueSeCruza() {
        ListaEspera ocupada = espera(4L, ana, null, "2030-01-08", "2030-01-20", FranjaPreferida.CUALQUIERA, EstadoListaEspera.ACTIVA);
        ListaEspera libre = espera(5L, luis, null, "2030-01-08", "2030-01-20", FranjaPreferida.CUALQUIERA, EstadoListaEspera.ACTIVA);
        candidatos(ocupada, libre);
        when(citaRepository.contarSolapadasPacienteSin(eq(9L), any(), any(), anyCollection(), anyLong())).thenReturn(1L);

        assertThat(servicio.ofrecer(new HorarioLiberadoEvent(5L, 1, INI, FIN))).isTrue();
        verify(esperaRepository).reclamarParaOferta(eq(5L), any(), any(), any(), any(), any());
        verify(esperaRepository, never()).reclamarParaOferta(eq(4L), any(), any(), any(), any(), any());
    }

    @Test
    void omiteAPacientesInactivos() {
        ana.setActivo(false);
        candidatos(espera(4L, ana, null, "2030-01-08", "2030-01-20", FranjaPreferida.CUALQUIERA, EstadoListaEspera.ACTIVA));

        assertThat(servicio.ofrecer(new HorarioLiberadoEvent(5L, 1, INI, FIN))).isFalse();
    }

    @Test
    void noSeVuelveAOfrecerElHorarioAQuienLoAcabaDeRechazar() {
        ListaEspera rechazo = espera(4L, ana, null, "2030-01-08", "2030-01-20", FranjaPreferida.CUALQUIERA, EstadoListaEspera.ACTIVA);
        ListaEspera siguiente = espera(5L, luis, null, "2030-01-08", "2030-01-20", FranjaPreferida.CUALQUIERA, EstadoListaEspera.ACTIVA);
        candidatos(rechazo, siguiente);

        assertThat(servicio.ofrecer(new HorarioLiberadoEvent(5L, 1, INI, FIN, 4L))).isTrue();
        verify(esperaRepository).reclamarParaOferta(eq(5L), any(), any(), any(), any(), any());
        verify(esperaRepository, never()).reclamarParaOferta(eq(4L), any(), any(), any(), any(), any());
    }

    @Test
    void siOtroProcesoReclamoLaSolicitudSeIntentaConLaSiguiente() {
        candidatos(espera(4L, ana, null, "2030-01-08", "2030-01-20", FranjaPreferida.CUALQUIERA, EstadoListaEspera.ACTIVA),
                espera(5L, luis, null, "2030-01-08", "2030-01-20", FranjaPreferida.CUALQUIERA, EstadoListaEspera.ACTIVA));
        when(esperaRepository.reclamarParaOferta(eq(4L), any(), any(), any(), any(), any())).thenReturn(0);

        assertThat(servicio.ofrecer(new HorarioLiberadoEvent(5L, 1, INI, FIN))).isTrue();
        verify(esperaRepository).reclamarParaOferta(eq(5L), any(), any(), any(), any(), any());
    }

    // ================================================================== aceptar / rechazar / cancelar

    @Test
    void aceptarCreaLaCitaUnaSolaVezYNoPermiteConfirmacionesDuplicadas() {
        ListaEspera e = ofertada(Fixtures.AHORA.plus(Duration.ofMinutes(10)));
        when(citaService.reservarDesdeOferta(9L, 5L, 1, INI, 20L)).thenReturn(citaCreada());
        Fixtures.autenticarComo(50L, NombreRol.PACIENTE);

        CitaResponse cita = servicio.aceptar(20L);

        assertThat(cita.codigoReserva()).isEqualTo("MG-OFERTA01");
        assertThat(e.getEstado()).isEqualTo(EstadoListaEspera.ACEPTADA);

        assertCodigo(() -> servicio.aceptar(20L), "OFERTA_NO_VIGENTE"); // segundo intento (doble clic)
        verify(citaService, times(1)).reservarDesdeOferta(anyLong(), anyLong(), anyInt(), any(), anyLong());
    }

    @Test
    void unaOfertaVencidaNoSePuedeAceptar() {
        ofertada(Fixtures.AHORA.minusSeconds(60));
        Fixtures.autenticarComo(50L, NombreRol.PACIENTE);

        assertCodigo(() -> servicio.aceptar(20L), "OFERTA_VENCIDA");
        verify(citaService, never()).reservarDesdeOferta(anyLong(), anyLong(), anyInt(), any(), anyLong());
    }

    @Test
    void soloElDuenoDeLaSolicitudAceptaLaOferta() {
        ofertada(Fixtures.AHORA.plus(Duration.ofMinutes(10)));
        Fixtures.autenticarComo(51L, NombreRol.PACIENTE); // Luis

        assertThatThrownBy(() -> servicio.aceptar(20L)).isInstanceOf(AccessDeniedException.class);
        verify(citaService, never()).reservarDesdeOferta(anyLong(), anyLong(), anyInt(), any(), anyLong());
    }

    @Test
    void aceptarUnaSolicitudInexistenteDa404() {
        when(esperaRepository.existsById(99L)).thenReturn(false);
        Fixtures.autenticarComo(50L, NombreRol.PACIENTE);

        assertThatThrownBy(() -> servicio.aceptar(99L)).isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void rechazarDevuelveLaSolicitudALaColaYLiberaElHorarioParaLosDemas() {
        ListaEspera e = ofertada(Fixtures.AHORA.plus(Duration.ofMinutes(10)));
        Fixtures.autenticarComo(50L, NombreRol.PACIENTE);

        ListaEsperaResponse r = servicio.rechazar(20L);

        assertThat(r.estado()).isEqualTo(EstadoListaEspera.ACTIVA);
        assertThat(e.getOfertaInicioUtc()).isNull();
        ArgumentCaptor<HorarioLiberadoEvent> evento = ArgumentCaptor.forClass(HorarioLiberadoEvent.class);
        verify(eventos).publishEvent(evento.capture());
        assertThat(evento.getValue().inicio()).isEqualTo(INI);
        assertThat(evento.getValue().excluirEsperaId()).isEqualTo(20L);
    }

    @Test
    void cancelarUnaOfertaLiberaElHorarioPeroConservaElRegistro() {
        ListaEspera e = ofertada(Fixtures.AHORA.plus(Duration.ofMinutes(10)));
        Fixtures.autenticarComo(50L, NombreRol.PACIENTE);

        servicio.cancelar(20L);

        assertThat(e.getEstado()).isEqualTo(EstadoListaEspera.CANCELADA);
        verify(esperaRepository, never()).delete(any(ListaEspera.class));
        verify(eventos).publishEvent(any(HorarioLiberadoEvent.class));
    }

    @Test
    void noSeCancelaUnaSolicitudYaResuelta() {
        ListaEspera e = ofertada(Fixtures.AHORA.plus(Duration.ofMinutes(10)));
        e.setEstado(EstadoListaEspera.ACEPTADA);
        Fixtures.autenticarComo(50L, NombreRol.PACIENTE);

        assertCodigo(() -> servicio.cancelar(20L), "ESPERA_NO_CANCELABLE");
    }

    // ================================================================== vencimiento

    @Test
    void venceLasOfertasNoAceptadasYLasSolicitudesCaducadas() {
        ListaEspera vencida = ofertada(Fixtures.AHORA.minusSeconds(60));
        ListaEspera yaAceptada = espera(21L, luis, null, "2030-01-08", "2030-01-20", FranjaPreferida.CUALQUIERA, EstadoListaEspera.ACEPTADA);
        ListaEspera caducada = espera(22L, luis, null, "2029-12-01", "2030-01-02", FranjaPreferida.CUALQUIERA, EstadoListaEspera.ACTIVA);
        when(esperaRepository.idsOfertasVencidas(any())).thenReturn(List.of(20L, 21L));
        when(esperaRepository.obtenerConBloqueo(21L)).thenReturn(Optional.of(yaAceptada));
        when(esperaRepository.idsSolicitudesCaducadas(any())).thenReturn(List.of(22L));
        when(esperaRepository.obtenerConBloqueo(22L)).thenReturn(Optional.of(caducada));

        int n = servicio.vencerPendientes();

        assertThat(n).isEqualTo(2);
        assertThat(vencida.getEstado()).isEqualTo(EstadoListaEspera.VENCIDA);
        assertThat(yaAceptada.getEstado()).isEqualTo(EstadoListaEspera.ACEPTADA); // no se pisa una aceptación concurrente
        assertThat(caducada.getEstado()).isEqualTo(EstadoListaEspera.VENCIDA);
        ArgumentCaptor<HorarioLiberadoEvent> evento = ArgumentCaptor.forClass(HorarioLiberadoEvent.class);
        verify(eventos).publishEvent(evento.capture()); // el horario de la oferta vencida pasa al siguiente
        assertThat(evento.getValue().excluirEsperaId()).isEqualTo(20L);
    }
}
