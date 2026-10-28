package com.medigestsystem.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.medigestsystem.auditoria.AuditoriaService;
import com.medigestsystem.auth.dto.CambiarPasswordRequest;
import com.medigestsystem.auth.dto.LoginRequest;
import com.medigestsystem.auth.dto.RegistroPacienteRequest;
import com.medigestsystem.auth.dto.TokenResponse;
import com.medigestsystem.pacientes.Paciente;
import com.medigestsystem.pacientes.PacienteRepository;
import com.medigestsystem.pacientes.TipoDocumento;
import com.medigestsystem.security.JwtService;
import com.medigestsystem.shared.exception.ConflictoException;
import com.medigestsystem.shared.exception.CredencialesInvalidasException;
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
class AuthServiceTest {

    private static final Instant AHORA = Instant.parse("2026-10-27T15:00:00Z");

    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private RolRepository rolRepository;
    @Mock
    private PacienteRepository pacienteRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuditoriaService auditoriaService;

    private JwtService jwtService;
    private AuthService servicio;

    @BeforeEach
    void preparar() {
        Clock reloj = Clock.fixed(AHORA, ZoneOffset.UTC);
        jwtService = new JwtService(
                Fixtures.propiedades(Fixtures.SECRETO_PRUEBAS),
                reloj, new MockEnvironment());
        servicio = new AuthService(authenticationManager, usuarioRepository, rolRepository, pacienteRepository,
                passwordEncoder, jwtService, auditoriaService, reloj);
    }

    // ---------------------------------------------------------------- registro

    @Test
    void registraUnPacienteConRolPacienteYContrasenaCifrada() {
        Rol rolPaciente = new Rol();
        rolPaciente.setNombre(NombreRol.PACIENTE);
        when(rolRepository.findByNombre(NombreRol.PACIENTE)).thenReturn(Optional.of(rolPaciente));
        when(passwordEncoder.encode("Clave2026")).thenReturn("hash-bcrypt");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(i -> {
            Usuario u = i.getArgument(0);
            u.setId(10L);
            return u;
        });
        when(pacienteRepository.save(any(Paciente.class))).thenAnswer(i -> i.getArgument(0));

        var respuesta = servicio.registrarPaciente(solicitud("  Ana.Lopez@Correo.COM ", "12345678"));

        ArgumentCaptor<Usuario> usuario = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(usuario.capture());
        assertThat(usuario.getValue().getEmail()).isEqualTo("ana.lopez@correo.com");
        assertThat(usuario.getValue().getPasswordHash()).isEqualTo("hash-bcrypt").isNotEqualTo("Clave2026");
        assertThat(usuario.getValue().getRoles()).extracting(Rol::getNombre).containsExactly(NombreRol.PACIENTE);

        ArgumentCaptor<Paciente> paciente = ArgumentCaptor.forClass(Paciente.class);
        verify(pacienteRepository).save(paciente.capture());
        assertThat(paciente.getValue().getNumeroDocumento()).isEqualTo("12345678");
        assertThat(paciente.getValue().getUsuario()).isSameAs(usuario.getValue());
        assertThat(respuesta.roles()).containsExactly("PACIENTE");
    }

    @Test
    void rechazaUnCorreoYaRegistrado() {
        when(usuarioRepository.existsByEmailIgnoreCase("ana@correo.com")).thenReturn(true);

        assertThatThrownBy(() -> servicio.registrarPaciente(solicitud("ana@correo.com", "12345678")))
                .isInstanceOfSatisfying(ConflictoException.class,
                        e -> assertThat(e.getCodigo()).isEqualTo("EMAIL_DUPLICADO"));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void rechazaUnDocumentoYaRegistrado() {
        when(pacienteRepository.existsByTipoDocumentoAndNumeroDocumento(TipoDocumento.DNI, "12345678"))
                .thenReturn(true);

        assertThatThrownBy(() -> servicio.registrarPaciente(solicitud("ana@correo.com", "12345678")))
                .isInstanceOfSatisfying(ConflictoException.class,
                        e -> assertThat(e.getCodigo()).isEqualTo("DOCUMENTO_DUPLICADO"));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void rechazaUnDniQueNoTieneOchoDigitos() {
        assertThatThrownBy(() -> servicio.registrarPaciente(solicitud("ana@correo.com", "1234567")))
                .isInstanceOf(SolicitudInvalidaException.class);
        verify(usuarioRepository, never()).save(any());
    }

    // ------------------------------------------------------------------- login

    @Test
    void loginCorrectoDevuelveTokensDeAccesoYRefresco() {
        Usuario usuario = usuarioConHash("hash-1");
        when(usuarioRepository.findByEmailIgnoreCase("ana@correo.com")).thenReturn(Optional.of(usuario));

        TokenResponse tokens = servicio.login(new LoginRequest("Ana@Correo.com", "Clave2026"));

        assertThat(tokens.tokenType()).isEqualTo("Bearer");
        assertThat(tokens.expiresIn()).isEqualTo(900);
        assertThat(jwtService.decodificadorAcceso().decode(tokens.accessToken()).getSubject()).isEqualTo("5");
        assertThat(jwtService.decodificarRefresco(tokens.refreshToken()).getSubject()).isEqualTo("5");
    }

    @Test
    void loginConCredencialesInvalidasDevuelveUnMensajeGenerico() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("x"));

        assertThatThrownBy(() -> servicio.login(new LoginRequest("ana@correo.com", "mala")))
                .isInstanceOf(CredencialesInvalidasException.class)
                .hasMessageNotContaining("bloqueada por");
    }

    @Test
    void unaCuentaBloqueadaSeResponde_IgualQueUnaClaveIncorrecta() {
        when(authenticationManager.authenticate(any())).thenThrow(new LockedException("bloqueada"));

        assertThatThrownBy(() -> servicio.login(new LoginRequest("ana@correo.com", "Clave2026")))
                .isInstanceOf(CredencialesInvalidasException.class)
                .hasMessage("Credenciales inválidas o cuenta temporalmente bloqueada.");
    }

    // ----------------------------------------------------------------- refresco

    @Test
    void refrescarConUnTokenDeAccesoEsRechazado() {
        Usuario usuario = usuarioConHash("hash-1");
        String acceso = jwtService.emitirAcceso(usuario);

        assertThatThrownBy(() -> servicio.refrescar(acceso)).isInstanceOf(CredencialesInvalidasException.class);
    }

    @Test
    void refrescarConUnTokenValidoEmiteUnParNuevo() {
        Usuario usuario = usuarioConHash("hash-1");
        when(usuarioRepository.findById(5L)).thenReturn(Optional.of(usuario));

        TokenResponse nuevos = servicio.refrescar(jwtService.emitirRefresco(usuario));

        assertThat(nuevos.accessToken()).isNotBlank();
        assertThat(nuevos.refreshToken()).isNotBlank();
    }

    @Test
    void cambiarLaContrasenaInvalidaLosTokensDeRefrescoAnteriores() {
        Usuario usuario = usuarioConHash("hash-1");
        when(usuarioRepository.findById(5L)).thenReturn(Optional.of(usuario));
        String refresco = jwtService.emitirRefresco(usuario);

        usuario.setPasswordHash("hash-2");

        assertThatThrownBy(() -> servicio.refrescar(refresco)).isInstanceOf(CredencialesInvalidasException.class);
    }

    @Test
    void unUsuarioDesactivadoNoPuedeRefrescar() {
        Usuario usuario = usuarioConHash("hash-1");
        when(usuarioRepository.findById(5L)).thenReturn(Optional.of(usuario));
        String refresco = jwtService.emitirRefresco(usuario);

        usuario.setActivo(false);

        assertThatThrownBy(() -> servicio.refrescar(refresco)).isInstanceOf(CredencialesInvalidasException.class);
    }

    @Test
    void unUsuarioBloqueadoNoPuedeRefrescar() {
        Usuario usuario = usuarioConHash("hash-1");
        when(usuarioRepository.findById(5L)).thenReturn(Optional.of(usuario));
        String refresco = jwtService.emitirRefresco(usuario);

        usuario.setBloqueadoHasta(AHORA.plusSeconds(600));

        assertThatThrownBy(() -> servicio.refrescar(refresco)).isInstanceOf(CredencialesInvalidasException.class);
    }

    // -------------------------------------------------------- cambio de clave

    @Test
    void cambiarContrasenaExigeLaActualCorrecta() {
        Usuario usuario = usuarioConHash("hash-1");
        when(usuarioRepository.findById(5L)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("incorrecta1", "hash-1")).thenReturn(false);

        assertThatThrownBy(() -> servicio.cambiarPassword(5L, new CambiarPasswordRequest("incorrecta1", "Nueva2026")))
                .isInstanceOf(ReglaNegocioException.class);
        assertThat(usuario.getPasswordHash()).isEqualTo("hash-1");
    }

    @Test
    void cambiarContrasenaGuardaElNuevoHash() {
        Usuario usuario = usuarioConHash("hash-1");
        when(usuarioRepository.findById(5L)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("Actual2026", "hash-1")).thenReturn(true);
        when(passwordEncoder.matches("Nueva2026", "hash-1")).thenReturn(false);
        when(passwordEncoder.encode("Nueva2026")).thenReturn("hash-2");

        servicio.cambiarPassword(5L, new CambiarPasswordRequest("Actual2026", "Nueva2026"));

        assertThat(usuario.getPasswordHash()).isEqualTo("hash-2");
        verify(usuarioRepository).save(usuario);
    }

    @Test
    void noPermiteReutilizarLaMismaContrasena() {
        Usuario usuario = usuarioConHash("hash-1");
        when(usuarioRepository.findById(5L)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("Actual2026", "hash-1")).thenReturn(true);

        assertThatThrownBy(() -> servicio.cambiarPassword(5L, new CambiarPasswordRequest("Actual2026", "Actual2026")))
                .isInstanceOf(ReglaNegocioException.class);
    }

    // ------------------------------------------------------------------- apoyo

    private static Usuario usuarioConHash(String hash) {
        return Fixtures.usuario(5L, "ana@correo.com", hash, NombreRol.PACIENTE);
    }

    private static RegistroPacienteRequest solicitud(String email, String dni) {
        return new RegistroPacienteRequest("Ana", "López", TipoDocumento.DNI, dni,
                LocalDate.of(1990, 5, 20), null, email, "999111222", "Clave2026");
    }
}
