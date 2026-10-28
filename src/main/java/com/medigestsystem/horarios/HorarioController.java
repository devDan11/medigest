package com.medigestsystem.horarios;

import java.util.List;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.medigestsystem.horarios.dto.HorarioRequest;
import com.medigestsystem.horarios.dto.HorarioResponse;

@RestController
@RequestMapping("/api/v1/medicos/{medicoId}/horarios")
@Tag(name = "Horarios", description = "Horario semanal de atención de cada médico")
@SecurityRequirement(name = "bearerAuth")
public class HorarioController {

    private final HorarioService horarioService;

    public HorarioController(HorarioService horarioService) {
        this.horarioService = horarioService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Horario semanal vigente del médico (hora local de la clínica)")
    public List<HorarioResponse> obtener(@PathVariable Long medicoId) {
        return horarioService.obtener(medicoId);
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Reemplaza el horario semanal completo del médico")
    public List<HorarioResponse> reemplazar(@PathVariable Long medicoId, @Valid @RequestBody HorarioRequest solicitud) {
        return horarioService.reemplazar(medicoId, solicitud);
    }
}
