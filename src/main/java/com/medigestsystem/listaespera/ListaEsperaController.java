package com.medigestsystem.listaespera;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.medigestsystem.citas.dto.CitaResponse;
import com.medigestsystem.listaespera.dto.ListaEsperaRequest;
import com.medigestsystem.listaespera.dto.ListaEsperaResponse;
import com.medigestsystem.shared.dto.PaginaResponse;

@RestController
@RequestMapping("/api/v1/lista-espera")
@Tag(name = "Lista de espera", description = "Solicitudes de espera y ofertas de horarios liberados")
@SecurityRequirement(name = "bearerAuth")
public class ListaEsperaController {

    private final ListaEsperaService listaEsperaService;

    public ListaEsperaController(ListaEsperaService listaEsperaService) {
        this.listaEsperaService = listaEsperaService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','RECEPCIONISTA','PACIENTE')")
    @Operation(summary = "Registra una solicitud de espera por especialidad (y médico opcional)")
    public ResponseEntity<ListaEsperaResponse> crear(@Valid @RequestBody ListaEsperaRequest solicitud) {
        return ResponseEntity.status(HttpStatus.CREATED).body(listaEsperaService.crear(solicitud));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','RECEPCIONISTA','PACIENTE')")
    @Operation(summary = "Lista solicitudes con su posición en la cola (el paciente solo ve las suyas)")
    public PaginaResponse<ListaEsperaResponse> listar(@RequestParam(required = false) EstadoListaEspera estado,
                                                      @RequestParam(required = false) Integer especialidadId,
                                                      @RequestParam(required = false) Long pacienteId,
                                                      @RequestParam(defaultValue = "0") int pagina,
                                                      @RequestParam(defaultValue = "20") int tamano) {
        return listaEsperaService.listar(estado, especialidadId, pacienteId, pagina, tamano);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','RECEPCIONISTA','PACIENTE')")
    @Operation(summary = "Detalle de una solicitud de espera")
    public ListaEsperaResponse obtener(@PathVariable Long id) {
        return listaEsperaService.obtener(id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','RECEPCIONISTA','PACIENTE')")
    @Operation(summary = "Retira la solicitud de espera (el registro se conserva como CANCELADA)")
    public ResponseEntity<Void> cancelar(@PathVariable Long id) {
        listaEsperaService.cancelar(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/aceptar")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','RECEPCIONISTA','PACIENTE')")
    @Operation(summary = "Acepta el horario ofrecido y crea la cita",
            description = "Solo mientras la oferta esté vigente. Una segunda aceptación es rechazada: no genera citas duplicadas.")
    public ResponseEntity<CitaResponse> aceptar(@PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.CREATED).body(listaEsperaService.aceptar(id));
    }

    @PostMapping("/{id}/rechazar")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','RECEPCIONISTA','PACIENTE')")
    @Operation(summary = "Rechaza el horario ofrecido; la solicitud sigue en espera")
    public ListaEsperaResponse rechazar(@PathVariable Long id) {
        return listaEsperaService.rechazar(id);
    }
}
