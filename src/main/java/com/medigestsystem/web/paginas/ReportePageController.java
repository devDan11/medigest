package com.medigestsystem.web.paginas;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.medigestsystem.especialidades.EspecialidadService;
import com.medigestsystem.reportes.ReporteService;
import com.medigestsystem.reportes.dto.FilaResumen;
import com.medigestsystem.reportes.dto.ReporteCitasResponse;
import com.medigestsystem.reportes.dto.ReporteEspecialidadesResponse;
import com.medigestsystem.shared.util.Csv;
import com.medigestsystem.web.Formato;

/** Reportes y estadísticas de citas (administrador y recepción). */
@Controller
@PreAuthorize("hasAnyRole('ADMINISTRADOR','RECEPCIONISTA')")
public class ReportePageController {

    private final ReporteService reporteService;
    private final EspecialidadService especialidadService;
    private final Formato formato;

    public ReportePageController(ReporteService reporteService, EspecialidadService especialidadService, Formato formato) {
        this.reporteService = reporteService;
        this.especialidadService = especialidadService;
        this.formato = formato;
    }

    @GetMapping("/reportes")
    public String reportes(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                           @RequestParam(required = false) Integer especialidadId, Model model) {
        ReporteCitasResponse r = reporteService.citas(desde, hasta, null, especialidadId);
        ReporteEspecialidadesResponse e = reporteService.especialidades(r.desde(), r.hasta());

        List<String> etqEstado = new ArrayList<>();
        List<String> valEstado = new ArrayList<>();
        r.porEstado().forEach((estado, n) -> {
            etqEstado.add(formato.estado(estado));
            valEstado.add(String.valueOf(n));
        });
        model.addAttribute("r", r);
        model.addAttribute("e", e);
        model.addAttribute("etiquetasEstado", String.join("|", etqEstado));
        model.addAttribute("valoresEstado", String.join(",", valEstado));
        model.addAttribute("etiquetasDias", r.porDia().stream().map(d -> formato.diaCorto(d.fecha())).collect(Collectors.joining("|")));
        model.addAttribute("valoresDias", r.porDia().stream().map(d -> String.valueOf(d.total())).collect(Collectors.joining(",")));
        model.addAttribute("etiquetasEsp", e.especialidades().stream().map(FilaResumen::nombre).collect(Collectors.joining("|")));
        model.addAttribute("valoresEsp", e.especialidades().stream().map(f -> String.valueOf(f.total())).collect(Collectors.joining(",")));
        model.addAttribute("especialidades", especialidadService.listar(false));
        model.addAttribute("fDesde", r.desde());
        model.addAttribute("fHasta", r.hasta());
        model.addAttribute("fEspecialidad", especialidadId);
        return "reportes/index";
    }

    @GetMapping("/reportes/citas.csv")
    public ResponseEntity<byte[]> csvCitas(@RequestParam(defaultValue = "dia") String vista,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                           @RequestParam(required = false) Integer especialidadId) {
        return Csv.respuesta("citas-por-" + vista + ".csv", reporteService.csvCitas(vista, desde, hasta, null, especialidadId));
    }

    @GetMapping("/reportes/especialidades.csv")
    public ResponseEntity<byte[]> csvEspecialidades(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return Csv.respuesta("citas-por-especialidad.csv", reporteService.csvEspecialidades(desde, hasta));
    }
}
