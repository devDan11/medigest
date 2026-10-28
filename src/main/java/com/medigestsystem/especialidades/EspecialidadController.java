package com.medigestsystem.especialidades;

import java.util.List;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.HttpStatus;
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

import com.medigestsystem.especialidades.dto.EspecialidadRequest;
import com.medigestsystem.especialidades.dto.EspecialidadResponse;
import com.medigestsystem.shared.dto.EstadoRequest;

@RestController
@RequestMapping("/api/v1/especialidades")
@Tag(name = "Especialidades", description = "Catálogo de especialidades médicas")
@SecurityRequirement(name = "bearerAuth")
public class EspecialidadController {

    private final EspecialidadService especialidadService;

    public EspecialidadController(EspecialidadService especialidadService) {
        this.especialidadService = especialidadService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Lista las especialidades (los administradores pueden incluir las inactivas)")
    public List<EspecialidadResponse> listar(@RequestParam(defaultValue = "false") boolean incluirInactivas) {
        return especialidadService.listar(incluirInactivas);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Obtiene una especialidad")
    public EspecialidadResponse obtener(@PathVariable Integer id) {
        return especialidadService.obtener(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Crea una especialidad")
    public ResponseEntity<EspecialidadResponse> crear(@Valid @RequestBody EspecialidadRequest solicitud) {
        return ResponseEntity.status(HttpStatus.CREATED).body(especialidadService.crear(solicitud));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Modifica una especialidad")
    public EspecialidadResponse actualizar(@PathVariable Integer id, @Valid @RequestBody EspecialidadRequest solicitud) {
        return especialidadService.actualizar(id, solicitud);
    }

    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Activa o desactiva una especialidad (no borra historial)")
    public EspecialidadResponse cambiarEstado(@PathVariable Integer id, @Valid @RequestBody EstadoRequest solicitud) {
        return especialidadService.cambiarEstado(id, solicitud.activo());
    }
}
