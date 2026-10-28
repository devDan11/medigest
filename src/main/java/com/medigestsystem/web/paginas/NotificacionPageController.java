package com.medigestsystem.web.paginas;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.medigestsystem.notificaciones.EstadoNotificacion;
import com.medigestsystem.notificaciones.NotificacionService;
import com.medigestsystem.notificaciones.TipoNotificacion;

/** Cola de correos del sistema (solo administrador). */
@Controller
@PreAuthorize("hasRole('ADMINISTRADOR')")
public class NotificacionPageController {

    private final NotificacionService notificacionService;

    public NotificacionPageController(NotificacionService notificacionService) {
        this.notificacionService = notificacionService;
    }

    @GetMapping("/notificaciones")
    public String listar(@RequestParam(required = false) EstadoNotificacion estado,
                         @RequestParam(required = false) TipoNotificacion tipo,
                         @RequestParam(defaultValue = "0") int pagina, Model model) {
        model.addAttribute("pagina", notificacionService.listar(estado, tipo, Math.max(pagina, 0), 15));
        model.addAttribute("resumen", notificacionService.resumen());
        model.addAttribute("correoHabilitado", notificacionService.correoHabilitado());
        model.addAttribute("estados", EstadoNotificacion.values());
        model.addAttribute("tipos", TipoNotificacion.values());
        model.addAttribute("fEstado", estado);
        model.addAttribute("fTipo", tipo);
        return "notificaciones/lista";
    }

    @PostMapping("/notificaciones/{id}/reintentar")
    public String reintentar(@PathVariable Long id, RedirectAttributes redirect) {
        notificacionService.reintentar(id);
        redirect.addFlashAttribute("mgOk", "La notificación volvió a la cola de envío.");
        return "redirect:/notificaciones";
    }
}
