package com.medigestsystem.usuarios;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.medigestsystem.security.SecurityUtils;
import com.medigestsystem.shared.dto.PaginaResponse;
import com.medigestsystem.usuarios.dto.EstadoUsuarioRequest;
import com.medigestsystem.usuarios.dto.RolesUsuarioRequest;
import com.medigestsystem.usuarios.dto.UsuarioResponse;

@RestController
@RequestMapping("/api/v1/usuarios")
@PreAuthorize("hasRole('ADMINISTRADOR')")
@Tag(name = "Usuarios", description = "Administración de cuentas (solo ADMINISTRADOR)")
@SecurityRequirement(name = "bearerAuth")
public class UsuarioAdminController {

    private final UsuarioAdminService usuarioAdminService;

    public UsuarioAdminController(UsuarioAdminService usuarioAdminService) {
        this.usuarioAdminService = usuarioAdminService;
    }

    @GetMapping
    @Operation(summary = "Lista usuarios con búsqueda por correo y paginación")
    public PaginaResponse<UsuarioResponse> listar(@RequestParam(required = false) String q,
                                                  @RequestParam(defaultValue = "0") int pagina,
                                                  @RequestParam(defaultValue = "20") int tamano) {
        return usuarioAdminService.listar(q, pagina, tamano);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtiene un usuario por id")
    public UsuarioResponse obtener(@PathVariable Long id) {
        return usuarioAdminService.obtener(id);
    }

    @PatchMapping("/{id}/estado")
    @Operation(summary = "Activa o desactiva una cuenta")
    public UsuarioResponse cambiarEstado(@PathVariable Long id, @Valid @RequestBody EstadoUsuarioRequest solicitud) {
        return usuarioAdminService.cambiarEstado(id, solicitud.activo(), actorId());
    }

    @PostMapping("/{id}/desbloquear")
    @Operation(summary = "Levanta el bloqueo temporal por intentos fallidos")
    public ResponseEntity<UsuarioResponse> desbloquear(@PathVariable Long id) {
        return ResponseEntity.ok(usuarioAdminService.desbloquear(id));
    }

    @PutMapping("/{id}/roles")
    @Operation(summary = "Reemplaza los roles de un usuario")
    public UsuarioResponse asignarRoles(@PathVariable Long id, @Valid @RequestBody RolesUsuarioRequest solicitud) {
        return usuarioAdminService.asignarRoles(id, solicitud.roles(), actorId());
    }

    private Long actorId() {
        return SecurityUtils.usuarioIdActual().orElse(null);
    }
}
