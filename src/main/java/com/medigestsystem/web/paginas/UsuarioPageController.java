package com.medigestsystem.web.paginas;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.medigestsystem.security.SecurityUtils;
import com.medigestsystem.shared.exception.SolicitudInvalidaException;
import com.medigestsystem.usuarios.NombreRol;
import com.medigestsystem.usuarios.UsuarioAdminService;

/** Administración de cuentas de usuario (solo administrador). */
@Controller
@PreAuthorize("hasRole('ADMINISTRADOR')")
public class UsuarioPageController {

    private final UsuarioAdminService usuarioAdminService;

    public UsuarioPageController(UsuarioAdminService usuarioAdminService) {
        this.usuarioAdminService = usuarioAdminService;
    }

    @GetMapping("/usuarios")
    public String listar(@RequestParam(required = false) String q, @RequestParam(defaultValue = "0") int pagina,
                         Model model) {
        String texto = q == null || q.isBlank() ? null : q.trim();
        model.addAttribute("pagina", usuarioAdminService.listar(texto, Math.max(pagina, 0), 15));
        model.addAttribute("roles", NombreRol.values());
        model.addAttribute("fQ", texto);
        model.addAttribute("yo", SecurityUtils.usuarioIdActual().orElse(null));
        return "usuarios/lista";
    }

    @PostMapping("/usuarios/{id}/estado")
    public String cambiarEstado(@PathVariable Long id, @RequestParam boolean activo, RedirectAttributes redirect) {
        usuarioAdminService.cambiarEstado(id, activo, actor());
        redirect.addFlashAttribute("mgOk", activo ? "Cuenta activada." : "Cuenta desactivada.");
        return "redirect:/usuarios";
    }

    @PostMapping("/usuarios/{id}/desbloquear")
    public String desbloquear(@PathVariable Long id, RedirectAttributes redirect) {
        usuarioAdminService.desbloquear(id);
        redirect.addFlashAttribute("mgOk", "Cuenta desbloqueada.");
        return "redirect:/usuarios";
    }

    @PostMapping("/usuarios/{id}/roles")
    public String asignarRoles(@PathVariable Long id, @RequestParam(name = "roles", required = false) List<NombreRol> roles,
                               RedirectAttributes redirect) {
        if (roles == null || roles.isEmpty()) {
            throw new SolicitudInvalidaException("ROLES_REQUERIDOS", "Debe indicar al menos un rol.");
        }
        Set<NombreRol> conjunto = EnumSet.copyOf(roles);
        usuarioAdminService.asignarRoles(id, conjunto, actor());
        redirect.addFlashAttribute("mgOk", "Roles actualizados.");
        return "redirect:/usuarios";
    }

    private Long actor() {
        return SecurityUtils.usuarioIdActual().orElseThrow();
    }
}
