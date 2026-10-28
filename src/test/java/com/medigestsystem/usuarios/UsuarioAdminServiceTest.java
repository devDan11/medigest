package com.medigestsystem.usuarios;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.medigestsystem.auditoria.AuditoriaService;
import com.medigestsystem.shared.exception.RecursoNoEncontradoException;
import com.medigestsystem.shared.exception.ReglaNegocioException;
import com.medigestsystem.testsupport.Fixtures;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UsuarioAdminServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private RolRepository rolRepository;
    @Mock
    private AuditoriaService auditoriaService;

    private UsuarioAdminService servicio;
    private Usuario admin;
    private Usuario medico;

    @BeforeEach
    void preparar() {
        servicio = new UsuarioAdminService(usuarioRepository, rolRepository, auditoriaService);
        admin = Fixtures.usuario(1L, "admin@medigest.test", "h", NombreRol.ADMINISTRADOR);
        medico = Fixtures.usuario(2L, "medico@medigest.test", "h", NombreRol.MEDICO);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(medico));
        when(usuarioRepository.countByRolesNombreAndActivoTrue(NombreRol.ADMINISTRADOR)).thenReturn(2L);
        when(rolRepository.findAll()).thenReturn(List.of(
                Fixtures.rol(NombreRol.ADMINISTRADOR), Fixtures.rol(NombreRol.MEDICO),
                Fixtures.rol(NombreRol.RECEPCIONISTA), Fixtures.rol(NombreRol.PACIENTE)));
    }

    @Test
    void desactivaUnaCuentaAjena() {
        servicio.cambiarEstado(2L, false, 1L);

        assertThat(medico.isActivo()).isFalse();
        verify(usuarioRepository).save(medico);
    }

    @Test
    void unAdministradorNoPuedeDesactivarseASiMismo() {
        assertThatThrownBy(() -> servicio.cambiarEstado(1L, false, 1L)).isInstanceOf(ReglaNegocioException.class);
        assertThat(admin.isActivo()).isTrue();
    }

    @Test
    void noSePuedeDesactivarAlUltimoAdministradorActivo() {
        when(usuarioRepository.countByRolesNombreAndActivoTrue(NombreRol.ADMINISTRADOR)).thenReturn(1L);

        assertThatThrownBy(() -> servicio.cambiarEstado(1L, false, 2L)).isInstanceOf(ReglaNegocioException.class);
        assertThat(admin.isActivo()).isTrue();
    }

    @Test
    void desbloquearLimpiaElBloqueoYLosIntentos() {
        medico.setBloqueadoHasta(java.time.Instant.parse("2030-01-01T00:00:00Z"));
        medico.setIntentosFallidos(4);

        servicio.desbloquear(2L);

        assertThat(medico.getBloqueadoHasta()).isNull();
        assertThat(medico.getIntentosFallidos()).isZero();
    }

    @Test
    void asignaRolesNuevos() {
        servicio.asignarRoles(2L, Set.of(NombreRol.MEDICO, NombreRol.RECEPCIONISTA), 1L);

        assertThat(medico.getRoles()).extracting(Rol::getNombre)
                .containsExactlyInAnyOrder(NombreRol.MEDICO, NombreRol.RECEPCIONISTA);
    }

    @Test
    void rechazaUnConjuntoDeRolesVacio() {
        assertThatThrownBy(() -> servicio.asignarRoles(2L, Set.of(), 1L)).isInstanceOf(ReglaNegocioException.class);
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void unAdministradorNoPuedeQuitarseSuPropioRol() {
        assertThatThrownBy(() -> servicio.asignarRoles(1L, Set.of(NombreRol.MEDICO), 1L))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    void noSePuedeQuitarElRolAlUltimoAdministrador() {
        when(usuarioRepository.countByRolesNombreAndActivoTrue(NombreRol.ADMINISTRADOR)).thenReturn(1L);

        assertThatThrownBy(() -> servicio.asignarRoles(1L, Set.of(NombreRol.RECEPCIONISTA), 2L))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    void unUsuarioInexistenteDevuelveNoEncontrado() {
        when(usuarioRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.obtener(99L)).isInstanceOf(RecursoNoEncontradoException.class);
    }
}
