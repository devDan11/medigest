package com.medigestsystem.notificaciones;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.medigestsystem.notificaciones.dto.NotificacionResponse;
import com.medigestsystem.shared.dto.PaginaResponse;

@RestController
@RequestMapping("/api/v1/notificaciones")
@PreAuthorize("hasRole('ADMINISTRADOR')")
@Tag(name = "Notificaciones", description = "Cola de correos del sistema (solo administrador)")
@SecurityRequirement(name = "bearerAuth")
public class NotificacionController {

    private final NotificacionService notificacionService;

    public NotificacionController(NotificacionService notificacionService) {
        this.notificacionService = notificacionService;
    }

    @GetMapping
    @Operation(summary = "Lista las notificaciones, de la más reciente a la más antigua")
    public PaginaResponse<NotificacionResponse> listar(@RequestParam(required = false) EstadoNotificacion estado,
                                                       @RequestParam(required = false) TipoNotificacion tipo,
                                                       @RequestParam(defaultValue = "0") int pagina,
                                                       @RequestParam(defaultValue = "20") int tamano) {
        return notificacionService.listar(estado, tipo, pagina, tamano);
    }

    @PostMapping("/{id}/reintentar")
    @Operation(summary = "Devuelve a la cola una notificación fallida u omitida")
    public NotificacionResponse reintentar(@PathVariable Long id) {
        return notificacionService.reintentar(id);
    }
}
