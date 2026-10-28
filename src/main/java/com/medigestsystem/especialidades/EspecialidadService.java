package com.medigestsystem.especialidades;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medigestsystem.auditoria.AuditoriaService;
import com.medigestsystem.auditoria.ResultadoAuditoria;
import com.medigestsystem.especialidades.dto.EspecialidadRequest;
import com.medigestsystem.especialidades.dto.EspecialidadResponse;
import com.medigestsystem.security.SecurityUtils;
import com.medigestsystem.shared.exception.ConflictoException;
import com.medigestsystem.shared.exception.RecursoNoEncontradoException;
import com.medigestsystem.usuarios.NombreRol;

@Service
public class EspecialidadService {

    private static final String ENTIDAD = "especialidades";

    private final EspecialidadRepository especialidadRepository;
    private final AuditoriaService auditoriaService;

    public EspecialidadService(EspecialidadRepository especialidadRepository, AuditoriaService auditoriaService) {
        this.especialidadRepository = especialidadRepository;
        this.auditoriaService = auditoriaService;
    }

    /** Los usuarios que no son administradores solo ven especialidades activas. */
    @Transactional(readOnly = true)
    public List<EspecialidadResponse> listar(boolean incluirInactivas) {
        boolean puedeVerInactivas = incluirInactivas && SecurityUtils.tieneRol(NombreRol.ADMINISTRADOR);
        List<Especialidad> lista = puedeVerInactivas
                ? especialidadRepository.findAll(org.springframework.data.domain.Sort.by("nombre"))
                : especialidadRepository.findByActivaTrueOrderByNombreAsc();
        return lista.stream().map(EspecialidadResponse::de).toList();
    }

    @Transactional(readOnly = true)
    public EspecialidadResponse obtener(Integer id) {
        Especialidad e = buscar(id);
        if (!e.isActiva() && !SecurityUtils.tieneRol(NombreRol.ADMINISTRADOR)) {
            throw new RecursoNoEncontradoException("No existe la especialidad solicitada.");
        }
        return EspecialidadResponse.de(e);
    }

    @Transactional
    public EspecialidadResponse crear(EspecialidadRequest solicitud) {
        String nombre = solicitud.nombre().trim();
        if (especialidadRepository.existsByNombreIgnoreCase(nombre)) {
            throw new ConflictoException("ESPECIALIDAD_DUPLICADA", "Ya existe una especialidad con ese nombre.");
        }
        Especialidad e = new Especialidad();
        aplicar(e, solicitud, nombre);
        e.setActiva(true);
        e = especialidadRepository.save(e);
        auditoriaService.registrar("ESPECIALIDAD_CREADA", ENTIDAD, String.valueOf(e.getId()),
                ResultadoAuditoria.EXITO, e.getNombre());
        return EspecialidadResponse.de(e);
    }

    @Transactional
    public EspecialidadResponse actualizar(Integer id, EspecialidadRequest solicitud) {
        Especialidad e = buscar(id);
        String nombre = solicitud.nombre().trim();
        especialidadRepository.findByNombreIgnoreCase(nombre)
                .filter(otra -> !otra.getId().equals(id))
                .ifPresent(otra -> {
                    throw new ConflictoException("ESPECIALIDAD_DUPLICADA", "Ya existe una especialidad con ese nombre.");
                });
        aplicar(e, solicitud, nombre);
        e = especialidadRepository.save(e);
        auditoriaService.registrar("ESPECIALIDAD_ACTUALIZADA", ENTIDAD, String.valueOf(id),
                ResultadoAuditoria.EXITO, e.getNombre());
        return EspecialidadResponse.de(e);
    }

    /**
     * Una especialidad inactiva no puede asignarse a médicos nuevos, pero los médicos y citas que ya la usan
     * conservan su historial.
     */
    @Transactional
    public EspecialidadResponse cambiarEstado(Integer id, boolean activa) {
        Especialidad e = buscar(id);
        e.setActiva(activa);
        e = especialidadRepository.save(e);
        auditoriaService.registrar(activa ? "ESPECIALIDAD_ACTIVADA" : "ESPECIALIDAD_DESACTIVADA", ENTIDAD,
                String.valueOf(id), ResultadoAuditoria.EXITO, e.getNombre());
        return EspecialidadResponse.de(e);
    }

    private Especialidad buscar(Integer id) {
        return especialidadRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe la especialidad solicitada."));
    }

    private static void aplicar(Especialidad e, EspecialidadRequest s, String nombre) {
        e.setNombre(nombre);
        e.setDescripcion(s.descripcion() == null || s.descripcion().isBlank() ? null : s.descripcion().trim());
        e.setDuracionSugeridaMin(s.duracionSugeridaMin());
    }
}
