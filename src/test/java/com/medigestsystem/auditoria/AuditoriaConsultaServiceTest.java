package com.medigestsystem.auditoria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.jpa.domain.Specification;

import com.medigestsystem.auditoria.dto.AuditoriaResponse;
import com.medigestsystem.shared.dto.PaginaResponse;
import com.medigestsystem.testsupport.Fixtures;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuditoriaConsultaServiceTest {

    @Mock AuditoriaRepository repository;
    @Mock AuditoriaService auditoriaService;
    AuditoriaConsultaService servicio;

    @BeforeEach
    void preparar() {
        servicio = new AuditoriaConsultaService(repository, auditoriaService, Fixtures.fechaHora(), Fixtures.LIMA);
    }

    Auditoria evento(String detalle) {
        Auditoria a = new Auditoria();
        a.setId(1L);
        a.setUsuarioEmail("admin@medigest.test");
        a.setAccion("CITA_CANCELADA");
        a.setEntidad("citas");
        a.setRecursoId("10");
        a.setResultado(ResultadoAuditoria.EXITO);
        a.setDetalle(detalle);
        a.setCreadoEn(Fixtures.AHORA);
        return a;
    }

    @Test
    void listaConLaHoraDeLima() {
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(evento("ok"))));

        PaginaResponse<AuditoriaResponse> p = servicio.listar(
                new AuditoriaConsultaService.Filtro(null, null, null, null, null, null), 0, 20);

        assertThat(p.contenido()).hasSize(1);
        assertThat(p.contenido().get(0).fecha().getOffset().getTotalSeconds()).isEqualTo(-5 * 3600);
        assertThat(p.contenido().get(0).accion()).isEqualTo("CITA_CANCELADA");
    }

    @Test
    void elTamanoDePaginaTieneUnTope() {
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        servicio.listar(null, 0, 5000);

        var captor = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(any(Specification.class), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(AuditoriaConsultaService.TAMANO_MAXIMO);
    }

    @Test
    void exportarGeneraCsvNeutralizaFormulasYAudita() {
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(evento("=1+1, \"x\""))));

        byte[] csv = servicio.exportarCsv(null);

        String texto = new String(csv, 3, csv.length - 3, StandardCharsets.UTF_8);
        assertThat(texto).startsWith("Id;Fecha;Usuario;Acción");
        assertThat(texto).contains("\"'=1+1, \"\"x\"\"\"");
        verify(auditoriaService).registrar(eq("AUDITORIA_EXPORTADA"), eq("auditoria"), eq(null),
                eq(ResultadoAuditoria.EXITO), eq("1 eventos"));
    }
}
