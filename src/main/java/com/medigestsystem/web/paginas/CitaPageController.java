package com.medigestsystem.web.paginas;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.medigestsystem.citas.AccionAsistencia;
import com.medigestsystem.citas.CitaService;
import com.medigestsystem.citas.EstadoCita;
import com.medigestsystem.citas.dto.CancelarRequest;
import com.medigestsystem.citas.dto.CitaRequest;
import com.medigestsystem.citas.dto.CitaResponse;
import com.medigestsystem.citas.dto.ReprogramarRequest;
import com.medigestsystem.especialidades.EspecialidadService;
import com.medigestsystem.shared.exception.SolicitudInvalidaException;

/**
 * Páginas de citas, comunes a todos los roles: cada servicio aplica las reglas de acceso
 * (un paciente solo ve sus citas, un médico las de su agenda) y calcula las acciones permitidas.
 */
@Controller
public class CitaPageController {

    private static final String REDIRIGIR_DETALLE = "redirect:/citas/";

    private final CitaService citaService;
    private final EspecialidadService especialidadService;
    private final ZoneId zona;

    public CitaPageController(CitaService citaService, EspecialidadService especialidadService, ZoneId zonaClinica) {
        this.citaService = citaService;
        this.especialidadService = especialidadService;
        this.zona = zonaClinica;
    }

    @GetMapping("/citas")
    public String listar(@RequestParam(required = false) EstadoCita estado,
                         @RequestParam(required = false) Integer especialidadId,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                         @RequestParam(defaultValue = "0") int pagina,
                         Model model) {
        OffsetDateTime inicio = desde == null ? null : desde.atStartOfDay(zona).toOffsetDateTime();
        OffsetDateTime fin = hasta == null ? null : hasta.plusDays(1).atStartOfDay(zona).toOffsetDateTime();
        model.addAttribute("pagina", citaService.listar(null, null, especialidadId, null, estado, inicio, fin,
                Math.max(pagina, 0), 15));
        model.addAttribute("estados", EstadoCita.values());
        model.addAttribute("especialidades", especialidadService.listar(false));
        model.addAttribute("fEstado", estado);
        model.addAttribute("fEspecialidad", especialidadId);
        model.addAttribute("fDesde", desde);
        model.addAttribute("fHasta", hasta);
        LocalDate hoy = LocalDate.now(zona);
        model.addAttribute("hoy", hoy);
        model.addAttribute("finSemana", hoy.plusDays(6));
        return "citas/lista";
    }

    @GetMapping("/citas/nueva")
    public String nueva(Model model) {
        model.addAttribute("especialidades", especialidadService.listar(false));
        return "citas/nueva";
    }

    @PostMapping("/citas/nueva")
    public String reservar(@RequestParam(required = false) Long pacienteId,
                           @RequestParam Long medicoId,
                           @RequestParam Integer especialidadId,
                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime inicio,
                           @RequestParam(required = false) String motivo,
                           RedirectAttributes redirect) {
        CitaResponse cita = citaService.reservar(new CitaRequest(pacienteId, medicoId, especialidadId, inicio,
                vacioANulo(motivo)));
        redirect.addFlashAttribute("mgOk", "Cita reservada. Código " + cita.codigoReserva() + ".");
        return REDIRIGIR_DETALLE + cita.id();
    }

    @GetMapping("/citas/{id}")
    public String detalle(@PathVariable Long id, Model model) {
        model.addAttribute("c", citaService.obtener(id));
        model.addAttribute("historial", citaService.historial(id));
        return "citas/detalle";
    }

    @PostMapping("/citas/{id}/confirmar")
    public String confirmar(@PathVariable Long id, RedirectAttributes redirect) {
        citaService.confirmar(id);
        redirect.addFlashAttribute("mgOk", "Cita confirmada.");
        return REDIRIGIR_DETALLE + id;
    }

    @PostMapping("/citas/{id}/cancelar")
    public String cancelar(@PathVariable Long id, @RequestParam(required = false) String motivo,
                           RedirectAttributes redirect) {
        citaService.cancelar(id, new CancelarRequest(exigirMotivo(motivo)));
        redirect.addFlashAttribute("mgOk", "Cita cancelada.");
        return REDIRIGIR_DETALLE + id;
    }

    @PostMapping("/citas/{id}/reprogramar")
    public String reprogramar(@PathVariable Long id,
                              @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime nuevoInicio,
                              @RequestParam(required = false) String motivo,
                              RedirectAttributes redirect) {
        citaService.reprogramar(id, new ReprogramarRequest(nuevoInicio, exigirMotivo(motivo)));
        redirect.addFlashAttribute("mgOk", "Cita reprogramada.");
        return REDIRIGIR_DETALLE + id;
    }

    @PostMapping("/citas/{id}/asistencia")
    public String asistencia(@PathVariable Long id, @RequestParam AccionAsistencia accion, RedirectAttributes redirect) {
        citaService.registrarAsistencia(id, accion);
        redirect.addFlashAttribute("mgOk", "Estado de la cita actualizado.");
        return REDIRIGIR_DETALLE + id;
    }

    @GetMapping("/calendario")
    public String calendario(Model model) {
        return "calendario";
    }

    private static String vacioANulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }

    private static String exigirMotivo(String motivo) {
        String limpio = vacioANulo(motivo);
        if (limpio == null) {
            throw new SolicitudInvalidaException("MOTIVO_REQUERIDO", "Indique el motivo.");
        }
        if (limpio.length() > 300) {
            throw new SolicitudInvalidaException("MOTIVO_LARGO", "El motivo admite hasta 300 caracteres.");
        }
        return limpio;
    }
}
