package com.medigestsystem.horarios;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import jakarta.persistence.criteria.Predicate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medigestsystem.auditoria.AuditoriaService;
import com.medigestsystem.auditoria.ResultadoAuditoria;
import com.medigestsystem.citas.CitaRepository;
import com.medigestsystem.citas.EstadoCita;
import com.medigestsystem.consultorios.Consultorio;
import com.medigestsystem.consultorios.ConsultorioRepository;
import com.medigestsystem.horarios.dto.BloqueoRequest;
import com.medigestsystem.horarios.dto.BloqueoResponse;
import com.medigestsystem.medicos.Medico;
import com.medigestsystem.medicos.MedicoRepository;
import com.medigestsystem.security.SecurityUtils;
import com.medigestsystem.shared.dto.PaginaResponse;
import com.medigestsystem.shared.exception.RecursoNoEncontradoException;
import com.medigestsystem.shared.exception.ReglaNegocioException;
import com.medigestsystem.shared.exception.SolicitudInvalidaException;
import com.medigestsystem.shared.util.FechaHoraUtil;
import com.medigestsystem.usuarios.NombreRol;
import com.medigestsystem.usuarios.Usuario;
import com.medigestsystem.usuarios.UsuarioRepository;

/**
 * Vacaciones, ausencias y mantenimientos. Un bloqueo no puede cubrir citas vigentes: primero deben
 * reprogramarse o cancelarse. Las fechas se reciben con desfase y se guardan en UTC.
 */
@Service
public class BloqueoService {

    private static final String ENTIDAD = "bloqueos_horarios";
    private static final int TAMANO_MAXIMO = 100;
    private static final Set<EstadoCita> NO_OCUPAN = EnumSet.of(EstadoCita.CANCELADA, EstadoCita.NO_ASISTIO);

    private final BloqueoHorarioRepository bloqueoRepository;
    private final MedicoRepository medicoRepository;
    private final ConsultorioRepository consultorioRepository;
    private final UsuarioRepository usuarioRepository;
    private final CitaRepository citaRepository;
    private final AuditoriaService auditoriaService;
    private final FechaHoraUtil fechaHora;

    public BloqueoService(BloqueoHorarioRepository bloqueoRepository, MedicoRepository medicoRepository,
                          ConsultorioRepository consultorioRepository, UsuarioRepository usuarioRepository,
                          CitaRepository citaRepository, AuditoriaService auditoriaService,
                          FechaHoraUtil fechaHora) {
        this.bloqueoRepository = bloqueoRepository;
        this.medicoRepository = medicoRepository;
        this.consultorioRepository = consultorioRepository;
        this.usuarioRepository = usuarioRepository;
        this.citaRepository = citaRepository;
        this.auditoriaService = auditoriaService;
        this.fechaHora = fechaHora;
    }

    // ------------------------------------------------------------------ consulta

    @Transactional(readOnly = true)
    public PaginaResponse<BloqueoResponse> listar(Long medicoId, Integer consultorioId, EstadoBloqueo estado,
                                                  OffsetDateTime desde, OffsetDateTime hasta,
                                                  int pagina, int tamano) {
        Long medicoFiltro = medicoId;
        if (!esPersonal()) {
            // Un médico solo ve sus propios bloqueos, sin importar el filtro enviado.
            Medico propio = medicoActual();
            if (propio == null) {
                throw new AccessDeniedException("Sin permiso para consultar bloqueos");
            }
            medicoFiltro = propio.getId();
        }
        final Long medicoFinal = medicoFiltro;
        Specification<BloqueoHorario> spec = (root, query, cb) -> {
            List<Predicate> filtros = new ArrayList<>();
            if (medicoFinal != null) {
                filtros.add(cb.equal(root.get("medico").get("id"), medicoFinal));
            }
            if (consultorioId != null) {
                filtros.add(cb.equal(root.get("consultorio").get("id"), consultorioId));
            }
            if (estado != null) {
                filtros.add(cb.equal(root.get("estado"), estado));
            }
            if (desde != null) {
                filtros.add(cb.greaterThan(root.get("finUtc"), desde.toInstant()));
            }
            if (hasta != null) {
                filtros.add(cb.lessThan(root.get("inicioUtc"), hasta.toInstant()));
            }
            return cb.and(filtros.toArray(new Predicate[0]));
        };
        Page<BloqueoHorario> resultado = bloqueoRepository.findAll(spec, PageRequest.of(
                Math.max(pagina, 0), Math.min(Math.max(tamano, 1), TAMANO_MAXIMO), Sort.by("inicioUtc").descending()));
        return PaginaResponse.de(resultado.map(this::aResponse));
    }

    // ------------------------------------------------------------------ escritura

    @Transactional
    public BloqueoResponse crear(BloqueoRequest s) {
        boolean admin = SecurityUtils.tieneRol(NombreRol.ADMINISTRADOR);
        Instant inicio = s.inicio().toInstant();
        Instant fin = s.fin().toInstant();
        if (!fin.isAfter(inicio)) {
            throw new SolicitudInvalidaException("RANGO_INVALIDO", "La fecha de fin debe ser posterior a la de inicio.");
        }
        if (!fin.isAfter(fechaHora.ahoraUtc())) {
            throw new SolicitudInvalidaException("BLOQUEO_PASADO", "No se puede bloquear un período que ya terminó.");
        }

        BloqueoHorario b = new BloqueoHorario();
        b.setInicioUtc(inicio);
        b.setFinUtc(fin);
        b.setTipo(s.tipo());
        b.setMotivo(s.motivo() == null || s.motivo().isBlank() ? null : s.motivo().trim());
        b.setCreadoPor(usuarioActual());
        b.setCreadoEn(fechaHora.ahoraUtc());

        if (admin) {
            if ((s.medicoId() == null) == (s.consultorioId() == null)) {
                throw new SolicitudInvalidaException("BLOQUEO_OBJETIVO",
                        "Indique un médico o un consultorio (solo uno de los dos).");
            }
            if (s.medicoId() != null) {
                b.setMedico(medicoRepository.findById(s.medicoId())
                        .orElseThrow(() -> new RecursoNoEncontradoException("No existe el médico indicado.")));
            } else {
                b.setConsultorio(consultorioRepository.findById(s.consultorioId())
                        .orElseThrow(() -> new RecursoNoEncontradoException("No existe el consultorio indicado.")));
            }
            verificarSinCitas(b);
            b.setEstado(EstadoBloqueo.APROBADO);
        } else {
            Medico propio = medicoActual();
            if (propio == null) {
                throw new AccessDeniedException("Solo un médico puede solicitar bloqueos");
            }
            if (s.consultorioId() != null || (s.medicoId() != null && !s.medicoId().equals(propio.getId()))) {
                throw new AccessDeniedException("Un médico solo puede solicitar bloqueos de su propia agenda");
            }
            if (s.tipo() == TipoBloqueo.MANTENIMIENTO) {
                throw new SolicitudInvalidaException("TIPO_NO_PERMITIDO",
                        "El mantenimiento solo lo registra un administrador.");
            }
            b.setMedico(propio);
            b.setEstado(EstadoBloqueo.SOLICITADO);
        }
        b = bloqueoRepository.save(b);
        auditoriaService.registrar(admin ? "BLOQUEO_CREADO" : "BLOQUEO_SOLICITADO", ENTIDAD,
                String.valueOf(b.getId()), ResultadoAuditoria.EXITO, b.getTipo().name());
        return aResponse(b);
    }

    @Transactional
    public BloqueoResponse aprobar(Long id) {
        BloqueoHorario b = buscar(id);
        exigirSolicitado(b);
        verificarSinCitas(b);
        b.setEstado(EstadoBloqueo.APROBADO);
        b = bloqueoRepository.save(b);
        auditoriaService.registrar("BLOQUEO_APROBADO", ENTIDAD, String.valueOf(id), ResultadoAuditoria.EXITO, null);
        return aResponse(b);
    }

    @Transactional
    public BloqueoResponse rechazar(Long id) {
        BloqueoHorario b = buscar(id);
        exigirSolicitado(b);
        b.setEstado(EstadoBloqueo.RECHAZADO);
        b = bloqueoRepository.save(b);
        auditoriaService.registrar("BLOQUEO_RECHAZADO", ENTIDAD, String.valueOf(id), ResultadoAuditoria.EXITO, null);
        return aResponse(b);
    }

    /** El administrador elimina cualquiera; un médico solo sus propias solicitudes aún pendientes. */
    @Transactional
    public void eliminar(Long id) {
        BloqueoHorario b = buscar(id);
        if (!SecurityUtils.tieneRol(NombreRol.ADMINISTRADOR)) {
            Medico propio = medicoActual();
            boolean suyo = propio != null && b.getMedico() != null && b.getMedico().getId().equals(propio.getId());
            if (!suyo) {
                throw new AccessDeniedException("Sin permiso para eliminar este bloqueo");
            }
            if (b.getEstado() != EstadoBloqueo.SOLICITADO) {
                throw new ReglaNegocioException("BLOQUEO_NO_ELIMINABLE",
                        "Solo se pueden retirar solicitudes pendientes; contacte al administrador.");
            }
        }
        bloqueoRepository.delete(b);
        auditoriaService.registrar("BLOQUEO_ELIMINADO", ENTIDAD, String.valueOf(id), ResultadoAuditoria.EXITO, null);
    }

    // ------------------------------------------------------------------ apoyo

    private void verificarSinCitas(BloqueoHorario b) {
        long citas = b.getMedico() != null
                ? citaRepository.contarSolapadasMedico(b.getMedico().getId(), b.getInicioUtc(), b.getFinUtc(), NO_OCUPAN)
                : citaRepository.contarSolapadasConsultorio(b.getConsultorio().getId(), b.getInicioUtc(),
                        b.getFinUtc(), NO_OCUPAN);
        if (citas > 0) {
            throw new ReglaNegocioException("BLOQUEO_CON_CITAS",
                    "El período contiene " + citas + " cita(s) vigente(s). Reprográmelas o cancélelas primero.");
        }
    }

    private static void exigirSolicitado(BloqueoHorario b) {
        if (b.getEstado() != EstadoBloqueo.SOLICITADO) {
            throw new ReglaNegocioException("BLOQUEO_YA_RESUELTO", "La solicitud ya fue resuelta.");
        }
    }

    private BloqueoHorario buscar(Long id) {
        return bloqueoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el bloqueo solicitado."));
    }

    private Medico medicoActual() {
        return SecurityUtils.usuarioIdActual().flatMap(medicoRepository::findByUsuarioId).orElse(null);
    }

    private Usuario usuarioActual() {
        return SecurityUtils.usuarioIdActual().flatMap(usuarioRepository::findById).orElse(null);
    }

    private static boolean esPersonal() {
        return SecurityUtils.tieneRol(NombreRol.ADMINISTRADOR) || SecurityUtils.tieneRol(NombreRol.RECEPCIONISTA);
    }

    private BloqueoResponse aResponse(BloqueoHorario b) {
        Medico m = b.getMedico();
        Consultorio c = b.getConsultorio();
        return new BloqueoResponse(
                b.getId(),
                m == null ? null : m.getId(),
                m == null ? null : m.getNombres() + " " + m.getApellidos(),
                c == null ? null : c.getId(),
                c == null ? null : c.getCodigo(),
                fechaHora.aZonaClinica(b.getInicioUtc()).toOffsetDateTime(),
                fechaHora.aZonaClinica(b.getFinUtc()).toOffsetDateTime(),
                b.getTipo(),
                b.getMotivo(),
                b.getEstado(),
                b.getCreadoPor() == null ? null : b.getCreadoPor().getEmail(),
                b.getCreadoEn() == null ? null : fechaHora.aZonaClinica(b.getCreadoEn()).toOffsetDateTime());
    }
}
