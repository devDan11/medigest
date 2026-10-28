package com.medigestsystem.integracion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SeguridadIT extends AbstractIT {

    @Test
    void laPaginaDeLoginLlevaLasCabecerasDeSeguridad() {
        Resp r = get("/login", null);

        assertThat(r.status()).isEqualTo(200);
        assertThat(r.cabecera("Content-Security-Policy")).contains("script-src 'self'").contains("frame-ancestors 'none'");
        assertThat(r.cabecera("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(r.cabecera("X-Frame-Options")).isEqualTo("DENY");
        assertThat(r.cabecera("Referrer-Policy")).isEqualTo("strict-origin-when-cross-origin");
        assertThat(r.cabecera("Permissions-Policy")).contains("camera=()");
        assertThat(r.body()).doesNotContain("cdn.jsdelivr.net");
    }

    @Test
    void losRecursosDeTercerosSeSirvenSinSesion() {
        assertThat(get("/vendor/bootstrap/bootstrap.min.css", null).status()).isEqualTo(200);
        assertThat(get("/vendor/chartjs/chart.umd.js", null).status()).isEqualTo(200);
        assertThat(get("/js/medigest.js", null).status()).isEqualTo(200);
    }

    @Test
    void lasPaginasPrivadasRedirigenAlLogin() {
        for (String ruta : new String[]{"/panel", "/citas", "/reportes", "/auditoria", "/notificaciones"}) {
            Resp r = get(ruta, null);
            assertThat(r.status()).as(ruta).isEqualTo(302);
            assertThat(r.cabecera("Location")).as(ruta).contains("/login");
        }
    }

    @Test
    void laApiExigeToken() {
        assertThat(get("/api/v1/citas", null).status()).isEqualTo(401);
        assertThat(get("/api/v1/citas", "token-invalido").status()).isEqualTo(401);
        assertThat(get("/api/v1/citas", null).cabecera("Content-Security-Policy")).contains("default-src 'none'");
    }

    @Test
    void unaContrasenaIncorrectaDa401SinRevelarSiElUsuarioExiste() {
        Resp existente = post("/api/v1/auth/login", null, "{\"email\":\"medico8@medigest.test\",\"password\":\"incorrecta-123\"}");
        Resp inexistente = post("/api/v1/auth/login", null, "{\"email\":\"nadie@medigest.test\",\"password\":\"incorrecta-123\"}");

        assertThat(existente.status()).isEqualTo(401);
        assertThat(inexistente.status()).isEqualTo(401);
        assertThat(existente.<String>json("$.codigo")).isEqualTo(inexistente.<String>json("$.codigo"));
    }

    @Test
    void cadaRolSoloAccedeAloSuyo() {
        String admin = token("admin");
        String recepcion = token("recepcion1");
        String medico = token("medico1");
        String paciente = token("paciente1");

        assertThat(get("/api/v1/reportes/citas", admin).status()).isEqualTo(200);
        assertThat(get("/api/v1/reportes/citas", recepcion).status()).isEqualTo(200);
        assertThat(get("/api/v1/reportes/citas", medico).status()).isEqualTo(403);
        assertThat(get("/api/v1/reportes/citas", paciente).status()).isEqualTo(403);

        assertThat(get("/api/v1/auditoria", admin).status()).isEqualTo(200);
        assertThat(get("/api/v1/auditoria", recepcion).status()).isEqualTo(403);
        assertThat(get("/api/v1/notificaciones", admin).status()).isEqualTo(200);
        assertThat(get("/api/v1/notificaciones", paciente).status()).isEqualTo(403);
        assertThat(get("/api/v1/usuarios", paciente).status()).isEqualTo(403);
    }

    @Test
    void unPacienteSoloVeSusPropiasCitas() {
        Resp r = get("/api/v1/citas?tamano=100", token("paciente1"));

        assertThat(r.status()).isEqualTo(200);
        java.util.List<Integer> pacientes = r.json("$.contenido[*].pacienteId");
        assertThat(new java.util.HashSet<>(pacientes)).hasSizeLessThanOrEqualTo(1);
    }
}
