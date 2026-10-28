package com.medigestsystem.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medigestsystem.auditoria.AuditoriaService;
import com.medigestsystem.auditoria.ResultadoAuditoria;
import com.medigestsystem.config.MediGestProperties;
import com.medigestsystem.shared.exception.SolicitudInvalidaException;
import com.medigestsystem.usuarios.Usuario;
import com.medigestsystem.usuarios.UsuarioRepository;

/**
 * Recuperación de contraseña con token temporal de un solo uso.
 * Solo se guarda el hash SHA-256 del token; el valor original únicamente viaja en el enlace.
 */
@Service
public class RecuperacionService {

    private static final Duration ESPERA_ENTRE_SOLICITUDES = Duration.ofMinutes(1);
    private static final String MENSAJE_TOKEN = "El enlace de recuperación no es válido o ya expiró.";

    private final UsuarioRepository usuarioRepository;
    private final TokenRecuperacionRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final RecuperacionNotificador notificador;
    private final AuditoriaService auditoriaService;
    private final Clock clock;
    private final int minutosVigencia;
    private final String urlBase;
    private final SecureRandom random = new SecureRandom();

    public RecuperacionService(UsuarioRepository usuarioRepository, TokenRecuperacionRepository tokenRepository,
                               PasswordEncoder passwordEncoder, RecuperacionNotificador notificador,
                               AuditoriaService auditoriaService, Clock clock, MediGestProperties properties) {
        this.usuarioRepository = usuarioRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.notificador = notificador;
        this.auditoriaService = auditoriaService;
        this.clock = clock;
        this.minutosVigencia = properties.seguridad().recuperacionMinutos();
        this.urlBase = quitarBarraFinal(properties.aplicacion().urlBase());
    }

    /**
     * Genera y entrega un enlace de recuperación. No revela si el correo existe: el resultado
     * es siempre el mismo para quien consulta.
     */
    @Transactional
    public void solicitar(String emailCrudo) {
        String email = AuthService.normalizarEmail(emailCrudo);
        Optional<Usuario> encontrado = usuarioRepository.findByEmailIgnoreCase(email);
        if (encontrado.isEmpty() || !encontrado.get().isActivo()) {
            auditoriaService.registrarComo(null, email, "RECUPERACION_SOLICITADA", "usuarios", null,
                    ResultadoAuditoria.FALLO, "Correo no registrado o cuenta desactivada");
            return;
        }
        Usuario usuario = encontrado.get();
        Instant ahora = clock.instant();

        if (tokenRepository.existsByUsuarioIdAndCreadoEnAfter(usuario.getId(), ahora.minus(ESPERA_ENTRE_SOLICITUDES))) {
            auditoriaService.registrarComo(usuario.getId(), usuario.getEmail(), "RECUPERACION_SOLICITADA", "usuarios",
                    String.valueOf(usuario.getId()), ResultadoAuditoria.DENEGADO, "Solicitud repetida en menos de 1 minuto");
            return;
        }
        // Un solo enlace vigente por usuario: los anteriores quedan inutilizados.
        for (TokenRecuperacion previo : tokenRepository.findByUsuarioIdAndUsadoEnIsNull(usuario.getId())) {
            previo.setUsadoEn(ahora);
        }

        String token = generarToken();
        TokenRecuperacion registro = new TokenRecuperacion();
        registro.setUsuario(usuario);
        registro.setTokenHash(hash(token));
        registro.setExpiraEn(ahora.plus(Duration.ofMinutes(minutosVigencia)));
        tokenRepository.save(registro);

        notificador.enviarEnlace(usuario, urlBase + "/restablecer?token=" + token, minutosVigencia);
        auditoriaService.registrarComo(usuario.getId(), usuario.getEmail(), "RECUPERACION_SOLICITADA", "usuarios",
                String.valueOf(usuario.getId()), ResultadoAuditoria.EXITO, null);
    }

    @Transactional
    public void restablecer(String token, String nuevaPassword) {
        if (token == null || token.isBlank()) {
            throw new SolicitudInvalidaException("TOKEN_INVALIDO", MENSAJE_TOKEN);
        }
        if (!PoliticaPassword.esValida(nuevaPassword)) {
            throw new SolicitudInvalidaException("PASSWORD_DEBIL", PoliticaPassword.MENSAJE);
        }
        Instant ahora = clock.instant();
        TokenRecuperacion registro = tokenRepository.findByTokenHash(hash(token.trim()))
                .filter(t -> t.getUsadoEn() == null && t.getExpiraEn().isAfter(ahora))
                .orElseThrow(() -> {
                    auditoriaService.registrarComo(null, null, "RECUPERACION_RESTABLECIDA", "usuarios", null,
                            ResultadoAuditoria.FALLO, "Token inválido, usado o vencido");
                    return new SolicitudInvalidaException("TOKEN_INVALIDO", MENSAJE_TOKEN);
                });
        Usuario usuario = registro.getUsuario();
        if (!usuario.isActivo()) {
            throw new SolicitudInvalidaException("TOKEN_INVALIDO", MENSAJE_TOKEN);
        }
        usuario.setPasswordHash(passwordEncoder.encode(nuevaPassword));
        usuario.setIntentosFallidos(0);
        usuario.setBloqueadoHasta(null);
        usuarioRepository.save(usuario);
        registro.setUsadoEn(ahora);
        tokenRepository.save(registro);
        auditoriaService.registrarComo(usuario.getId(), usuario.getEmail(), "RECUPERACION_RESTABLECIDA", "usuarios",
                String.valueOf(usuario.getId()), ResultadoAuditoria.EXITO, null);
    }

    private String generarToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String token) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    private static String quitarBarraFinal(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
