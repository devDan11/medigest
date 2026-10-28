package com.medigestsystem.web.paginas;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.medigestsystem.consultorios.ConsultorioService;
import com.medigestsystem.consultorios.dto.ConsultorioRequest;
import com.medigestsystem.especialidades.EspecialidadService;
import com.medigestsystem.especialidades.dto.EspecialidadRequest;

/** Catálogos administrables: especialidades y consultorios. */
@Controller
@PreAuthorize("hasRole('ADMINISTRADOR')")
public class CatalogoPageController {

    private final EspecialidadService especialidadService;
    private final ConsultorioService consultorioService;
    private final ValidadorWeb validador;

    public CatalogoPageController(EspecialidadService especialidadService, ConsultorioService consultorioService,
                                  ValidadorWeb validador) {
        this.especialidadService = especialidadService;
        this.consultorioService = consultorioService;
        this.validador = validador;
    }

    // ------------------------------------------------------------------ especialidades

    @GetMapping("/especialidades")
    public String especialidades(Model model) {
        model.addAttribute("items", especialidadService.listar(true));
        return "catalogos/especialidades";
    }

    /** Sin {@code id} crea; con {@code id} actualiza. */
    @PostMapping("/especialidades")
    public String guardarEspecialidad(@RequestParam(required = false) Integer id, @RequestParam String nombre,
                                      @RequestParam(required = false) String descripcion,
                                      @RequestParam(required = false) Integer duracionSugeridaMin,
                                      RedirectAttributes redirect) {
        EspecialidadRequest s = validador.validar(new EspecialidadRequest(PacienteForm.texto(nombre),
                PacienteForm.texto(descripcion), duracionSugeridaMin));
        if (id == null) {
            especialidadService.crear(s);
            redirect.addFlashAttribute("mgOk", "Especialidad creada.");
        } else {
            especialidadService.actualizar(id, s);
            redirect.addFlashAttribute("mgOk", "Especialidad actualizada.");
        }
        return "redirect:/especialidades";
    }

    @PostMapping("/especialidades/{id}/estado")
    public String estadoEspecialidad(@PathVariable Integer id, @RequestParam boolean activa, RedirectAttributes redirect) {
        especialidadService.cambiarEstado(id, activa);
        redirect.addFlashAttribute("mgOk", activa ? "Especialidad activada." : "Especialidad desactivada.");
        return "redirect:/especialidades";
    }

    // ------------------------------------------------------------------ consultorios

    @GetMapping("/consultorios")
    public String consultorios(Model model) {
        model.addAttribute("items", consultorioService.listar(true));
        return "catalogos/consultorios";
    }

    @PostMapping("/consultorios")
    public String guardarConsultorio(@RequestParam(required = false) Integer id, @RequestParam String codigo,
                                     @RequestParam(required = false) String ubicacion, RedirectAttributes redirect) {
        ConsultorioRequest s = validador.validar(new ConsultorioRequest(PacienteForm.texto(codigo),
                PacienteForm.texto(ubicacion)));
        if (id == null) {
            consultorioService.crear(s);
            redirect.addFlashAttribute("mgOk", "Consultorio creado.");
        } else {
            consultorioService.actualizar(id, s);
            redirect.addFlashAttribute("mgOk", "Consultorio actualizado.");
        }
        return "redirect:/consultorios";
    }

    @PostMapping("/consultorios/{id}/estado")
    public String estadoConsultorio(@PathVariable Integer id, @RequestParam boolean activo, RedirectAttributes redirect) {
        consultorioService.cambiarEstado(id, activo);
        redirect.addFlashAttribute("mgOk", activo ? "Consultorio activado." : "Consultorio desactivado.");
        return "redirect:/consultorios";
    }
}
