package com.medigestsystem.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

import com.medigestsystem.config.MediGestProperties;
import com.medigestsystem.usuarios.Usuario;

/**
 * Emisión y validación de tokens JWT firmados con HS256.
 * <ul>
 *   <li>Token de acceso: corta vigencia, lleva el correo y los roles; es el único aceptado en {@code /api/**}.</li>
 *   <li>Token de refresco: larga vigencia, solo sirve en {@code /auth/refresh}. Incluye una huella de la
 *       contraseña vigente, de modo que un cambio de contraseña invalida los refrescos anteriores.</li>
 * </ul>
 */
@Service
public class JwtService {

    public static final String CLAIM_TIPO = "tipo";
    public static final String CLAIM_ROLES = "roles";
    public static final String CLAIM_EMAIL = "email";
    public static final String CLAIM_CREDENCIAL = "cred";
    public static final String TIPO_ACCESO = "acceso";
    public static final String TIPO_REFRESCO = "refresco";

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);
    private static final int LONGITUD_MINIMA_SECRETO = 32;
    private static final Duration TOLERANCIA_RELOJ = Duration.ofSeconds(10);

    private final SecretKey clave;
    private final JwtEncoder encoder;
    private final NimbusJwtDecoder decoderAcceso;
    private final NimbusJwtDecoder decoderRefresco;
    private final Clock clock;
    private final String emisor;
    private final Duration vigenciaAcceso;
    private final Duration vigenciaRefresco;

    public JwtService(MediGestProperties properties, Clock clock, Environment environment) {
        MediGestProperties.Jwt cfg = properties.seguridad().jwt();
        this.clock = clock;
        this.emisor = cfg.emisor();
        this.vigenciaAcceso = Duration.ofMinutes(cfg.accesoMinutos());
        this.vigenciaRefresco = Duration.ofMinutes(cfg.refrescoMinutos());
        this.clave = new SecretKeySpec(resolverClave(cfg.secreto(), environment), "HmacSHA256");
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(clave));
        this.decoderAcceso = crearDecoder(TIPO_ACCESO);
        this.decoderRefresco = crearDecoder(TIPO_REFRESCO);
    }

    /** Decodificador para el servidor de recursos (solo acepta tokens de acceso). */
    public JwtDecoder decodificadorAcceso() {
        return decoderAcceso;
    }

    public String emitirAcceso(Usuario usuario) {
        Instant ahora = clock.instant();
        List<String> roles = usuario.getRoles().stream().map(r -> r.getNombre().name()).sorted().toList();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(emisor)
                .subject(String.valueOf(usuario.getId()))
                .issuedAt(ahora)
                .expiresAt(ahora.plus(vigenciaAcceso))
                .id(UUID.randomUUID().toString())
                .claim(CLAIM_EMAIL, usuario.getEmail())
                .claim(CLAIM_ROLES, roles)
                .claim(CLAIM_TIPO, TIPO_ACCESO)
                .build();
        return firmar(claims);
    }

    public String emitirRefresco(Usuario usuario) {
        Instant ahora = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(emisor)
                .subject(String.valueOf(usuario.getId()))
                .issuedAt(ahora)
                .expiresAt(ahora.plus(vigenciaRefresco))
                .id(UUID.randomUUID().toString())
                .claim(CLAIM_CREDENCIAL, huellaCredencial(usuario.getPasswordHash()))
                .claim(CLAIM_TIPO, TIPO_REFRESCO)
                .build();
        return firmar(claims);
    }

    /** Valida firma, vigencia, emisor y tipo. Lanza {@link org.springframework.security.oauth2.jwt.JwtException}. */
    public Jwt decodificarRefresco(String token) {
        return decoderRefresco.decode(token);
    }

    public long segundosVigenciaAcceso() {
        return vigenciaAcceso.toSeconds();
    }

    /** Huella corta de la contraseña vigente; cambia cuando cambia la contraseña. */
    public static String huellaCredencial(String passwordHash) {
        return sha256Hex(passwordHash == null ? "" : passwordHash).substring(0, 16);
    }

    static String sha256Hex(String texto) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    private String firmar(JwtClaimsSet claims) {
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private NimbusJwtDecoder crearDecoder(String tipoEsperado) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(clave).macAlgorithm(MacAlgorithm.HS256).build();
        JwtTimestampValidator tiempo = new JwtTimestampValidator(TOLERANCIA_RELOJ);
        tiempo.setClock(clock);
        OAuth2TokenValidator<Jwt> tipo = jwt -> tipoEsperado.equals(jwt.getClaimAsString(CLAIM_TIPO))
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Tipo de token no válido", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(tiempo, new JwtIssuerValidator(emisor), tipo));
        return decoder;
    }

    private static byte[] resolverClave(String secreto, Environment environment) {
        if (secreto == null || secreto.isBlank()) {
            if (environment.acceptsProfiles(Profiles.of("prod"))) {
                throw new IllegalStateException(
                        "Falta la clave JWT. Defina la variable de entorno JWT_SECRET (mínimo 32 caracteres).");
            }
            log.warn("JWT_SECRET no está definido: se genera una clave efímera. "
                    + "Los tokens dejarán de ser válidos al reiniciar la aplicación.");
            byte[] aleatoria = new byte[48];
            new SecureRandom().nextBytes(aleatoria);
            return aleatoria;
        }
        byte[] bytes = secreto.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < LONGITUD_MINIMA_SECRETO) {
            throw new IllegalStateException("La clave JWT debe tener al menos " + LONGITUD_MINIMA_SECRETO
                    + " caracteres.");
        }
        return bytes;
    }
}
