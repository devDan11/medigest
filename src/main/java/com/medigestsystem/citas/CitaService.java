package com.medigestsystem.citas;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import jakarta.persistence.criteria.Predicate;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medigestsystem.notificaciones.AvisosNegocio;
import com.medigestsystem.notificaciones.TipoNotificacion;
import com.medigestsystem.auditoria.AuditoriaService;
import com.medigestsystem.auditoria.ResultadoAuditoria;
import com.medigestsystem.citas.dto.CancelarRequest;
import com.medigestsystem.citas.dto.CitaRequest;
import com.medigestsystem.citas.dto.CitaResponse;
import com.medigestsystem.citas.dto.CitaResumenResponse;
import com.medigestsystem.citas.dto.HistorialCitaResponse;
import com.medigestsystem.citas.dto.ReprogramarRequest;
import com.medigestsystem.config.PoliticaCitasProperties;
import com.medigestsystem.consultorios.Consultorio;
import com.medigestsystem.consultorios.ConsultorioRepository;
import com.medigestsystem.disponibilidad.DisponibilidadService;
import com.medigestsystem.especialidades.Especialidad;
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
import com.medigestsystem.usuarios.UsuarioRepository;

/**
 * Gestión del ciclo de vida de las citas.
 *
 * <p><b>Concurrencia.</b> Toda operación que ocupa un horario toma bloqueos pesimistas sobre las filas del
 * médico, del consultorio y del paciente, siempre en ese orden (y la cita antes que todos ellos), y recién
 * entonces comprueba las superposiciones. Así, dos solicitudes simultáneas sobre el mismo médico se ejecutan
 * una tras otra y la segunda ve la cita de la primera. Como red de seguridad, índices únicos filtrados de la
 * base de datos impiden dos citas vigentes con el mismo inicio para un médico, paciente o consultorio, y el
 * campo {@code version} detecta modificaciones simultáneas de una misma cita.
 */
@Service
public class CitaService {

    private static final String ENTIDAD = "citas";
    private static final int TAMANO_MAXIMO = 100;
    private static final Set<EstadoCita> PENDIENTES = EnumSet.of(EstadoCita.PENDIENTE, EstadoCita.CONFIRMADA);

    private final CitaRepository citaRepository;
    private final HistorialCitaRepository historialRepository;
    private final MedicoRepository medicoRepository;
    private final PacienteRepository pacienteRepository;
    private final ConsultorioRepository consultorioRepository;
    private final UsuarioRepository usuarioRepository;
    private final DisponibilidadService disponibilidad;
    private final AuditoriaService auditoriaService;
    private final PoliticaCitasProperties politica;
    private final FechaHoraUtil fechaHora;
    private final ApplicationEventPublisher eventos;
    private final CodigoReservaGenerator codigos;
    private final AvisosNegocio avisos;

    public CitaService(CitaRepository citaRepository, HistorialCitaRepository historialRepository,
                       MedicoRepository medicoRepository, PacienteRepository pacienteRepository,
                       ConsultorioRepository consultorioRepository, UsuarioRepository usuarioRepository,
                       DisponibilidadService disponibilidad, AuditoriaService auditoriaService,
                       PoliticaCitasProperties politica, FechaHoraUtil fechaHora,
                       ApplicationEventPublisher eventos, CodigoReservaGenerator codigos,
                       AvisosNegocio avisos) {
        this.citaRepository = citaRepository;
        this.historialRepository = historialRepository;
        this.medicoRepository = medicoRepository;
        this.pacienteRepository = pacienteRepository;
        this.consultorioRepository = consultorioRepository;
        this.usuarioRepository = usuarioRepository;
        this.disponibilidad = disponibilidad;
        this.auditoriaService = auditoriaService;
        this.politica = politica;
        this.fechaHora = fechaHora;
        this.eventos = eventos;
        this.codigos = codigos;
        this.avisos = avisos;
    }

    // ================================================================== consulta

    /** Citas filtradas y paginadas. Un paciente solo ve las suyas y un médico solo las de su agenda. */
    @Transactional(readOnly = true)
    public PaginaResponse<CitaResumenResponse> listar(Long pacienteId, Long medicoId, Integer especialidadId,
                                                      Integer consultorioId, EstadoCita estado,
                                                      OffsetDateTime desde, OffsetDateTime hasta,
                                                      int pagina, int tamano) {
        Actor actor = actorActual();
        Long pacienteFiltro = pacienteId;
        Long medicoFiltro = medicoId;
        if (actor.paciente()) {
            pacienteFiltro = actor.pacienteId();
        } else if (actor.medico() && !actor.admin()) {
            medicoFiltro = actor.medicoId();
        } else if (!actor.personal()) {
            throw new AccessDeniedException("Sin permiso para consultar citas");
        }
        if ((actor.paciente() && pacienteFiltro == null) || (actor.medico() && !actor.admin() && medicoFiltro == null)) {
            // Cuenta sin ficha asociada: no hay nada que mostrar.
            return PaginaResponse.de(Page.empty(PageRequest.of(0, 1)));
        }
        final Long pFinal = pacienteFiltro;
        final Long mFinal = medicoFiltro;
        Specification<Cita> spec = (root, query, cb) -> {
            List<Predicate> filtros = new ArrayList<>();
            if (pFinal != null) {
                filtros.add(cb.equal(root.get("paciente").get("id"), pFinal));
            }
            if (mFinal != null) {
                filtros.add(cb.equal(root.get("medico").get("id"), mFinal));
            }
            if (especialidadId != null) {
                filtros.add(cb.equal(root.get("especialidad").get("id"), especialidadId));
            }
            if (consultorioId != null) {
                filtros.add(cb.equal(root.get("consultorio").get("id"), consultorioId));
            }
            if (estado != null) {
                filtros.add(cb.equal(root.get("estado"), estado));
            }
            if (desde != null) {
                filtros.add(cb.greaterThanOrEqualTo(root.get("inicioUtc"), desde.toInstant()));
            }
            if (hasta != null) {
                filtros.add(cb.lessThan(root.get("inicioUtc"), hasta.toInstant()));
            }
            return cb.and(filtros.toArray(new Predicate[0]));
        };
        Page<Cita> resultado = citaRepository.findAll(spec, PageRequest.of(
                Math.max(pagina, 0), Math.min(Math.max(tamano, 1), TAMANO_MAXIMO), Sort.by("inicioUtc").ascending()));
        return PaginaResponse.de(resultado.map(c -> CitaMapper.aResumen(c, fechaHora)));
    }

    @Transactional(readOnly = true)
    public CitaResponse obtener(Long id) {
        Actor actor = actorActual();
        Cita c = citaRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe la cita solicitada."));
        exigirVisible(c, actor);
        return aResponse(c, actor);
    }

    @Transactional(readOnly = true)
    public List<HistorialCitaResponse> historial(Long id) {
        Actor actor = actorActual();
        Cita c = citaRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe la cita solicitada."));
        exigirVisible(c, actor);
        return historialRepository.findByCitaIdOrderByCreadoEnAsc(id).stream()
                .map(h -> new HistorialCitaResponse(
                        h.getId(), h.getTipoEvento(), h.getEstadoAnterior(), h.getEstadoNuevo(),
                        h.getInicioAnteriorUtc() == null ? null : fechaHora.aZonaClinica(h.getInicioAnteriorUtc()).toOffsetDateTime(),
                        h.getInicioNuevoUtc() == null ? null : fechaHora.aZonaClinica(h.getInicioNuevoUtc()).toOffsetDateTime(),
                        h.getMotivo(),
                        actor.personal() && h.getUsuario() != null ? h.getUsuario().getEmail() : null,
                        fechaHora.aZonaClinica(h.getCreadoEn()).toOffsetDateTime()))
                .toList();
    }

    // ================================================================== reserva

    @Transactional
    public CitaResponse reservar(CitaRequest s) {
        Actor actor = actorActual();
        if (!actor.personal() && !actor.paciente()) {
            throw new AccessDeniedException("Solo el personal administrativo o un paciente pueden reservar citas");
        }
        Long pacienteId;
        if (actor.paciente()) {
            pacienteId = actor.pacienteId();
            if (pacienteId == null) {
                throw new ReglaNegocioException("SIN_FICHA_PACIENTE", "Su cuenta no tiene una ficha de paciente asociada.");
            }
        } else {
            pacienteId = s.pacienteId();
            if (pacienteId == null) {
                throw new SolicitudInvalidaException("PACIENTE_REQUERIDO", "Indique el paciente de la cita.");
            }
        }
        return crear(actor, pacienteId, s.medicoId(), s.especialidadId(), s.inicio().toInstant(), s.motivo(),
                null, actor.paciente());
    }

    /**
     * Reserva el horario ofrecido por la lista de espera. Se invoca desde la aceptación de la oferta;
     * ignora la retención temporal de esa misma oferta.
     */
    @Transactional
    public CitaResponse reservarDesdeOferta(Long pacienteId, Long medicoId, Integer especialidadId,
                                            Instant inicio, Long ofertaId) {
        return crear(actorActual(), pacienteId, medicoId, especialidadId, inicio, null, ofertaId, false);
    }

    private CitaResponse crear(Actor actor, Long pacienteId, Long medicoId, Integer especialidadId, Instant inicio,
                               String motivo, Long ofertaId, boolean aplicaAnticipacion) {
        // Orden de bloqueos: médico -> consultorio -> paciente.
        Medico medico = medicoRepository.obtenerConBloqueo(medicoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el médico indicado."));
        Consultorio consultorio = medico.getConsultorio() == null ? null
                : consultorioRepository.obtenerConBloqueo(medico.getConsultorio().getId()).orElse(null);
        Paciente paciente = pacienteRepository.obtenerConBloqueo(pacienteId)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el paciente indicado."));

        if (!medico.isActivo()) {
            throw new ReglaNegocioException("MEDICO_INACTIVO", "No se puede reservar con un médico inactivo.");
        }
        if (!paciente.isActivo()) {
            throw new ReglaNegocioException("PACIENTE_INACTIVO", "El paciente está inactivo.");
        }
        Especialidad especialidad = medico.getEspecialidades().stream()
                .filter(e -> e.getId().equals(especialidadId))
                .findFirst()
                .orElseThrow(() -> new ReglaNegocioException("ESPECIALIDAD_NO_ATENDIDA",
                        "El médico no atiende la especialidad indicada."));
        if (!especialidad.isActiva()) {
            throw new ReglaNegocioException("ESPECIALIDAD_INACTIVA", "La especialidad indicada está inactiva.");
        }
        if (consultorio != null && !consultorio.isActivo()) {
            throw new ReglaNegocioException("CONSULTORIO_INACTIVO", "El consultorio del médico está inactivo.");
        }

        Instant fin = inicio.plus(Duration.ofMinutes(medico.getDuracionConsultaMin()));
        disponibilidad.validarReserva(medico, consultorio, paciente.getId(), inicio, fin, null, ofertaId,
                aplicaAnticipacion);

        Instant ahora = fechaHora.ahoraUtc();
        Cita c = new Cita();
        c.setCodigoReserva(codigos.nuevo());
        c.setPaciente(paciente);
        c.setMedico(medico);
        c.setEspecialidad(especialidad);
        c.setConsultorio(consultorio);
        c.setInicioUtc(inicio);
        c.setFinUtc(fin);
        c.setEstado(EstadoCita.PENDIENTE);
        c.setMotivo(motivo == null || motivo.isBlank() ? null : motivo.trim());
        c.setCreadoPor(usuarioRepository.getReferenceById(actor.usuarioId()));
        c.setModificadoPor(c.getCreadoPor());
        try {
            c = citaRepository.saveAndFlush(c);
        } catch (DataIntegrityViolationException e) {
            // Red de seguridad: el índice único filtrado rechazó un horario ya tomado.
            throw new ConflictoException("HORARIO_NO_DISPONIBLE", "El horario ya fue reservado por otra solicitud.");
        }
        registrarHistorial(c, TipoEventoCita.CREADA, null, EstadoCita.PENDIENTE, null, inicio, c.getMotivo(),
                actor.usuarioId(), ahora);
        auditoriaService.registrar("CITA_CREADA", ENTIDAD, String.valueOf(c.getId()), ResultadoAuditoria.EXITO,
                c.getCodigoReserva());
        return aResponse(c, actor);
    }

    // ================================================================== reprogramación y cancelación

    @Transactional
    public CitaResponse reprogramar(Long id, ReprogramarRequest s) {
        Actor actor = actorActual();
        Cita c = cargarConBloqueo(id);
        exigirVisible(c, actor);
        exigir(evaluar(c, AccionCita.REPROGRAMAR, actor));

        Instant nuevoInicio = s.nuevoInicio().toInstant();
        if (nuevoInicio.equals(c.getInicioUtc())) {
            throw new SolicitudInvalidaException("SIN_CAMBIOS", "El nuevo horario es igual al actual.");
        }
        Medico medico = medicoRepository.obtenerConBloqueo(c.getMedico().getId())
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el médico de la cita."));
        Consultorio consultorio = c.getConsultorio() == null ? null
                : consultorioRepository.obtenerConBloqueo(c.getConsultorio().getId()).orElse(null);
        Paciente paciente = pacienteRepository.obtenerConBloqueo(c.getPaciente().getId())
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el paciente de la cita."));
        if (!medico.isActivo()) {
            throw new ReglaNegocioException("MEDICO_INACTIVO", "No se puede reservar con un médico inactivo.");
        }

        Instant nuevoFin = nuevoInicio.plus(Duration.ofMinutes(medico.getDuracionConsultaMin()));
        disponibilidad.validarReserva(medico, consultorio, paciente.getId(), nuevoInicio, nuevoFin, c.getId(), null,
                actor.paciente());

        Instant inicioAnterior = c.getInicioUtc();
        Instant finAnterior = c.getFinUtc();
        c.setInicioUtc(nuevoInicio);
        c.setFinUtc(nuevoFin);
        c.setModificadoPor(usuarioRepository.getReferenceById(actor.usuarioId()));
        try {
            c = citaRepository.saveAndFlush(c);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictoException("HORARIO_NO_DISPONIBLE", "El horario ya fue reservado por otra solicitud.");
        }
        registrarHistorial(c, TipoEventoCita.REPROGRAMADA, c.getEstado(), c.getEstado(), inicioAnterior, nuevoInicio,
                s.motivo().trim(), actor.usuarioId(), fechaHora.ahoraUtc());
        auditoriaService.registrar("CITA_REPROGRAMADA", ENTIDAD, String.valueOf(id), ResultadoAuditoria.EXITO,
                c.getCodigoReserva());
        eventos.publishEvent(new HorarioLiberadoEvent(medico.getId(), c.getEspecialidad().getId(),
                inicioAnterior, finAnterior));
        return aResponse(c, actor);
    }

    @Transactional
    public CitaResponse cancelar(Long id, CancelarRequest s) {
        Actor actor = actorActual();
        Cita c = cargarConBloqueo(id);
        exigirVisible(c, actor);
        exigir(evaluar(c, AccionCita.CANCELAR, actor));

        Instant ahora = fechaHora.ahoraUtc();
        EstadoCita anterior = c.getEstado();
        c.setEstado(EstadoCita.CANCELADA);
        c.setMotivoCancelacion(s.motivo().trim());
        c.setCanceladaEn(ahora);
        c.setModificadoPor(usuarioRepository.getReferenceById(actor.usuarioId()));
        c = citaRepository.saveAndFlush(c);
        registrarHistorial(c, TipoEventoCita.CANCELADA, anterior, EstadoCita.CANCELADA, c.getInicioUtc(), null,
                c.getMotivoCancelacion(), actor.usuarioId(), ahora);
        auditoriaService.registrar("CITA_CANCELADA", ENTIDAD, String.valueOf(id), ResultadoAuditoria.EXITO,
                c.getCodigoReserva());
        if (c.getInicioUtc().isAfter(ahora)) {
            eventos.publishEvent(new HorarioLiberadoEvent(c.getMedico().getId(), c.getEspecialidad().getId(),
                    c.getInicioUtc(), c.getFinUtc()));
        }
        return aResponse(c, actor);
    }

    // ================================================================== estados

    @Transactional
    public CitaResponse confirmar(Long id) {
        Actor actor = actorActual();
        Cita c = cargarConBloqueo(id);
        exigirVisible(c, actor);
        exigir(evaluar(c, AccionCita.CONFIRMAR, actor));
        return cambiarEstado(c, actor, EstadoCita.CONFIRMADA, TipoEventoCita.CONFIRMADA, "CITA_CONFIRMADA");
    }

    @Transactional
    public CitaResponse registrarAsistencia(Long id, AccionAsistencia accion) {
        Actor actor = actorActual();
        Cita c = cargarConBloqueo(id);
        exigirVisible(c, actor);
        return switch (accion) {
            case LLEGADA -> {
                exigir(evaluar(c, AccionCita.MARCAR_LLEGADA, actor));
                yield cambiarEstado(c, actor, EstadoCita.EN_ESPERA, TipoEventoCita.ASISTENCIA, "CITA_LLEGADA");
            }
            case INICIAR -> {
                exigir(evaluar(c, AccionCita.INICIAR_ATENCION, actor));
                yield cambiarEstado(c, actor, EstadoCita.EN_ATENCION, TipoEventoCita.ASISTENCIA, "CITA_ATENCION_INICIADA");
            }
            case FINALIZAR -> {
                exigir(evaluar(c, AccionCita.FINALIZAR_ATENCION, actor));
                yield cambiarEstado(c, actor, EstadoCita.ATENDIDA, TipoEventoCita.ASISTENCIA, "CITA_ATENDIDA");
            }
            case NO_ASISTIO -> {
                exigir(evaluar(c, AccionCita.MARCAR_NO_ASISTIO, actor));
                CitaResponse r = cambiarEstado(c, actor, EstadoCita.NO_ASISTIO, TipoEventoCita.CAMBIO_ESTADO, "CITA_NO_ASISTIO");
                yield r;
            }
        };
    }

    private CitaResponse cambiarEstado(Cita c, Actor actor, EstadoCita destino, TipoEventoCita evento, String accionAuditoria) {
        EstadoCita anterior = c.getEstado();
        c.setEstado(destino);
        c.setModificadoPor(usuarioRepository.getReferenceById(actor.usuarioId()));
        c = citaRepository.saveAndFlush(c);
        registrarHistorial(c, evento, anterior, destino, null, null, null, actor.usuarioId(), fechaHora.ahoraUtc());
        auditoriaService.registrar(accionAuditoria, ENTIDAD, String.valueOf(c.getId()), ResultadoAuditoria.EXITO,
                anterior + " -> " + destino);
        return aResponse(c, actor);
    }

    // ================================================================== reglas de acceso y de estado

    /** Resultado de evaluar si una acción es posible: permitida, prohibida para el rol (403) o contraria a una regla (422). */
    record Veredicto(Tipo tipo, String codigo, String mensaje) {
        enum Tipo { PERMITIDO, DENEGADO, REGLA }

        static final Veredicto OK = new Veredicto(Tipo.PERMITIDO, null, null);

        static Veredicto denegado(String mensaje) {
            return new Veredicto(Tipo.DENEGADO, "ACCESO_DENEGADO", mensaje);
        }

        static Veredicto regla(String codigo, String mensaje) {
            return new Veredicto(Tipo.REGLA, codigo, mensaje);
        }

        boolean permitido() {
            return tipo == Tipo.PERMITIDO;
        }
    }

    /** Usuario autenticado y las fichas asociadas a su cuenta. */
    record Actor(Long usuarioId, boolean admin, boolean recepcion, boolean medico, boolean paciente,
                 Long medicoId, Long pacienteId) {
        boolean personal() {
            return admin || recepcion;
        }
    }

    Veredicto evaluar(Cita c, AccionCita accion, Actor actor) {
        Instant ahora = fechaHora.ahoraUtc();
        EstadoCita estado = c.getEstado();
        switch (accion) {
            case CONFIRMAR: {
                if (!actor.personal()) {
                    return Veredicto.denegado("Solo el personal administrativo confirma citas.");
                }
                Veredicto v = transicion(estado, EstadoCita.CONFIRMADA);
                if (!v.permitido()) {
                    return v;
                }
                return c.getInicioUtc().isAfter(ahora) ? Veredicto.OK
                        : Veredicto.regla("CITA_PASADA", "No se puede confirmar una cita que ya pasó.");
            }
            case REPROGRAMAR: {
                if (!actor.personal() && !(actor.paciente() && esDelPaciente(c, actor))) {
                    return Veredicto.denegado("No puede reprogramar esta cita.");
                }
                if (!estado.permiteReprogramar()) {
                    return Veredicto.regla("CITA_NO_REPROGRAMABLE",
                            "Una cita " + estado + " no puede reprogramarse.");
                }
                if (!c.getInicioUtc().isAfter(ahora)) {
                    return Veredicto.regla("CITA_PASADA", "No se puede reprogramar una cita que ya pasó.");
                }
                if (actor.paciente()) {
                    if (horasHasta(c, ahora) < politica.limiteCancelacionHoras()) {
                        return Veredicto.regla("REPROGRAMACION_TARDIA", "Solo puede reprogramar con al menos "
                                + politica.limiteCancelacionHoras() + " horas de anticipación.");
                    }
                    if (historialRepository.countByCitaIdAndTipoEvento(c.getId(), TipoEventoCita.REPROGRAMADA)
                            >= politica.maxReprogramacionesPaciente()) {
                        return Veredicto.regla("LIMITE_REPROGRAMACIONES", "Alcanzó el máximo de "
                                + politica.maxReprogramacionesPaciente() + " reprogramaciones para esta cita.");
                    }
                }
                return Veredicto.OK;
            }
            case CANCELAR: {
                if (!actor.personal() && !(actor.paciente() && esDelPaciente(c, actor))) {
                    return Veredicto.denegado("No puede cancelar esta cita.");
                }
                Veredicto v = transicion(estado, EstadoCita.CANCELADA);
                if (!v.permitido()) {
                    return v;
                }
                if (actor.paciente()) {
                    if (estado == EstadoCita.EN_ESPERA) {
                        return Veredicto.regla("CANCELACION_NO_PERMITIDA",
                                "Su llegada ya fue registrada; consulte en recepción para cancelar.");
                    }
                    if (horasHasta(c, ahora) < politica.limiteCancelacionHoras()) {
                        return Veredicto.regla("CANCELACION_TARDIA", "Solo puede cancelar con al menos "
                                + politica.limiteCancelacionHoras() + " horas de anticipación; contacte a recepción.");
                    }
                }
                return Veredicto.OK;
            }
            case MARCAR_LLEGADA: {
                if (!actor.personal()) {
                    return Veredicto.denegado("Solo el personal administrativo registra la llegada.");
                }
                Veredicto v = transicion(estado, EstadoCita.EN_ESPERA);
                if (!v.permitido()) {
                    return v;
                }
                LocalDate hoy = fechaHora.hoyEnClinica();
                return fechaHora.aZonaClinica(c.getInicioUtc()).toLocalDate().equals(hoy) ? Veredicto.OK
                        : Veredicto.regla("FUERA_DE_FECHA", "La llegada solo se registra el día de la cita.");
            }
            case INICIAR_ATENCION: {
                if (!actor.admin() && !(actor.medico() && esDelMedico(c, actor))) {
                    return Veredicto.denegado("Solo el médico de la cita inicia la atención.");
                }
                return transicion(estado, EstadoCita.EN_ATENCION);
            }
            case FINALIZAR_ATENCION: {
                if (!actor.admin() && !(actor.medico() && esDelMedico(c, actor))) {
                    return Veredicto.denegado("Solo el médico de la cita finaliza la atención.");
                }
                return transicion(estado, EstadoCita.ATENDIDA);
            }
            case MARCAR_NO_ASISTIO: {
                if (!actor.personal()) {
                    return Veredicto.denegado("Solo el personal administrativo registra la inasistencia.");
                }
                Veredicto v = transicion(estado, EstadoCita.NO_ASISTIO);
                if (!v.permitido()) {
                    return v;
                }
                return c.getInicioUtc().isAfter(ahora)
                        ? Veredicto.regla("CITA_NO_INICIADA", "La cita aún no ha comenzado.") : Veredicto.OK;
            }
            default:
                return Veredicto.denegado("Acción desconocida");
        }
    }

    /** Aplica la tabla de transiciones de {@link EstadoCita} (reglas 7 y 8: sin cancelada→atendida ni atendida→pendiente). */
    private static Veredicto transicion(EstadoCita actual, EstadoCita destino) {
        return actual.puedeTransicionarA(destino) ? Veredicto.OK
                : Veredicto.regla("TRANSICION_INVALIDA", "Una cita " + actual + " no puede pasar a " + destino + ".");
    }

    private static void exigir(Veredicto v) {
        switch (v.tipo()) {
            case DENEGADO -> throw new AccessDeniedException(v.mensaje());
            case REGLA -> throw new ReglaNegocioException(v.codigo(), v.mensaje());
            default -> { }
        }
    }

    private long horasHasta(Cita c, Instant ahora) {
        return Duration.between(ahora, c.getInicioUtc()).toHours();
    }

    private static boolean esDelPaciente(Cita c, Actor actor) {
        return actor.pacienteId() != null && actor.pacienteId().equals(c.getPaciente().getId());
    }

    private static boolean esDelMedico(Cita c, Actor actor) {
        return actor.medicoId() != null && actor.medicoId().equals(c.getMedico().getId());
    }

    /** Regla 10: cada paciente solo ve sus citas; el médico solo las suyas; el personal ve todas. */
    private void exigirVisible(Cita c, Actor actor) {
        boolean visible = actor.personal()
                || actor.admin()
                || (actor.paciente() && esDelPaciente(c, actor))
                || (actor.medico() && esDelMedico(c, actor));
        if (!visible) {
            throw new AccessDeniedException("Sin permiso para consultar esta cita");
        }
    }

    // ================================================================== apoyo

    private Cita cargarConBloqueo(Long id) {
        return citaRepository.obtenerConBloqueo(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe la cita solicitada."));
    }

    private Actor actorActual() {
        Long usuarioId = SecurityUtils.usuarioIdActual()
                .orElseThrow(() -> new AccessDeniedException("Se requiere autenticación"));
        boolean paciente = SecurityUtils.tieneRol(NombreRol.PACIENTE);
        boolean medico = SecurityUtils.tieneRol(NombreRol.MEDICO);
        return new Actor(usuarioId,
                SecurityUtils.tieneRol(NombreRol.ADMINISTRADOR),
                SecurityUtils.tieneRol(NombreRol.RECEPCIONISTA),
                medico,
                paciente,
                medico ? medicoRepository.buscarIdPorUsuario(usuarioId).orElse(null) : null,
                paciente ? pacienteRepository.buscarIdPorUsuario(usuarioId).orElse(null) : null);
    }

    private void registrarHistorial(Cita c, TipoEventoCita tipo, EstadoCita anterior, EstadoCita nuevo,
                                    Instant inicioAnterior, Instant inicioNuevo, String motivo,
                                    Long usuarioId, Instant ahora) {
        HistorialCita h = new HistorialCita();
        h.setCita(c);
        h.setTipoEvento(tipo);
        h.setEstadoAnterior(anterior);
        h.setEstadoNuevo(nuevo);
        h.setInicioAnteriorUtc(inicioAnterior);
        h.setInicioNuevoUtc(inicioNuevo);
        h.setMotivo(motivo);
        h.setUsuario(usuarioRepository.getReferenceById(usuarioId));
        h.setCreadoEn(ahora);
        historialRepository.save(h);
        TipoNotificacion aviso = switch (tipo) {
            case CREADA -> TipoNotificacion.CITA_RESERVADA;
            case CONFIRMADA -> TipoNotificacion.CITA_CONFIRMADA;
            case REPROGRAMADA -> TipoNotificacion.CITA_REPROGRAMADA;
            case CANCELADA -> TipoNotificacion.CITA_CANCELADA;
            default -> null;
        };
        if (aviso != null) {
            avisos.cita(c.getId(), aviso);
        }
    }

    private CitaResponse aResponse(Cita c, Actor actor) {
        List<AccionCita> acciones = new ArrayList<>();
        for (AccionCita a : AccionCita.values()) {
            if (evaluar(c, a, actor).permitido()) {
                acciones.add(a);
            }
        }
        Consultorio k = c.getConsultorio();
        return new CitaResponse(
                c.getId(),
                c.getCodigoReserva(),
                c.getPaciente().getId(),
                c.getPaciente().getNombres() + " " + c.getPaciente().getApellidos(),
                c.getMedico().getId(),
                c.getMedico().getNombres() + " " + c.getMedico().getApellidos(),
                c.getEspecialidad().getId(),
                c.getEspecialidad().getNombre(),
                k == null ? null : k.getId(),
                k == null ? null : k.getCodigo(),
                fechaHora.aZonaClinica(c.getInicioUtc()).toOffsetDateTime(),
                fechaHora.aZonaClinica(c.getFinUtc()).toOffsetDateTime(),
                c.getEstado(),
                c.getMotivo(),
                c.getMotivoCancelacion(),
                c.getCanceladaEn() == null ? null : fechaHora.aZonaClinica(c.getCanceladaEn()).toOffsetDateTime(),
                c.getCreadoEn() == null ? null : fechaHora.aZonaClinica(c.getCreadoEn()).toOffsetDateTime(),
                c.getActualizadoEn() == null ? null : fechaHora.aZonaClinica(c.getActualizadoEn()).toOffsetDateTime(),
                acciones);
    }
}
