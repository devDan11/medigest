package com.medigestsystem.integracion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ArranqueIT extends AbstractIT {

    @Test
    void flywayAplicoTodasLasMigracionesYLosDatosBase() {
        Integer migraciones = jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1", Integer.class);
        Integer roles = jdbc.queryForObject("SELECT COUNT(*) FROM roles", Integer.class);

        assertThat(migraciones).isGreaterThanOrEqualTo(3);
        assertThat(roles).isEqualTo(4);
    }

    @Test
    void elPingConfirmaLaConexionConLaBaseDeDatos() {
        Resp r = get("/api/v1/system/ping", null);

        assertThat(r.status()).isEqualTo(200);
        assertThat(r.body()).contains(BASE);
    }

    @Test
    void losDatosDeDemostracionSeCargaron() {
        String admin = token("admin");

        assertThat(get("/api/v1/especialidades", admin).<java.util.List<Object>>json("$")).hasSizeGreaterThanOrEqualTo(8);
        assertThat(pacientesActivos(admin)).hasSizeGreaterThanOrEqualTo(10);
    }
}
