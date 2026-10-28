package com.medigestsystem.integracion;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.jayway.jsonpath.JsonPath;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * Base de las pruebas de integración: SQL Server real en un contenedor (Testcontainers), Flyway aplicando las
 * migraciones, los datos de demostración cargados y la aplicación levantada en un puerto aleatorio.
 * El contenedor se comparte entre todas las clases de prueba (patrón "singleton container").
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test", "demo"})
public abstract class AbstractIT {

    static final String CLAVE_SA = "Sa-Clave-IT-2030!x";
    static final String CLAVE_DEMO = "Demo-Clave-IT-2030!";
    static final String BASE = "MediGestIT";
    static final ZoneId LIMA = ZoneId.of("America/Lima");

    static final GenericContainer<?> SQL = new GenericContainer<>(DockerImageName.parse("mcr.microsoft.com/mssql/server:2022-latest"))
            .withEnv("ACCEPT_EULA", "Y")
            .withEnv("MSSQL_PID", "Developer")
            .withEnv("MSSQL_SA_PASSWORD", CLAVE_SA)
            .withExposedPorts(1433)
            .waitingFor(Wait.forLogMessage(".*SQL Server is now ready for client connections.*\\n", 1))
            .withStartupTimeout(Duration.ofMinutes(6));

    static {
        SQL.start();
        crearBaseDeDatos();
    }

    private static String urlServidor() {
        return "jdbc:sqlserver://" + SQL.getHost() + ":" + SQL.getMappedPort(1433) + ";encrypt=true;trustServerCertificate=true";
    }

    private static void crearBaseDeDatos() {
        Exception ultimo = null;
        for (int i = 0; i < 30; i++) {
            try (Connection c = DriverManager.getConnection(urlServidor(), "sa", CLAVE_SA); Statement s = c.createStatement()) {
                s.execute("CREATE DATABASE " + BASE + " COLLATE Latin1_General_100_CI_AI");
                return;
            } catch (Exception e) {
                ultimo = e;
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        throw new IllegalStateException("No se pudo crear la base de datos de pruebas", ultimo);
    }

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> urlServidor() + ";databaseName=" + BASE);
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> CLAVE_SA);
        registry.add("medigest.demo.password", () -> CLAVE_DEMO);
        registry.add("medigest.seguridad.jwt.secreto", () -> "clave-jwt-de-pruebas-de-integracion-con-mas-de-32-caracteres");
        registry.add("medigest.tareas.habilitadas", () -> "false");
        registry.add("medigest.correo.habilitado", () -> "false");
    }

    // ------------------------------------------------------------------ cliente HTTP

    protected record Resp(int status, String body, HttpHeaders headers) {
        public String cabecera(String nombre) {
            return headers.firstValue(nombre).orElse(null);
        }

        public <T> T json(String ruta) {
            return JsonPath.read(body, ruta);
        }
    }

    protected record Horario(long medicoId, int especialidadId, String inicio) {
    }

    private static final Map<String, String> TOKENS = new ConcurrentHashMap<>();

    @Value("${local.server.port}")
    protected int puerto;

    @Autowired
    protected JdbcTemplate jdbc;

    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(10)).build();

    protected Resp enviar(String metodo, String ruta, String token, String json) {
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
                    .timeout(Duration.ofSeconds(60));
            if (token != null) {
                b.header("Authorization", "Bearer " + token);
            }
            if (json != null) {
                b.header("Content-Type", "application/json");
                b.method(metodo, HttpRequest.BodyPublishers.ofString(json));
            } else {
                b.method(metodo, HttpRequest.BodyPublishers.noBody());
            }
            HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new Resp(r.statusCode(), r.body(), r.headers());
        } catch (Exception e) {
            throw new IllegalStateException("Fallo la petición " + metodo + " " + ruta, e);
        }
    }

    protected Resp get(String ruta, String token) {
        return enviar("GET", ruta, token, null);
    }

    protected Resp post(String ruta, String token, String json) {
        return enviar("POST", ruta, token, json);
    }

    protected Resp patch(String ruta, String token, String json) {
        return enviar("PATCH", ruta, token, json);
    }

    protected String token(String usuario) {
        return TOKENS.computeIfAbsent(usuario, u -> {
            Resp r = post("/api/v1/auth/login", null, "{\"email\":\"" + u + "@medigest.test\",\"password\":\"" + CLAVE_DEMO + "\"}");
            if (r.status() != 200) {
                throw new IllegalStateException("No se pudo iniciar sesión como " + u + ": " + r.status() + " " + r.body());
            }
            return r.json("$.accessToken");
        });
    }

    // ------------------------------------------------------------------ datos de apoyo

    /** Primer horario libre de cualquier médico en los próximos días. */
    protected Horario buscarHorarioLibre(String token) {
        LocalDate hoy = LocalDate.now(LIMA);
        String rango = "desde=" + hoy.plusDays(2) + "&hasta=" + hoy.plusDays(13);
        List<Integer> especialidades = get("/api/v1/especialidades", token).json("$[*].id");
        for (Integer e : especialidades) {
            Resp r = get("/api/v1/disponibilidad/medicos?especialidadId=" + e + "&" + rango, token);
            List<Map<String, Object>> medicos = r.json("$[?(@.proximoHorario)]");
            if (!medicos.isEmpty()) {
                Map<String, Object> m = medicos.get(0);
                @SuppressWarnings("unchecked")
                Map<String, Object> h = (Map<String, Object>) m.get("proximoHorario");
                return new Horario(((Number) m.get("medicoId")).longValue(), e, (String) h.get("inicio"));
            }
        }
        throw new IllegalStateException("No hay horarios libres en los datos de demostración");
    }

    protected List<Integer> pacientesActivos(String token) {
        return get("/api/v1/pacientes?tamano=20&activo=true", token).json("$.contenido[*].id");
    }

    protected Resp reservar(String token, int pacienteId, Horario h) {
        return post("/api/v1/citas", token, "{\"pacienteId\":" + pacienteId + ",\"medicoId\":" + h.medicoId()
                + ",\"especialidadId\":" + h.especialidadId() + ",\"inicio\":\"" + h.inicio() + "\",\"motivo\":\"prueba de integración\"}");
    }
}
