package com.medigestsystem.shared.exception;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.RestController;

import com.medigestsystem.auditoria.AuditoriaService;
import com.medigestsystem.auditoria.ResultadoAuditoria;

/**
 * Manejo uniforme de errores de la API en formato Problem Details (RFC 9457).
 * Solo aplica a controladores REST; las páginas Thymeleaf manejan sus errores por separado.
 */
@RestControllerAdvice(annotations = RestController.class)
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final AuditoriaService auditoriaService;

    public GlobalExceptionHandler(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetail> manejarApi(ApiException ex, HttpServletRequest request) {
        return respuesta(ex.getEstado(), ex.getCodigo(), ex.getMessage(), request, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> manejarValidacion(MethodArgumentNotValidException ex,
                                                           HttpServletRequest request) {
        List<Map<String, String>> errores = new ArrayList<>();
        ex.getBindingResult().getFieldErrors().forEach(e -> errores.add(Map.of(
                "campo", e.getField(),
                "mensaje", e.getDefaultMessage() == null ? "Valor inválido" : e.getDefaultMessage())));
        ex.getBindingResult().getGlobalErrors().forEach(e -> errores.add(Map.of(
                "campo", e.getObjectName(),
                "mensaje", e.getDefaultMessage() == null ? "Valor inválido" : e.getDefaultMessage())));
        return respuesta(HttpStatus.BAD_REQUEST, "VALIDACION", "Los datos enviados no son válidos.", request, errores);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> manejarRestricciones(ConstraintViolationException ex,
                                                              HttpServletRequest request) {
        List<Map<String, String>> errores = new ArrayList<>();
        ex.getConstraintViolations().forEach(v -> errores.add(Map.of(
                "campo", v.getPropertyPath().toString(),
                "mensaje", v.getMessage())));
        return respuesta(HttpStatus.BAD_REQUEST, "VALIDACION", "Los datos enviados no son válidos.", request, errores);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ProblemDetail> manejarSolicitudMalFormada(Exception ex, HttpServletRequest request) {
        return respuesta(HttpStatus.BAD_REQUEST, "SOLICITUD_INVALIDA",
                "La solicitud está mal formada o contiene valores no válidos.", request, null);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> manejarAutenticacion(AuthenticationException ex,
                                                              HttpServletRequest request) {
        return respuesta(HttpStatus.UNAUTHORIZED, "NO_AUTENTICADO",
                "Credenciales inválidas o cuenta temporalmente bloqueada.", request, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> manejarAccesoDenegado(AccessDeniedException ex,
                                                               HttpServletRequest request) {
        auditoriaService.registrar("ACCESO_DENEGADO", "api", request.getMethod() + " " + request.getRequestURI(),
                ResultadoAuditoria.DENEGADO, "Permisos insuficientes");
        return respuesta(HttpStatus.FORBIDDEN, "ACCESO_DENEGADO",
                "No tiene permisos para realizar esta operación.", request, null);
    }

    @ExceptionHandler(ConcurrencyFailureException.class)
    public ResponseEntity<ProblemDetail> manejarConcurrencia(ConcurrencyFailureException ex,
                                                             HttpServletRequest request) {
        log.warn("Conflicto de concurrencia en {}: {}", request.getRequestURI(), ex.getMessage());
        return respuesta(HttpStatus.CONFLICT, "CONFLICTO_CONCURRENCIA",
                "Otra operación modificó estos datos al mismo tiempo. Actualice e intente nuevamente.", request, null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> manejarIntegridad(DataIntegrityViolationException ex,
                                                           HttpServletRequest request) {
        log.warn("Violación de integridad de datos en {}: {}", request.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return respuesta(HttpStatus.CONFLICT, "CONFLICTO",
                "La operación entra en conflicto con datos existentes.", request, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> manejarGenerica(Exception ex, HttpServletRequest request) {
        if (ex instanceof ErrorResponse errorResponse) {
            // Excepciones estándar de Spring MVC (404, 405, 415...) conservan su estado.
            ProblemDetail detalle = errorResponse.getBody();
            detalle.setProperty("codigo", "SOLICITUD_INVALIDA");
            detalle.setInstance(URI.create(request.getRequestURI()));
            return ResponseEntity.status(errorResponse.getStatusCode())
                    .headers(errorResponse.getHeaders())
                    .body(detalle);
        }
        log.error("Error no controlado en {} {}", request.getMethod(), request.getRequestURI(), ex);
        return respuesta(HttpStatus.INTERNAL_SERVER_ERROR, "ERROR_INTERNO",
                "Ocurrió un error inesperado. Intente nuevamente más tarde.", request, null);
    }

    private ResponseEntity<ProblemDetail> respuesta(HttpStatus estado, String codigo, String detalleTexto,
                                                    HttpServletRequest request, List<Map<String, String>> errores) {
        ProblemDetail detalle = ProblemDetail.forStatusAndDetail(estado, detalleTexto);
        detalle.setTitle(estado.getReasonPhrase());
        detalle.setInstance(URI.create(request.getRequestURI()));
        detalle.setProperty("codigo", codigo);
        if (errores != null && !errores.isEmpty()) {
            detalle.setProperty("errores", errores);
        }
        return ResponseEntity.status(estado).body(detalle);
    }
}
