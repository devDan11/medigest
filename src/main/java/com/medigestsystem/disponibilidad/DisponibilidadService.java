package com.medigestsystem.disponibilidad;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medigestsystem.citas.Cita;
import com.medigestsystem.citas.CitaRepository;
import com.medigestsystem.citas.EstadoCita;
import com.medigestsystem.config.PoliticaCitasProperties;
import com.medigestsystem.consultorios.Consultorio;
import com.medigestsystem.disponibilidad.dto.DiaDisponibilidadResponse;
import com.medigestsystem.disponibilidad.dto.MedicoDisponibleResponse;
import com.medigestsystem.disponibilidad.dto.SlotResponse;
import com.medigestsystem.horarios.BloqueoHorarioRepository;
import com.medigestsystem.horarios.EstadoBloqueo;
import com.medigestsystem.horarios.HorarioMedico;
import com.medigestsystem.horarios.HorarioMedicoRepository;
import com.medigestsystem.listaespera.ListaEspera;
import com.medigestsystem.listaespera.ListaEsperaRepository;
import com.medigestsystem.medicos.Medico;
import com.medigestsystem.medicos.MedicoRepository;
import com.medigestsystem.shared.exception.ConflictoException;
import com.medigestsystem.shared.exception.RecursoNoEncontradoException;
import com.medigestsystem.shared.exception.ReglaNegocioException;
import com.medigestsystem.shared.exception.SolicitudInvalidaException;
import com.medigestsystem.shared.util.FechaHoraUtil;

/**
 * Cálculo de disponibilidad y validación de horarios. Un horario está libre cuando:
 * está dentro de la jornada semanal (sin el descanso), alineado a la duración de consulta,
 * sin bloqueos aprobados del médico o del consultorio, sin citas vigentes del médico o del consultorio
 * y sin una oferta vigente de la lista de espera.
 */
@Service
public class DisponibilidadService {

    static final int MAX_DIAS_MEDICO = 31;
    static final int MAX_DIAS_BUSQUEDA = 14;
    private static final Set<EstadoCita> NO_OCUPAN = EnumSet.of(EstadoCita.CANCELADA, EstadoCita.NO_ASISTIO);

    private final HorarioMedicoRepository horarioRepository;
    private final CitaRepository citaRepository;
    private final BloqueoHorarioRepository bloqueoRepository;
    private final ListaEsperaRepository listaEsperaRepository;
    private final MedicoRepository medicoRepository;
    private final PoliticaCitasProperties politica;
    private final FechaHoraUtil fechaHora;
    private final ZoneId zona;

    public DisponibilidadService(HorarioMedicoRepository horarioRepository, CitaRepository citaRepository,
                                 BloqueoHorarioRepository bloqueoRepository,
                                 ListaEsperaRepository listaEsperaRepository, MedicoRepository medicoRepository,
                                 PoliticaCitasProperties politica, FechaHoraUtil fechaHora, ZoneId zonaClinica) {
        this.horarioRepository = horarioRepository;
        this.citaRepository = citaRepository;
        this.bloqueoRepository = bloqueoRepository;
        this.listaEsperaRepository = listaEsperaRepository;
        this.medicoRepository = medicoRepository;
        this.politica = politica;
        this.fechaHora = fechaHora;
        this.zona = zonaClinica;
    }

    // ------------------------------------------------------------------ consulta pública

    /** Horarios libres de un médico agrupados por día (solo días con al menos un horario). */
    @Transactional(readOnly = true)
    public List<DiaDisponibilidadResponse> disponibilidadDeMedico(Long medicoId, LocalDate desde, LocalDate hasta) {
        validarRango(desde, hasta, MAX_DIAS_MEDICO);
        Medico medico = medicoRepository.findById(medicoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el médico solicitado."));
        Map<LocalDate, List<SlotResponse>> porDia = new TreeMap<>();
        for (Slot s : calcular(medico, desde, hasta)) {
            LocalDate dia = s.inicio().atZone(zona).toLocalDate();
            porDia.computeIfAbsent(dia, d -> new ArrayList<>()).add(aRespuesta(s));
        }
        return porDia.entrySet().stream()
                .map(e -> new DiaDisponibilidadResponse(e.getKey(), e.getValue()))
                .toList();
    }

    /** Médicos activos de una especialidad con horarios libres en el rango, con su próximo horario. */
    @Transactional(readOnly = true)
    public List<MedicoDisponibleResponse> medicosDisponibles(Integer especialidadId, LocalDate desde, LocalDate hasta) {
        validarRango(desde, hasta, MAX_DIAS_BUSQUEDA);
        List<MedicoDisponibleResponse> resultado = new ArrayList<>();
        for (Medico m : medicoRepository.findByActivoTrueAndEspecialidadesId(especialidadId)) {
            List<Slot> slots = calcular(m, desde, hasta);
            if (!slots.isEmpty()) {
                resultado.add(new MedicoDisponibleResponse(
                        m.getId(),
                        m.getNombres() + " " + m.getApellidos(),
                        m.getConsultorio() == null ? null : m.getConsultorio().getCodigo(),
                        m.getDuracionConsultaMin(),
                        slots.size(),
                        aRespuesta(slots.get(0))));
            }
        }
        resultado.sort(Comparator.comparing(r -> r.proximoHorario().inicio()));
        return resultado;
    }

    // ------------------------------------------------------------------ cálculo

    /**
     * Calcula los horarios libres de un médico entre dos fechas (hora de la clínica, ambas inclusivas).
     * No incluye horarios que ya pasaron ni los que superan el horizonte de reserva.
     */
    public List<Slot> calcular(Medico medico, LocalDate desde, LocalDate hasta) {
        if (!medico.isActivo()) {
            return List.of();
        }
        Instant ahora = fechaHora.ahoraUtc();
        Instant limite = ahora.plus(Duration.ofDays(politica.horizonteDias()));
        Instant rangoIni = desde.atStartOfDay(zona).toInstant();
        Instant rangoFin = hasta.plusDays(1).atStartOfDay(zona).toInstant();

        Map<Integer, List<HorarioMedico>> porDia = horarioRepository
                .findByMedicoIdAndActivoTrueOrderByDiaSemanaAscHoraInicioAsc(medico.getId()).stream()
                .collect(Collectors.groupingBy(HorarioMedico::getDiaSemana));
        if (porDia.isEmpty()) {
            return List.of();
        }

        Integer consultorioId = medico.getConsultorio() == null ? null : medico.getConsultorio().getId();
        List<Intervalo> ocupados = new ArrayList<>();
        for (Cita c : citaRepository.findByMedicoIdAndInicioUtcLessThanAndFinUtcGreaterThanAndEstadoNotIn(
                medico.getId(), rangoFin, rangoIni, NO_OCUPAN)) {
            ocupados.add(new Intervalo(c.getInicioUtc(), c.getFinUtc()));
        }
        bloqueoRepository.findByMedicoIdAndEstadoAndInicioUtcLessThanAndFinUtcGreaterThan(
                medico.getId(), EstadoBloqueo.APROBADO, rangoFin, rangoIni)
                .forEach(b -> ocupados.add(new Intervalo(b.getInicioUtc(), b.getFinUtc())));
        if (consultorioId != null) {
            citaRepository.findByConsultorioIdAndInicioUtcLessThanAndFinUtcGreaterThanAndEstadoNotIn(
                    consultorioId, rangoFin, rangoIni, NO_OCUPAN)
                    .forEach(c -> ocupados.add(new Intervalo(c.getInicioUtc(), c.getFinUtc())));
            bloqueoRepository.findByConsultorioIdAndEstadoAndInicioUtcLessThanAndFinUtcGreaterThan(
                    consultorioId, EstadoBloqueo.APROBADO, rangoFin, rangoIni)
                    .forEach(b -> ocupados.add(new Intervalo(b.getInicioUtc(), b.getFinUtc())));
        }
        for (ListaEspera o : listaEsperaRepository.findOfertasVigentesDelMedico(medico.getId(), ahora)) {
            ocupados.add(new Intervalo(o.getOfertaInicioUtc(), o.getOfertaFinUtc()));
        }

        List<Slot> libres = new ArrayList<>();
        for (LocalDate dia = desde; !dia.isAfter(hasta); dia = dia.plusDays(1)) {
            List<HorarioMedico> franjas = porDia.get(dia.getDayOfWeek().getValue());
            if (franjas == null) {
                continue;
            }
            for (LocalDateTime inicioLocal : generarInicios(dia, franjas, medico.getDuracionConsultaMin())) {
                Instant ini = inicioLocal.atZone(zona).toInstant();
                Instant fin = ini.plus(Duration.ofMinutes(medico.getDuracionConsultaMin()));
                if (!ini.isAfter(ahora) || ini.isAfter(limite)) {
                    continue;
                }
                if (ocupados.stream().noneMatch(o -> o.seCruzaCon(ini, fin))) {
                    libres.add(new Slot(ini, fin));
                }
            }
        }
        return libres;
    }

    /**
     * Inicios posibles de consulta para un día: se recorre cada tramo de la jornada (antes y después del
     * descanso) de a una duración de consulta, sin sobrepasar el fin del tramo.
     */
    static List<LocalDateTime> generarInicios(LocalDate dia, List<HorarioMedico> franjas, int duracionMin) {
        List<LocalDateTime> inicios = new ArrayList<>();
        for (HorarioMedico f : franjas) {
            List<LocalTime[]> tramos = new ArrayList<>();
            if (f.getDescansoInicio() != null && f.getDescansoFin() != null) {
                tramos.add(new LocalTime[]{f.getHoraInicio(), f.getDescansoInicio()});
                tramos.add(new LocalTime[]{f.getDescansoFin(), f.getHoraFin()});
            } else {
                tramos.add(new LocalTime[]{f.getHoraInicio(), f.getHoraFin()});
            }
            for (LocalTime[] tramo : tramos) {
                LocalTime t = tramo[0];
                while (!t.plusMinutes(duracionMin).isAfter(tramo[1]) && !t.plusMinutes(duracionMin).isBefore(t)) {
                    inicios.add(dia.atTime(t));
                    t = t.plusMinutes(duracionMin);
                }
            }
        }
        inicios.sort(Comparator.naturalOrder());
        return inicios;
    }

    // ------------------------------------------------------------------ validación de una reserva

    /**
     * Valida que el intervalo pueda reservarse. Debe llamarse con los bloqueos pesimistas del médico,
     * consultorio y paciente ya tomados, para que las comprobaciones de superposición sean definitivas.
     *
     * @param excluirCitaId       cita que se está reprogramando (se ignora en las superposiciones), o null
     * @param excluirOfertaId     oferta de lista de espera del propio paciente (se ignora), o null
     * @param aplicaAnticipacion  true si la reserva la hace el paciente (se exige la anticipación mínima)
     */
    public void validarReserva(Medico medico, Consultorio consultorio, Long pacienteId, Instant inicio, Instant fin,
                               Long excluirCitaId, Long excluirOfertaId, boolean aplicaAnticipacion) {
        Instant ahora = fechaHora.ahoraUtc();
        if (!inicio.isAfter(ahora)) {
            throw new ReglaNegocioException("FECHA_PASADA", "No se pueden registrar citas en fechas pasadas.");
        }
        if (aplicaAnticipacion && inicio.isBefore(ahora.plus(Duration.ofMinutes(politica.anticipacionMinimaPacienteMin())))) {
            throw new ReglaNegocioException("ANTICIPACION_INSUFICIENTE",
                    "Las reservas deben hacerse con al menos " + politica.anticipacionMinimaPacienteMin()
                            + " minutos de anticipación.");
        }
        if (inicio.isAfter(ahora.plus(Duration.ofDays(politica.horizonteDias())))) {
            throw new ReglaNegocioException("FUERA_DE_HORIZONTE",
                    "Solo se pueden reservar citas dentro de los próximos " + politica.horizonteDias() + " días.");
        }
        if (!estaEnJornada(medico, inicio)) {
            throw new ReglaNegocioException("FUERA_DE_HORARIO",
                    "El horario elegido está fuera de la jornada de atención del médico.");
        }
        if (bloqueoRepository.existsByMedicoIdAndEstadoAndInicioUtcLessThanAndFinUtcGreaterThan(
                medico.getId(), EstadoBloqueo.APROBADO, fin, inicio)) {
            throw new ReglaNegocioException("MEDICO_BLOQUEADO", "El médico no atiende en ese período.");
        }
        long excluirCita = excluirCitaId == null ? -1L : excluirCitaId;
        if (citaRepository.contarSolapadasMedicoSin(medico.getId(), inicio, fin, NO_OCUPAN, excluirCita) > 0) {
            throw new ConflictoException("MEDICO_OCUPADO", "El médico ya tiene una cita en ese horario.");
        }
        if (consultorio != null) {
            if (bloqueoRepository.existsByConsultorioIdAndEstadoAndInicioUtcLessThanAndFinUtcGreaterThan(
                    consultorio.getId(), EstadoBloqueo.APROBADO, fin, inicio)) {
                throw new ReglaNegocioException("CONSULTORIO_BLOQUEADO", "El consultorio no está disponible en ese período.");
            }
            if (citaRepository.contarSolapadasConsultorioSin(consultorio.getId(), inicio, fin, NO_OCUPAN, excluirCita) > 0) {
                throw new ConflictoException("CONSULTORIO_OCUPADO", "El consultorio ya está reservado en ese horario.");
            }
        }
        if (citaRepository.contarSolapadasPacienteSin(pacienteId, inicio, fin, NO_OCUPAN, excluirCita) > 0) {
            throw new ConflictoException("PACIENTE_OCUPADO", "El paciente ya tiene una cita que se cruza con ese horario.");
        }
        long ofertas = listaEsperaRepository.contarOfertasVigentesSolapadas(
                medico.getId(), inicio, fin, ahora, excluirOfertaId == null ? -1L : excluirOfertaId);
        if (ofertas > 0) {
            throw new ConflictoException("HORARIO_RESERVADO_TEMPORALMENTE",
                    "El horario está reservado temporalmente para otro paciente de la lista de espera.");
        }
    }

    /** Indica si el intervalo podría reservarse (para decidir si ofrecer un horario liberado). */
    public Optional<String> motivoNoDisponible(Medico medico, Consultorio consultorio, Long pacienteId,
                                               Instant inicio, Instant fin, Long excluirOfertaId) {
        try {
            validarReserva(medico, consultorio, pacienteId, inicio, fin, null, excluirOfertaId, false);
            return Optional.empty();
        } catch (ReglaNegocioException | ConflictoException e) {
            return Optional.of(e.getMessage());
        }
    }

    /** True si el inicio coincide exactamente con un horario generado a partir de la jornada del médico. */
    boolean estaEnJornada(Medico medico, Instant inicio) {
        LocalDateTime local = inicio.atZone(zona).toLocalDateTime();
        List<HorarioMedico> franjas = horarioRepository
                .findByMedicoIdAndDiaSemanaAndActivoTrue(medico.getId(), local.getDayOfWeek().getValue());
        return generarInicios(local.toLocalDate(), franjas, medico.getDuracionConsultaMin()).contains(local);
    }

    // ------------------------------------------------------------------ apoyo

    private static void validarRango(LocalDate desde, LocalDate hasta, int maxDias) {
        if (hasta.isBefore(desde)) {
            throw new SolicitudInvalidaException("RANGO_INVALIDO", "La fecha final no puede ser anterior a la inicial.");
        }
        if (java.time.temporal.ChronoUnit.DAYS.between(desde, hasta) + 1 > maxDias) {
            throw new SolicitudInvalidaException("RANGO_EXCESIVO", "El rango máximo de consulta es de " + maxDias + " días.");
        }
    }

    private SlotResponse aRespuesta(Slot s) {
        return new SlotResponse(fechaHora.aZonaClinica(s.inicio()).toOffsetDateTime(),
                fechaHora.aZonaClinica(s.fin()).toOffsetDateTime());
    }

    private record Intervalo(Instant inicio, Instant fin) {
        boolean seCruzaCon(Instant otroInicio, Instant otroFin) {
            return inicio.isBefore(otroFin) && fin.isAfter(otroInicio);
        }
    }
}
