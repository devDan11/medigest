package com.medigestsystem.pacientes;

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

import com.medigestsystem.citas.dto.CitaResumenResponse;
import com.medigestsystem.pacientes.dto.PacienteRequest;
import com.medigestsystem.pacientes.dto.PacienteResponse;
import com.medigestsystem.pacientes.dto.PacienteResumenResponse;
import com.medigestsystem.pacientes.dto.PerfilPacienteRequest;
import com.medigestsystem.shared.dto.EstadoRequest;
import com.medigestsystem.shared.dto.PaginaResponse;

@RestController
@RequestMapping("/api/v1/pacientes")
@Tag(name = "Pacientes", description = "Registro y consulta de pacientes")
@SecurityRequirement(name = "bearerAuth")
public class PacienteController {

    private final PacienteService pacienteService;

    public PacienteController(PacienteService pacienteService) {
        this.pacienteService = pacienteService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','RECEPCIONISTA')")
    @Operation(summary = "Busca pacientes por nombre o documento, con filtro de estado y paginación")
    public PaginaResponse<PacienteResponse> buscar(@RequestParam(required = false) String q,
                                                   @RequestParam(required = false) Boolean activo,
                                                   @RequestParam(defaultValue = "0") int pagina,
                                                   @RequestParam(defaultValue = "20") int tamano) {
        return pacienteService.buscar(q, activo, pagina, tamano);
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('PACIENTE')")
    @Operation(summary = "Ficha del paciente autenticado")
    public PacienteResponse miFicha() {
        return pacienteService.obtenerPropio();
    }

    @PutMapping("/me")
    @PreAuthorize("hasRole('PACIENTE')")
    @Operation(summary = "El paciente actualiza sus datos de contacto")
    public PacienteResponse actualizarMiFicha(@Valid @RequestBody PerfilPacienteRequest solicitud) {
        return pacienteService.actualizarPerfil(solicitud);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Obtiene un paciente",
            description = "Personal: cualquiera. Médico: solo pacientes con citas suyas (vista reducida). Paciente: solo el propio.")
    public PacienteResponse obtener(@PathVariable Long id) {
        return pacienteService.obtener(id);
    }

    @GetMapping("/{id}/resumen")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Citas atendidas, canceladas, pendientes y próxima cita del paciente")
    public PacienteResumenResponse resumen(@PathVariable Long id) {
        return pacienteService.resumen(id);
    }

    @GetMapping("/{id}/citas")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Historial de citas del paciente (más recientes primero)")
    public PaginaResponse<CitaResumenResponse> historial(@PathVariable Long id,
                                                         @RequestParam(defaultValue = "0") int pagina,
                                                         @RequestParam(defaultValue = "20") int tamano) {
        return pacienteService.historial(id, pagina, tamano);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','RECEPCIONISTA')")
    @Operation(summary = "Registra un paciente")
    public ResponseEntity<PacienteResponse> crear(@Valid @RequestBody PacienteRequest solicitud) {
        return ResponseEntity.status(HttpStatus.CREATED).body(pacienteService.crear(solicitud));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','RECEPCIONISTA')")
    @Operation(summary = "Actualiza los datos de un paciente")
    public PacienteResponse actualizar(@PathVariable Long id, @Valid @RequestBody PacienteRequest solicitud) {
        return pacienteService.actualizar(id, solicitud);
    }

    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Activa o desactiva un paciente sin perder su historial")
    public PacienteResponse cambiarEstado(@PathVariable Long id, @Valid @RequestBody EstadoRequest solicitud) {
        return pacienteService.cambiarEstado(id, solicitud.activo());
    }
}
