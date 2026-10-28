package com.medigestsystem.demo;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.medigestsystem.auditoria.Auditoria;
import com.medigestsystem.auditoria.AuditoriaRepository;
import com.medigestsystem.auditoria.ResultadoAuditoria;
import com.medigestsystem.citas.Cita;
import com.medigestsystem.citas.CitaRepository;
import com.medigestsystem.citas.EstadoCita;
import com.medigestsystem.citas.HistorialCita;
import com.medigestsystem.citas.HistorialCitaRepository;
import com.medigestsystem.citas.TipoEventoCita;
import com.medigestsystem.consultorios.Consultorio;
import com.medigestsystem.consultorios.ConsultorioRepository;
import com.medigestsystem.especialidades.Especialidad;
import com.medigestsystem.especialidades.EspecialidadRepository;
import com.medigestsystem.horarios.HorarioMedico;
import com.medigestsystem.horarios.HorarioMedicoRepository;
import com.medigestsystem.listaespera.EstadoListaEspera;
import com.medigestsystem.listaespera.FranjaPreferida;
import com.medigestsystem.listaespera.ListaEspera;
import com.medigestsystem.listaespera.ListaEsperaRepository;
import com.medigestsystem.medicos.Medico;
import com.medigestsystem.medicos.MedicoRepository;
import com.medigestsystem.pacientes.Paciente;
import com.medigestsystem.pacientes.PacienteRepository;
import com.medigestsystem.pacientes.Sexo;
import com.medigestsystem.pacientes.TipoDocumento;
import com.medigestsystem.shared.util.FechaHoraUtil;
import com.medigestsystem.usuarios.NombreRol;
import com.medigestsystem.usuarios.Rol;
import com.medigestsystem.usuarios.RolRepository;
import com.medigestsystem.usuarios.Usuario;
import com.medigestsystem.usuarios.UsuarioRepository;

/**
 * Carga datos completamente ficticios para demostraciones. Solo existe con el perfil
 * {@code demo}, se niega a ejecutarse junto al perfil {@code prod} y no hace nada si la
 * base ya contiene usuarios.
 *
 * <p>Los horarios de las citas se generan sin solapamientos por construcción:
 * los médicos 1 a 5 atienden por la mañana, los médicos 6 a 8 por la tarde (comparten
 * consultorio con los 1 a 3, pero en franja distinta) y cada ronda de citas usa una hora distinta.</p>
 */
@Component
@Profile("demo")
public class DemoDataLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataLoader.class);

    private static final String DOMINIO = "@medigest.test";
    private static final int TOTAL_CITAS = 30;

    private static final String[][] ESPECIALIDADES = {
            {"Medicina General", "Atención integral y primaria del adulto", "30"},
            {"Pediatría", "Atención médica de niños y adolescentes", "30"},
            {"Cardiología", "Diagnóstico y tratamiento de enfermedades del corazón", "40"},
            {"Dermatología", "Cuidado de la piel, el cabello y las uñas", "20"},
            {"Traumatología", "Lesiones del sistema musculoesquelético", "30"},
            {"Neurología", "Enfermedades del sistema nervioso", "45"},
            {"Ginecología", "Salud ginecológica y reproductiva", "30"},
            {"Oftalmología", "Salud visual y enfermedades de los ojos", "25"},
    };

    private static final String[][] CONSULTORIOS = {
            {"C-101", "Piso 1"}, {"C-102", "Piso 1"}, {"C-201", "Piso 2"},
            {"C-202", "Piso 2"}, {"C-301", "Piso 3"},
    };

    private static final String[] NOMBRES_MEDICOS = {
            "Alberto", "Beatriz", "Carlos", "Daniela", "Eduardo", "Fabiola", "Gonzalo", "Helena",
    };

    private static final String[] APELLIDOS_MEDICOS = {
            "Quispe Huamán", "Salazar Prieto", "Villanueva Cruz", "Paredes León",
            "Montoya Díaz", "Aguilar Ríos", "Cárdenas Soto", "Medina Vega",
    };

    private static final String[] NOMBRES_PACIENTES = {
            "Lucía", "Mateo", "Valentina", "Santiago", "Camila", "Sebastián", "Isabella",
            "Diego", "Mariana", "Andrés", "Sofía", "Gabriel", "Daniela", "Nicolás", "Paula",
            "Javier", "Fernanda", "Rodrigo", "Andrea", "Martín",
    };

    private static final String[] APELLIDOS_PACIENTES = {
            "García", "Rodríguez", "Fernández", "López", "Martínez", "Sánchez", "Pérez",
            "Gómez", "Ramírez", "Torres", "Flores", "Rivera", "Vargas", "Castillo", "Mendoza",
            "Rojas", "Ortega", "Silva", "Navarro", "Herrera",
    };

    private static final EstadoCita[] ESTADOS_CITAS = {
            EstadoCita.ATENDIDA, EstadoCita.ATENDIDA, EstadoCita.ATENDIDA, EstadoCita.ATENDIDA,
            EstadoCita.ATENDIDA, EstadoCita.ATENDIDA, EstadoCita.ATENDIDA, EstadoCita.ATENDIDA,
            EstadoCita.CANCELADA, EstadoCita.CANCELADA, EstadoCita.CANCELADA, EstadoCita.CANCELADA,
            EstadoCita.NO_ASISTIO, EstadoCita.NO_ASISTIO, EstadoCita.NO_ASISTIO,
            EstadoCita.PENDIENTE, EstadoCita.PENDIENTE, EstadoCita.PENDIENTE,
            EstadoCita.PENDIENTE, EstadoCita.PENDIENTE, EstadoCita.PENDIENTE,
            EstadoCita.CONFIRMADA, EstadoCita.CONFIRMADA, EstadoCita.CONFIRMADA,
            EstadoCita.CONFIRMADA, EstadoCita.CONFIRMADA, EstadoCita.CONFIRMADA,
            EstadoCita.EN_ESPERA, EstadoCita.EN_ATENCION, EstadoCita.ATENDIDA,
    };

    /** Desplazamiento en días hábiles respecto a hoy (negativo = pasado). */
    private static final int[] DESPLAZAMIENTOS = {
            -12, -11, -10, -9, -8, -7, -6, -5,
            -4, -3, 2, 3,
            -6, -4, -2,
            1, 2, 3, 4, 5, 6,
            1, 2, 3, 4, 5, 7,
            0, 0, 0,
    };

    private final Environment environment;
    private final String claveDemo;
    private final PasswordEncoder passwordEncoder;
    private final FechaHoraUtil fechaHoraUtil;
    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final EspecialidadRepository especialidadRepository;
    private final ConsultorioRepository consultorioRepository;
    private final PacienteRepository pacienteRepository;
    private final MedicoRepository medicoRepository;
    private final HorarioMedicoRepository horarioMedicoRepository;
    private final CitaRepository citaRepository;
    private final HistorialCitaRepository historialCitaRepository;
    private final ListaEsperaRepository listaEsperaRepository;
    private final AuditoriaRepository auditoriaRepository;

    public DemoDataLoader(Environment environment,
                          @Value("${medigest.demo.password:}") String claveDemo,
                          PasswordEncoder passwordEncoder,
                          FechaHoraUtil fechaHoraUtil,
                          UsuarioRepository usuarioRepository,
                          RolRepository rolRepository,
                          EspecialidadRepository especialidadRepository,
                          ConsultorioRepository consultorioRepository,
                          PacienteRepository pacienteRepository,
                          MedicoRepository medicoRepository,
                          HorarioMedicoRepository horarioMedicoRepository,
                          CitaRepository citaRepository,
                          HistorialCitaRepository historialCitaRepository,
                          ListaEsperaRepository listaEsperaRepository,
                          AuditoriaRepository auditoriaRepository) {
        this.environment = environment;
        this.claveDemo = claveDemo;
        this.passwordEncoder = passwordEncoder;
        this.fechaHoraUtil = fechaHoraUtil;
        this.usuarioRepository = usuarioRepository;
        this.rolRepository = rolRepository;
        this.especialidadRepository = especialidadRepository;
        this.consultorioRepository = consultorioRepository;
        this.pacienteRepository = pacienteRepository;
        this.medicoRepository = medicoRepository;
        this.horarioMedicoRepository = horarioMedicoRepository;
        this.citaRepository = citaRepository;
        this.historialCitaRepository = historialCitaRepository;
        this.listaEsperaRepository = listaEsperaRepository;
        this.auditoriaRepository = auditoriaRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (environment.acceptsProfiles(Profiles.of("prod"))) {
            throw new IllegalStateException("El perfil demo no puede activarse junto al perfil prod.");
        }
        if (claveDemo == null || claveDemo.isBlank()) {
            throw new IllegalStateException(
                    "Defina la variable de entorno DEMO_PASSWORD (por ejemplo en .env) para cargar los datos demo.");
        }
        if (usuarioRepository.count() > 0) {
            log.info("Datos demo omitidos: la base de datos ya contiene usuarios.");
            return;
        }

        String hash = passwordEncoder.encode(claveDemo);
        Map<NombreRol, Rol> roles = new EnumMap<>(NombreRol.class);
        for (Rol rol : rolRepository.findAll()) {
            roles.put(rol.getNombre(), rol);
        }
        if (roles.size() != NombreRol.values().length) {
            throw new IllegalStateException("Faltan roles base: ¿se aplicó la migración V2?");
        }

        Usuario admin = crearUsuario("admin" + DOMINIO, hash, roles.get(NombreRol.ADMINISTRADOR));
        crearUsuario("recepcion1" + DOMINIO, hash, roles.get(NombreRol.RECEPCIONISTA));
        crearUsuario("recepcion2" + DOMINIO, hash, roles.get(NombreRol.RECEPCIONISTA));

        List<Especialidad> especialidades = crearEspecialidades();
        List<Consultorio> consultorios = crearConsultorios();
        List<Medico> medicos = crearMedicos(hash, roles.get(NombreRol.MEDICO), especialidades, consultorios);
        List<Paciente> pacientes = crearPacientes(hash, roles.get(NombreRol.PACIENTE));
        crearCitas(medicos, pacientes, admin);
        crearListaEspera(pacientes, medicos, especialidades);
        registrarAuditoria();

        log.info("Datos demo cargados: 1 administrador, 2 recepcionistas, {} médicos, {} especialidades, "
                        + "{} consultorios, {} pacientes y {} citas. Cuentas: admin{}, recepcion1{}, medico1{}, paciente1{}.",
                medicos.size(), especialidades.size(), consultorios.size(), pacientes.size(), TOTAL_CITAS,
                DOMINIO, DOMINIO, DOMINIO, DOMINIO);
    }

    private Usuario crearUsuario(String email, String hash, Rol rol) {
        Usuario usuario = new Usuario();
        usuario.setEmail(email);
        usuario.setPasswordHash(hash);
        usuario.setActivo(true);
        usuario.getRoles().add(rol);
        return usuarioRepository.save(usuario);
    }

    private List<Especialidad> crearEspecialidades() {
        List<Especialidad> lista = new ArrayList<>();
        for (String[] datos : ESPECIALIDADES) {
            Especialidad e = new Especialidad();
            e.setNombre(datos[0]);
            e.setDescripcion(datos[1]);
            e.setDuracionSugeridaMin(Integer.parseInt(datos[2]));
            e.setActiva(true);
            lista.add(especialidadRepository.save(e));
        }
        return lista;
    }

    private List<Consultorio> crearConsultorios() {
        List<Consultorio> lista = new ArrayList<>();
        for (String[] datos : CONSULTORIOS) {
            Consultorio c = new Consultorio();
            c.setCodigo(datos[0]);
            c.setUbicacion(datos[1]);
            c.setActivo(true);
            lista.add(consultorioRepository.save(c));
        }
        return lista;
    }

    private List<Medico> crearMedicos(String hash, Rol rolMedico,
                                      List<Especialidad> especialidades, List<Consultorio> consultorios) {
        List<Medico> lista = new ArrayList<>();
        for (int i = 0; i < NOMBRES_MEDICOS.length; i++) {
            Especialidad especialidad = especialidades.get(i);
            boolean turnoManana = i < 5;

            Medico medico = new Medico();
            medico.setUsuario(crearUsuario("medico" + (i + 1) + DOMINIO, hash, rolMedico));
            medico.setNombres(NOMBRES_MEDICOS[i]);
            medico.setApellidos(APELLIDOS_MEDICOS[i]);
            medico.setNumeroColegiatura(String.format("CMP-%05d", 10001 + i));
            medico.setEmail("medico" + (i + 1) + DOMINIO);
            medico.setTelefono(String.format("9%08d", 51000000 + i));
            medico.setConsultorio(consultorios.get(i % consultorios.size()));
            medico.setDuracionConsultaMin(especialidad.getDuracionSugeridaMin());
            medico.setActivo(true);
            medico.getEspecialidades().add(especialidad);
            medico = medicoRepository.save(medico);
            lista.add(medico);

            for (int dia = 1; dia <= 5; dia++) {
                HorarioMedico horario = new HorarioMedico();
                horario.setMedico(medico);
                horario.setDiaSemana(dia);
                horario.setHoraInicio(turnoManana ? LocalTime.of(8, 0) : LocalTime.of(14, 0));
                horario.setHoraFin(turnoManana ? LocalTime.of(14, 0) : LocalTime.of(19, 0));
                horario.setDescansoInicio(turnoManana ? LocalTime.of(12, 0) : LocalTime.of(18, 0));
                horario.setDescansoFin(turnoManana ? LocalTime.of(13, 0) : LocalTime.of(18, 30));
                horario.setActivo(true);
                horarioMedicoRepository.save(horario);
            }
        }
        // Las filas de medico_especialidades se insertan al hacer flush; las citas las referencian
        // mediante una clave foránea compuesta, así que deben existir antes de crear las citas.
        medicoRepository.flush();
        return lista;
    }

    private List<Paciente> crearPacientes(String hash, Rol rolPaciente) {
        List<Paciente> lista = new ArrayList<>();
        for (int i = 0; i < NOMBRES_PACIENTES.length; i++) {
            Paciente p = new Paciente();
            // Los 5 primeros pacientes tienen cuenta de acceso para probar el portal del paciente.
            if (i < 5) {
                p.setUsuario(crearUsuario("paciente" + (i + 1) + DOMINIO, hash, rolPaciente));
            }
            p.setNombres(NOMBRES_PACIENTES[i]);
            p.setApellidos(APELLIDOS_PACIENTES[i] + " " + APELLIDOS_PACIENTES[(i + 7) % APELLIDOS_PACIENTES.length]);
            p.setTipoDocumento(TipoDocumento.DNI);
            p.setNumeroDocumento(String.format("7%07d", 1000 + i));
            p.setFechaNacimiento(LocalDate.of(1960 + i * 2, 1 + (i % 12), 1 + (i % 27)));
            p.setSexo(i % 2 == 0 ? Sexo.F : Sexo.M);
            p.setEmail("paciente" + (i + 1) + DOMINIO);
            p.setTelefono(String.format("9%08d", 52000000 + i));
            p.setDireccion("Calle Ficticia " + (100 + i) + ", Lima");
            p.setContactoEmergenciaNombre("Contacto de " + NOMBRES_PACIENTES[i]);
            p.setContactoEmergenciaTelefono(String.format("9%08d", 53000000 + i));
            p.setActivo(true);
            lista.add(pacienteRepository.save(p));
        }
        return lista;
    }

    private void crearCitas(List<Medico> medicos, List<Paciente> pacientes, Usuario admin) {
        LocalDate base = fechaHoraUtil.hoyEnClinica();
        while (!esDiaHabil(base)) {
            base = base.minusDays(1);
        }
        Instant ahora = fechaHoraUtil.ahoraUtc();

        for (int i = 0; i < TOTAL_CITAS; i++) {
            int indiceMedico = i % medicos.size();
            int ronda = i / medicos.size();
            Medico medico = medicos.get(indiceMedico);
            boolean turnoManana = indiceMedico < 5;

            LocalDate dia = diaHabil(base, DESPLAZAMIENTOS[i]);
            LocalDateTime inicioLocal = LocalDateTime.of(dia, LocalTime.of((turnoManana ? 8 : 14) + ronda, 0));
            Instant inicio = fechaHoraUtil.aInstante(inicioLocal);
            Instant fin = inicio.plus(medico.getDuracionConsultaMin(), ChronoUnit.MINUTES);
            EstadoCita estado = ESTADOS_CITAS[i];

            Cita cita = new Cita();
            cita.setCodigoReserva(String.format("MG-%s-%04d", dia.format(DateTimeFormatter.BASIC_ISO_DATE), i + 1));
            cita.setPaciente(pacientes.get(i % pacientes.size()));
            cita.setMedico(medico);
            cita.setEspecialidad(medico.getEspecialidades().iterator().next());
            cita.setConsultorio(medico.getConsultorio());
            cita.setInicioUtc(inicio);
            cita.setFinUtc(fin);
            cita.setEstado(estado);
            cita.setMotivo("Consulta de demostración");
            cita.setCreadoPor(admin);
            cita.setModificadoPor(admin);
            if (estado == EstadoCita.CANCELADA) {
                cita.setMotivoCancelacion("Cancelada por el paciente (demostración)");
                cita.setCanceladaEn(ahora);
            }
            cita = citaRepository.save(cita);

            registrarHistorial(cita, TipoEventoCita.CREADA, null, EstadoCita.PENDIENTE, admin);
            if (estado != EstadoCita.PENDIENTE) {
                TipoEventoCita tipo = switch (estado) {
                    case CANCELADA -> TipoEventoCita.CANCELADA;
                    case CONFIRMADA -> TipoEventoCita.CONFIRMADA;
                    default -> TipoEventoCita.CAMBIO_ESTADO;
                };
                registrarHistorial(cita, tipo, EstadoCita.PENDIENTE, estado, admin);
            }
        }
    }

    private void registrarHistorial(Cita cita, TipoEventoCita tipo, EstadoCita anterior,
                                    EstadoCita nuevo, Usuario usuario) {
        HistorialCita h = new HistorialCita();
        h.setCita(cita);
        h.setTipoEvento(tipo);
        h.setEstadoAnterior(anterior);
        h.setEstadoNuevo(nuevo);
        h.setInicioNuevoUtc(cita.getInicioUtc());
        h.setUsuario(usuario);
        historialCitaRepository.save(h);
    }

    private void crearListaEspera(List<Paciente> pacientes, List<Medico> medicos, List<Especialidad> especialidades) {
        LocalDate hoy = fechaHoraUtil.hoyEnClinica();
        FranjaPreferida[] franjas = {
                FranjaPreferida.CUALQUIERA, FranjaPreferida.MANANA, FranjaPreferida.TARDE, FranjaPreferida.CUALQUIERA,
        };
        for (int k = 0; k < 4; k++) {
            int indiceEspecialidad = (k * 2) % especialidades.size();
            ListaEspera espera = new ListaEspera();
            espera.setPaciente(pacientes.get(14 + k));
            espera.setEspecialidad(especialidades.get(indiceEspecialidad));
            if (k % 2 == 1) {
                espera.setMedico(medicos.get(indiceEspecialidad));
            }
            espera.setFechaDesde(hoy);
            espera.setFechaHasta(hoy.plusDays(14));
            espera.setFranja(franjas[k]);
            espera.setEstado(k == 3 ? EstadoListaEspera.VENCIDA : EstadoListaEspera.ACTIVA);
            listaEsperaRepository.save(espera);
        }
    }

    private void registrarAuditoria() {
        Auditoria a = new Auditoria();
        a.setAccion("CARGA_DATOS_DEMO");
        a.setEntidad("SISTEMA");
        a.setResultado(ResultadoAuditoria.EXITO);
        a.setDetalle("Datos ficticios generados con el perfil demo");
        auditoriaRepository.save(a);
    }

    private static boolean esDiaHabil(LocalDate fecha) {
        DayOfWeek d = fecha.getDayOfWeek();
        return d != DayOfWeek.SATURDAY && d != DayOfWeek.SUNDAY;
    }

    private static LocalDate diaHabil(LocalDate base, int desplazamiento) {
        int paso = desplazamiento >= 0 ? 1 : -1;
        int restantes = Math.abs(desplazamiento);
        LocalDate fecha = base;
        while (restantes > 0) {
            fecha = fecha.plusDays(paso);
            if (esDiaHabil(fecha)) {
                restantes--;
            }
        }
        return fecha;
    }
}
