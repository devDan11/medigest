package com.medigestsystem.web.paginas;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.medigestsystem.pacientes.PacienteService;
import com.medigestsystem.pacientes.dto.PerfilPacienteRequest;

/** Datos de contacto del paciente autenticado (el documento y el nombre solo los cambia el personal). */
@Controller
@PreAuthorize("hasRole('PACIENTE')")
public class PerfilPageController {

    private final PacienteService pacienteService;
    private final ValidadorWeb validador;

    public PerfilPageController(PacienteService pacienteService, ValidadorWeb validador) {
        this.pacienteService = pacienteService;
        this.validador = validador;
    }

    @GetMapping("/perfil")
    public String ver(Model model) {
        model.addAttribute("p", pacienteService.obtenerPropio());
        return "perfil";
    }

    @PostMapping("/perfil")
    public String guardar(@RequestParam(required = false) String email, @RequestParam(required = false) String telefono,
                          @RequestParam(required = false) String direccion,
                          @RequestParam(required = false) String contactoEmergenciaNombre,
                          @RequestParam(required = false) String contactoEmergenciaTelefono,
                          RedirectAttributes redirect) {
        pacienteService.actualizarPerfil(validador.validar(new PerfilPacienteRequest(
                PacienteForm.texto(email), PacienteForm.texto(telefono), PacienteForm.texto(direccion),
                PacienteForm.texto(contactoEmergenciaNombre), PacienteForm.texto(contactoEmergenciaTelefono))));
        redirect.addFlashAttribute("mgOk", "Sus datos de contacto fueron actualizados.");
        return "redirect:/perfil";
    }
}
