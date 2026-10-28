package com.medigestsystem.web.paginas;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.medigestsystem.citas.EstadoCita;
import com.medigestsystem.dashboard.DashboardResponse;
import com.medigestsystem.dashboard.DashboardService;
import com.medigestsystem.web.Formato;

/** Panel de inicio común a todos los roles; el contenido lo acota {@link DashboardService}. */
@Controller
public class PanelPageController {

    private final DashboardService dashboardService;
    private final Formato formato;

    public PanelPageController(DashboardService dashboardService, Formato formato) {
        this.dashboardService = dashboardService;
        this.formato = formato;
    }

    @GetMapping("/panel")
    public String panel(Model model) {
        DashboardResponse r = dashboardService.resumen();
        model.addAttribute("r", r);

        List<String> etiquetasEstado = new ArrayList<>();
        List<Long> valoresEstado = new ArrayList<>();
        for (Map.Entry<EstadoCita, Long> e : r.porEstadoHoy().entrySet()) {
            etiquetasEstado.add(formato.estado(e.getKey()));
            valoresEstado.add(e.getValue());
        }
        model.addAttribute("etiquetasEstado", String.join("|", etiquetasEstado));
        model.addAttribute("valoresEstado", String.join(",", valoresEstado.stream().map(String::valueOf).toList()));
        model.addAttribute("etiquetasDias", String.join("|",
                r.proximosDias().stream().map(d -> formato.diaCorto(d.fecha())).toList()));
        model.addAttribute("valoresDias", String.join(",",
                r.proximosDias().stream().map(d -> String.valueOf(d.total())).toList()));
        return "panel";
    }
}
