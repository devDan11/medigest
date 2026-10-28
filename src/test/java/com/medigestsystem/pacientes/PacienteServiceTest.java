package com.medigestsystem.pacientes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
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
import com.medigestsystem.medicos.Medico;
import com.medigestsystem.medicos.MedicoRepository;
import com.medigestsystem.pacientes.dto.PacienteRequest;
import com.medigestsystem.pacientes.dto.PacienteResponse;
import com.medigestsystem.shared.exception.ConflictoException;
import com.medigestsystem.shared.exception.ReglaNegocioException;
import com.medigestsystem.shared.exception.SolicitudInvalidaException;
import com.medigestsystem.testsupport.Fixtures;
import com.medigestsystem.usuarios.NombreRol;
import com.medigestsystem.usuarios.Usuario;
import com.medigestsystem.usuarios.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PacienteServiceTest {

    @Mock PacienteRepository pacienteRepository;
    @Mock UsuarioRepository usuarioRepository;
    @Mock MedicoRepository medicoRepository;
    @Mock CitaRepository citaRepository;
    @Mock AuditoriaService auditoriaService;

    PacienteService servicio;
    Paciente paciente;

    @BeforeEach
    void preparar() {
        servicio = new PacienteService(pacienteRepository, usuarioRepository, medicoRepository,
                citaRepository, auditoriaService, Fixtures.fechaHora());
        Usuario cuenta = Fixtures.usuario(50L, "ana@test.com", "h", NombreRol.PACIENTE);
        paciente = new Paciente();
        paciente.setId(1L);
        paciente.setUsuario(cuenta);
        paciente.setNombres("Ana");
        paciente.setApellidos("Quispe");
        paciente.setTipoDocumento(TipoDocumento.DNI);
        paciente.setNumeroDocumento("12345678");
        paciente.setFechaNacimiento(LocalDate.of(1990, 5, 20));
        paciente.setEmail("ana@test.com");
        paciente.setDireccion("Av. Lima 123");
        paciente.setActivo(true);
        when(pacienteRepository.findById(1L)).thenReturn(Optional.of(paciente));
        when(pacienteRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach
    void limpiar() {
        Fixtures.cerrarSesion();
    }

    private static PacienteRequest solicitud(String documento) {
        return new PacienteRequest("Luis", "Rojas", TipoDocumento.DNI, documento, LocalDate.of(1985, 1, 1),
                null, "luis@test.com", "999888777", null, null, null);
    }

    @Test
    void recepcionistaVeDetalleCompleto() {
        Fixtures.autenticarComo(2L, NombreRol.RECEPCIONISTA);
        PacienteResponse r = servicio.obtener(1L);
        assertThat(r.email()).isEqualTo("ana@test.com");
        assertThat(r.direccion()).isEqualTo("Av. Lima 123");
        assertThat(r.edad()).isEqualTo(39);
    }

    @Test
    void pacienteVeSoloSuPropiaFicha() {
        Fixtures.autenticarComo(50L, NombreRol.PACIENTE);
        assertThat(servicio.obtener(1L).nombres()).isEqualTo("Ana");

        Fixtures.autenticarComo(51L, NombreRol.PACIENTE);
        assertThatThrownBy(() -> servicio.obtener(1L)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void medicoConCitaVeVistaReducida() {
        Medico medico = new Medico();
        medico.setId(9L);
        when(medicoRepository.findByUsuarioId(60L)).thenReturn(Optional.of(medico));
        when(citaRepository.existsByMedicoIdAndPacienteId(9L, 1L)).thenReturn(true);
        Fixtures.autenticarComo(60L, NombreRol.MEDICO);

        PacienteResponse r = servicio.obtener(1L);
        assertThat(r.nombres()).isEqualTo("Ana");
        assertThat(r.email()).isNull();
        assertThat(r.direccion()).isNull();
    }

    @Test
    void medicoSinRelacionConElPacienteNoAccede() {
        Medico medico = new Medico();
        medico.setId(9L);
        when(medicoRepository.findByUsuarioId(60L)).thenReturn(Optional.of(medico));
        when(citaRepository.existsByMedicoIdAndPacienteId(9L, 1L)).thenReturn(false);
        Fixtures.autenticarComo(60L, NombreRol.MEDICO);

        assertThatThrownBy(() -> servicio.obtener(1L)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void crearRechazaDocumentoDuplicado() {
        when(pacienteRepository.existsByTipoDocumentoAndNumeroDocumento(TipoDocumento.DNI, "87654321")).thenReturn(true);
        assertThatThrownBy(() -> servicio.crear(solicitud("87654321"))).isInstanceOf(ConflictoException.class);
        verify(pacienteRepository, never()).save(any());
    }

    @Test
    void crearRechazaDocumentoConFormatoInvalido() {
        assertThatThrownBy(() -> servicio.crear(solicitud("123"))).isInstanceOf(SolicitudInvalidaException.class);
    }

    @Test
    void crearGuardaPacienteActivo() {
        PacienteResponse r = servicio.crear(solicitud("87654321"));
        assertThat(r.activo()).isTrue();
        assertThat(r.numeroDocumento()).isEqualTo("87654321");
    }

    @Test
    void noSeDesactivaConCitasFuturas() {
        when(citaRepository.countByPacienteIdAndInicioUtcAfterAndEstadoNotIn(anyLong(), any(), anyCollection()))
                .thenReturn(2L);
        assertThatThrownBy(() -> servicio.cambiarEstado(1L, false)).isInstanceOf(ReglaNegocioException.class);
        assertThat(paciente.isActivo()).isTrue();
    }

    @Test
    void desactivarSincronizaLaCuentaDeAcceso() {
        when(citaRepository.countByPacienteIdAndInicioUtcAfterAndEstadoNotIn(anyLong(), any(), anyCollection()))
                .thenReturn(0L);
        servicio.cambiarEstado(1L, false);
        assertThat(paciente.isActivo()).isFalse();
        assertThat(paciente.getUsuario().isActivo()).isFalse();
        verify(usuarioRepository).save(paciente.getUsuario());
    }
}
