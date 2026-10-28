package com.medigestsystem.web.datos;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.medigestsystem.citas.CitaService;
import com.medigestsystem.citas.dto.CitaResumenResponse;
import com.medigestsystem.disponibilidad.DisponibilidadService;
import com.medigestsystem.disponibilidad.dto.DiaDisponibilidadResponse;
import com.medigestsystem.disponibilidad.dto.MedicoDisponibleResponse;
import com.medigestsystem.medicos.MedicoRepository;
import com.medigestsystem.pacientes.PacienteService;
import com.medigestsystem.security.SecurityUtils;
import com.medigestsystem.shared.exception.SolicitudInvalidaException;
import com.medigestsystem.usuarios.NombreRol;
import com.medigestsystem.web.Formato;

/**
 * Datos JSON para el JavaScript de las páginas (selector de horarios, buscador de pacientes, calendario).
 * Usan la sesión web (no JWT) y delegan en los mismos servicios que la API, que aplican los permisos.
 */
@RestController
@RequestMapping("/app/datos")
public class DatosWebController {

    private static final int PAGINAS_MAXIMAS_CALENDARIO = 5;

    private final DisponibilidadService disponibilidadService;
    private final PacienteService pacienteService;
    private final CitaService citaService;
    private final MedicoRepository medicoRepository;
    private final Formato formato;
    private final ZoneId zona;

    public DatosWebController(DisponibilidadService disponibilidadService, PacienteService pacienteService,
                              CitaService citaService, MedicoRepository medicoRepository, Formato formato,
                              ZoneId zonaClinica) {
        this.disponibilidadService = disponibilidadService;
        this.pacienteService = pacienteService;
        this.citaService = citaService;
        this.medicoRepository = medicoRepository;
        this.formato = formato;
        this.zona = zonaClinica;
    }

    @GetMapping("/medicos-disponibles")
    @PreAuthorize("isAuthenticated()")
    public List<MedicoDisponibleResponse> medicosDisponibles(
            @RequestParam Integer especialidadId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return disponibilidadService.medicosDisponibles(especialidadId, desde, hasta);
    }

    @GetMapping("/medicos/{medicoId}/horarios")
    @PreAuthorize("isAuthenticated()")
    public List<DiaDisponibilidadResponse> horarios(
            @PathVariable Long medicoId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return disponibilidadService.disponibilidadDeMedico(medicoId, desde, hasta);
    }

    /** Médicos activos de una especialidad (para filtrar la lista de espera). */
    @GetMapping("/medicos")
    @PreAuthorize("isAuthenticated()")
    public List<OpcionResponse> medicosDeEspecialidad(@RequestParam Integer especialidadId) {
        return medicoRepository.findByActivoTrueAndEspecialidadesId(especialidadId).stream()
                .map(m -> new OpcionResponse(m.getId(), m.getNombres() + " " + m.getApellidos()))
                .toList();
    }

    @GetMapping("/pacientes")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','RECEPCIONISTA')")
    public List<OpcionResponse> buscarPacientes(@RequestParam String q) {
        if (q == null || q.trim().length() < 2) {
            return List.of();
        }
        return pacienteService.buscar(q.trim(), true, 0, 10).contenido().stream()
                .map(p -> new OpcionResponse(p.id(),
                        p.apellidos() + ", " + p.nombres() + " · " + p.numeroDocumento()))
                .toList();
    }

    /**
     * Citas para FullCalendar. El calendario trabaja con la hora local de la clínica (zona "UTC" en el cliente
     * sobre horas sin desplazamiento), por eso los límites se interpretan como hora local de Lima.
     */
    @GetMapping("/eventos")
    @PreAuthorize("isAuthenticated()")
    public List<EventoCalendario> eventos(@RequestParam String start, @RequestParam String end) {
        OffsetDateTime desde = aHoraLocal(start);
        OffsetDateTime hasta = aHoraLocal(end);
        boolean verPaciente = !SecurityUtils.tieneRol(NombreRol.PACIENTE);
        List<EventoCalendario> eventos = new ArrayList<>();
        for (int pagina = 0; pagina < PAGINAS_MAXIMAS_CALENDARIO; pagina++) {
            var resultado = citaService.listar(null, null, null, null, null, desde, hasta, pagina, 100);
            for (CitaResumenResponse c : resultado.contenido()) {
                eventos.add(aEvento(c, verPaciente));
            }
            if (pagina + 1 >= resultado.totalPaginas()) {
                break;
            }
        }
        return eventos;
    }

    private EventoCalendario aEvento(CitaResumenResponse c, boolean verPaciente) {
        String titulo = (verPaciente ? c.paciente() : c.medico()) + " · " + c.especialidad();
        return new EventoCalendario(
                String.valueOf(c.id()),
                titulo,
                c.inicio().toLocalDateTime().toString(),
                c.fin().toLocalDateTime().toString(),
                colorDe(c),
                "/citas/" + c.id(),
                formato.estado(c.estado()));
    }

    private static String colorDe(CitaResumenResponse c) {
        return switch (c.estado()) {
            case PENDIENTE -> "#e0a800";
            case CONFIRMADA -> "#0d6efd";
            case EN_ESPERA -> "#0dcaf0";
            case EN_ATENCION -> "#212529";
            case ATENDIDA -> "#198754";
            case CANCELADA -> "#dc3545";
            case NO_ASISTIO -> "#6c757d";
        };
    }

    private OffsetDateTime aHoraLocal(String valor) {
        if (valor == null || valor.length() < 19) {
            throw new SolicitudInvalidaException("RANGO_INVALIDO", "Rango de fechas inválido.");
        }
        try {
            return LocalDateTime.parse(valor.substring(0, 19)).atZone(zona).toOffsetDateTime();
        } catch (java.time.format.DateTimeParseException e) {
            throw new SolicitudInvalidaException("RANGO_INVALIDO", "Rango de fechas inválido.");
        }
    }

    public record OpcionResponse(Long id, String texto) {
    }

    public record EventoCalendario(String id, String title, String start, String end, String color, String url,
                                   String estado) {
    }
}
