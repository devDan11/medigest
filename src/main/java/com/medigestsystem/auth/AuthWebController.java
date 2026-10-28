package com.medigestsystem.auth;

import jakarta.validation.Valid;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.medigestsystem.pacientes.Sexo;
import com.medigestsystem.pacientes.TipoDocumento;
import com.medigestsystem.security.UsuarioPrincipal;
import com.medigestsystem.shared.exception.ApiException;
import com.medigestsystem.shared.exception.ConflictoException;

/** Páginas web de autenticación (sesión + CSRF). La lógica vive en {@link AuthService} y {@link RecuperacionService}. */
@Controller
public class AuthWebController {

    private static final String VISTA_REGISTRO = "auth/registro";
    private static final String VISTA_RESTABLECER = "auth/restablecer";

    private final AuthService authService;
    private final RecuperacionService recuperacionService;

    public AuthWebController(AuthService authService, RecuperacionService recuperacionService) {
        this.authService = authService;
        this.recuperacionService = recuperacionService;
    }

    @GetMapping("/")
    public String raiz() {
        return "redirect:/inicio";
    }

    @GetMapping("/login")
    public String login(Authentication authentication) {
        return estaAutenticado(authentication) ? "redirect:/inicio" : "auth/login";
    }

    @GetMapping("/inicio")
    public String inicio() {
        return "redirect:/panel";
    }

    @GetMapping("/registro")
    public String registro(Authentication authentication, Model model) {
        if (estaAutenticado(authentication)) {
            return "redirect:/inicio";
        }
        model.addAttribute("form", new RegistroForm());
        cargarCatalogos(model);
        return VISTA_REGISTRO;
    }

    @PostMapping("/registro")
    public String registrar(@Valid @ModelAttribute("form") RegistroForm form, BindingResult resultado, Model model) {
        if (form.getPassword() != null && !form.getPassword().equals(form.getConfirmacion())) {
            resultado.rejectValue("confirmacion", "confirmacion.noCoincide", "Las contraseñas no coinciden");
        }
        if (!resultado.hasErrors()) {
            try {
                authService.registrarPaciente(form.aSolicitud());
                return "redirect:/login?registrado";
            } catch (ConflictoException e) {
                String campo = "EMAIL_DUPLICADO".equals(e.getCodigo()) ? "email" : "numeroDocumento";
                resultado.rejectValue(campo, e.getCodigo(), e.getMessage());
            } catch (ApiException e) {
                resultado.rejectValue("numeroDocumento", e.getCodigo(), e.getMessage());
            }
        }
        cargarCatalogos(model);
        return VISTA_REGISTRO;
    }

    @GetMapping("/recuperar")
    public String recuperar() {
        return "auth/recuperar";
    }

    @PostMapping("/recuperar")
    public String solicitarRecuperacion(@RequestParam String email, Model model) {
        if (email == null || email.isBlank() || email.length() > 150) {
            model.addAttribute("error", "Ingrese un correo válido.");
            return "auth/recuperar";
        }
        recuperacionService.solicitar(email);
        // Respuesta idéntica exista o no la cuenta.
        return "redirect:/recuperar?enviado";
    }

    @GetMapping("/restablecer")
    public String restablecer(@RequestParam(required = false) String token, Model model) {
        model.addAttribute("token", token == null ? "" : token);
        return VISTA_RESTABLECER;
    }

    @PostMapping("/restablecer")
    public String aplicarRestablecimiento(@RequestParam String token, @RequestParam String password,
                                          @RequestParam String confirmacion, Model model) {
        model.addAttribute("token", token);
        if (!password.equals(confirmacion)) {
            model.addAttribute("error", "Las contraseñas no coinciden.");
            return VISTA_RESTABLECER;
        }
        try {
            recuperacionService.restablecer(token, password);
            return "redirect:/login?restablecido";
        } catch (ApiException e) {
            model.addAttribute("error", e.getMessage());
            return VISTA_RESTABLECER;
        }
    }

    private static boolean estaAutenticado(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

    private static void cargarCatalogos(Model model) {
        model.addAttribute("tiposDocumento", TipoDocumento.values());
        model.addAttribute("sexos", Sexo.values());
    }
}
