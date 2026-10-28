package com.medigestsystem.citas;

import java.time.OffsetDateTime;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.medigestsystem.citas.dto.AsistenciaRequest;
import com.medigestsystem.citas.dto.CancelarRequest;
import com.medigestsystem.citas.dto.CitaRequest;
import com.medigestsystem.citas.dto.CitaResponse;
import com.medigestsystem.citas.dto.CitaResumenResponse;
import com.medigestsystem.citas.dto.HistorialCitaResponse;
import com.medigestsystem.citas.dto.ReprogramarRequest;
import com.medigestsystem.shared.dto.PaginaResponse;

@RestController
@RequestMapping("/api/v1/citas")
@Tag(name = "Citas", description = "Reserva, reprogramación, cancelación y control de asistencia")
@SecurityRequirement(name = "bearerAuth")
public class CitaController {

    private final CitaService citaService;

    public CitaController(CitaService citaService) {
        this.citaService = citaService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Lista citas con filtros y paginación",
            description = "Un paciente solo ve las suyas; un médico solo las de su agenda. Fechas con desfase, por ejemplo 2030-01-07T00:00:00-05:00.")
    public PaginaResponse<CitaResumenResponse> listar(
            @RequestParam(required = false) Long pacienteId,
            @RequestParam(required = false) Long medicoId,
            @RequestParam(required = false) Integer especialidadId,
            @RequestParam(required = false) Integer consultorioId,
            @RequestParam(required = false) EstadoCita estado,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime hasta,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        return citaService.listar(pacienteId, medicoId, especialidadId, consultorioId, estado, desde, hasta,
                pagina, tamano);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Detalle de una cita con las acciones permitidas al usuario actual")
    public CitaResponse obtener(@PathVariable Long id) {
        return citaService.obtener(id);
    }

    @GetMapping("/{id}/historial")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Historial de eventos de una cita (creación, reprogramaciones, cambios de estado)")
    public List<HistorialCitaResponse> historial(@PathVariable Long id) {
        return citaService.historial(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','RECEPCIONISTA','PACIENTE')")
    @Operation(summary = "Reserva una cita",
            description = "Valida médico activo, jornada, descanso, bloqueos, superposiciones de médico, consultorio y paciente. "
                    + "Es seguro ante solicitudes simultáneas: solo una reserva por horario.")
    public ResponseEntity<CitaResponse> reservar(@Valid @RequestBody CitaRequest solicitud) {
        return ResponseEntity.status(HttpStatus.CREATED).body(citaService.reservar(solicitud));
    }

    @PatchMapping("/{id}/confirmar")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','RECEPCIONISTA')")
    @Operation(summary = "Confirma una cita pendiente")
    public CitaResponse confirmar(@PathVariable Long id) {
        return citaService.confirmar(id);
    }

    @PatchMapping("/{id}/reprogramar")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','RECEPCIONISTA','PACIENTE')")
    @Operation(summary = "Cambia la fecha y hora de una cita, conservando el historial")
    public CitaResponse reprogramar(@PathVariable Long id, @Valid @RequestBody ReprogramarRequest solicitud) {
        return citaService.reprogramar(id, solicitud);
    }

    @PatchMapping("/{id}/cancelar")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','RECEPCIONISTA','PACIENTE')")
    @Operation(summary = "Cancela una cita con motivo (no se elimina)")
    public CitaResponse cancelar(@PathVariable Long id, @Valid @RequestBody CancelarRequest solicitud) {
        return citaService.cancelar(id, solicitud);
    }

    @PatchMapping("/{id}/asistencia")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','RECEPCIONISTA','MEDICO')")
    @Operation(summary = "Control de asistencia: LLEGADA, INICIAR, FINALIZAR o NO_ASISTIO",
            description = "LLEGADA y NO_ASISTIO: recepción. INICIAR y FINALIZAR: el médico de la cita.")
    public CitaResponse asistencia(@PathVariable Long id, @Valid @RequestBody AsistenciaRequest solicitud) {
        return citaService.registrarAsistencia(id, solicitud.accion());
    }
}
