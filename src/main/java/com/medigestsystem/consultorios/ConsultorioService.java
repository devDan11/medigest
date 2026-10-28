package com.medigestsystem.consultorios;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medigestsystem.auditoria.AuditoriaService;
import com.medigestsystem.auditoria.ResultadoAuditoria;
import com.medigestsystem.citas.CitaRepository;
import com.medigestsystem.citas.EstadoCita;
import com.medigestsystem.consultorios.dto.ConsultorioRequest;
import com.medigestsystem.consultorios.dto.ConsultorioResponse;
import com.medigestsystem.medicos.MedicoRepository;
import com.medigestsystem.shared.exception.ConflictoException;
import com.medigestsystem.shared.exception.RecursoNoEncontradoException;
import com.medigestsystem.shared.exception.ReglaNegocioException;
import com.medigestsystem.shared.util.FechaHoraUtil;

@Service
public class ConsultorioService {

    private static final String ENTIDAD = "consultorios";
    private static final Set<EstadoCita> NO_OCUPAN = EnumSet.of(EstadoCita.CANCELADA, EstadoCita.NO_ASISTIO);

    private final ConsultorioRepository consultorioRepository;
    private final MedicoRepository medicoRepository;
    private final CitaRepository citaRepository;
    private final AuditoriaService auditoriaService;
    private final FechaHoraUtil fechaHoraUtil;

    public ConsultorioService(ConsultorioRepository consultorioRepository, MedicoRepository medicoRepository,
                              CitaRepository citaRepository, AuditoriaService auditoriaService,
                              FechaHoraUtil fechaHoraUtil) {
        this.consultorioRepository = consultorioRepository;
        this.medicoRepository = medicoRepository;
        this.citaRepository = citaRepository;
        this.auditoriaService = auditoriaService;
        this.fechaHoraUtil = fechaHoraUtil;
    }

    @Transactional(readOnly = true)
    public List<ConsultorioResponse> listar(boolean incluirInactivos) {
        List<Consultorio> lista = incluirInactivos
                ? consultorioRepository.findAll(Sort.by("codigo"))
                : consultorioRepository.findByActivoTrueOrderByCodigoAsc();
        return lista.stream().map(ConsultorioResponse::de).toList();
    }

    @Transactional(readOnly = true)
    public ConsultorioResponse obtener(Integer id) {
        return ConsultorioResponse.de(buscar(id));
    }

    @Transactional
    public ConsultorioResponse crear(ConsultorioRequest solicitud) {
        String codigo = normalizarCodigo(solicitud.codigo());
        if (consultorioRepository.existsByCodigoIgnoreCase(codigo)) {
            throw new ConflictoException("CONSULTORIO_DUPLICADO", "Ya existe un consultorio con ese código.");
        }
        Consultorio c = new Consultorio();
        aplicar(c, solicitud, codigo);
        c.setActivo(true);
        c = consultorioRepository.save(c);
        auditoriaService.registrar("CONSULTORIO_CREADO", ENTIDAD, String.valueOf(c.getId()),
                ResultadoAuditoria.EXITO, c.getCodigo());
        return ConsultorioResponse.de(c);
    }

    @Transactional
    public ConsultorioResponse actualizar(Integer id, ConsultorioRequest solicitud) {
        Consultorio c = buscar(id);
        String codigo = normalizarCodigo(solicitud.codigo());
        consultorioRepository.findByCodigoIgnoreCase(codigo)
                .filter(otro -> !otro.getId().equals(id))
                .ifPresent(otro -> {
                    throw new ConflictoException("CONSULTORIO_DUPLICADO", "Ya existe un consultorio con ese código.");
                });
        aplicar(c, solicitud, codigo);
        c = consultorioRepository.save(c);
        auditoriaService.registrar("CONSULTORIO_ACTUALIZADO", ENTIDAD, String.valueOf(id),
                ResultadoAuditoria.EXITO, c.getCodigo());
        return ConsultorioResponse.de(c);
    }

    /**
     * Desactivar un consultorio no puede dejar médicos activos asignados ni citas futuras sin sala:
     * primero deben reasignarse o reprogramarse.
     */
    @Transactional
    public ConsultorioResponse cambiarEstado(Integer id, boolean activo) {
        Consultorio c = buscar(id);
        if (!activo) {
            if (medicoRepository.existsByConsultorioIdAndActivoTrue(id)) {
                throw new ReglaNegocioException("CONSULTORIO_EN_USO",
                        "Hay médicos activos asignados a este consultorio. Reasígnelos antes de desactivarlo.");
            }
            long futuras = citaRepository.countByConsultorioIdAndInicioUtcAfterAndEstadoNotIn(
                    id, fechaHoraUtil.ahoraUtc(), NO_OCUPAN);
            if (futuras > 0) {
                throw new ReglaNegocioException("CONSULTORIO_CON_CITAS",
                        "El consultorio tiene " + futuras + " cita(s) futura(s). Reprográmelas antes de desactivarlo.");
            }
        }
        c.setActivo(activo);
        c = consultorioRepository.save(c);
        auditoriaService.registrar(activo ? "CONSULTORIO_ACTIVADO" : "CONSULTORIO_DESACTIVADO", ENTIDAD,
                String.valueOf(id), ResultadoAuditoria.EXITO, c.getCodigo());
        return ConsultorioResponse.de(c);
    }

    private Consultorio buscar(Integer id) {
        return consultorioRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el consultorio solicitado."));
    }

    private static String normalizarCodigo(String codigo) {
        return codigo.trim().toUpperCase(Locale.ROOT);
    }

    private static void aplicar(Consultorio c, ConsultorioRequest s, String codigo) {
        c.setCodigo(codigo);
        c.setUbicacion(s.ubicacion() == null || s.ubicacion().isBlank() ? null : s.ubicacion().trim());
    }
}
