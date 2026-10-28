package com.medigestsystem.web.paginas;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.medigestsystem.pacientes.PacienteService;
import com.medigestsystem.pacientes.Sexo;
import com.medigestsystem.pacientes.TipoDocumento;
import com.medigestsystem.pacientes.dto.PacienteResponse;

/** Gestión de pacientes para el personal administrativo. */
@Controller
@PreAuthorize("hasAnyRole('ADMINISTRADOR','RECEPCIONISTA')")
public class PacientePageController {

    private final PacienteService pacienteService;
    private final ValidadorWeb validador;

    public PacientePageController(PacienteService pacienteService, ValidadorWeb validador) {
        this.pacienteService = pacienteService;
        this.validador = validador;
    }

    @GetMapping("/pacientes")
    public String listar(@RequestParam(required = false) String q, @RequestParam(required = false) Boolean activo,
                         @RequestParam(defaultValue = "0") int pagina, Model model) {
        String texto = q == null || q.isBlank() ? null : q.trim();
        model.addAttribute("pagina", pacienteService.buscar(texto, activo, Math.max(pagina, 0), 15));
        model.addAttribute("fQ", texto);
        model.addAttribute("fActivo", activo);
        return "pacientes/lista";
    }

    @GetMapping("/pacientes/nuevo")
    public String nuevo(Model model) {
        cargarFormulario(model, null, "/pacientes/nuevo", "Nuevo paciente");
        return "pacientes/form";
    }

    @PostMapping("/pacientes/nuevo")
    public String crear(@ModelAttribute PacienteForm form, RedirectAttributes redirect) {
        PacienteResponse creado = pacienteService.crear(validador.validar(form.aSolicitud()));
        redirect.addFlashAttribute("mgOk", "Paciente registrado.");
        return "redirect:/pacientes/" + creado.id();
    }

    @GetMapping("/pacientes/{id}")
    public String detalle(@PathVariable Long id, Model model) {
        model.addAttribute("p", pacienteService.obtener(id));
        model.addAttribute("resumen", pacienteService.resumen(id));
        model.addAttribute("citas", pacienteService.historial(id, 0, 10));
        return "pacientes/detalle";
    }

    @GetMapping("/pacientes/{id}/editar")
    public String editar(@PathVariable Long id, Model model) {
        cargarFormulario(model, pacienteService.obtener(id), "/pacientes/" + id + "/editar", "Editar paciente");
        return "pacientes/form";
    }

    @PostMapping("/pacientes/{id}/editar")
    public String actualizar(@PathVariable Long id, @ModelAttribute PacienteForm form, RedirectAttributes redirect) {
        pacienteService.actualizar(id, validador.validar(form.aSolicitud()));
        redirect.addFlashAttribute("mgOk", "Datos del paciente actualizados.");
        return "redirect:/pacientes/" + id;
    }

    @PostMapping("/pacientes/{id}/estado")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public String cambiarEstado(@PathVariable Long id, @RequestParam boolean activo, RedirectAttributes redirect) {
        pacienteService.cambiarEstado(id, activo);
        redirect.addFlashAttribute("mgOk", activo ? "Paciente activado." : "Paciente desactivado.");
        return "redirect:/pacientes/" + id;
    }

    private void cargarFormulario(Model model, PacienteResponse p, String accion, String titulo) {
        model.addAttribute("p", p);
        model.addAttribute("accion", accion);
        model.addAttribute("titulo", titulo);
        model.addAttribute("tiposDocumento", TipoDocumento.values());
        model.addAttribute("sexos", Sexo.values());
    }
}
