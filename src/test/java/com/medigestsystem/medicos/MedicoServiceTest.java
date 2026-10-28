package com.medigestsystem.medicos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.medigestsystem.auditoria.AuditoriaService;
import com.medigestsystem.citas.CitaRepository;
import com.medigestsystem.consultorios.ConsultorioRepository;
import com.medigestsystem.especialidades.Especialidad;
import com.medigestsystem.especialidades.EspecialidadRepository;
import com.medigestsystem.medicos.dto.CuentaMedicoRequest;
import com.medigestsystem.medicos.dto.MedicoRequest;
import com.medigestsystem.medicos.dto.MedicoResponse;
import com.medigestsystem.shared.exception.ConflictoException;
import com.medigestsystem.shared.exception.ReglaNegocioException;
import com.medigestsystem.shared.exception.SolicitudInvalidaException;
import com.medigestsystem.testsupport.Fixtures;
import com.medigestsystem.usuarios.NombreRol;
import com.medigestsystem.usuarios.Rol;
import com.medigestsystem.usuarios.RolRepository;
import com.medigestsystem.usuarios.Usuario;
import com.medigestsystem.usuarios.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MedicoServiceTest {

    @Mock MedicoRepository medicoRepository;
    @Mock EspecialidadRepository especialidadRepository;
    @Mock ConsultorioRepository consultorioRepository;
    @Mock UsuarioRepository usuarioRepository;
    @Mock RolRepository rolRepository;
    @Mock CitaRepository citaRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock AuditoriaService auditoriaService;

    MedicoService servicio;
    Especialidad cardiologia;
    Especialidad pediatria;
    Medico medico;

    @BeforeEach
    void preparar() {
        servicio = new MedicoService(medicoRepository, especialidadRepository, consultorioRepository,
                usuarioRepository, rolRepository, citaRepository, passwordEncoder, auditoriaService,
                Fixtures.fechaHora());
        cardiologia = especialidad(1, "Cardiología");
        pediatria = especialidad(2, "Pediatría");
        medico = new Medico();
        medico.setId(5L);
        medico.setNombres("Carlos");
        medico.setApellidos("Paredes");
        medico.setNumeroColegiatura("CMP-1000");
        medico.setDuracionConsultaMin(30);
        medico.setActivo(true);
        medico.setEspecialidades(new HashSet<>(Set.of(cardiologia, pediatria)));
        when(medicoRepository.findById(5L)).thenReturn(Optional.of(medico));
        when(medicoRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(especialidadRepository.findById(1)).thenReturn(Optional.of(cardiologia));
        when(especialidadRepository.findById(2)).thenReturn(Optional.of(pediatria));
        when(rolRepository.findByNombre(NombreRol.MEDICO)).thenReturn(Optional.of(Fixtures.rol(NombreRol.MEDICO)));
        when(passwordEncoder.encode(any())).thenReturn("hash");
        when(usuarioRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach
    void limpiar() {
        Fixtures.cerrarSesion();
    }

    private static Especialidad especialidad(int id, String nombre) {
        Especialidad e = new Especialidad();
        e.setId(id);
        e.setNombre(nombre);
        e.setActiva(true);
        return e;
    }

    private static MedicoRequest solicitud(String colegiatura, Set<Integer> especialidades, CuentaMedicoRequest cuenta) {
        return new MedicoRequest("Carlos", "Paredes", colegiatura, "carlos@test.com", null, null, 30,
                especialidades, cuenta);
    }

    @Test
    void crearRechazaColegiaturaDuplicada() {
        when(medicoRepository.existsByNumeroColegiatura("CMP-2000")).thenReturn(true);
        assertThatThrownBy(() -> servicio.crear(solicitud("cmp-2000", Set.of(1), null)))
                .isInstanceOf(ConflictoException.class);
    }

    @Test
    void crearConCuentaGeneraUsuarioConRolMedico() {
        when(usuarioRepository.existsByEmailIgnoreCase("nuevo@test.com")).thenReturn(false);
        MedicoResponse r = servicio.crear(solicitud("CMP-2000", Set.of(1),
                new CuentaMedicoRequest("Nuevo@Test.com", "Clave1234")));
        assertThat(r.tieneCuenta()).isTrue();
        assertThat(r.numeroColegiatura()).isEqualTo("CMP-2000");
        verify(usuarioRepository).save(any(Usuario.class));
    }

    @Test
    void crearRechazaCorreoDeCuentaYaRegistrado() {
        when(usuarioRepository.existsByEmailIgnoreCase("nuevo@test.com")).thenReturn(true);
        assertThatThrownBy(() -> servicio.crear(solicitud("CMP-2000", Set.of(1),
                new CuentaMedicoRequest("nuevo@test.com", "Clave1234"))))
                .isInstanceOf(ConflictoException.class);
    }

    @Test
    void crearRechazaContrasenaDebil() {
        assertThatThrownBy(() -> servicio.crear(solicitud("CMP-2000", Set.of(1),
                new CuentaMedicoRequest("nuevo@test.com", "soloLetras"))))
                .isInstanceOf(SolicitudInvalidaException.class);
    }

    @Test
    void actualizarNoAceptaCredenciales() {
        assertThatThrownBy(() -> servicio.actualizar(5L, solicitud("CMP-1000", Set.of(1, 2),
                new CuentaMedicoRequest("x@test.com", "Clave1234"))))
                .isInstanceOf(SolicitudInvalidaException.class);
    }

    @Test
    void noSeRetiraUnaEspecialidadConCitasRegistradas() {
        when(citaRepository.existsByMedicoIdAndEspecialidadId(5L, 2)).thenReturn(true);
        assertThatThrownBy(() -> servicio.actualizar(5L, solicitud("CMP-1000", Set.of(1), null)))
                .isInstanceOf(ReglaNegocioException.class);
        assertThat(medico.getEspecialidades()).hasSize(2);
    }

    @Test
    void seRetiraUnaEspecialidadSinCitas() {
        when(citaRepository.existsByMedicoIdAndEspecialidadId(5L, 2)).thenReturn(false);
        MedicoResponse r = servicio.actualizar(5L, solicitud("CMP-1000", Set.of(1), null));
        assertThat(r.especialidades()).hasSize(1);
    }

    @Test
    void noSeDesactivaConCitasFuturas() {
        when(citaRepository.countByMedicoIdAndInicioUtcAfterAndEstadoNotIn(anyLong(), any(), anyCollection()))
                .thenReturn(3L);
        assertThatThrownBy(() -> servicio.cambiarEstado(5L, false)).isInstanceOf(ReglaNegocioException.class);
        assertThat(medico.isActivo()).isTrue();
    }

    @Test
    void agendaRechazaRangosMayoresA31Dias() {
        Fixtures.autenticarComo(1L, NombreRol.ADMINISTRADOR);
        LocalDate desde = LocalDate.of(2030, 1, 1);
        assertThatThrownBy(() -> servicio.agenda(5L, desde, desde.plusDays(31)))
                .isInstanceOf(SolicitudInvalidaException.class);
        assertThatThrownBy(() -> servicio.agenda(5L, desde, desde.minusDays(1)))
                .isInstanceOf(SolicitudInvalidaException.class);
    }

    @Test
    void medicoNoConsultaLaAgendaDeOtro() {
        Usuario cuentaDelMedico = Fixtures.usuario(70L, "m@test.com", "h", NombreRol.MEDICO);
        medico.setUsuario(cuentaDelMedico);
        Fixtures.autenticarComo(71L, NombreRol.MEDICO);
        LocalDate dia = LocalDate.of(2030, 1, 8);
        assertThatThrownBy(() -> servicio.agenda(5L, dia, dia)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void usuarioSinPrivilegiosNoVeContactoDelMedico() {
        medico.setEmail("carlos@test.com");
        Fixtures.autenticarComo(80L, NombreRol.PACIENTE);
        MedicoResponse r = servicio.obtener(5L);
        assertThat(r.email()).isNull();
        assertThat(r.nombres()).isEqualTo("Carlos");
    }
}
