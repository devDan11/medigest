package com.medigestsystem.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.medigestsystem.auditoria.AuditoriaService;
import com.medigestsystem.auditoria.ResultadoAuditoria;
import com.medigestsystem.shared.util.JsonTexto;

/**
 * Respuestas 401/403 de la API en formato Problem Details. Cubre tokens ausentes, inválidos,
 * vencidos o con firma alterada, que se rechazan en el filtro (antes de llegar a un controlador).
 */
@Component
public class ApiSecurityHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final AuditoriaService auditoriaService;

    public ApiSecurityHandlers(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setHeader("WWW-Authenticate", "Bearer");
        escribir(response, request, HttpStatus.UNAUTHORIZED, "NO_AUTENTICADO",
                "Se requiere un token válido. Inicie sesión para obtenerlo.");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        auditoriaService.registrar("ACCESO_DENEGADO", "api", request.getMethod() + " " + request.getRequestURI(),
                ResultadoAuditoria.DENEGADO, "Permisos insuficientes");
        escribir(response, request, HttpStatus.FORBIDDEN, "ACCESO_DENEGADO",
                "No tiene permisos para realizar esta operación.");
    }

    private void escribir(HttpServletResponse response, HttpServletRequest request, HttpStatus estado,
                          String codigo, String detalle) throws IOException {
        response.setStatus(estado.value());
        response.setContentType("application/problem+json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String cuerpo = "{\"type\":\"about:blank\",\"title\":\"" + JsonTexto.escapar(estado.getReasonPhrase())
                + "\",\"status\":" + estado.value()
                + ",\"detail\":\"" + JsonTexto.escapar(detalle)
                + "\",\"instance\":\"" + JsonTexto.escapar(request.getRequestURI())
                + "\",\"codigo\":\"" + codigo + "\"}";
        response.getWriter().write(cuerpo);
    }
}
