package com.medigestsystem.system;

import java.util.Arrays;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.medigestsystem.shared.util.FechaHoraUtil;

@RestController
@RequestMapping("/api/v1/system")
@Tag(name = "Sistema", description = "Diagnóstico de la aplicación")
public class SystemController {

    private static final Logger log = LoggerFactory.getLogger(SystemController.class);

    private final JdbcTemplate jdbcTemplate;
    private final Environment environment;
    private final FechaHoraUtil fechaHoraUtil;

    public SystemController(JdbcTemplate jdbcTemplate,
                            Environment environment,
                            FechaHoraUtil fechaHoraUtil) {
        this.jdbcTemplate = jdbcTemplate;
        this.environment = environment;
        this.fechaHoraUtil = fechaHoraUtil;
    }

    @GetMapping("/ping")
    @Operation(summary = "Estado de la aplicación y de la conexión a SQL Server")
    public SystemStatusResponse ping() {
        boolean dbOk = false;
        String dbName = null;
        try {
            dbName = jdbcTemplate.queryForObject("SELECT DB_NAME()", String.class);
            dbOk = true;
        } catch (DataAccessException ex) {
            log.error("No se pudo consultar la base de datos: {}", ex.getMostSpecificCause().getMessage());
        }

        var ahora = fechaHoraUtil.ahoraUtc();
        return new SystemStatusResponse(
                environment.getProperty("spring.application.name"),
                Arrays.asList(environment.getActiveProfiles()),
                environment.getProperty("medigest.timezone", "America/Lima"),
                ahora,
                fechaHoraUtil.aZonaClinica(ahora),
                dbOk,
                dbName);
    }
}
