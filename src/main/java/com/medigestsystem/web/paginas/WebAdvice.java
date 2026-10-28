package com.medigestsystem.web.paginas;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.ui.Model;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.medigestsystem.security.SecurityUtils;
import com.medigestsystem.shared.exception.ApiException;
import com.medigestsystem.usuarios.NombreRol;

/**
 * Comportamiento común de las páginas Thymeleaf: datos del usuario para el menú y conversión de los errores
 * de negocio (los mismos que devuelve la API) en un mensaje visible tras redirigir a la página anterior.
 */
@ControllerAdvice(basePackages = "com.medigestsystem.web.paginas")
public class WebAdvice {

    @ModelAttribute
    public void datosDeSesion(Model model) {
        boolean admin = SecurityUtils.tieneRol(NombreRol.ADMINISTRADOR);
        boolean recepcion = SecurityUtils.tieneRol(NombreRol.RECEPCIONISTA);
        model.addAttribute("mgEmail", SecurityUtils.emailActual().orElse(""));
        model.addAttribute("mgAdmin", admin);
        model.addAttribute("mgRecepcion", recepcion);
        model.addAttribute("mgPersonal", admin || recepcion);
        model.addAttribute("mgMedico", SecurityUtils.tieneRol(NombreRol.MEDICO));
        model.addAttribute("mgPaciente", SecurityUtils.tieneRol(NombreRol.PACIENTE));
    }

    @ExceptionHandler(ApiException.class)
    public String errorDeNegocio(ApiException ex, HttpServletRequest request, RedirectAttributes redirect) {
        redirect.addFlashAttribute("mgError", ex.getMessage());
        conservarFormulario(request, redirect);
        return "redirect:" + destino(request);
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class,
            MethodArgumentNotValidException.class, BindException.class})
    public String solicitudInvalida(Exception ex, HttpServletRequest request, RedirectAttributes redirect) {
        redirect.addFlashAttribute("mgError", "Revise los datos ingresados: alguno falta o tiene un formato inválido.");
        conservarFormulario(request, redirect);
        return "redirect:" + destino(request);
    }

    /**
     * Devuelve lo que la persona escribió para que el formulario no quede vacío tras un error.
     * Nunca se conservan contraseñas ni el token CSRF; los campos repetidos se unen con comas.
     */
    private void conservarFormulario(HttpServletRequest request, RedirectAttributes redirect) {
        if (!"POST".equals(request.getMethod())) {
            return;
        }
        Map<String, String> valores = new LinkedHashMap<>();
        request.getParameterMap().forEach((nombre, v) -> {
            String clave = nombre.toLowerCase();
            if (!clave.contains("password") && !clave.contains("clave") && !nombre.startsWith("_")) {
                valores.put(nombre, String.join(",", v));
            }
        });
        redirect.addFlashAttribute("mgForm", valores);
    }

    /** Página anterior (solo del mismo sitio); si no hay o sería un bucle, el panel. */
    private String destino(HttpServletRequest request) {
        String referer = request.getHeader("Referer");
        if (referer != null) {
            try {
                URI uri = URI.create(referer);
                boolean mismoSitio = uri.getHost() != null && uri.getHost().equalsIgnoreCase(request.getServerName());
                String ruta = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
                boolean mismaRuta = ruta.equals(request.getRequestURI());
                if (mismoSitio && !(mismaRuta && "GET".equals(request.getMethod()))) {
                    return ruta + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
                }
            } catch (IllegalArgumentException ignorada) {
                // Referer mal formado: se usa el destino por defecto.
            }
        }
        return "/panel";
    }
}
