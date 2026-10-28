package com.medigestsystem.web.paginas;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.medigestsystem.auditoria.AuditoriaConsultaService;
import com.medigestsystem.auditoria.ResultadoAuditoria;
import com.medigestsystem.shared.util.Csv;

/** Registro de auditoría (solo administrador). */
@Controller
@PreAuthorize("hasRole('ADMINISTRADOR')")
public class AuditoriaPageController {

    private final AuditoriaConsultaService consultaService;

    public AuditoriaPageController(AuditoriaConsultaService consultaService) {
        this.consultaService = consultaService;
    }

    @GetMapping("/auditoria")
    public String listar(@RequestParam(required = false) String accion,
                         @RequestParam(required = false) String email,
                         @RequestParam(required = false) ResultadoAuditoria resultado,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                         @RequestParam(defaultValue = "0") int pagina, Model model) {
        model.addAttribute("pagina", consultaService.listar(
                new AuditoriaConsultaService.Filtro(accion, null, email, resultado, desde, hasta), Math.max(pagina, 0), 20));
        model.addAttribute("resultados", ResultadoAuditoria.values());
        model.addAttribute("fAccion", accion);
        model.addAttribute("fEmail", email);
        model.addAttribute("fResultado", resultado);
        model.addAttribute("fDesde", desde);
        model.addAttribute("fHasta", hasta);
        return "auditoria/lista";
    }

    @GetMapping("/auditoria/exportar")
    public ResponseEntity<byte[]> exportar(@RequestParam(required = false) String accion,
                                           @RequestParam(required = false) String email,
                                           @RequestParam(required = false) ResultadoAuditoria resultado,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return Csv.respuesta("auditoria.csv", consultaService.exportarCsv(
                new AuditoriaConsultaService.Filtro(accion, null, email, resultado, desde, hasta)));
    }
}
