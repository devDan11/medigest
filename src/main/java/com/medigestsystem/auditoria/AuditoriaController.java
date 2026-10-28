package com.medigestsystem.auditoria;

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

import com.medigestsystem.auditoria.dto.AuditoriaResponse;
import com.medigestsystem.shared.dto.PaginaResponse;
import com.medigestsystem.shared.util.Csv;

@RestController
@RequestMapping("/api/v1/auditoria")
@PreAuthorize("hasRole('ADMINISTRADOR')")
@Tag(name = "Auditoría", description = "Registro de eventos del sistema (solo administrador)")
@SecurityRequirement(name = "bearerAuth")
public class AuditoriaController {

    private final AuditoriaConsultaService consultaService;

    public AuditoriaController(AuditoriaConsultaService consultaService) {
        this.consultaService = consultaService;
    }

    @GetMapping
    @Operation(summary = "Lista eventos de auditoría, del más reciente al más antiguo")
    public PaginaResponse<AuditoriaResponse> listar(
            @RequestParam(required = false) String accion,
            @RequestParam(required = false) String entidad,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) ResultadoAuditoria resultado,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        return consultaService.listar(new AuditoriaConsultaService.Filtro(accion, entidad, email, resultado, desde, hasta),
                pagina, tamano);
    }

    @GetMapping("/exportar")
    @Operation(summary = "Exporta a CSV (hasta 10 000 eventos) con los mismos filtros")
    public ResponseEntity<byte[]> exportar(
            @RequestParam(required = false) String accion,
            @RequestParam(required = false) String entidad,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) ResultadoAuditoria resultado,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return Csv.respuesta("auditoria.csv", consultaService.exportarCsv(
                new AuditoriaConsultaService.Filtro(accion, entidad, email, resultado, desde, hasta)));
    }
}
