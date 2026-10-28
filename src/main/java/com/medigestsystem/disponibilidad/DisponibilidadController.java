package com.medigestsystem.disponibilidad;

import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.medigestsystem.disponibilidad.dto.DiaDisponibilidadResponse;
import com.medigestsystem.disponibilidad.dto.MedicoDisponibleResponse;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Disponibilidad", description = "Horarios libres para reservar citas")
@SecurityRequirement(name = "bearerAuth")
public class DisponibilidadController {

    private final DisponibilidadService disponibilidadService;

    public DisponibilidadController(DisponibilidadService disponibilidadService) {
        this.disponibilidadService = disponibilidadService;
    }

    @GetMapping("/medicos/{medicoId}/disponibilidad")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Horarios libres de un médico por día (máximo 31 días)",
            description = "Considera jornada semanal, descanso, duración de consulta, citas, bloqueos y disponibilidad del consultorio.")
    public List<DiaDisponibilidadResponse> disponibilidad(
            @PathVariable Long medicoId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return disponibilidadService.disponibilidadDeMedico(medicoId, desde, hasta);
    }

    @GetMapping("/disponibilidad/medicos")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Médicos de una especialidad con horarios libres (máximo 14 días)")
    public List<MedicoDisponibleResponse> medicosDisponibles(
            @RequestParam Integer especialidadId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return disponibilidadService.medicosDisponibles(especialidadId, desde, hasta);
    }
}
