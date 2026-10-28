package com.medigestsystem.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.medigestsystem.auditoria.AuditoriaService;
import com.medigestsystem.shared.exception.SolicitudInvalidaException;
import com.medigestsystem.testsupport.Fixtures;
import com.medigestsystem.usuarios.NombreRol;
import com.medigestsystem.usuarios.Usuario;
import com.medigestsystem.usuarios.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RecuperacionServiceTest {

    private static final Instant AHORA = Instant.parse("2026-10-27T15:00:00Z");

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private TokenRecuperacionRepository tokenRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private RecuperacionNotificador notificador;
    @Mock
    private AuditoriaService auditoriaService;

    private RecuperacionService servicio;
    private Usuario usuario;

    @BeforeEach
    void preparar() {
        servicio = new RecuperacionService(usuarioRepository, tokenRepository, passwordEncoder, notificador,
                auditoriaService, Clock.fixed(AHORA, ZoneOffset.UTC), Fixtures.propiedades(Fixtures.SECRETO_PRUEBAS));
        usuario = Fixtures.usuario(8L, "ana@correo.com", "hash-viejo", NombreRol.PACIENTE);
        when(usuarioRepository.findByEmailIgnoreCase("ana@correo.com")).thenReturn(Optional.of(usuario));
    }

    @Test
    void unCorreoDesconocidoNoGeneraTokenNiNotificaNiFalla() {
        when(usuarioRepository.findByEmailIgnoreCase("nadie@correo.com")).thenReturn(Optional.empty());

        servicio.solicitar("nadie@correo.com");

        verify(tokenRepository, never()).save(any());
        verify(notificador, never()).enviarEnlace(any(), any(), anyInt());
    }

    @Test
    void guardaSoloElHashDelTokenYEntregaElEnlaceConElTokenOriginal() {
        TokenRecuperacion previo = new TokenRecuperacion();
        when(tokenRepository.findByUsuarioIdAndUsadoEnIsNull(8L)).thenReturn(List.of(previo));

        servicio.solicitar("  Ana@Correo.com ");

        ArgumentCaptor<TokenRecuperacion> guardado = ArgumentCaptor.forClass(TokenRecuperacion.class);
        verify(tokenRepository).save(guardado.capture());
        ArgumentCaptor<String> enlace = ArgumentCaptor.forClass(String.class);
        verify(notificador).enviarEnlace(eq(usuario), enlace.capture(), eq(30));

        String prefijo = "http://localhost:8080/restablecer?token=";
        assertThat(enlace.getValue()).startsWith(prefijo);
        String tokenOriginal = enlace.getValue().substring(prefijo.length());
        assertThat(tokenOriginal).hasSizeGreaterThanOrEqualTo(40);
        assertThat(guardado.getValue().getTokenHash())
                .hasSize(64)
                .isNotEqualTo(tokenOriginal)
                .isEqualTo(RecuperacionService.hash(tokenOriginal));
        assertThat(guardado.getValue().getExpiraEn()).isEqualTo(AHORA.plus(Duration.ofMinutes(30)));
        assertThat(previo.getUsadoEn()).as("el enlace anterior queda inutilizado").isEqualTo(AHORA);
    }

    @Test
    void ignoraUnaSegundaSolicitudEnMenosDeUnMinuto() {
        when(tokenRepository.existsByUsuarioIdAndCreadoEnAfter(eq(8L), any())).thenReturn(true);

        servicio.solicitar("ana@correo.com");

        verify(tokenRepository, never()).save(any());
        verify(notificador, never()).enviarEnlace(any(), any(), anyInt());
    }

    @Test
    void restablecerConUnTokenValidoCambiaLaClaveDesbloqueaYConsumeElToken() {
        TokenRecuperacion registro = registroValido("token-de-prueba");
        usuario.setIntentosFallidos(3);
        usuario.setBloqueadoHasta(AHORA.plusSeconds(300));
        when(passwordEncoder.encode("Nueva2026")).thenReturn("hash-nuevo");

        servicio.restablecer("token-de-prueba", "Nueva2026");

        assertThat(usuario.getPasswordHash()).isEqualTo("hash-nuevo");
        assertThat(usuario.getIntentosFallidos()).isZero();
        assertThat(usuario.getBloqueadoHasta()).isNull();
        assertThat(registro.getUsadoEn()).isEqualTo(AHORA);
    }

    @Test
    void unTokenYaUsadoNoSirveDeNuevo() {
        TokenRecuperacion registro = registroValido("token-usado");
        registro.setUsadoEn(AHORA.minusSeconds(60));

        assertThatThrownBy(() -> servicio.restablecer("token-usado", "Nueva2026"))
                .isInstanceOf(SolicitudInvalidaException.class);
        assertThat(usuario.getPasswordHash()).isEqualTo("hash-viejo");
    }

    @Test
    void unTokenVencidoNoSirve() {
        TokenRecuperacion registro = registroValido("token-vencido");
        registro.setExpiraEn(AHORA.minusSeconds(1));

        assertThatThrownBy(() -> servicio.restablecer("token-vencido", "Nueva2026"))
                .isInstanceOf(SolicitudInvalidaException.class);
        assertThat(usuario.getPasswordHash()).isEqualTo("hash-viejo");
    }

    @Test
    void unTokenInexistenteNoSirve() {
        assertThatThrownBy(() -> servicio.restablecer("inventado", "Nueva2026"))
                .isInstanceOf(SolicitudInvalidaException.class);
    }

    @Test
    void rechazaUnaContrasenaQueNoCumpleLaPolitica() {
        registroValido("token-ok");

        assertThatThrownBy(() -> servicio.restablecer("token-ok", "corta"))
                .isInstanceOf(SolicitudInvalidaException.class);
        assertThat(usuario.getPasswordHash()).isEqualTo("hash-viejo");
    }

    @Test
    void unaCuentaDesactivadaNoPuedeRestablecer() {
        registroValido("token-ok");
        usuario.setActivo(false);

        assertThatThrownBy(() -> servicio.restablecer("token-ok", "Nueva2026"))
                .isInstanceOf(SolicitudInvalidaException.class);
    }

    private TokenRecuperacion registroValido(String tokenPlano) {
        TokenRecuperacion registro = new TokenRecuperacion();
        registro.setUsuario(usuario);
        registro.setTokenHash(RecuperacionService.hash(tokenPlano));
        registro.setExpiraEn(AHORA.plus(Duration.ofMinutes(20)));
        when(tokenRepository.findByTokenHash(RecuperacionService.hash(tokenPlano))).thenReturn(Optional.of(registro));
        return registro;
    }
}
