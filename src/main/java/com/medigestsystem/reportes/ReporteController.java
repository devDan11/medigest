package com.medigestsystem.reportes;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.medigestsystem.reportes.dto.ReporteCitasResponse;
import com.medigestsystem.reportes.dto.ReporteEspecialidadesResponse;
import com.medigestsystem.shared.util.Csv;

@RestController
@RequestMapping("/api/v1/reportes")
@PreAuthorize("hasAnyRole('ADMINISTRADOR','RECEPCIONISTA')")
@Tag(name = "Reportes", description = "Estadísticas de citas (administrador y recepción)")
@SecurityRequirement(name = "bearerAuth")
public class ReporteController {

    private final ReporteService reporteService;

    public ReporteController(ReporteService reporteService) {
        this.reporteService = reporteService;
    }

    @GetMapping("/citas")
    @Operation(summary = "Citas por estado, por día y por médico; por defecto los últimos 30 días")
    public ReporteCitasResponse citas(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Long medicoId,
            @RequestParam(required = false) Integer especialidadId) {
        return reporteService.citas(desde, hasta, medicoId, especialidadId);
    }

    @GetMapping("/citas/exportar")
    @Operation(summary = "Exporta una vista del reporte de citas a CSV (vista = estado | dia | medico)")
    public ResponseEntity<byte[]> exportarCitas(
            @RequestParam(defaultValue = "dia") String vista,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Long medicoId,
            @RequestParam(required = false) Integer especialidadId) {
        return Csv.respuesta("citas-por-" + vista + ".csv",
                reporteService.csvCitas(vista, desde, hasta, medicoId, especialidadId));
    }

    @GetMapping("/especialidades")
    @Operation(summary = "Citas por especialidad en el período")
    public ReporteEspecialidadesResponse especialidades(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return reporteService.especialidades(desde, hasta);
    }

    @GetMapping("/especialidades/exportar")
    @Operation(summary = "Exporta el reporte por especialidad a CSV")
    public ResponseEntity<byte[]> exportarEspecialidades(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return Csv.respuesta("citas-por-especialidad.csv", reporteService.csvEspecialidades(desde, hasta));
    }
}
