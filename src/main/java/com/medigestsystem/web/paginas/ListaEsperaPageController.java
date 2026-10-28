package com.medigestsystem.web.paginas;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.medigestsystem.citas.dto.CitaResponse;
import com.medigestsystem.especialidades.EspecialidadService;
import com.medigestsystem.listaespera.EstadoListaEspera;
import com.medigestsystem.listaespera.FranjaPreferida;
import com.medigestsystem.listaespera.ListaEsperaService;
import com.medigestsystem.listaespera.dto.ListaEsperaRequest;

/** Lista de espera: el paciente se anota y responde a los horarios ofrecidos; el personal la administra. */
@Controller
public class ListaEsperaPageController {

    private static final String REDIRIGIR_LISTA = "redirect:/lista-espera";

    private final ListaEsperaService listaEsperaService;
    private final EspecialidadService especialidadService;

    public ListaEsperaPageController(ListaEsperaService listaEsperaService, EspecialidadService especialidadService) {
        this.listaEsperaService = listaEsperaService;
        this.especialidadService = especialidadService;
    }

    @GetMapping("/lista-espera")
    public String listar(@RequestParam(required = false) EstadoListaEspera estado,
                         @RequestParam(defaultValue = "0") int pagina, Model model) {
        model.addAttribute("pagina", listaEsperaService.listar(estado, null, null, Math.max(pagina, 0), 15));
        model.addAttribute("estados", EstadoListaEspera.values());
        model.addAttribute("fEstado", estado);
        model.addAttribute("especialidades", especialidadService.listar(false));
        model.addAttribute("franjas", FranjaPreferida.values());
        model.addAttribute("hoy", LocalDate.now());
        return "lista-espera";
    }

    @PostMapping("/lista-espera")
    public String crear(@RequestParam(required = false) Long pacienteId,
                        @RequestParam Integer especialidadId,
                        @RequestParam(required = false) Long medicoId,
                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta,
                        @RequestParam(required = false) FranjaPreferida franja,
                        RedirectAttributes redirect) {
        listaEsperaService.crear(new ListaEsperaRequest(pacienteId, especialidadId, medicoId, fechaDesde, fechaHasta,
                franja));
        redirect.addFlashAttribute("mgOk", "Solicitud registrada en la lista de espera.");
        return REDIRIGIR_LISTA;
    }

    @PostMapping("/lista-espera/{id}/cancelar")
    public String cancelar(@PathVariable Long id, RedirectAttributes redirect) {
        listaEsperaService.cancelar(id);
        redirect.addFlashAttribute("mgOk", "Solicitud retirada de la lista de espera.");
        return REDIRIGIR_LISTA;
    }

    @PostMapping("/lista-espera/{id}/aceptar")
    public String aceptar(@PathVariable Long id, RedirectAttributes redirect) {
        CitaResponse cita = listaEsperaService.aceptar(id);
        redirect.addFlashAttribute("mgOk", "Horario aceptado. Su cita quedó reservada (" + cita.codigoReserva() + ").");
        return "redirect:/citas/" + cita.id();
    }

    @PostMapping("/lista-espera/{id}/rechazar")
    public String rechazar(@PathVariable Long id, RedirectAttributes redirect) {
        listaEsperaService.rechazar(id);
        redirect.addFlashAttribute("mgOk", "Horario rechazado. Sigue en la lista de espera.");
        return REDIRIGIR_LISTA;
    }
}
