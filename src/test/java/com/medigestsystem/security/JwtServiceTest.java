package com.medigestsystem.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;

import com.medigestsystem.config.MediGestProperties;
import com.medigestsystem.usuarios.NombreRol;
import com.medigestsystem.testsupport.Fixtures;
import com.medigestsystem.usuarios.Usuario;

class JwtServiceTest {

    private static final String SECRETO = "clave-de-pruebas-con-mas-de-32-caracteres!";
    private static final Instant INICIO = Instant.parse("2026-10-27T15:00:00Z");

    private MutableClock reloj;
    private JwtService servicio;
    private Usuario usuario;

    @BeforeEach
    void preparar() {
        reloj = new MutableClock(INICIO);
        servicio = new JwtService(Fixtures.propiedades(SECRETO), reloj, new MockEnvironment());
        usuario = Fixtures.usuario(7L, "paciente@medigest.test", "hash-bcrypt-1", NombreRol.PACIENTE);
    }

    @Test
    void tokenDeAccesoSeValidaYLlevaIdentidadYRoles() {
        String token = servicio.emitirAcceso(usuario);

        Jwt jwt = servicio.decodificadorAcceso().decode(token);

        assertThat(jwt.getSubject()).isEqualTo("7");
        assertThat(jwt.getClaimAsString(JwtService.CLAIM_EMAIL)).isEqualTo("paciente@medigest.test");
        assertThat(jwt.getClaimAsStringList(JwtService.CLAIM_ROLES)).containsExactly("PACIENTE");
        assertThat(jwt.getExpiresAt()).isEqualTo(INICIO.plus(Duration.ofMinutes(15)));
    }

    @Test
    void tokenDeRefrescoNoSirveComoTokenDeAcceso() {
        String refresco = servicio.emitirRefresco(usuario);

        assertThatThrownBy(() -> servicio.decodificadorAcceso().decode(refresco)).isInstanceOf(JwtException.class);
        assertThat(servicio.decodificarRefresco(refresco).getSubject()).isEqualTo("7");
    }

    @Test
    void tokenDeAccesoNoSirveComoTokenDeRefresco() {
        String acceso = servicio.emitirAcceso(usuario);

        assertThatThrownBy(() -> servicio.decodificarRefresco(acceso)).isInstanceOf(JwtException.class);
    }

    @Test
    void tokenVencidoEsRechazado() {
        String token = servicio.emitirAcceso(usuario);

        reloj.avanzar(Duration.ofMinutes(16));

        assertThatThrownBy(() -> servicio.decodificadorAcceso().decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void tokenConFirmaAlteradaEsRechazado() {
        String token = servicio.emitirAcceso(usuario);
        String[] partes = token.split("\\.");
        // Cambia el contenido (payload) conservando la firma original.
        String payloadAlterado = partes[1].substring(0, partes[1].length() - 2) + "AA";
        String adulterado = partes[0] + "." + payloadAlterado + "." + partes[2];

        assertThatThrownBy(() -> servicio.decodificadorAcceso().decode(adulterado)).isInstanceOf(JwtException.class);
    }

    @Test
    void tokenFirmadoConOtraClaveEsRechazado() {
        JwtService otro = new JwtService(Fixtures.propiedades("otra-clave-distinta-de-mas-de-32-caracteres"),
                reloj, new MockEnvironment());

        String ajeno = otro.emitirAcceso(usuario);

        assertThatThrownBy(() -> servicio.decodificadorAcceso().decode(ajeno)).isInstanceOf(JwtException.class);
    }

    @Test
    void textoQueNoEsUnTokenEsRechazado() {
        assertThatThrownBy(() -> servicio.decodificadorAcceso().decode("esto-no-es-un-jwt"))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void laHuellaDeCredencialCambiaCuandoCambiaLaContrasena() {
        String refrescoViejo = servicio.emitirRefresco(usuario);
        usuario.setPasswordHash("hash-bcrypt-2");
        String refrescoNuevo = servicio.emitirRefresco(usuario);

        String huellaVieja = servicio.decodificarRefresco(refrescoViejo).getClaimAsString(JwtService.CLAIM_CREDENCIAL);
        String huellaNueva = servicio.decodificarRefresco(refrescoNuevo).getClaimAsString(JwtService.CLAIM_CREDENCIAL);

        assertThat(huellaVieja).isNotEqualTo(huellaNueva);
        assertThat(huellaNueva).isEqualTo(JwtService.huellaCredencial("hash-bcrypt-2"));
    }

    @Test
    void claveCortaEsRechazadaAlArrancar() {
        assertThatThrownBy(() -> new JwtService(Fixtures.propiedades("corta"), reloj, new MockEnvironment()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void enProduccionLaClaveEsObligatoria() {
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");

        assertThatThrownBy(() -> new JwtService(Fixtures.propiedades(""), reloj, prod))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    void fueraDeProduccionSinClaveSeGeneraUnaEfimera() {
        JwtService efimero = new JwtService(Fixtures.propiedades(""), reloj, new MockEnvironment());

        String token = efimero.emitirAcceso(usuario);

        assertThat(efimero.decodificadorAcceso().decode(token).getSubject()).isEqualTo("7");
    }

    /** Reloj controlable para probar vencimientos sin esperar. */
    static final class MutableClock extends Clock {
        private Instant ahora;

        MutableClock(Instant inicio) {
            this.ahora = inicio;
        }

        void avanzar(Duration duracion) {
            ahora = ahora.plus(duracion);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return ahora;
        }
    }
}
