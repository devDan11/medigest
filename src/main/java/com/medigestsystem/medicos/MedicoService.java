package com.medigestsystem.medicos;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.persistence.criteria.Predicate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medigestsystem.auditoria.AuditoriaService;
import com.medigestsystem.auditoria.ResultadoAuditoria;
import com.medigestsystem.auth.PoliticaPassword;
import com.medigestsystem.citas.CitaMapper;
import com.medigestsystem.citas.CitaRepository;
import com.medigestsystem.citas.EstadoCita;
import com.medigestsystem.citas.dto.CitaResumenResponse;
import com.medigestsystem.consultorios.Consultorio;
import com.medigestsystem.consultorios.ConsultorioRepository;
import com.medigestsystem.especialidades.Especialidad;
import com.medigestsystem.especialidades.EspecialidadRepository;
import com.medigestsystem.medicos.dto.CuentaMedicoRequest;
import com.medigestsystem.medicos.dto.MedicoRequest;
import com.medigestsystem.medicos.dto.MedicoResponse;
import com.medigestsystem.medicos.dto.MedicoResumenResponse;
import com.medigestsystem.security.SecurityUtils;
import com.medigestsystem.shared.dto.PaginaResponse;
import com.medigestsystem.shared.exception.ConflictoException;
import com.medigestsystem.shared.exception.RecursoNoEncontradoException;
import com.medigestsystem.shared.exception.ReglaNegocioException;
import com.medigestsystem.shared.exception.SolicitudInvalidaException;
import com.medigestsystem.shared.util.FechaHoraUtil;
import com.medigestsystem.usuarios.NombreRol;
import com.medigestsystem.usuarios.Rol;
import com.medigestsystem.usuarios.RolRepository;
import com.medigestsystem.usuarios.Usuario;
import com.medigestsystem.usuarios.UsuarioRepository;

@Service
public class MedicoService {

    private static final String ENTIDAD = "medicos";
    private static final int TAMANO_MAXIMO = 100;
    static final int MAX_DIAS_AGENDA = 31;
    private static final Set<EstadoCita> NO_OCUPAN = EnumSet.of(EstadoCita.CANCELADA, EstadoCita.NO_ASISTIO);

    private final MedicoRepository medicoRepository;
    private final EspecialidadRepository especialidadRepository;
    private final ConsultorioRepository consultorioRepository;
    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final CitaRepository citaRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditoriaService auditoriaService;
    private final FechaHoraUtil fechaHora;

    public MedicoService(MedicoRepository medicoRepository, EspecialidadRepository especialidadRepository,
                         ConsultorioRepository consultorioRepository, UsuarioRepository usuarioRepository,
                         RolRepository rolRepository, CitaRepository citaRepository,
                         PasswordEncoder passwordEncoder, AuditoriaService auditoriaService,
                         FechaHoraUtil fechaHora) {
        this.medicoRepository = medicoRepository;
        this.especialidadRepository = especialidadRepository;
        this.consultorioRepository = consultorioRepository;
        this.usuarioRepository = usuarioRepository;
        this.rolRepository = rolRepository;
        this.citaRepository = citaRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditoriaService = auditoriaService;
        this.fechaHora = fechaHora;
    }

    // ------------------------------------------------------------------ consulta

    /** Listado con filtros opcionales. Quien no es personal administrativo solo ve médicos activos. */
    @Transactional(readOnly = true)
    public PaginaResponse<MedicoResponse> listar(String texto, Integer especialidadId, Boolean activo,
                                                 int pagina, int tamano) {
        boolean personal = esPersonal();
        Specification<Medico> spec = (root, query, cb) -> {
            List<Predicate> filtros = new ArrayList<>();
            if (texto != null && !texto.isBlank()) {
                String patron = "%" + texto.trim().toLowerCase() + "%";
                filtros.add(cb.or(
                        cb.like(cb.lower(root.get("nombres")), patron),
                        cb.like(cb.lower(root.get("apellidos")), patron),
                        cb.like(cb.lower(cb.concat(cb.concat(root.get("nombres"), " "), root.get("apellidos"))), patron),
                        cb.like(cb.lower(root.get("numeroColegiatura")), patron)));
            }
            if (especialidadId != null) {
                query.distinct(true);
                filtros.add(cb.equal(root.join("especialidades").get("id"), especialidadId));
            }
            if (!personal) {
                filtros.add(cb.isTrue(root.get("activo")));
            } else if (activo != null) {
                filtros.add(cb.equal(root.get("activo"), activo));
            }
            return cb.and(filtros.toArray(new Predicate[0]));
        };
        Page<Medico> resultado = medicoRepository.findAll(spec, PageRequest.of(
                Math.max(pagina, 0), Math.min(Math.max(tamano, 1), TAMANO_MAXIMO),
                Sort.by("apellidos").ascending().and(Sort.by("nombres").ascending())));
        return PaginaResponse.de(resultado.map(m -> MedicoMapper.aResponse(m, personal)));
    }

    @Transactional(readOnly = true)
    public MedicoResponse obtener(Long id) {
        Medico m = buscar(id);
        boolean personal = esPersonal();
        if (!personal && !m.isActivo() && !esPropio(m)) {
            throw new RecursoNoEncontradoException("No existe el médico solicitado.");
        }
        return MedicoMapper.aResponse(m, personal || esPropio(m));
    }

    @Transactional(readOnly = true)
    public MedicoResumenResponse resumen(Long id) {
        Medico m = buscar(id);
        verificarPersonalOPropio(m);
        return new MedicoResumenResponse(
                citaRepository.countByMedicoIdAndEstado(id, EstadoCita.ATENDIDA),
                citaRepository.countByMedicoIdAndEstado(id, EstadoCita.CANCELADA),
                citaRepository.countByMedicoIdAndEstado(id, EstadoCita.NO_ASISTIO),
                citaRepository.countByMedicoIdAndInicioUtcAfterAndEstadoNotIn(id, fechaHora.ahoraUtc(), NO_OCUPAN));
    }

    /** Citas del médico entre dos fechas (hora de la clínica, ambas inclusivas), máximo 31 días. */
    @Transactional(readOnly = true)
    public List<CitaResumenResponse> agenda(Long id, LocalDate desde, LocalDate hasta) {
        Medico m = buscar(id);
        verificarPersonalOPropio(m);
        if (hasta.isBefore(desde)) {
            throw new SolicitudInvalidaException("RANGO_INVALIDO", "La fecha final no puede ser anterior a la inicial.");
        }
        if (ChronoUnit.DAYS.between(desde, hasta) + 1 > MAX_DIAS_AGENDA) {
            throw new SolicitudInvalidaException("RANGO_EXCESIVO",
                    "El rango máximo de consulta es de " + MAX_DIAS_AGENDA + " días.");
        }
        return citaRepository
                .findByMedicoIdAndInicioUtcGreaterThanEqualAndInicioUtcLessThanOrderByInicioUtcAsc(id,
                        fechaHora.aInstante(desde.atStartOfDay()), fechaHora.aInstante(hasta.plusDays(1).atStartOfDay()))
                .stream().map(c -> CitaMapper.aResumen(c, fechaHora)).toList();
    }

    // ------------------------------------------------------------------ escritura

    @Transactional
    public MedicoResponse crear(MedicoRequest s) {
        String colegiatura = normalizarColegiatura(s.numeroColegiatura());
        if (medicoRepository.existsByNumeroColegiatura(colegiatura)) {
            throw new ConflictoException("COLEGIATURA_DUPLICADA", "Ya existe un médico con ese número de colegiatura.");
        }
        Medico m = new Medico();
        m.setNumeroColegiatura(colegiatura);
        aplicar(m, s);
        m.setActivo(true);

        CuentaMedicoRequest cuenta = s.cuenta();
        if (cuenta != null) {
            m.setUsuario(crearCuenta(cuenta));
            if (m.getEmail() == null) {
                m.setEmail(cuenta.email().trim().toLowerCase());
            }
        }
        m = medicoRepository.save(m);
        auditoriaService.registrar("MEDICO_CREADO", ENTIDAD, String.valueOf(m.getId()),
                ResultadoAuditoria.EXITO, colegiatura);
        return MedicoMapper.aResponse(m, true);
    }

    @Transactional
    public MedicoResponse actualizar(Long id, MedicoRequest s) {
        if (s.cuenta() != null) {
            throw new SolicitudInvalidaException("CUENTA_NO_EDITABLE",
                    "Las credenciales no se modifican desde la ficha del médico.");
        }
        Medico m = buscar(id);
        String colegiatura = normalizarColegiatura(s.numeroColegiatura());
        medicoRepository.findByNumeroColegiatura(colegiatura)
                .filter(otro -> !otro.getId().equals(id))
                .ifPresent(otro -> {
                    throw new ConflictoException("COLEGIATURA_DUPLICADA",
                            "Ya existe un médico con ese número de colegiatura.");
                });
        // Una especialidad con citas registradas (incluso pasadas) no puede retirarse: el historial la referencia.
        Set<Integer> nuevas = s.especialidadIds();
        for (Especialidad actual : m.getEspecialidades()) {
            if (!nuevas.contains(actual.getId())
                    && citaRepository.existsByMedicoIdAndEspecialidadId(id, actual.getId())) {
                throw new ReglaNegocioException("ESPECIALIDAD_CON_CITAS",
                        "No se puede retirar la especialidad «" + actual.getNombre()
                                + "»: el médico tiene citas registradas con ella.");
            }
        }
        m.setNumeroColegiatura(colegiatura);
        aplicar(m, s);
        m = medicoRepository.save(m);
        auditoriaService.registrar("MEDICO_ACTUALIZADO", ENTIDAD, String.valueOf(id), ResultadoAuditoria.EXITO, null);
        return MedicoMapper.aResponse(m, true);
    }

    @Transactional
    public MedicoResponse cambiarEstado(Long id, boolean activo) {
        Medico m = buscar(id);
        if (!activo) {
            long futuras = citaRepository.countByMedicoIdAndInicioUtcAfterAndEstadoNotIn(
                    id, fechaHora.ahoraUtc(), NO_OCUPAN);
            if (futuras > 0) {
                throw new ReglaNegocioException("MEDICO_CON_CITAS",
                        "El médico tiene " + futuras + " cita(s) futura(s). Reprográmelas o cancélelas antes de desactivarlo.");
            }
        }
        m.setActivo(activo);
        m = medicoRepository.save(m);
        Usuario cuenta = m.getUsuario();
        if (cuenta != null) {
            cuenta.setActivo(activo);
            usuarioRepository.save(cuenta);
        }
        auditoriaService.registrar(activo ? "MEDICO_ACTIVADO" : "MEDICO_DESACTIVADO", ENTIDAD,
                String.valueOf(id), ResultadoAuditoria.EXITO, null);
        return MedicoMapper.aResponse(m, true);
    }

    // ------------------------------------------------------------------ apoyo (compartido con otros módulos)

    public Medico buscar(Long id) {
        return medicoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el médico solicitado."));
    }

    /** Médico asociado al usuario autenticado, si lo hay. */
    public Medico medicoActual() {
        Long actor = SecurityUtils.usuarioIdActual().orElse(null);
        return actor == null ? null : medicoRepository.findByUsuarioId(actor).orElse(null);
    }

    public boolean esPropio(Medico m) {
        return m.getUsuario() != null
                && SecurityUtils.usuarioIdActual().map(a -> a.equals(m.getUsuario().getId())).orElse(false);
    }

    private static boolean esPersonal() {
        return SecurityUtils.tieneRol(NombreRol.ADMINISTRADOR) || SecurityUtils.tieneRol(NombreRol.RECEPCIONISTA);
    }

    private void verificarPersonalOPropio(Medico m) {
        if (!esPersonal() && !esPropio(m)) {
            throw new AccessDeniedException("Sin permiso para consultar a este médico");
        }
    }

    private void aplicar(Medico m, MedicoRequest s) {
        m.setNombres(s.nombres().trim());
        m.setApellidos(s.apellidos().trim());
        m.setEmail(s.email() == null || s.email().isBlank() ? m.getEmail() : s.email().trim().toLowerCase());
        m.setTelefono(s.telefono() == null || s.telefono().isBlank() ? null : s.telefono().trim());
        m.setDuracionConsultaMin(s.duracionConsultaMin());

        Consultorio consultorio = null;
        if (s.consultorioId() != null) {
            consultorio = consultorioRepository.findById(s.consultorioId())
                    .orElseThrow(() -> new RecursoNoEncontradoException("No existe el consultorio indicado."));
            if (!consultorio.isActivo()) {
                throw new ReglaNegocioException("CONSULTORIO_INACTIVO", "El consultorio indicado está inactivo.");
            }
        }
        m.setConsultorio(consultorio);

        Set<Especialidad> especialidades = new HashSet<>();
        for (Integer espId : s.especialidadIds()) {
            Especialidad e = especialidadRepository.findById(espId)
                    .orElseThrow(() -> new RecursoNoEncontradoException(
                            "No existe la especialidad con id " + espId + "."));
            boolean yaAsignada = m.getEspecialidades().contains(e);
            if (!e.isActiva() && !yaAsignada) {
                throw new ReglaNegocioException("ESPECIALIDAD_INACTIVA",
                        "La especialidad «" + e.getNombre() + "» está inactiva.");
            }
            especialidades.add(e);
        }
        m.getEspecialidades().clear();
        m.getEspecialidades().addAll(especialidades);
    }

    private Usuario crearCuenta(CuentaMedicoRequest cuenta) {
        String email = cuenta.email().trim().toLowerCase();
        if (!PoliticaPassword.esValida(cuenta.password())) {
            throw new SolicitudInvalidaException("PASSWORD_DEBIL", PoliticaPassword.MENSAJE);
        }
        if (usuarioRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictoException("EMAIL_DUPLICADO", "Ya existe una cuenta con ese correo.");
        }
        Rol rol = rolRepository.findByNombre(NombreRol.MEDICO)
                .orElseThrow(() -> new IllegalStateException("Falta el rol MEDICO en la base de datos"));
        Usuario u = new Usuario();
        u.setEmail(email);
        u.setPasswordHash(passwordEncoder.encode(cuenta.password()));
        u.setActivo(true);
        Set<Rol> roles = new HashSet<>();
        roles.add(rol);
        u.setRoles(roles);
        return usuarioRepository.save(u);
    }

    private static String normalizarColegiatura(String valor) {
        return valor.trim().toUpperCase();
    }
}
