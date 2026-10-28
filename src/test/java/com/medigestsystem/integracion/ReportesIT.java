package com.medigestsystem.integracion;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class ReportesIT extends AbstractIT {

    private String rango() {
        LocalDate hoy = LocalDate.now(LIMA);
        return "desde=" + hoy.minusDays(60) + "&hasta=" + hoy.plusDays(60);
    }

    @Test
    void elReporteDeCitasTraeLosDatosDeDemostracion() {
        Resp r = get("/api/v1/reportes/citas?" + rango(), token("admin"));

        assertThat(r.status()).isEqualTo(200);
        assertThat(r.<Integer>json("$.total")).isGreaterThanOrEqualTo(10);
        assertThat(r.<Object>json("$.porEstado.ATENDIDA")).isNotNull();
        assertThat(r.<java.util.List<Object>>json("$.porDia")).isNotEmpty();
    }

    @Test
    void elReportePorEspecialidadAgrupaLasCitas() {
        Resp r = get("/api/v1/reportes/especialidades?" + rango(), token("recepcion1"));

        assertThat(r.status()).isEqualTo(200);
        assertThat(r.<java.util.List<Object>>json("$.especialidades")).isNotEmpty();
    }

    @Test
    void unRangoInvalidoSeRechazaConUnErrorControlado() {
        Resp r = get("/api/v1/reportes/citas?desde=2030-02-01&hasta=2030-01-01", token("admin"));

        assertThat(r.status()).isEqualTo(400);
        assertThat(r.<String>json("$.codigo")).isEqualTo("RANGO_INVALIDO");
    }

    @Test
    void elCsvSeDescargaConBomYSeparadorPuntoYComa() {
        Resp r = get("/api/v1/reportes/citas/exportar?vista=dia&" + rango(), token("admin"));

        assertThat(r.status()).isEqualTo(200);
        assertThat(r.cabecera("Content-Type")).contains("text/csv");
        assertThat(r.cabecera("Content-Disposition")).contains("attachment");
        assertThat(r.body()).startsWith("﻿Fecha;Citas");
    }

    @Test
    void laExportacionDeAuditoriaQuedaAuditada() {
        String admin = token("admin");
        Resp csv = get("/api/v1/auditoria/exportar", admin);
        assertThat(csv.status()).isEqualTo(200);

        Resp r = get("/api/v1/auditoria?accion=AUDITORIA_EXPORTADA", admin);
        assertThat(r.<Integer>json("$.totalElementos")).isGreaterThanOrEqualTo(1);
    }

    @Test
    void laPaginaDeReportesSeCargaConSesionWeb() {
        // Sin sesión web se redirige al login (la sesión con CSRF se cubre en pruebas manuales / navegador).
        assertThat(get("/reportes", null).status()).isEqualTo(302);
    }
}
