package com.medigestsystem.listaespera;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import jakarta.persistence.criteria.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.medigestsystem.auditoria.AuditoriaService;
import com.medigestsystem.auditoria.ResultadoAuditoria;
import com.medigestsystem.citas.CitaRepository;
import com.medigestsystem.citas.EstadoCita;
import com.medigestsystem.citas.CitaService;
import com.medigestsystem.citas.HorarioLiberadoEvent;
import com.medigestsystem.citas.dto.CitaResponse;
import com.medigestsystem.config.PoliticaCitasProperties;
import com.medigestsystem.disponibilidad.DisponibilidadService;
import com.medigestsystem.especialidades.Especialidad;
import com.medigestsystem.especialidades.EspecialidadRepository;
import com.medigestsystem.listaespera.dto.ListaEsperaRequest;
import com.medigestsystem.listaespera.dto.ListaEsperaResponse;
import com.medigestsystem.listaespera.dto.OfertaResponse;
import com.medigestsystem.medicos.Medico;
import com.medigestsystem.medicos.MedicoRepository;
import com.medigestsystem.pacientes.Paciente;
import com.medigestsystem.pacientes.PacienteRepository;
import com.medigestsystem.security.SecurityUtils;
import com.medigestsystem.shared.dto.PaginaResponse;
import com.medigestsystem.shared.exception.ConflictoException;
import com.medigestsystem.shared.exception.RecursoNoEncontradoException;
import com.medigestsystem.shared.exception.ReglaNegocioException;
import com.medigestsystem.shared.exception.SolicitudInvalidaException;
import com.medigestsystem.shared.util.FechaHoraUtil;
import com.medigestsystem.usuarios.NombreRol;

/**
 * Lista de espera. Prioridad: orden de registro (FIFO) dentro de cada especialidad.
 *
 * <p>Cuando un horario se libera, se ofrece a la primera solicitud ACTIVA compatible (médico, fechas, franja,
 * sin citas que se crucen). La oferta retiene el horario durante {@code medigest.citas.oferta-espera-min}
 * minutos: nadie más puede reservarlo y, si no se acepta, vence y el horario pasa a la siguiente solicitud.
 * Aceptar es idempotente: una segunda aceptación es rechazada y no crea otra cita.
 */
@Service
public class ListaEsperaService {

    private static final Logger log = LoggerFactory.getLogger(ListaEsperaService.class);
    private static final String ENTIDAD = "lista_espera";
    private static final int TAMANO_MAXIMO = 100;
    private static final LocalTime MEDIODIA = LocalTime.NOON;
    private static final Set<EstadoListaEspera> VIGENTES = EnumSet.of(EstadoListaEspera.ACTIVA, EstadoListaEspera.OFERTADA);
    private static final Set<EstadoCita> NO_OCUPAN = EnumSet.of(EstadoCita.CANCELADA, EstadoCita.NO_ASISTIO);

    private final ListaEsperaRepository esperaRepository;
    private final PacienteRepository pacienteRepository;
    private final MedicoRepository medicoRepository;
    private final EspecialidadRepository especialidadRepository;
    private final CitaRepository citaRepository;
    private final CitaService citaService;
    private final DisponibilidadService disponibilidad;
    private final AuditoriaService auditoriaService;
    private final PoliticaCitasProperties politica;
    private final FechaHoraUtil fechaHora;
    private final ApplicationEventPublisher eventos;
    private final ZoneId zona;

    public ListaEsperaService(ListaEsperaRepository esperaRepository, PacienteRepository pacienteRepository,
                              MedicoRepository medicoRepository, EspecialidadRepository especialidadRepository,
                              CitaRepository citaRepository, CitaService citaService,
                              DisponibilidadService disponibilidad, AuditoriaService auditoriaService,
                              PoliticaCitasProperties politica, FechaHoraUtil fechaHora,
                              ApplicationEventPublisher eventos, ZoneId zonaClinica) {
        this.esperaRepository = esperaRepository;
        this.pacienteRepository = pacienteRepository;
        this.medicoRepository = medicoRepository;
        this.especialidadRepository = especialidadRepository;
        this.citaRepository = citaRepository;
        this.citaService = citaService;
        this.disponibilidad = disponibilidad;
        this.auditoriaService = auditoriaService;
        this.politica = politica;
        this.fechaHora = fechaHora;
        this.eventos = eventos;
        this.zona = zonaClinica;
    }

    // ================================================================== solicitudes

    @Transactional
    public ListaEsperaResponse crear(ListaEsperaRequest s) {
        Actor actor = actorActual();
        if (!actor.personal() && !actor.paciente()) {
            throw new AccessDeniedException("Solo el personal administrativo o un paciente registran solicitudes de espera");
        }
        Long pacienteId = actor.paciente() ? actor.pacienteId() : s.pacienteId();
        if (pacienteId == null) {
            throw new SolicitudInvalidaException("PACIENTE_REQUERIDO", "Indique el paciente de la solicitud.");
        }
        Paciente paciente = pacienteRepository.findById(pacienteId)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el paciente indicado."));
        if (!paciente.isActivo()) {
            throw new ReglaNegocioException("PACIENTE_INACTIVO", "El paciente está inactivo.");
        }
        Especialidad especialidad = especialidadRepository.findById(s.especialidadId())
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe la especialidad indicada."));
        if (!especialidad.isActiva()) {
            throw new ReglaNegocioException("ESPECIALIDAD_INACTIVA", "La especialidad indicada está inactiva.");
        }
        Medico medico = null;
        if (s.medicoId() != null) {
            medico = medicoRepository.findById(s.medicoId())
                    .orElseThrow(() -> new RecursoNoEncontradoException("No existe el médico indicado."));
            if (!medico.isActivo()) {
                throw new ReglaNegocioException("MEDICO_INACTIVO", "El médico indicado está inactivo.");
            }
            boolean atiende = medico.getEspecialidades().stream().anyMatch(e -> e.getId().equals(s.especialidadId()));
            if (!atiende) {
                throw new ReglaNegocioException("ESPECIALIDAD_NO_ATENDIDA", "El médico no atiende la especialidad indicada.");
            }
        }
        LocalDate hoy = fechaHora.hoyEnClinica();
        if (s.fechaDesde().isBefore(hoy)) {
            throw new SolicitudInvalidaException("FECHA_PASADA", "La fecha inicial no puede ser anterior a hoy.");
        }
        if (s.fechaHasta().isBefore(s.fechaDesde())) {
            throw new SolicitudInvalidaException("RANGO_INVALIDO", "La fecha final no puede ser anterior a la inicial.");
        }
        if (s.fechaHasta().isAfter(hoy.plusDays(politica.horizonteDias()))) {
            throw new SolicitudInvalidaException("FUERA_DE_HORIZONTE",
                    "La fecha final no puede superar los " + politica.horizonteDias() + " días.");
        }
        if (esperaRepository.existsByPacienteIdAndEspecialidadIdAndEstadoIn(pacienteId, s.especialidadId(), VIGENTES)) {
            throw new ConflictoException("ESPERA_DUPLICADA",
                    "El paciente ya tiene una solicitud de espera vigente para esta especialidad.");
        }

        ListaEspera e = new ListaEspera();
        e.setPaciente(paciente);
        e.setEspecialidad(especialidad);
        e.setMedico(medico);
        e.setFechaDesde(s.fechaDesde());
        e.setFechaHasta(s.fechaHasta());
        e.setFranja(s.franja() == null ? FranjaPreferida.CUALQUIERA : s.franja());
        e.setEstado(EstadoListaEspera.ACTIVA);
        try {
            e = esperaRepository.saveAndFlush(e);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictoException("ESPERA_DUPLICADA",
                    "El paciente ya tiene una solicitud de espera vigente para esta especialidad.");
        }
        auditoriaService.registrar("ESPERA_REGISTRADA", ENTIDAD, String.valueOf(e.getId()),
                ResultadoAuditoria.EXITO, null);
        return aResponse(e, actor);
    }

    @Transactional(readOnly = true)
    public PaginaResponse<ListaEsperaResponse> listar(EstadoListaEspera estado, Integer especialidadId,
                                                      Long pacienteId, int pagina, int tamano) {
        Actor actor = actorActual();
        Long pacienteFiltro = pacienteId;
        if (actor.paciente()) {
            pacienteFiltro = actor.pacienteId();
            if (pacienteFiltro == null) {
                return PaginaResponse.de(Page.<ListaEsperaResponse>empty(PageRequest.of(0, 1)));
            }
        } else if (!actor.personal()) {
            throw new AccessDeniedException("Sin permiso para consultar la lista de espera");
        }
        final Long pFinal = pacienteFiltro;
        Specification<ListaEspera> spec = (root, query, cb) -> {
            List<Predicate> filtros = new ArrayList<>();
            if (pFinal != null) {
                filtros.add(cb.equal(root.get("paciente").get("id"), pFinal));
            }
            if (especialidadId != null) {
                filtros.add(cb.equal(root.get("especialidad").get("id"), especialidadId));
            }
            if (estado != null) {
                filtros.add(cb.equal(root.get("estado"), estado));
            }
            return cb.and(filtros.toArray(new Predicate[0]));
        };
        Page<ListaEspera> resultado = esperaRepository.findAll(spec, PageRequest.of(
                Math.max(pagina, 0), Math.min(Math.max(tamano, 1), TAMANO_MAXIMO), Sort.by("creadoEn").ascending()));
        return PaginaResponse.de(resultado.map(e -> aResponse(e, actor)));
    }

    @Transactional(readOnly = true)
    public ListaEsperaResponse obtener(Long id) {
        Actor actor = actorActual();
        ListaEspera e = esperaRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe la solicitud de espera."));
        exigirAcceso(e, actor);
        return aResponse(e, actor);
    }

    /** Retira la solicitud (baja lógica: el registro se conserva). Si tenía una oferta, el horario se libera. */
    @Transactional
    public void cancelar(Long id) {
        Actor actor = actorActual();
        ListaEspera e = cargarConBloqueo(id);
        exigirAcceso(e, actor);
        if (!VIGENTES.contains(e.getEstado())) {
            throw new ReglaNegocioException("ESPERA_NO_CANCELABLE", "La solicitud ya no está vigente.");
        }
        boolean teniaOferta = e.getEstado() == EstadoListaEspera.OFERTADA;
        e.setEstado(EstadoListaEspera.CANCELADA);
        esperaRepository.save(e);
        auditoriaService.registrar("ESPERA_CANCELADA", ENTIDAD, String.valueOf(id), ResultadoAuditoria.EXITO, null);
        if (teniaOferta) {
            liberarOferta(e);
        }
    }

    // ================================================================== ofertas

    /** Acepta el horario ofrecido y crea la cita. Una segunda aceptación es rechazada. */
    @Transactional
    public CitaResponse aceptar(Long id) {
        Actor actor = actorActual();
        if (!esperaRepository.existsById(id)) {
            throw new RecursoNoEncontradoException("No existe la solicitud de espera.");
        }
        // Mismo orden de bloqueo que las reservas: primero el médico, después la solicitud.
        Long medicoOferta = esperaRepository.buscarMedicoDeOferta(id).orElse(null);
        if (medicoOferta != null) {
            medicoRepository.obtenerConBloqueo(medicoOferta);
        }
        ListaEspera e = cargarConBloqueo(id);
        exigirAcceso(e, actor);
        exigirOfertaVigente(e);

        CitaResponse cita = citaService.reservarDesdeOferta(e.getPaciente().getId(), medicoOferta,
                e.getEspecialidad().getId(), e.getOfertaInicioUtc(), e.getId());
        e.setEstado(EstadoListaEspera.ACEPTADA);
        e.setCita(citaRepository.getReferenceById(cita.id()));
        esperaRepository.save(e);
        auditoriaService.registrar("ESPERA_OFERTA_ACEPTADA", ENTIDAD, String.valueOf(id), ResultadoAuditoria.EXITO,
                cita.codigoReserva());
        return cita;
    }

    /** Rechaza el horario ofrecido: la solicitud sigue en espera y el horario pasa a la siguiente. */
    @Transactional
    public ListaEsperaResponse rechazar(Long id) {
        Actor actor = actorActual();
        ListaEspera e = cargarConBloqueo(id);
        exigirAcceso(e, actor);
        exigirOfertaVigente(e);
        liberarOferta(e);
        e.setEstado(EstadoListaEspera.ACTIVA);
        e.setOfertaMedico(null);
        e.setOfertaInicioUtc(null);
        e.setOfertaFinUtc(null);
        e.setOfertaExpiraEn(null);
        e = esperaRepository.save(e);
        auditoriaService.registrar("ESPERA_OFERTA_RECHAZADA", ENTIDAD, String.valueOf(id), ResultadoAuditoria.EXITO, null);
        return aResponse(e, actor);
    }

    /**
     * Ofrece un horario liberado a la primera solicitud compatible. Se ejecuta en su propia transacción,
     * después de confirmada la que liberó el horario. El horario se vuelve a validar con el médico bloqueado.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean ofrecer(HorarioLiberadoEvent ev) {
        Instant ahora = fechaHora.ahoraUtc();
        if (!ev.inicio().isAfter(ahora)) {
            return false;
        }
        Medico medico = medicoRepository.obtenerConBloqueo(ev.medicoId()).orElse(null);
        if (medico == null || !medico.isActivo()) {
            return false;
        }
        // ¿El horario sigue libre? (se usa un paciente inexistente para no mezclar su conflicto personal).
        if (disponibilidad.motivoNoDisponible(medico, medico.getConsultorio(), -1L, ev.inicio(), ev.fin(), null).isPresent()) {
            return false;
        }
        var local = ev.inicio().atZone(zona);
        LocalDate fecha = local.toLocalDate();
        LocalTime hora = local.toLocalTime();

        for (ListaEspera cand : esperaRepository.findByEspecialidadIdAndEstadoOrderByCreadoEnAsc(
                ev.especialidadId(), EstadoListaEspera.ACTIVA)) {
            if (cand.getId().equals(ev.excluirEsperaId())) {
                continue;
            }
            if (cand.getMedico() != null && !cand.getMedico().getId().equals(medico.getId())) {
                continue;
            }
            if (fecha.isBefore(cand.getFechaDesde()) || fecha.isAfter(cand.getFechaHasta())) {
                continue;
            }
            if (!franjaCompatible(cand.getFranja(), hora)) {
                continue;
            }
            Paciente paciente = cand.getPaciente();
            if (!paciente.isActivo()
                    || citaRepository.contarSolapadasPacienteSin(paciente.getId(), ev.inicio(), ev.fin(), NO_OCUPAN, -1L) > 0) {
                continue;
            }
            Instant expira = ahora.plus(Duration.ofMinutes(politica.ofertaEsperaMin()));
            if (esperaRepository.reclamarParaOferta(cand.getId(), medico, ev.inicio(), ev.fin(), expira, ahora) == 1) {
                auditoriaService.registrar("ESPERA_OFERTA_CREADA", ENTIDAD, String.valueOf(cand.getId()),
                        ResultadoAuditoria.EXITO, "Vence " + expira);
                log.info("Horario ofrecido a la solicitud de espera {} (vence {})", cand.getId(), expira);
                return true;
            }
        }
        return false;
    }

    /** Vence las ofertas no aceptadas y las solicitudes cuyo rango de fechas ya pasó. Devuelve cuántas modificó. */
    @Transactional
    public int vencerPendientes() {
        Instant ahora = fechaHora.ahoraUtc();
        int total = 0;
        for (Long id : esperaRepository.idsOfertasVencidas(ahora)) {
            ListaEspera e = esperaRepository.obtenerConBloqueo(id).orElse(null);
            if (e == null || e.getEstado() != EstadoListaEspera.OFERTADA
                    || e.getOfertaExpiraEn() == null || e.getOfertaExpiraEn().isAfter(ahora)) {
                continue; // otro proceso la aceptó, rechazó o canceló mientras tanto
            }
            e.setEstado(EstadoListaEspera.VENCIDA);
            esperaRepository.save(e);
            auditoriaService.registrar("ESPERA_OFERTA_VENCIDA", ENTIDAD, String.valueOf(id), ResultadoAuditoria.EXITO, null);
            liberarOferta(e);
            total++;
        }
        for (Long id : esperaRepository.idsSolicitudesCaducadas(fechaHora.hoyEnClinica())) {
            ListaEspera e = esperaRepository.obtenerConBloqueo(id).orElse(null);
            if (e != null && e.getEstado() == EstadoListaEspera.ACTIVA) {
                e.setEstado(EstadoListaEspera.VENCIDA);
                esperaRepository.save(e);
                auditoriaService.registrar("ESPERA_VENCIDA", ENTIDAD, String.valueOf(id), ResultadoAuditoria.EXITO, null);
                total++;
            }
        }
        return total;
    }

    // ================================================================== apoyo

    static boolean franjaCompatible(FranjaPreferida franja, LocalTime hora) {
        return switch (franja == null ? FranjaPreferida.CUALQUIERA : franja) {
            case CUALQUIERA -> true;
            case MANANA -> hora.isBefore(MEDIODIA);
            case TARDE -> !hora.isBefore(MEDIODIA);
        };
    }

    private void liberarOferta(ListaEspera e) {
        if (e.getOfertaMedico() == null || e.getOfertaInicioUtc() == null) {
            return;
        }
        eventos.publishEvent(new HorarioLiberadoEvent(e.getOfertaMedico().getId(), e.getEspecialidad().getId(),
                e.getOfertaInicioUtc(), e.getOfertaFinUtc(), e.getId()));
    }

    private void exigirOfertaVigente(ListaEspera e) {
        if (e.getEstado() != EstadoListaEspera.OFERTADA) {
            throw new ReglaNegocioException("OFERTA_NO_VIGENTE", "La solicitud no tiene una oferta vigente.");
        }
        if (e.getOfertaExpiraEn() == null || !e.getOfertaExpiraEn().isAfter(fechaHora.ahoraUtc())) {
            throw new ReglaNegocioException("OFERTA_VENCIDA", "La oferta venció; el horario pasó al siguiente paciente.");
        }
    }

    private ListaEspera cargarConBloqueo(Long id) {
        return esperaRepository.obtenerConBloqueo(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe la solicitud de espera."));
    }

    private void exigirAcceso(ListaEspera e, Actor actor) {
        boolean propio = actor.paciente() && actor.pacienteId() != null
                && actor.pacienteId().equals(e.getPaciente().getId());
        if (!actor.personal() && !propio) {
            throw new AccessDeniedException("Sin permiso para acceder a esta solicitud de espera");
        }
    }

    private record Actor(boolean admin, boolean recepcion, boolean paciente, Long pacienteId) {
        boolean personal() {
            return admin || recepcion;
        }
    }

    private Actor actorActual() {
        Long usuarioId = SecurityUtils.usuarioIdActual()
                .orElseThrow(() -> new AccessDeniedException("Se requiere autenticación"));
        boolean paciente = SecurityUtils.tieneRol(NombreRol.PACIENTE);
        return new Actor(SecurityUtils.tieneRol(NombreRol.ADMINISTRADOR),
                SecurityUtils.tieneRol(NombreRol.RECEPCIONISTA), paciente,
                paciente ? pacienteRepository.buscarIdPorUsuario(usuarioId).orElse(null) : null);
    }

    private ListaEsperaResponse aResponse(ListaEspera e, Actor actor) {
        Long posicion = e.getEstado() == EstadoListaEspera.ACTIVA
                ? esperaRepository.countByEspecialidadIdAndEstadoAndCreadoEnBefore(
                        e.getEspecialidad().getId(), EstadoListaEspera.ACTIVA, e.getCreadoEn()) + 1
                : null;
        OfertaResponse oferta = null;
        if (e.getEstado() == EstadoListaEspera.OFERTADA && e.getOfertaMedico() != null) {
            Medico m = e.getOfertaMedico();
            oferta = new OfertaResponse(m.getId(), m.getNombres() + " " + m.getApellidos(),
                    fechaHora.aZonaClinica(e.getOfertaInicioUtc()).toOffsetDateTime(),
                    fechaHora.aZonaClinica(e.getOfertaFinUtc()).toOffsetDateTime(),
                    fechaHora.aZonaClinica(e.getOfertaExpiraEn()).toOffsetDateTime());
        }
        Medico m = e.getMedico();
        Paciente p = e.getPaciente();
        return new ListaEsperaResponse(
                e.getId(),
                p.getId(),
                p.getNombres() + " " + p.getApellidos(),
                e.getEspecialidad().getId(),
                e.getEspecialidad().getNombre(),
                m == null ? null : m.getId(),
                m == null ? null : m.getNombres() + " " + m.getApellidos(),
                e.getFechaDesde(),
                e.getFechaHasta(),
                e.getFranja(),
                e.getEstado(),
                posicion,
                oferta,
                e.getCita() == null ? null : e.getCita().getId(),
                e.getCreadoEn() == null ? null : fechaHora.aZonaClinica(e.getCreadoEn()).toOffsetDateTime());
    }
}
