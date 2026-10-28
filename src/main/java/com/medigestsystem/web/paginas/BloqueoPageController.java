package com.medigestsystem.web.paginas;

import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.medigestsystem.consultorios.ConsultorioService;
import com.medigestsystem.horarios.BloqueoService;
import com.medigestsystem.horarios.EstadoBloqueo;
import com.medigestsystem.horarios.TipoBloqueo;
import com.medigestsystem.horarios.dto.BloqueoRequest;
import com.medigestsystem.medicos.MedicoService;
import com.medigestsystem.security.SecurityUtils;
import com.medigestsystem.usuarios.NombreRol;

/** Bloqueos de agenda: vacaciones, ausencias y mantenimiento. El médico los solicita; el administrador los aprueba. */
@Controller
@PreAuthorize("hasAnyRole('ADMINISTRADOR','RECEPCIONISTA','MEDICO')")
public class BloqueoPageController {

    private final BloqueoService bloqueoService;
    private final MedicoService medicoService;
    private final ConsultorioService consultorioService;
    private final ValidadorWeb validador;
    private final ZoneId zona;

    public BloqueoPageController(BloqueoService bloqueoService, MedicoService medicoService,
                                 ConsultorioService consultorioService, ValidadorWeb validador, ZoneId zonaClinica) {
        this.bloqueoService = bloqueoService;
        this.medicoService = medicoService;
        this.consultorioService = consultorioService;
        this.validador = validador;
        this.zona = zonaClinica;
    }

    @GetMapping("/bloqueos")
    public String listar(@RequestParam(required = false) EstadoBloqueo estado,
                         @RequestParam(defaultValue = "0") int pagina, Model model) {
        boolean admin = SecurityUtils.tieneRol(NombreRol.ADMINISTRADOR);
        model.addAttribute("pagina", bloqueoService.listar(null, null, estado, null, null, Math.max(pagina, 0), 15));
        model.addAttribute("estados", EstadoBloqueo.values());
        model.addAttribute("fEstado", estado);
        // Un médico no elige objetivo (solicita siempre sobre su agenda ni puede pedir mantenimiento).
        model.addAttribute("tipos", admin ? TipoBloqueo.values()
                : java.util.Arrays.stream(TipoBloqueo.values()).filter(t -> t != TipoBloqueo.MANTENIMIENTO).toList());
        if (admin) {
            model.addAttribute("medicos", medicoService.listar(null, null, true, 0, 100).contenido());
            model.addAttribute("consultorios", consultorioService.listar(false));
        }
        return "bloqueos/lista";
    }

    @PostMapping("/bloqueos")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','MEDICO')")
    public String crear(@RequestParam(required = false) Long medicoId, @RequestParam(required = false) Integer consultorioId,
                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime inicio,
                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fin,
                        @RequestParam TipoBloqueo tipo, @RequestParam(required = false) String motivo,
                        RedirectAttributes redirect) {
        boolean admin = SecurityUtils.tieneRol(NombreRol.ADMINISTRADOR);
        BloqueoRequest s = validador.validar(new BloqueoRequest(admin ? medicoId : null, admin ? consultorioId : null,
                inicio.atZone(zona).toOffsetDateTime(), fin.atZone(zona).toOffsetDateTime(), tipo,
                PacienteForm.texto(motivo)));
        bloqueoService.crear(s);
        redirect.addFlashAttribute("mgOk", admin ? "Bloqueo registrado." : "Solicitud enviada; el administrador debe aprobarla.");
        return "redirect:/bloqueos";
    }

    @PostMapping("/bloqueos/{id}/aprobar")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public String aprobar(@PathVariable Long id, RedirectAttributes redirect) {
        bloqueoService.aprobar(id);
        redirect.addFlashAttribute("mgOk", "Bloqueo aprobado.");
        return "redirect:/bloqueos";
    }

    @PostMapping("/bloqueos/{id}/rechazar")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public String rechazar(@PathVariable Long id, RedirectAttributes redirect) {
        bloqueoService.rechazar(id);
        redirect.addFlashAttribute("mgOk", "Solicitud rechazada.");
        return "redirect:/bloqueos";
    }

    @PostMapping("/bloqueos/{id}/eliminar")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','MEDICO')")
    public String eliminar(@PathVariable Long id, RedirectAttributes redirect) {
        bloqueoService.eliminar(id);
        redirect.addFlashAttribute("mgOk", "Bloqueo eliminado.");
        return "redirect:/bloqueos";
    }
}
