package com.medigestsystem.notificaciones;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import jakarta.persistence.criteria.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.medigestsystem.auditoria.AuditoriaService;
import com.medigestsystem.auditoria.ResultadoAuditoria;
import com.medigestsystem.citas.Cita;
import com.medigestsystem.citas.CitaRepository;
import com.medigestsystem.citas.EstadoCita;
import com.medigestsystem.listaespera.EstadoListaEspera;
import com.medigestsystem.listaespera.ListaEspera;
import com.medigestsystem.listaespera.ListaEsperaRepository;
import com.medigestsystem.notificaciones.dto.NotificacionResponse;
import com.medigestsystem.pacientes.Paciente;
import com.medigestsystem.shared.dto.PaginaResponse;
import com.medigestsystem.shared.exception.ConflictoException;
import com.medigestsystem.shared.exception.RecursoNoEncontradoException;
import com.medigestsystem.shared.util.FechaHoraUtil;

/**
 * Cola de notificaciones por correo: crea (de forma idempotente), envía con reintentos y consulta.
 * Las notificaciones no contienen datos clínicos y nunca se guarda el enlace de recuperación de contraseña.
 */
@Service
public class NotificacionService {

    private static final Logger log = LoggerFactory.getLogger(NotificacionService.class);
    private static final Set<EstadoCita> ACTIVAS = EnumSet.of(EstadoCita.PENDIENTE, EstadoCita.CONFIRMADA);
    private static final int TAMANO_MAXIMO = 100;

    private final NotificacionRepository repository;
    private final CitaRepository citaRepository;
    private final ListaEsperaRepository esperaRepository;
    private final PlantillasCorreo plantillas;
    private final ObjectProvider<CanalCorreo> canal;
    private final CorreoProperties propiedades;
    private final AuditoriaService auditoriaService;
    private final FechaHoraUtil fechaHora;

    public NotificacionService(NotificacionRepository repository, CitaRepository citaRepository,
                               ListaEsperaRepository esperaRepository, PlantillasCorreo plantillas,
                               ObjectProvider<CanalCorreo> canal, CorreoProperties propiedades,
                               AuditoriaService auditoriaService, FechaHoraUtil fechaHora) {
        this.repository = repository;
        this.citaRepository = citaRepository;
        this.esperaRepository = esperaRepository;
        this.plantillas = plantillas;
        this.canal = canal;
        this.propiedades = propiedades;
        this.auditoriaService = auditoriaService;
        this.fechaHora = fechaHora;
    }

    // ================================================================== creación

    /** Registra la notificación de una cita para su paciente. Devuelve false si ya existía o no hay correo. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean encolarParaCita(Long citaId, TipoNotificacion tipo) {
        Cita c = citaRepository.findById(citaId).orElse(null);
        if (c == null) {
            return false;
        }
        if (tipo == TipoNotificacion.RECORDATORIO_CITA && !ACTIVAS.contains(c.getEstado())) {
            return false;
        }
        Paciente p = c.getPaciente();
        String clave = switch (tipo) {
            case CITA_REPROGRAMADA, RECORDATORIO_CITA -> tipo + ":" + c.getId() + ":" + c.getInicioUtc().getEpochSecond();
            default -> tipo + ":" + c.getId();
        };
        if (p.getEmail() == null || p.getEmail().isBlank()) {
            log.debug("Paciente {} sin correo: no se notifica {}", p.getId(), tipo);
            return false;
        }
        if (repository.existsByClaveIdempotencia(clave)) {
            return false;
        }
        PlantillasCorreo.Mensaje m = plantillas.deCita(tipo, c);
        guardar(p, c, tipo, clave, m);
        return true;
    }

    /** Registra el aviso de un horario ofrecido desde la lista de espera. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean encolarOferta(Long esperaId) {
        ListaEspera e = esperaRepository.findById(esperaId).orElse(null);
        if (e == null || e.getEstado() != EstadoListaEspera.OFERTADA || e.getOfertaInicioUtc() == null
                || e.getOfertaExpiraEn() == null) {
            return false;
        }
        Paciente p = e.getPaciente();
        if (p.getEmail() == null || p.getEmail().isBlank()) {
            return false;
        }
        String clave = TipoNotificacion.OFERTA_LISTA_ESPERA + ":" + e.getId() + ":" + e.getOfertaInicioUtc().getEpochSecond();
        if (repository.existsByClaveIdempotencia(clave)) {
            return false;
        }
        guardar(p, null, TipoNotificacion.OFERTA_LISTA_ESPERA, clave, plantillas.deOferta(e));
        return true;
    }

    /** Ids de las citas activas que empiezan dentro de la ventana del recordatorio. */
    @Transactional(readOnly = true)
    public List<Long> citasParaRecordatorio() {
        Instant ahora = fechaHora.ahoraUtc();
        return citaRepository.idsParaRecordatorio(ahora, ahora.plus(Duration.ofHours(propiedades.recordatorioHoras())), ACTIVAS);
    }

    private void guardar(Paciente p, Cita c, TipoNotificacion tipo, String clave, PlantillasCorreo.Mensaje m) {
        Notificacion n = new Notificacion();
        n.setUsuario(p.getUsuario());
        n.setCita(c);
        n.setDestinatario(p.getEmail());
        n.setTipo(tipo);
        n.setCanal(CanalNotificacion.EMAIL);
        n.setAsunto(m.asunto());
        n.setCuerpo(m.cuerpo());
        n.setEstado(EstadoNotificacion.PENDIENTE);
        n.setIntentos(0);
        n.setClaveIdempotencia(clave);
        repository.save(n);
    }

    // ================================================================== envío

    /** Ids pendientes de envío (los que ya cumplieron su espera de reintento), hasta el tamaño del lote. */
    @Transactional(readOnly = true)
    public List<Long> idsPendientes() {
        return repository.idsPendientes(fechaHora.ahoraUtc(), PageRequest.of(0, Math.max(propiedades.tamanoLote(), 1)));
    }

    /**
     * Intenta enviar una notificación. Éxito: ENVIADA. Fallo: se reprograma con espera creciente y, agotados los
     * intentos, queda FALLIDA. Con el correo deshabilitado queda OMITIDA (se puede reintentar al habilitarlo).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public EstadoNotificacion procesar(Long id) {
        Notificacion n = repository.findById(id).orElse(null);
        if (n == null || n.getEstado() != EstadoNotificacion.PENDIENTE) {
            return null;
        }
        Instant ahora = fechaHora.ahoraUtc();
        CanalCorreo salida = propiedades.habilitado() ? canal.getIfAvailable() : null;
        if (salida == null) {
            n.setEstado(EstadoNotificacion.OMITIDA);
            n.setUltimoError("Envío de correo deshabilitado (medigest.correo.habilitado=false).");
            return n.getEstado();
        }
        n.setIntentos(n.getIntentos() + 1);
        try {
            salida.enviar(n.getDestinatario(), n.getAsunto(), n.getCuerpo());
            n.setEstado(EstadoNotificacion.ENVIADA);
            n.setEnviadaEn(ahora);
            n.setUltimoError(null);
        } catch (RuntimeException e) {
            n.setUltimoError(truncar(e.getClass().getSimpleName() + ": " + e.getMessage(), 500));
            if (n.getIntentos() >= propiedades.maxIntentos()) {
                n.setEstado(EstadoNotificacion.FALLIDA);
                log.warn("Notificación {} marcada como FALLIDA tras {} intentos", n.getId(), n.getIntentos());
            } else {
                n.setProgramadaPara(ahora.plus(Duration.ofMinutes((long) propiedades.backoffMinutos() * n.getIntentos())));
            }
        }
        return n.getEstado();
    }

    // ================================================================== administración

    @Transactional(readOnly = true)
    public PaginaResponse<NotificacionResponse> listar(EstadoNotificacion estado, TipoNotificacion tipo, int pagina, int tamano) {
        Specification<Notificacion> spec = (root, query, cb) -> {
            List<Predicate> filtros = new ArrayList<>();
            if (estado != null) {
                filtros.add(cb.equal(root.get("estado"), estado));
            }
            if (tipo != null) {
                filtros.add(cb.equal(root.get("tipo"), tipo));
            }
            return cb.and(filtros.toArray(new Predicate[0]));
        };
        Page<Notificacion> resultado = repository.findAll(spec, PageRequest.of(
                Math.max(pagina, 0), Math.min(Math.max(tamano, 1), TAMANO_MAXIMO), Sort.by("id").descending()));
        return PaginaResponse.de(resultado.map(this::aResponse));
    }

    /** Conteo de notificaciones por estado (todas las claves presentes, incluso con 0). */
    @Transactional(readOnly = true)
    public java.util.Map<EstadoNotificacion, Long> resumen() {
        java.util.Map<EstadoNotificacion, Long> mapa = new java.util.EnumMap<>(EstadoNotificacion.class);
        for (EstadoNotificacion e : EstadoNotificacion.values()) {
            mapa.put(e, 0L);
        }
        for (Object[] fila : repository.contarPorEstado()) {
            mapa.put((EstadoNotificacion) fila[0], (Long) fila[1]);
        }
        return mapa;
    }

    public boolean correoHabilitado() {
        return propiedades.habilitado() && canal.getIfAvailable() != null;
    }

    /** Devuelve a la cola una notificación FALLIDA u OMITIDA. */
    @Transactional
    public NotificacionResponse reintentar(Long id) {
        Notificacion n = repository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe la notificación."));
        if (n.getEstado() != EstadoNotificacion.FALLIDA && n.getEstado() != EstadoNotificacion.OMITIDA) {
            throw new ConflictoException("ESTADO_INVALIDO", "Solo se pueden reintentar notificaciones fallidas u omitidas.");
        }
        n.setEstado(EstadoNotificacion.PENDIENTE);
        n.setIntentos(0);
        n.setProgramadaPara(null);
        n.setUltimoError(null);
        auditoriaService.registrar("NOTIFICACION_REINTENTADA", "notificaciones", String.valueOf(id),
                ResultadoAuditoria.EXITO, null);
        return aResponse(n);
    }

    private NotificacionResponse aResponse(Notificacion n) {
        return new NotificacionResponse(
                n.getId(), n.getTipo(), n.getEstado(), n.getDestinatario(), n.getAsunto(), n.getIntentos(),
                n.getUltimoError(), n.getCita() == null ? null : n.getCita().getId(),
                n.getProgramadaPara() == null ? null : fechaHora.aZonaClinica(n.getProgramadaPara()).toOffsetDateTime(),
                n.getEnviadaEn() == null ? null : fechaHora.aZonaClinica(n.getEnviadaEn()).toOffsetDateTime(),
                n.getCreadoEn() == null ? null : fechaHora.aZonaClinica(n.getCreadoEn()).toOffsetDateTime());
    }

    private static String truncar(String texto, int max) {
        return texto.length() <= max ? texto : texto.substring(0, max - 1) + "…";
    }
}
