package com.medigestsystem.usuarios;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medigestsystem.auditoria.AuditoriaService;
import com.medigestsystem.auditoria.ResultadoAuditoria;
import com.medigestsystem.shared.dto.PaginaResponse;
import com.medigestsystem.shared.exception.RecursoNoEncontradoException;
import com.medigestsystem.shared.exception.ReglaNegocioException;
import com.medigestsystem.usuarios.dto.UsuarioResponse;

/** Administración de cuentas de usuario (solo rol ADMINISTRADOR). */
@Service
public class UsuarioAdminService {

    private static final String ENTIDAD = "usuarios";
    private static final int TAMANO_MAXIMO = 100;

    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final AuditoriaService auditoriaService;

    public UsuarioAdminService(UsuarioRepository usuarioRepository, RolRepository rolRepository,
                               AuditoriaService auditoriaService) {
        this.usuarioRepository = usuarioRepository;
        this.rolRepository = rolRepository;
        this.auditoriaService = auditoriaService;
    }

    @Transactional(readOnly = true)
    public PaginaResponse<UsuarioResponse> listar(String texto, int pagina, int tamano) {
        int tamanoSeguro = Math.min(Math.max(tamano, 1), TAMANO_MAXIMO);
        PageRequest pageable = PageRequest.of(Math.max(pagina, 0), tamanoSeguro, Sort.by("email").ascending());
        String filtro = texto == null ? "" : texto.trim();
        Page<Usuario> resultado = usuarioRepository.findByEmailContainingIgnoreCase(filtro, pageable);
        return PaginaResponse.de(resultado.map(UsuarioResponse::de));
    }

    @Transactional(readOnly = true)
    public UsuarioResponse obtener(Long id) {
        return UsuarioResponse.de(buscar(id));
    }

    @Transactional
    public UsuarioResponse cambiarEstado(Long id, boolean activo, Long actorId) {
        Usuario usuario = buscar(id);
        if (!activo) {
            if (id.equals(actorId)) {
                throw new ReglaNegocioException("AUTODESACTIVACION", "No puede desactivar su propia cuenta.");
            }
            if (esUltimoAdministradorActivo(usuario)) {
                throw new ReglaNegocioException("ULTIMO_ADMINISTRADOR",
                        "No se puede desactivar al último administrador activo.");
            }
        }
        usuario.setActivo(activo);
        usuarioRepository.save(usuario);
        auditoriaService.registrar(activo ? "USUARIO_ACTIVADO" : "USUARIO_DESACTIVADO", ENTIDAD,
                String.valueOf(id), ResultadoAuditoria.EXITO, null);
        return UsuarioResponse.de(usuario);
    }

    @Transactional
    public UsuarioResponse desbloquear(Long id) {
        Usuario usuario = buscar(id);
        usuario.setBloqueadoHasta(null);
        usuario.setIntentosFallidos(0);
        usuarioRepository.save(usuario);
        auditoriaService.registrar("USUARIO_DESBLOQUEADO", ENTIDAD, String.valueOf(id),
                ResultadoAuditoria.EXITO, null);
        return UsuarioResponse.de(usuario);
    }

    @Transactional
    public UsuarioResponse asignarRoles(Long id, Set<NombreRol> nuevosRoles, Long actorId) {
        if (nuevosRoles == null || nuevosRoles.isEmpty()) {
            throw new ReglaNegocioException("ROLES_VACIOS", "Debe indicar al menos un rol.");
        }
        Usuario usuario = buscar(id);
        boolean eraAdmin = tieneRol(usuario, NombreRol.ADMINISTRADOR);
        boolean seraAdmin = nuevosRoles.contains(NombreRol.ADMINISTRADOR);
        if (eraAdmin && !seraAdmin) {
            if (id.equals(actorId)) {
                throw new ReglaNegocioException("AUTODEGRADACION",
                        "No puede quitarse a sí mismo el rol de administrador.");
            }
            if (esUltimoAdministradorActivo(usuario)) {
                throw new ReglaNegocioException("ULTIMO_ADMINISTRADOR",
                        "No se puede quitar el rol al último administrador activo.");
            }
        }
        Map<NombreRol, Rol> catalogo = new EnumMap<>(NombreRol.class);
        rolRepository.findAll().forEach(r -> catalogo.put(r.getNombre(), r));
        Set<Rol> asignados = new HashSet<>();
        for (NombreRol nombre : nuevosRoles) {
            Rol rol = catalogo.get(nombre);
            if (rol == null) {
                throw new ReglaNegocioException("ROL_INEXISTENTE", "El rol " + nombre + " no existe.");
            }
            asignados.add(rol);
        }
        usuario.setRoles(asignados);
        usuarioRepository.save(usuario);
        auditoriaService.registrar("ROLES_ACTUALIZADOS", ENTIDAD, String.valueOf(id), ResultadoAuditoria.EXITO,
                "Roles: " + nuevosRoles);
        return UsuarioResponse.de(usuario);
    }

    private Usuario buscar(Long id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el usuario solicitado."));
    }

    private boolean esUltimoAdministradorActivo(Usuario usuario) {
        return usuario.isActivo()
                && tieneRol(usuario, NombreRol.ADMINISTRADOR)
                && usuarioRepository.countByRolesNombreAndActivoTrue(NombreRol.ADMINISTRADOR) <= 1;
    }

    private static boolean tieneRol(Usuario usuario, NombreRol nombre) {
        return usuario.getRoles().stream().anyMatch(r -> r.getNombre() == nombre);
    }
}
