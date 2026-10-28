package com.medigestsystem.web.paginas;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.medigestsystem.consultorios.ConsultorioService;
import com.medigestsystem.especialidades.EspecialidadService;
import com.medigestsystem.horarios.BloqueoService;
import com.medigestsystem.horarios.HorarioService;
import com.medigestsystem.horarios.dto.FranjaRequest;
import com.medigestsystem.horarios.dto.HorarioRequest;
import com.medigestsystem.horarios.dto.HorarioResponse;
import com.medigestsystem.medicos.Medico;
import com.medigestsystem.medicos.MedicoService;
import com.medigestsystem.medicos.dto.MedicoResponse;
import com.medigestsystem.shared.exception.SolicitudInvalidaException;

/** Médicos: el personal los consulta; el administrador los crea, edita y define su horario semanal. */
@Controller
public class MedicoPageController {

    private static final String[] DIAS = {"Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo"};

    private final MedicoService medicoService;
    private final HorarioService horarioService;
    private final BloqueoService bloqueoService;
    private final EspecialidadService especialidadService;
    private final ConsultorioService consultorioService;
    private final ValidadorWeb validador;

    public MedicoPageController(MedicoService medicoService, HorarioService horarioService,
                                BloqueoService bloqueoService, EspecialidadService especialidadService,
                                ConsultorioService consultorioService, ValidadorWeb validador) {
        this.medicoService = medicoService;
        this.horarioService = horarioService;
        this.bloqueoService = bloqueoService;
        this.especialidadService = especialidadService;
        this.consultorioService = consultorioService;
        this.validador = validador;
    }

    @GetMapping("/medicos")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','RECEPCIONISTA')")
    public String listar(@RequestParam(required = false) String q, @RequestParam(required = false) Integer especialidadId,
                         @RequestParam(required = false) Boolean activo, @RequestParam(defaultValue = "0") int pagina,
                         Model model) {
        String texto = q == null || q.isBlank() ? null : q.trim();
        model.addAttribute("pagina", medicoService.listar(texto, especialidadId, activo, Math.max(pagina, 0), 15));
        model.addAttribute("especialidades", especialidadService.listar(false));
        model.addAttribute("fQ", texto);
        model.addAttribute("fEspecialidad", especialidadId);
        model.addAttribute("fActivo", activo);
        return "medicos/lista";
    }

    /** Acceso directo de un médico a su propia ficha y horario. */
    @GetMapping("/medicos/mio")
    @PreAuthorize("hasRole('MEDICO')")
    public String propio() {
        Medico m = medicoService.medicoActual();
        if (m == null) {
            throw new SolicitudInvalidaException("SIN_FICHA", "Su cuenta no tiene una ficha de médico asociada.");
        }
        return "redirect:/medicos/" + m.getId();
    }

    @GetMapping("/medicos/nuevo")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public String nuevo(Model model) {
        cargarFormulario(model, null, "/medicos/nuevo", "Nuevo médico");
        return "medicos/form";
    }

    @PostMapping("/medicos/nuevo")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public String crear(@ModelAttribute MedicoForm form, RedirectAttributes redirect) {
        MedicoResponse creado = medicoService.crear(validador.validar(form.aSolicitud(true)));
        redirect.addFlashAttribute("mgOk", "Médico registrado. Defina ahora su horario semanal.");
        return "redirect:/medicos/" + creado.id();
    }

    @GetMapping("/medicos/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','RECEPCIONISTA','MEDICO')")
    public String detalle(@PathVariable Long id, Model model) {
        MedicoResponse m = medicoService.obtener(id);
        List<HorarioResponse> horarios = horarioService.obtener(id);
        model.addAttribute("m", m);
        model.addAttribute("horarios", horarios);
        model.addAttribute("filas", filasDeHorario(horarios));
        model.addAttribute("bloqueos", bloqueoService.listar(id, null, null, null, null, 0, 10));
        Medico propio = medicoService.medicoActual();
        model.addAttribute("esFichaPropia", propio != null && propio.getId().equals(id));
        return "medicos/detalle";
    }

    @GetMapping("/medicos/{id}/editar")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public String editar(@PathVariable Long id, Model model) {
        cargarFormulario(model, medicoService.obtener(id), "/medicos/" + id + "/editar", "Editar médico");
        return "medicos/form";
    }

    @PostMapping("/medicos/{id}/editar")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public String actualizar(@PathVariable Long id, @ModelAttribute MedicoForm form, RedirectAttributes redirect) {
        medicoService.actualizar(id, validador.validar(form.aSolicitud(false)));
        redirect.addFlashAttribute("mgOk", "Datos del médico actualizados.");
        return "redirect:/medicos/" + id;
    }

    @PostMapping("/medicos/{id}/estado")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public String cambiarEstado(@PathVariable Long id, @RequestParam boolean activo, RedirectAttributes redirect) {
        medicoService.cambiarEstado(id, activo);
        redirect.addFlashAttribute("mgOk", activo ? "Médico activado." : "Médico desactivado.");
        return "redirect:/medicos/" + id;
    }

    /**
     * Reemplaza el horario semanal. Llegan filas paralelas (día, inicio, fin, descanso); las filas sin hora de
     * inicio ni de fin se ignoran. El servicio rechaza cruces y cualquier cambio que deje fuera citas ya reservadas.
     */
    @PostMapping("/medicos/{id}/horarios")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public String guardarHorario(@PathVariable Long id,
                                 @RequestParam(name = "dia", required = false) List<Integer> dias,
                                 @RequestParam(name = "inicio", required = false) List<String> inicios,
                                 @RequestParam(name = "fin", required = false) List<String> fines,
                                 @RequestParam(name = "descansoInicio", required = false) List<String> descansosIni,
                                 @RequestParam(name = "descansoFin", required = false) List<String> descansosFin,
                                 RedirectAttributes redirect) {
        List<FranjaRequest> franjas = new ArrayList<>();
        int filas = dias == null ? 0 : dias.size();
        for (int i = 0; i < filas; i++) {
            String ini = valorEn(inicios, i);
            String fin = valorEn(fines, i);
            if (ini == null && fin == null) {
                continue;
            }
            franjas.add(new FranjaRequest(dias.get(i), hora(ini), hora(fin), hora(valorEn(descansosIni, i)),
                    hora(valorEn(descansosFin, i))));
        }
        horarioService.reemplazar(id, validador.validar(new HorarioRequest(franjas)));
        redirect.addFlashAttribute("mgOk", "Horario semanal actualizado.");
        return "redirect:/medicos/" + id;
    }

    // ------------------------------------------------------------------ apoyo

    private void cargarFormulario(Model model, MedicoResponse m, String accion, String titulo) {
        model.addAttribute("m", m);
        model.addAttribute("accion", accion);
        model.addAttribute("titulo", titulo);
        model.addAttribute("especialidades", especialidadService.listar(false));
        model.addAttribute("consultorios", consultorioService.listar(false));
        model.addAttribute("idsEspecialidad", m == null ? List.of()
                : m.especialidades().stream().map(e -> e.id()).collect(Collectors.toList()));
    }

    /** Una fila por cada franja existente más una vacía por día, para poder agregar otra. */
    private List<Map<String, Object>> filasDeHorario(List<HorarioResponse> horarios) {
        List<Map<String, Object>> filas = new ArrayList<>();
        for (int dia = 1; dia <= 7; dia++) {
            final int d = dia;
            for (HorarioResponse h : horarios.stream().filter(x -> x.diaSemana() == d).toList()) {
                filas.add(fila(d, h));
            }
            filas.add(fila(d, null));
        }
        return filas;
    }

    private Map<String, Object> fila(int dia, HorarioResponse h) {
        return Map.of(
                "dia", dia,
                "nombreDia", DIAS[dia - 1],
                "inicio", h == null ? "" : h.horaInicio().toString(),
                "fin", h == null ? "" : h.horaFin().toString(),
                "descansoInicio", h == null || h.descansoInicio() == null ? "" : h.descansoInicio().toString(),
                "descansoFin", h == null || h.descansoFin() == null ? "" : h.descansoFin().toString());
    }

    private static String valorEn(List<String> lista, int i) {
        if (lista == null || i >= lista.size() || lista.get(i) == null || lista.get(i).isBlank()) {
            return null;
        }
        return lista.get(i).trim();
    }

    private static LocalTime hora(String texto) {
        if (texto == null) {
            return null;
        }
        try {
            return LocalTime.parse(texto);
        } catch (DateTimeParseException e) {
            throw new SolicitudInvalidaException("HORA_INVALIDA", "Hay una hora con formato inválido en el horario.");
        }
    }

}
