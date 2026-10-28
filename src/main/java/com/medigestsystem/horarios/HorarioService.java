package com.medigestsystem.horarios;

import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medigestsystem.auditoria.AuditoriaService;
import com.medigestsystem.auditoria.ResultadoAuditoria;
import com.medigestsystem.citas.Cita;
import com.medigestsystem.citas.CitaRepository;
import com.medigestsystem.citas.EstadoCita;
import com.medigestsystem.horarios.dto.FranjaRequest;
import com.medigestsystem.horarios.dto.HorarioRequest;
import com.medigestsystem.horarios.dto.HorarioResponse;
import com.medigestsystem.medicos.Medico;
import com.medigestsystem.medicos.MedicoRepository;
import com.medigestsystem.shared.exception.RecursoNoEncontradoException;
import com.medigestsystem.shared.exception.ReglaNegocioException;
import com.medigestsystem.shared.exception.SolicitudInvalidaException;
import com.medigestsystem.shared.util.FechaHoraUtil;

@Service
public class HorarioService {

    private static final String ENTIDAD = "horarios_medicos";
    private static final Set<EstadoCita> NO_OCUPAN = EnumSet.of(EstadoCita.CANCELADA, EstadoCita.NO_ASISTIO);

    private final HorarioMedicoRepository horarioRepository;
    private final MedicoRepository medicoRepository;
    private final CitaRepository citaRepository;
    private final AuditoriaService auditoriaService;
    private final FechaHoraUtil fechaHora;

    public HorarioService(HorarioMedicoRepository horarioRepository, MedicoRepository medicoRepository,
                          CitaRepository citaRepository, AuditoriaService auditoriaService,
                          FechaHoraUtil fechaHora) {
        this.horarioRepository = horarioRepository;
        this.medicoRepository = medicoRepository;
        this.citaRepository = citaRepository;
        this.auditoriaService = auditoriaService;
        this.fechaHora = fechaHora;
    }

    @Transactional(readOnly = true)
    public List<HorarioResponse> obtener(Long medicoId) {
        buscarMedico(medicoId);
        return horarioRepository.findByMedicoIdAndActivoTrueOrderByDiaSemanaAscHoraInicioAsc(medicoId)
                .stream().map(HorarioResponse::de).toList();
    }

    /**
     * Reemplaza el horario semanal completo. Se valida cada franja, que no se solapen entre sí y que
     * ninguna cita futura vigente quede fuera del nuevo horario (no se invalidan citas ya reservadas).
     */
    @Transactional
    public List<HorarioResponse> reemplazar(Long medicoId, HorarioRequest solicitud) {
        Medico medico = buscarMedico(medicoId);
        List<FranjaRequest> franjas = solicitud.franjas().stream()
                .sorted(Comparator.comparing(FranjaRequest::diaSemana).thenComparing(FranjaRequest::horaInicio))
                .toList();

        validarFranjas(franjas, medico.getDuracionConsultaMin());
        verificarCitasFuturas(medicoId, franjas);

        // Hibernate ejecuta los INSERT antes que los DELETE: se borra y se sincroniza primero
        // para no chocar con la restricción única (médico, día, hora de inicio).
        List<HorarioMedico> actuales = horarioRepository.findByMedicoId(medicoId);
        horarioRepository.deleteAll(actuales);
        horarioRepository.flush();

        List<HorarioMedico> nuevos = franjas.stream().map(f -> {
            HorarioMedico h = new HorarioMedico();
            h.setMedico(medico);
            h.setDiaSemana(f.diaSemana());
            h.setHoraInicio(f.horaInicio());
            h.setHoraFin(f.horaFin());
            h.setDescansoInicio(f.descansoInicio());
            h.setDescansoFin(f.descansoFin());
            h.setActivo(true);
            return h;
        }).toList();
        List<HorarioMedico> guardados = horarioRepository.saveAll(nuevos);

        auditoriaService.registrar("HORARIO_REEMPLAZADO", ENTIDAD, String.valueOf(medicoId),
                ResultadoAuditoria.EXITO, franjas.size() + " franja(s)");
        return guardados.stream().map(HorarioResponse::de).toList();
    }

    // ------------------------------------------------------------------ validaciones

    static void validarFranjas(List<FranjaRequest> franjas, int duracionConsultaMin) {
        FranjaRequest anterior = null;
        for (FranjaRequest f : franjas) {
            if (!f.horaFin().isAfter(f.horaInicio())) {
                throw new SolicitudInvalidaException("FRANJA_INVALIDA",
                        "En el día " + f.diaSemana() + " la hora de fin debe ser posterior a la de inicio.");
            }
            boolean hayIni = f.descansoInicio() != null;
            boolean hayFin = f.descansoFin() != null;
            if (hayIni != hayFin) {
                throw new SolicitudInvalidaException("DESCANSO_INVALIDO",
                        "El descanso requiere hora de inicio y de fin.");
            }
            if (hayIni) {
                if (!f.descansoFin().isAfter(f.descansoInicio())
                        || f.descansoInicio().isBefore(f.horaInicio())
                        || f.descansoFin().isAfter(f.horaFin())) {
                    throw new SolicitudInvalidaException("DESCANSO_INVALIDO",
                            "En el día " + f.diaSemana() + " el descanso debe estar dentro de la franja de atención.");
                }
            }
            if (!cabeUnaConsulta(f, duracionConsultaMin)) {
                throw new SolicitudInvalidaException("FRANJA_CORTA",
                        "En el día " + f.diaSemana() + " la franja no admite ni una consulta de "
                                + duracionConsultaMin + " minutos.");
            }
            if (anterior != null && anterior.diaSemana().equals(f.diaSemana())
                    && f.horaInicio().isBefore(anterior.horaFin())) {
                throw new SolicitudInvalidaException("FRANJAS_SOLAPADAS",
                        "Hay franjas solapadas el día " + f.diaSemana() + ".");
            }
            anterior = f;
        }
    }

    private static boolean cabeUnaConsulta(FranjaRequest f, int minutos) {
        if (f.descansoInicio() == null) {
            return minutosEntre(f.horaInicio(), f.horaFin()) >= minutos;
        }
        return minutosEntre(f.horaInicio(), f.descansoInicio()) >= minutos
                || minutosEntre(f.descansoFin(), f.horaFin()) >= minutos;
    }

    private static long minutosEntre(LocalTime a, LocalTime b) {
        return java.time.Duration.between(a, b).toMinutes();
    }

    private void verificarCitasFuturas(Long medicoId, List<FranjaRequest> franjas) {
        List<Cita> futuras = citaRepository.findByMedicoIdAndInicioUtcAfterAndEstadoNotIn(
                medicoId, fechaHora.ahoraUtc(), NO_OCUPAN);
        long fuera = futuras.stream().filter(c -> !cabeEnHorario(c, franjas)).count();
        if (fuera > 0) {
            throw new ReglaNegocioException("HORARIO_CONFLICTO_CITAS",
                    "El nuevo horario deja fuera " + fuera + " cita(s) futura(s) ya reservada(s). "
                            + "Reprográmelas o cancélelas primero.");
        }
    }

    private boolean cabeEnHorario(Cita c, List<FranjaRequest> franjas) {
        ZonedDateTime ini = fechaHora.aZonaClinica(c.getInicioUtc());
        ZonedDateTime fin = fechaHora.aZonaClinica(c.getFinUtc());
        if (!ini.toLocalDate().equals(fin.toLocalDate())) {
            return false;
        }
        int dia = ini.getDayOfWeek().getValue();
        LocalTime desde = ini.toLocalTime();
        LocalTime hasta = fin.toLocalTime();
        return franjas.stream().anyMatch(f -> f.diaSemana() == dia
                && !desde.isBefore(f.horaInicio())
                && !hasta.isAfter(f.horaFin())
                && (f.descansoInicio() == null
                    || !desde.isBefore(f.descansoFin()) || !hasta.isAfter(f.descansoInicio())));
    }

    private Medico buscarMedico(Long id) {
        return medicoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el médico solicitado."));
    }
}
