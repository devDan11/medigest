package com.medigestsystem.auth;

import java.time.Clock;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medigestsystem.auditoria.AuditoriaService;
import com.medigestsystem.auditoria.ResultadoAuditoria;
import com.medigestsystem.auth.dto.CambiarPasswordRequest;
import com.medigestsystem.auth.dto.LoginRequest;
import com.medigestsystem.auth.dto.RegistroPacienteRequest;
import com.medigestsystem.auth.dto.TokenResponse;
import com.medigestsystem.pacientes.Paciente;
import com.medigestsystem.pacientes.PacienteRepository;
import com.medigestsystem.pacientes.TipoDocumento;
import com.medigestsystem.pacientes.ValidadorDocumento;
import com.medigestsystem.security.JwtService;
import com.medigestsystem.shared.exception.ConflictoException;
import com.medigestsystem.shared.exception.CredencialesInvalidasException;
import com.medigestsystem.shared.exception.ReglaNegocioException;
import com.medigestsystem.usuarios.NombreRol;
import com.medigestsystem.usuarios.Rol;
import com.medigestsystem.usuarios.RolRepository;
import com.medigestsystem.usuarios.Usuario;
import com.medigestsystem.usuarios.UsuarioRepository;
import com.medigestsystem.usuarios.dto.UsuarioResponse;

/** Autenticación por API (JWT), registro de pacientes y cambio de contraseña. */
@Service
public class AuthService {

    private static final String MENSAJE_CREDENCIALES = "Credenciales inválidas o cuenta temporalmente bloqueada.";

    private final AuthenticationManager authenticationManager;
    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final PacienteRepository pacienteRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditoriaService auditoriaService;
    private final Clock clock;

    public AuthService(AuthenticationManager authenticationManager, UsuarioRepository usuarioRepository,
                       RolRepository rolRepository, PacienteRepository pacienteRepository,
                       PasswordEncoder passwordEncoder, JwtService jwtService,
                       AuditoriaService auditoriaService, Clock clock) {
        this.authenticationManager = authenticationManager;
        this.usuarioRepository = usuarioRepository;
        this.rolRepository = rolRepository;
        this.pacienteRepository = pacienteRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.auditoriaService = auditoriaService;
        this.clock = clock;
    }

    /**
     * Valida correo y contraseña. El bloqueo por intentos y la auditoría los realiza
     * {@code LoginEventListener} a partir de los eventos del {@link AuthenticationManager}.
     */
    public TokenResponse login(LoginRequest solicitud) {
        String email = normalizarEmail(solicitud.email());
        try {
            authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(email, solicitud.password()));
        } catch (AuthenticationException e) {
            throw new CredencialesInvalidasException(MENSAJE_CREDENCIALES);
        }
        Usuario usuario = usuarioRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new CredencialesInvalidasException(MENSAJE_CREDENCIALES));
        return emitirTokens(usuario);
    }

    /** Canjea un token de refresco válido por un nuevo par de tokens. */
    public TokenResponse refrescar(String tokenRefresco) {
        Jwt jwt;
        try {
            jwt = jwtService.decodificarRefresco(tokenRefresco);
        } catch (JwtException e) {
            throw new CredencialesInvalidasException("TOKEN_INVALIDO", "El token de refresco no es válido o expiró.");
        }
        Usuario usuario = cargarUsuario(jwt.getSubject());
        boolean bloqueado = usuario.getBloqueadoHasta() != null
                && usuario.getBloqueadoHasta().isAfter(clock.instant());
        boolean huellaVigente = JwtService.huellaCredencial(usuario.getPasswordHash())
                .equals(jwt.getClaimAsString(JwtService.CLAIM_CREDENCIAL));
        if (!usuario.isActivo() || bloqueado || !huellaVigente) {
            throw new CredencialesInvalidasException("TOKEN_INVALIDO", "El token de refresco no es válido o expiró.");
        }
        return emitirTokens(usuario);
    }

    @Transactional
    public UsuarioResponse registrarPaciente(RegistroPacienteRequest solicitud) {
        String email = normalizarEmail(solicitud.email());
        TipoDocumento tipo = solicitud.tipoDocumento();
        String documento = ValidadorDocumento.normalizar(solicitud.numeroDocumento());
        ValidadorDocumento.validar(tipo, documento);

        if (usuarioRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictoException("EMAIL_DUPLICADO", "Ya existe una cuenta registrada con ese correo.");
        }
        if (pacienteRepository.existsByTipoDocumentoAndNumeroDocumento(tipo, documento)) {
            throw new ConflictoException("DOCUMENTO_DUPLICADO",
                    "Ese documento ya está registrado. Si es suyo, comuníquese con recepción.");
        }
        Rol rolPaciente = rolRepository.findByNombre(NombreRol.PACIENTE)
                .orElseThrow(() -> new IllegalStateException("El rol PACIENTE no existe en la base de datos"));

        Usuario usuario = new Usuario();
        usuario.setEmail(email);
        usuario.setPasswordHash(passwordEncoder.encode(solicitud.password()));
        usuario.setActivo(true);
        Set<Rol> roles = new HashSet<>();
        roles.add(rolPaciente);
        usuario.setRoles(roles);

        Paciente paciente = new Paciente();
        paciente.setNombres(solicitud.nombres().trim());
        paciente.setApellidos(solicitud.apellidos().trim());
        paciente.setTipoDocumento(tipo);
        paciente.setNumeroDocumento(documento);
        paciente.setFechaNacimiento(solicitud.fechaNacimiento());
        paciente.setSexo(solicitud.sexo());
        paciente.setEmail(email);
        paciente.setTelefono(vacioANulo(solicitud.telefono()));
        paciente.setActivo(true);

        try {
            usuario = usuarioRepository.save(usuario);
            paciente.setUsuario(usuario);
            pacienteRepository.save(paciente);
        } catch (DataIntegrityViolationException e) {
            // Solicitudes simultáneas con el mismo correo o documento: gana la primera.
            throw new ConflictoException("DUPLICADO", "Ya existe una cuenta con esos datos.");
        }
        auditoriaService.registrarComo(usuario.getId(), usuario.getEmail(), "REGISTRO_PACIENTE", "pacientes",
                String.valueOf(paciente.getId()), ResultadoAuditoria.EXITO, null);
        return UsuarioResponse.de(usuario);
    }

    @Transactional
    public void cambiarPassword(Long usuarioId, CambiarPasswordRequest solicitud) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new CredencialesInvalidasException(MENSAJE_CREDENCIALES));
        if (!passwordEncoder.matches(solicitud.passwordActual(), usuario.getPasswordHash())) {
            auditoriaService.registrarComo(usuario.getId(), usuario.getEmail(), "CAMBIO_PASSWORD", "usuarios",
                    String.valueOf(usuario.getId()), ResultadoAuditoria.FALLO, "Contraseña actual incorrecta");
            throw new ReglaNegocioException("PASSWORD_ACTUAL_INCORRECTA", "La contraseña actual no es correcta.");
        }
        if (passwordEncoder.matches(solicitud.nuevaPassword(), usuario.getPasswordHash())) {
            throw new ReglaNegocioException("PASSWORD_REPETIDA", "La nueva contraseña debe ser distinta de la actual.");
        }
        usuario.setPasswordHash(passwordEncoder.encode(solicitud.nuevaPassword()));
        usuarioRepository.save(usuario);
        auditoriaService.registrarComo(usuario.getId(), usuario.getEmail(), "CAMBIO_PASSWORD", "usuarios",
                String.valueOf(usuario.getId()), ResultadoAuditoria.EXITO, null);
    }

    public UsuarioResponse perfil(Long usuarioId) {
        return usuarioRepository.findById(usuarioId)
                .map(UsuarioResponse::de)
                .orElseThrow(() -> new CredencialesInvalidasException(MENSAJE_CREDENCIALES));
    }

    private Usuario cargarUsuario(String subject) {
        try {
            return usuarioRepository.findById(Long.valueOf(subject))
                    .orElseThrow(() -> new CredencialesInvalidasException("TOKEN_INVALIDO",
                            "El token de refresco no es válido o expiró."));
        } catch (NumberFormatException e) {
            throw new CredencialesInvalidasException("TOKEN_INVALIDO", "El token de refresco no es válido o expiró.");
        }
    }

    private TokenResponse emitirTokens(Usuario usuario) {
        return new TokenResponse(
                jwtService.emitirAcceso(usuario),
                jwtService.emitirRefresco(usuario),
                "Bearer",
                jwtService.segundosVigenciaAcceso(),
                UsuarioResponse.de(usuario));
    }

    static String normalizarEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private static String vacioANulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }
}
