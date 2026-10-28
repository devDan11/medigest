package com.medigestsystem.pacientes;

import java.time.Instant;
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
import com.medigestsystem.citas.CitaMapper;
import com.medigestsystem.citas.CitaRepository;
import com.medigestsystem.citas.EstadoCita;
import com.medigestsystem.citas.dto.CitaResumenResponse;
import com.medigestsystem.medicos.Medico;
import com.medigestsystem.medicos.MedicoRepository;
import com.medigestsystem.pacientes.dto.PacienteRequest;
import com.medigestsystem.pacientes.dto.PacienteResponse;
import com.medigestsystem.pacientes.dto.PacienteResumenResponse;
import com.medigestsystem.pacientes.dto.PerfilPacienteRequest;
import com.medigestsystem.security.SecurityUtils;
import com.medigestsystem.shared.dto.PaginaResponse;
import com.medigestsystem.shared.exception.ConflictoException;
import com.medigestsystem.shared.exception.RecursoNoEncontradoException;
import com.medigestsystem.shared.exception.ReglaNegocioException;
import com.medigestsystem.shared.util.FechaHoraUtil;
import com.medigestsystem.usuarios.NombreRol;
import com.medigestsystem.usuarios.Usuario;
import com.medigestsystem.usuarios.UsuarioRepository;

/**
 * Gestión de pacientes. El acceso a datos personales se valida aquí (backend), no solo en la interfaz:
 * el personal administrativo ve todo, el médico solo a pacientes que ha atendido o tiene agendados,
 * y el paciente únicamente su propia ficha.
 */
@Service
public class PacienteService {

    private static final String ENTIDAD = "pacientes";
    private static final int TAMANO_MAXIMO = 100;
    private static final Set<EstadoCita> NO_OCUPAN = EnumSet.of(EstadoCita.CANCELADA, EstadoCita.NO_ASISTIO);
    private static final Set<EstadoCita> PENDIENTES =
            EnumSet.of(EstadoCita.PENDIENTE, EstadoCita.CONFIRMADA, EstadoCita.EN_ESPERA);

    private final PacienteRepository pacienteRepository;
    private final UsuarioRepository usuarioRepository;
    private final MedicoRepository medicoRepository;
    private final CitaRepository citaRepository;
    private final AuditoriaService auditoriaService;
    private final FechaHoraUtil fechaHora;

    public PacienteService(PacienteRepository pacienteRepository, UsuarioRepository usuarioRepository,
                           MedicoRepository medicoRepository, CitaRepository citaRepository,
                           AuditoriaService auditoriaService, FechaHoraUtil fechaHora) {
        this.pacienteRepository = pacienteRepository;
        this.usuarioRepository = usuarioRepository;
        this.medicoRepository = medicoRepository;
        this.citaRepository = citaRepository;
        this.auditoriaService = auditoriaService;
        this.fechaHora = fechaHora;
    }

    // ------------------------------------------------------------------ consulta

    /** Búsqueda por nombre, apellido o documento, con filtro de estado y paginación (solo personal). */
    @Transactional(readOnly = true)
    public PaginaResponse<PacienteResponse> buscar(String texto, Boolean activo, int pagina, int tamano) {
        Specification<Paciente> spec = (root, query, cb) -> {
            List<Predicate> filtros = new ArrayList<>();
            if (texto != null && !texto.isBlank()) {
                String patron = "%" + texto.trim().toLowerCase() + "%";
                filtros.add(cb.or(
                        cb.like(cb.lower(root.get("nombres")), patron),
                        cb.like(cb.lower(root.get("apellidos")), patron),
                        cb.like(cb.lower(cb.concat(cb.concat(root.get("nombres"), " "), root.get("apellidos"))), patron),
                        cb.like(cb.lower(root.get("numeroDocumento")), patron)));
            }
            if (activo != null) {
                filtros.add(cb.equal(root.get("activo"), activo));
            }
            return cb.and(filtros.toArray(new Predicate[0]));
        };
        Page<Paciente> resultado = pacienteRepository.findAll(spec, pagina(pagina, tamano,
                Sort.by("apellidos").ascending().and(Sort.by("nombres").ascending())));
        boolean completo = !SecurityUtils.tieneRol(NombreRol.MEDICO);
        return PaginaResponse.de(resultado.map(p -> PacienteMapper.aResponse(p, completo, fechaHora)));
    }

    @Transactional(readOnly = true)
    public PacienteResponse obtener(Long id) {
        Paciente p = buscarPorId(id);
        return PacienteMapper.aResponse(p, verificarAcceso(p), fechaHora);
    }

    @Transactional(readOnly = true)
    public PacienteResponse obtenerPropio() {
        return PacienteMapper.aResponse(pacienteActual(), true, fechaHora);
    }

    @Transactional(readOnly = true)
    public PaginaResponse<CitaResumenResponse> historial(Long id, int pagina, int tamano) {
        Paciente p = buscarPorId(id);
        verificarAcceso(p);
        Page<com.medigestsystem.citas.Cita> citas = citaRepository.findByPacienteId(id,
                pagina(pagina, tamano, Sort.by("inicioUtc").descending()));
        return PaginaResponse.de(citas.map(c -> CitaMapper.aResumen(c, fechaHora)));
    }

    @Transactional(readOnly = true)
    public PacienteResumenResponse resumen(Long id) {
        Paciente p = buscarPorId(id);
        verificarAcceso(p);
        Instant ahora = fechaHora.ahoraUtc();
        CitaResumenResponse proxima = citaRepository
                .findFirstByPacienteIdAndInicioUtcAfterAndEstadoInOrderByInicioUtcAsc(id, ahora, PENDIENTES)
                .map(c -> CitaMapper.aResumen(c, fechaHora))
                .orElse(null);
        return new PacienteResumenResponse(
                citaRepository.countByPacienteIdAndEstado(id, EstadoCita.ATENDIDA),
                citaRepository.countByPacienteIdAndEstado(id, EstadoCita.CANCELADA),
                citaRepository.countByPacienteIdAndEstado(id, EstadoCita.NO_ASISTIO),
                citaRepository.countByPacienteIdAndInicioUtcAfterAndEstadoNotIn(id, ahora, NO_OCUPAN),
                proxima);
    }

    // ------------------------------------------------------------------ escritura

    @Transactional
    public PacienteResponse crear(PacienteRequest s) {
        TipoDocumento tipo = s.tipoDocumento();
        String documento = ValidadorDocumento.normalizar(s.numeroDocumento());
        ValidadorDocumento.validar(tipo, documento);
        if (pacienteRepository.existsByTipoDocumentoAndNumeroDocumento(tipo, documento)) {
            throw new ConflictoException("DOCUMENTO_DUPLICADO", "Ya existe un paciente con ese documento.");
        }
        Paciente p = new Paciente();
        aplicarIdentidad(p, s, documento);
        aplicarContacto(p, s.email(), s.telefono(), s.direccion(), s.contactoEmergenciaNombre(),
                s.contactoEmergenciaTelefono());
        p.setActivo(true);
        p = pacienteRepository.save(p);
        auditoriaService.registrar("PACIENTE_REGISTRADO", ENTIDAD, String.valueOf(p.getId()),
                ResultadoAuditoria.EXITO, null);
        return PacienteMapper.aResponse(p, true, fechaHora);
    }

    @Transactional
    public PacienteResponse actualizar(Long id, PacienteRequest s) {
        Paciente p = buscarPorId(id);
        TipoDocumento tipo = s.tipoDocumento();
        String documento = ValidadorDocumento.normalizar(s.numeroDocumento());
        ValidadorDocumento.validar(tipo, documento);
        pacienteRepository.findByTipoDocumentoAndNumeroDocumento(tipo, documento)
                .filter(otro -> !otro.getId().equals(id))
                .ifPresent(otro -> {
                    throw new ConflictoException("DOCUMENTO_DUPLICADO", "Ya existe un paciente con ese documento.");
                });
        aplicarIdentidad(p, s, documento);
        aplicarContacto(p, s.email(), s.telefono(), s.direccion(), s.contactoEmergenciaNombre(),
                s.contactoEmergenciaTelefono());
        p = pacienteRepository.save(p);
        auditoriaService.registrar("PACIENTE_ACTUALIZADO", ENTIDAD, String.valueOf(id), ResultadoAuditoria.EXITO, null);
        return PacienteMapper.aResponse(p, true, fechaHora);
    }

    /** El paciente edita solo sus datos de contacto. */
    @Transactional
    public PacienteResponse actualizarPerfil(PerfilPacienteRequest s) {
        Paciente p = pacienteActual();
        aplicarContacto(p, s.email(), s.telefono(), s.direccion(), s.contactoEmergenciaNombre(),
                s.contactoEmergenciaTelefono());
        p = pacienteRepository.save(p);
        auditoriaService.registrar("PACIENTE_PERFIL_ACTUALIZADO", ENTIDAD, String.valueOf(p.getId()),
                ResultadoAuditoria.EXITO, null);
        return PacienteMapper.aResponse(p, true, fechaHora);
    }

    /**
     * Desactiva o reactiva al paciente sin borrar nada. Si tiene cuenta de acceso, se activa o desactiva con él.
     * No se puede desactivar con citas futuras vigentes: primero deben cancelarse o atenderse.
     */
    @Transactional
    public PacienteResponse cambiarEstado(Long id, boolean activo) {
        Paciente p = buscarPorId(id);
        if (!activo) {
            long futuras = citaRepository.countByPacienteIdAndInicioUtcAfterAndEstadoNotIn(
                    id, fechaHora.ahoraUtc(), NO_OCUPAN);
            if (futuras > 0) {
                throw new ReglaNegocioException("PACIENTE_CON_CITAS",
                        "El paciente tiene " + futuras + " cita(s) futura(s). Cancélelas antes de desactivarlo.");
            }
        }
        p.setActivo(activo);
        p = pacienteRepository.save(p);
        Usuario cuenta = p.getUsuario();
        if (cuenta != null) {
            cuenta.setActivo(activo);
            usuarioRepository.save(cuenta);
        }
        auditoriaService.registrar(activo ? "PACIENTE_ACTIVADO" : "PACIENTE_DESACTIVADO", ENTIDAD,
                String.valueOf(id), ResultadoAuditoria.EXITO, null);
        return PacienteMapper.aResponse(p, true, fechaHora);
    }

    // ------------------------------------------------------------------ apoyo

    /**
     * Verifica que el usuario actual pueda ver al paciente.
     *
     * @return true si puede ver el detalle completo; false si solo la vista clínica reducida (médico).
     * @throws AccessDeniedException si no tiene relación con el paciente
     */
    boolean verificarAcceso(Paciente p) {
        if (SecurityUtils.tieneRol(NombreRol.ADMINISTRADOR) || SecurityUtils.tieneRol(NombreRol.RECEPCIONISTA)) {
            return true;
        }
        Long actor = SecurityUtils.usuarioIdActual().orElse(null);
        if (actor != null && SecurityUtils.tieneRol(NombreRol.MEDICO)) {
            Medico medico = medicoRepository.findByUsuarioId(actor).orElse(null);
            if (medico != null && citaRepository.existsByMedicoIdAndPacienteId(medico.getId(), p.getId())) {
                return false;
            }
        }
        if (actor != null && SecurityUtils.tieneRol(NombreRol.PACIENTE)
                && p.getUsuario() != null && actor.equals(p.getUsuario().getId())) {
            return true;
        }
        throw new AccessDeniedException("Sin permiso para consultar a este paciente");
    }

    private Paciente pacienteActual() {
        Long actor = SecurityUtils.usuarioIdActual()
                .orElseThrow(() -> new AccessDeniedException("Se requiere autenticación"));
        return pacienteRepository.findByUsuarioId(actor)
                .orElseThrow(() -> new RecursoNoEncontradoException("Su cuenta no tiene una ficha de paciente asociada."));
    }

    private Paciente buscarPorId(Long id) {
        return pacienteRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el paciente solicitado."));
    }

    private static PageRequest pagina(int pagina, int tamano, Sort orden) {
        return PageRequest.of(Math.max(pagina, 0), Math.min(Math.max(tamano, 1), TAMANO_MAXIMO), orden);
    }

    private static void aplicarIdentidad(Paciente p, PacienteRequest s, String documento) {
        p.setNombres(s.nombres().trim());
        p.setApellidos(s.apellidos().trim());
        p.setTipoDocumento(s.tipoDocumento());
        p.setNumeroDocumento(documento);
        p.setFechaNacimiento(s.fechaNacimiento());
        p.setSexo(s.sexo());
    }

    private static void aplicarContacto(Paciente p, String email, String telefono, String direccion,
                                        String contactoNombre, String contactoTelefono) {
        p.setEmail(limpiar(email));
        p.setTelefono(limpiar(telefono));
        p.setDireccion(limpiar(direccion));
        p.setContactoEmergenciaNombre(limpiar(contactoNombre));
        p.setContactoEmergenciaTelefono(limpiar(contactoTelefono));
    }

    private static String limpiar(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }
}
