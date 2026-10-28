package com.medigestsystem.medicos;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.format.annotation.DateTimeFormat;
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

import com.medigestsystem.citas.dto.CitaResumenResponse;
import com.medigestsystem.medicos.dto.MedicoRequest;
import com.medigestsystem.medicos.dto.MedicoResponse;
import com.medigestsystem.medicos.dto.MedicoResumenResponse;
import com.medigestsystem.shared.dto.EstadoRequest;
import com.medigestsystem.shared.dto.PaginaResponse;

@RestController
@RequestMapping("/api/v1/medicos")
@Tag(name = "Médicos", description = "Gestión de médicos, especialidades y agenda")
@SecurityRequirement(name = "bearerAuth")
public class MedicoController {

    private final MedicoService medicoService;

    public MedicoController(MedicoService medicoService) {
        this.medicoService = medicoService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Lista médicos por nombre, colegiatura, especialidad y estado",
            description = "El personal ve todos los datos; el resto solo médicos activos sin datos de contacto.")
    public PaginaResponse<MedicoResponse> listar(@RequestParam(required = false) String q,
                                                 @RequestParam(required = false) Integer especialidadId,
                                                 @RequestParam(required = false) Boolean activo,
                                                 @RequestParam(defaultValue = "0") int pagina,
                                                 @RequestParam(defaultValue = "20") int tamano) {
        return medicoService.listar(q, especialidadId, activo, pagina, tamano);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Obtiene un médico")
    public MedicoResponse obtener(@PathVariable Long id) {
        return medicoService.obtener(id);
    }

    @GetMapping("/{id}/resumen")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','RECEPCIONISTA','MEDICO')")
    @Operation(summary = "Contadores de citas del médico (el médico solo ve el suyo)")
    public MedicoResumenResponse resumen(@PathVariable Long id) {
        return medicoService.resumen(id);
    }

    @GetMapping("/{id}/agenda")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','RECEPCIONISTA','MEDICO')")
    @Operation(summary = "Agenda del médico entre dos fechas (máximo 31 días)")
    public List<CitaResumenResponse> agenda(@PathVariable Long id,
                                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return medicoService.agenda(id, desde, hasta);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Registra un médico, opcionalmente con su cuenta de acceso")
    public ResponseEntity<MedicoResponse> crear(@Valid @RequestBody MedicoRequest solicitud) {
        return ResponseEntity.status(HttpStatus.CREATED).body(medicoService.crear(solicitud));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Actualiza los datos y especialidades de un médico")
    public MedicoResponse actualizar(@PathVariable Long id, @Valid @RequestBody MedicoRequest solicitud) {
        return medicoService.actualizar(id, solicitud);
    }

    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Activa o desactiva un médico (no se borra, se conserva su historial)")
    public MedicoResponse cambiarEstado(@PathVariable Long id, @Valid @RequestBody EstadoRequest solicitud) {
        return medicoService.cambiarEstado(id, solicitud.activo());
    }
}
