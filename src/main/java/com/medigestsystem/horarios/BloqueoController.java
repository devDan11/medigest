package com.medigestsystem.horarios;

import java.time.OffsetDateTime;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.medigestsystem.horarios.dto.BloqueoRequest;
import com.medigestsystem.horarios.dto.BloqueoResponse;
import com.medigestsystem.shared.dto.PaginaResponse;

@RestController
@RequestMapping("/api/v1/bloqueos")
@Tag(name = "Bloqueos", description = "Vacaciones, ausencias y mantenimientos de agenda")
@SecurityRequirement(name = "bearerAuth")
public class BloqueoController {

    private final BloqueoService bloqueoService;

    public BloqueoController(BloqueoService bloqueoService) {
        this.bloqueoService = bloqueoService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','RECEPCIONISTA','MEDICO')")
    @Operation(summary = "Lista bloqueos con filtros (el médico solo ve los suyos)")
    public PaginaResponse<BloqueoResponse> listar(
            @RequestParam(required = false) Long medicoId,
            @RequestParam(required = false) Integer consultorioId,
            @RequestParam(required = false) EstadoBloqueo estado,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime hasta,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        return bloqueoService.listar(medicoId, consultorioId, estado, desde, hasta, pagina, tamano);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','MEDICO')")
    @Operation(summary = "Crea un bloqueo (administrador) o solicita uno propio (médico)")
    public ResponseEntity<BloqueoResponse> crear(@Valid @RequestBody BloqueoRequest solicitud) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bloqueoService.crear(solicitud));
    }

    @PatchMapping("/{id}/aprobar")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Aprueba una solicitud de bloqueo")
    public BloqueoResponse aprobar(@PathVariable Long id) {
        return bloqueoService.aprobar(id);
    }

    @PatchMapping("/{id}/rechazar")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Rechaza una solicitud de bloqueo")
    public BloqueoResponse rechazar(@PathVariable Long id) {
        return bloqueoService.rechazar(id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','MEDICO')")
    @Operation(summary = "Elimina un bloqueo (el médico solo sus solicitudes pendientes)")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        bloqueoService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
