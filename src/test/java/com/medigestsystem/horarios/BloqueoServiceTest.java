package com.medigestsystem.horarios;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
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

import com.medigestsystem.auditoria.AuditoriaService;
import com.medigestsystem.citas.CitaRepository;
import com.medigestsystem.consultorios.ConsultorioRepository;
import com.medigestsystem.horarios.dto.BloqueoRequest;
import com.medigestsystem.horarios.dto.BloqueoResponse;
import com.medigestsystem.medicos.Medico;
import com.medigestsystem.medicos.MedicoRepository;
import com.medigestsystem.shared.exception.ReglaNegocioException;
import com.medigestsystem.shared.exception.SolicitudInvalidaException;
import com.medigestsystem.testsupport.Fixtures;
import com.medigestsystem.usuarios.NombreRol;
import com.medigestsystem.usuarios.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BloqueoServiceTest {

    @Mock BloqueoHorarioRepository bloqueoRepository;
    @Mock MedicoRepository medicoRepository;
    @Mock ConsultorioRepository consultorioRepository;
    @Mock UsuarioRepository usuarioRepository;
    @Mock CitaRepository citaRepository;
    @Mock AuditoriaService auditoriaService;

    BloqueoService servicio;
    Medico medico;

    static final OffsetDateTime INICIO = OffsetDateTime.parse("2030-02-01T08:00:00-05:00");
    static final OffsetDateTime FIN = OffsetDateTime.parse("2030-02-05T18:00:00-05:00");

    @BeforeEach
    void preparar() {
        servicio = new BloqueoService(bloqueoRepository, medicoRepository, consultorioRepository,
                usuarioRepository, citaRepository, auditoriaService, Fixtures.fechaHora());
        medico = new Medico();
        medico.setId(5L);
        medico.setNombres("Carlos");
        medico.setApellidos("Paredes");
        when(medicoRepository.findById(5L)).thenReturn(Optional.of(medico));
        when(medicoRepository.findByUsuarioId(70L)).thenReturn(Optional.of(medico));
        when(bloqueoRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(citaRepository.contarSolapadasMedico(anyLong(), any(), any(), anyCollection())).thenReturn(0L);
    }

    @AfterEach
    void limpiar() {
        Fixtures.cerrarSesion();
    }

    @Test
    void administradorCreaBloqueoAprobado() {
        Fixtures.autenticarComo(1L, NombreRol.ADMINISTRADOR);
        BloqueoResponse r = servicio.crear(new BloqueoRequest(5L, null, INICIO, FIN, TipoBloqueo.VACACIONES, "Viaje"));
        assertThat(r.estado()).isEqualTo(EstadoBloqueo.APROBADO);
        assertThat(r.medicoId()).isEqualTo(5L);
    }

    @Test
    void debeIndicarMedicoOConsultorioPeroNoAmbosNiNinguno() {
        Fixtures.autenticarComo(1L, NombreRol.ADMINISTRADOR);
        assertThatThrownBy(() -> servicio.crear(new BloqueoRequest(null, null, INICIO, FIN, TipoBloqueo.OTRO, null)))
                .isInstanceOf(SolicitudInvalidaException.class);
        assertThatThrownBy(() -> servicio.crear(new BloqueoRequest(5L, 1, INICIO, FIN, TipoBloqueo.OTRO, null)))
                .isInstanceOf(SolicitudInvalidaException.class);
    }

    @Test
    void rangoInvertidoOPasadoSeRechaza() {
        Fixtures.autenticarComo(1L, NombreRol.ADMINISTRADOR);
        assertThatThrownBy(() -> servicio.crear(new BloqueoRequest(5L, null, FIN, INICIO, TipoBloqueo.OTRO, null)))
                .isInstanceOf(SolicitudInvalidaException.class);
        OffsetDateTime pasadoIni = OffsetDateTime.parse("2020-01-01T08:00:00-05:00");
        OffsetDateTime pasadoFin = OffsetDateTime.parse("2020-01-02T08:00:00-05:00");
        assertThatThrownBy(() -> servicio.crear(new BloqueoRequest(5L, null, pasadoIni, pasadoFin, TipoBloqueo.OTRO, null)))
                .isInstanceOf(SolicitudInvalidaException.class);
    }

    @Test
    void noSeBloqueaUnPeriodoConCitasVigentes() {
        Fixtures.autenticarComo(1L, NombreRol.ADMINISTRADOR);
        when(citaRepository.contarSolapadasMedico(anyLong(), any(), any(), anyCollection())).thenReturn(2L);
        assertThatThrownBy(() -> servicio.crear(new BloqueoRequest(5L, null, INICIO, FIN, TipoBloqueo.AUSENCIA, null)))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    void medicoSoloSolicitaBloqueosPropios() {
        Fixtures.autenticarComo(70L, NombreRol.MEDICO);
        BloqueoResponse r = servicio.crear(new BloqueoRequest(null, null, INICIO, FIN, TipoBloqueo.VACACIONES, null));
        assertThat(r.estado()).isEqualTo(EstadoBloqueo.SOLICITADO);

        assertThatThrownBy(() -> servicio.crear(new BloqueoRequest(99L, null, INICIO, FIN, TipoBloqueo.VACACIONES, null)))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> servicio.crear(new BloqueoRequest(null, 3, INICIO, FIN, TipoBloqueo.VACACIONES, null)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void medicoNoPuedeSolicitarMantenimiento() {
        Fixtures.autenticarComo(70L, NombreRol.MEDICO);
        assertThatThrownBy(() -> servicio.crear(new BloqueoRequest(null, null, INICIO, FIN, TipoBloqueo.MANTENIMIENTO, null)))
                .isInstanceOf(SolicitudInvalidaException.class);
    }

    @Test
    void aprobarSoloAplicaASolicitudesPendientes() {
        BloqueoHorario b = new BloqueoHorario();
        b.setId(10L);
        b.setMedico(medico);
        b.setInicioUtc(INICIO.toInstant());
        b.setFinUtc(FIN.toInstant());
        b.setTipo(TipoBloqueo.VACACIONES);
        b.setEstado(EstadoBloqueo.APROBADO);
        when(bloqueoRepository.findById(10L)).thenReturn(Optional.of(b));
        assertThatThrownBy(() -> servicio.aprobar(10L)).isInstanceOf(ReglaNegocioException.class);

        b.setEstado(EstadoBloqueo.SOLICITADO);
        assertThat(servicio.aprobar(10L).estado()).isEqualTo(EstadoBloqueo.APROBADO);
    }

    @Test
    void medicoNoEliminaBloqueoAprobadoNiAjeno() {
        BloqueoHorario b = new BloqueoHorario();
        b.setId(11L);
        b.setMedico(medico);
        b.setEstado(EstadoBloqueo.APROBADO);
        when(bloqueoRepository.findById(11L)).thenReturn(Optional.of(b));
        Fixtures.autenticarComo(70L, NombreRol.MEDICO);
        assertThatThrownBy(() -> servicio.eliminar(11L)).isInstanceOf(ReglaNegocioException.class);

        Medico otro = new Medico();
        otro.setId(6L);
        when(medicoRepository.findByUsuarioId(71L)).thenReturn(Optional.of(otro));
        Fixtures.autenticarComo(71L, NombreRol.MEDICO);
        assertThatThrownBy(() -> servicio.eliminar(11L)).isInstanceOf(AccessDeniedException.class);
    }
}
