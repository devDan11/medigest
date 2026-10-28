package com.medigestsystem.dashboard;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medigestsystem.citas.CitaRepository;
import com.medigestsystem.citas.CitaService;
import com.medigestsystem.citas.EstadoCita;
import com.medigestsystem.citas.dto.CitaResumenResponse;
import com.medigestsystem.dashboard.DashboardResponse.DiaConteo;
import com.medigestsystem.listaespera.EstadoListaEspera;
import com.medigestsystem.listaespera.ListaEsperaService;
import com.medigestsystem.medicos.MedicoRepository;
import com.medigestsystem.pacientes.PacienteRepository;
import com.medigestsystem.security.SecurityUtils;
import com.medigestsystem.shared.util.FechaHoraUtil;
import com.medigestsystem.usuarios.NombreRol;

/** Indicadores del panel de inicio, acotados al rol del usuario (mínimo privilegio). */
@Service
public class DashboardService {

    private static final long SIN_FILTRO = -1L;
    private static final long NINGUNO = -2L;
    private static final int DIAS_VISTA = 7;
    private static final Set<EstadoCita> FINALES = Set.of(EstadoCita.CANCELADA, EstadoCita.ATENDIDA, EstadoCita.NO_ASISTIO);

    private final CitaRepository citaRepository;
    private final CitaService citaService;
    private final ListaEsperaService listaEsperaService;
    private final MedicoRepository medicoRepository;
    private final PacienteRepository pacienteRepository;
    private final FechaHoraUtil fechaHora;
    private final ZoneId zona;

    public DashboardService(CitaRepository citaRepository, CitaService citaService,
                            ListaEsperaService listaEsperaService, MedicoRepository medicoRepository,
                            PacienteRepository pacienteRepository, FechaHoraUtil fechaHora, ZoneId zonaClinica) {
        this.citaRepository = citaRepository;
        this.citaService = citaService;
        this.listaEsperaService = listaEsperaService;
        this.medicoRepository = medicoRepository;
        this.pacienteRepository = pacienteRepository;
        this.fechaHora = fechaHora;
        this.zona = zonaClinica;
    }

    @Transactional(readOnly = true)
    public DashboardResponse resumen() {
        Long usuarioId = SecurityUtils.usuarioIdActual()
                .orElseThrow(() -> new AccessDeniedException("Se requiere autenticación"));
        boolean personal = SecurityUtils.tieneRol(NombreRol.ADMINISTRADOR)
                || SecurityUtils.tieneRol(NombreRol.RECEPCIONISTA);
        long medicoId = SIN_FILTRO;
        long pacienteId = SIN_FILTRO;
        if (!personal) {
            if (SecurityUtils.tieneRol(NombreRol.MEDICO)) {
                medicoId = medicoRepository.buscarIdPorUsuario(usuarioId).orElse(NINGUNO);
            } else if (SecurityUtils.tieneRol(NombreRol.PACIENTE)) {
                pacienteId = pacienteRepository.buscarIdPorUsuario(usuarioId).orElse(NINGUNO);
            } else {
                throw new AccessDeniedException("Sin permiso para ver el panel");
            }
        }

        LocalDate hoy = fechaHora.hoyEnClinica();
        Instant inicioHoy = hoy.atStartOfDay(zona).toInstant();
        Instant inicioManiana = hoy.plusDays(1).atStartOfDay(zona).toInstant();
        Instant finVista = hoy.plusDays(DIAS_VISTA).atStartOfDay(zona).toInstant();
        Instant ahora = fechaHora.ahoraUtc();

        Map<EstadoCita, Long> porEstado = new EnumMap<>(EstadoCita.class);
        for (EstadoCita e : EstadoCita.values()) {
            porEstado.put(e, 0L);
        }
        long citasHoy = 0;
        for (Object[] fila : citaRepository.contarPorEstado(inicioHoy, inicioManiana, medicoId, pacienteId)) {
            long total = ((Number) fila[1]).longValue();
            porEstado.put((EstadoCita) fila[0], total);
            citasHoy += total;
        }

        long pendientes = 0;
        for (Object[] fila : citaRepository.contarPorEstado(ahora, ahora.plusSeconds(30L * 24 * 3600), medicoId, pacienteId)) {
            if (fila[0] == EstadoCita.PENDIENTE) {
                pendientes = ((Number) fila[1]).longValue();
            }
        }

        long[] porDia = new long[DIAS_VISTA];
        for (Instant inicio : citaRepository.iniciosEntre(inicioHoy, finVista, medicoId, pacienteId)) {
            int indice = (int) java.time.temporal.ChronoUnit.DAYS.between(hoy, LocalDate.ofInstant(inicio, zona));
            if (indice >= 0 && indice < DIAS_VISTA) {
                porDia[indice]++;
            }
        }
        List<DiaConteo> dias = new ArrayList<>();
        for (int i = 0; i < DIAS_VISTA; i++) {
            dias.add(new DiaConteo(hoy.plusDays(i), porDia[i]));
        }

        List<CitaResumenResponse> proximas = citaService.listar(null, null, null, null, null,
                        fechaHora.aZonaClinica(ahora).toOffsetDateTime(), null, 0, 20)
                .contenido().stream()
                .filter(c -> !FINALES.contains(c.estado()))
                .limit(8)
                .toList();

        Long espera = personal || SecurityUtils.tieneRol(NombreRol.PACIENTE)
                ? listaEsperaService.listar(EstadoListaEspera.ACTIVA, null, null, 0, 1).totalElementos()
                : null;
        return new DashboardResponse(personal, citasHoy, pendientes, porEstado, dias,
                personal ? pacienteRepository.countByActivo(true) : null,
                personal ? medicoRepository.countByActivo(true) : null,
                espera,
                proximas);
    }
}
