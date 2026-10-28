package com.medigestsystem.consultorios;

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

import com.medigestsystem.consultorios.dto.ConsultorioRequest;
import com.medigestsystem.consultorios.dto.ConsultorioResponse;
import com.medigestsystem.shared.dto.EstadoRequest;

@RestController
@RequestMapping("/api/v1/consultorios")
@Tag(name = "Consultorios", description = "Consultorios físicos de la clínica")
@SecurityRequirement(name = "bearerAuth")
public class ConsultorioController {

    private final ConsultorioService consultorioService;

    public ConsultorioController(ConsultorioService consultorioService) {
        this.consultorioService = consultorioService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','RECEPCIONISTA','MEDICO')")
    @Operation(summary = "Lista los consultorios (por defecto solo los activos)")
    public List<ConsultorioResponse> listar(@RequestParam(defaultValue = "false") boolean incluirInactivos) {
        return consultorioService.listar(incluirInactivos);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','RECEPCIONISTA','MEDICO')")
    @Operation(summary = "Obtiene un consultorio")
    public ConsultorioResponse obtener(@PathVariable Integer id) {
        return consultorioService.obtener(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Registra un consultorio")
    public ResponseEntity<ConsultorioResponse> crear(@Valid @RequestBody ConsultorioRequest solicitud) {
        return ResponseEntity.status(HttpStatus.CREATED).body(consultorioService.crear(solicitud));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Modifica un consultorio")
    public ConsultorioResponse actualizar(@PathVariable Integer id, @Valid @RequestBody ConsultorioRequest solicitud) {
        return consultorioService.actualizar(id, solicitud);
    }

    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Activa o desactiva un consultorio")
    public ConsultorioResponse cambiarEstado(@PathVariable Integer id, @Valid @RequestBody EstadoRequest solicitud) {
        return consultorioService.cambiarEstado(id, solicitud.activo());
    }
}
