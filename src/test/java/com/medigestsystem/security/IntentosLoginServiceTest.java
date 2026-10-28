package com.medigestsystem.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.medigestsystem.auditoria.AuditoriaService;
import com.medigestsystem.auditoria.ResultadoAuditoria;
import com.medigestsystem.testsupport.Fixtures;
import com.medigestsystem.usuarios.NombreRol;
import com.medigestsystem.usuarios.Usuario;
import com.medigestsystem.usuarios.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IntentosLoginServiceTest {

    private static final Instant AHORA = Instant.parse("2026-10-27T15:00:00Z");

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private AuditoriaService auditoriaService;

    private IntentosLoginService servicio;
    private Usuario usuario;

    @BeforeEach
    void preparar() {
        Clock reloj = Clock.fixed(AHORA, ZoneOffset.UTC);
        servicio = new IntentosLoginService(usuarioRepository, auditoriaService, reloj,
                Fixtures.propiedades(Fixtures.SECRETO_PRUEBAS));
        usuario = Fixtures.usuario(3L, "medico@medigest.test", "hash", NombreRol.MEDICO);
        when(usuarioRepository.findByEmailIgnoreCase("medico@medigest.test")).thenReturn(Optional.of(usuario));
    }

    @Test
    void cadaFalloSumaUnIntentoSinBloquearAntesDelLimite() {
        servicio.registrarFallo("medico@medigest.test");
        servicio.registrarFallo("medico@medigest.test");

        assertThat(usuario.getIntentosFallidos()).isEqualTo(2);
        assertThat(usuario.getBloqueadoHasta()).isNull();
    }

    @Test
    void elQuintoFalloBloqueaLaCuentaQuinceMinutos() {
        for (int i = 0; i < 5; i++) {
            servicio.registrarFallo("medico@medigest.test");
        }

        assertThat(usuario.getBloqueadoHasta()).isEqualTo(AHORA.plus(Duration.ofMinutes(15)));
        assertThat(usuario.getIntentosFallidos()).isZero();
        verify(auditoriaService).registrarComo(eq(3L), eq("medico@medigest.test"), eq("CUENTA_BLOQUEADA"),
                eq("usuarios"), eq("3"), eq(ResultadoAuditoria.DENEGADO), any());
    }

    @Test
    void unIntentoDuranteElBloqueoNoSumaMasFallos() {
        usuario.setBloqueadoHasta(AHORA.plus(Duration.ofMinutes(5)));

        servicio.registrarFallo("medico@medigest.test");

        assertThat(usuario.getIntentosFallidos()).isZero();
        assertThat(usuario.getBloqueadoHasta()).isEqualTo(AHORA.plus(Duration.ofMinutes(5)));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void conBloqueoVencidoElConteoEmpiezaDeCero() {
        usuario.setBloqueadoHasta(AHORA.minus(Duration.ofMinutes(1)));
        usuario.setIntentosFallidos(4);

        servicio.registrarFallo("medico@medigest.test");

        assertThat(usuario.getBloqueadoHasta()).isNull();
        assertThat(usuario.getIntentosFallidos()).isEqualTo(1);
    }

    @Test
    void unInicioDeSesionExitosoReiniciaElConteoYRegistraElAcceso() {
        usuario.setIntentosFallidos(3);

        servicio.registrarExito("medico@medigest.test");

        assertThat(usuario.getIntentosFallidos()).isZero();
        assertThat(usuario.getBloqueadoHasta()).isNull();
        assertThat(usuario.getUltimoAcceso()).isEqualTo(AHORA);
        verify(auditoriaService).registrarComo(eq(3L), eq("medico@medigest.test"), eq("LOGIN_EXITO"),
                eq("usuarios"), eq("3"), eq(ResultadoAuditoria.EXITO), any());
    }

    @Test
    void unCorreoInexistenteSeAuditaSinFallar() {
        when(usuarioRepository.findByEmailIgnoreCase("nadie@medigest.test")).thenReturn(Optional.empty());

        assertThatCode(() -> servicio.registrarFallo("nadie@medigest.test")).doesNotThrowAnyException();

        verify(auditoriaService).registrarComo(isNull(), eq("nadie@medigest.test"), eq("LOGIN_FALLIDO"),
                eq("usuarios"), isNull(), eq(ResultadoAuditoria.FALLO), any());
        verify(usuarioRepository, never()).save(any());
    }
}
