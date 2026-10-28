package com.medigestsystem.auditoria;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.criteria.Predicate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medigestsystem.auditoria.dto.AuditoriaResponse;
import com.medigestsystem.shared.dto.PaginaResponse;
import com.medigestsystem.shared.util.Csv;
import com.medigestsystem.shared.util.FechaHoraUtil;

/** Consulta y exportación del registro de auditoría. Solo lectura; el acceso (administrador) lo valida el controlador. */
@Service
public class AuditoriaConsultaService {

    static final int TAMANO_MAXIMO = 100;
    static final int MAX_FILAS_EXPORTACION = 10_000;

    /** Filtros opcionales; las fechas son días de la clínica, ambas inclusivas. */
    public record Filtro(String accion, String entidad, String email, ResultadoAuditoria resultado,
                         LocalDate desde, LocalDate hasta) {
    }

    private final AuditoriaRepository repository;
    private final AuditoriaService auditoriaService;
    private final FechaHoraUtil fechaHora;
    private final ZoneId zona;

    public AuditoriaConsultaService(AuditoriaRepository repository, AuditoriaService auditoriaService,
                                    FechaHoraUtil fechaHora, ZoneId zonaClinica) {
        this.repository = repository;
        this.auditoriaService = auditoriaService;
        this.fechaHora = fechaHora;
        this.zona = zonaClinica;
    }

    @Transactional(readOnly = true)
    public PaginaResponse<AuditoriaResponse> listar(Filtro filtro, int pagina, int tamano) {
        Page<Auditoria> resultado = repository.findAll(especificacion(filtro), PageRequest.of(
                Math.max(pagina, 0), Math.min(Math.max(tamano, 1), TAMANO_MAXIMO), ORDEN));
        return PaginaResponse.de(resultado.map(this::aResponse));
    }

    /** CSV con hasta {@value #MAX_FILAS_EXPORTACION} eventos (los más recientes). La exportación también se audita. */
    @Transactional(readOnly = true)
    public byte[] exportarCsv(Filtro filtro) {
        List<Auditoria> filas = repository.findAll(especificacion(filtro), PageRequest.of(0, MAX_FILAS_EXPORTACION, ORDEN))
                .getContent();
        List<List<Object>> datos = new ArrayList<>();
        for (Auditoria a : filas) {
            AuditoriaResponse r = aResponse(a);
            datos.add(java.util.Arrays.asList(r.id(), r.fecha(), r.usuarioEmail(), r.accion(), r.entidad(),
                    r.recursoId(), r.resultado(), r.detalle(), r.ip()));
        }
        auditoriaService.registrar("AUDITORIA_EXPORTADA", "auditoria", null, ResultadoAuditoria.EXITO,
                filas.size() + " eventos");
        return Csv.generar(List.of("Id", "Fecha", "Usuario", "Acción", "Entidad", "Recurso", "Resultado", "Detalle", "IP"),
                datos);
    }

    private static final Sort ORDEN = Sort.by(Sort.Order.desc("creadoEn"), Sort.Order.desc("id"));

    private Specification<Auditoria> especificacion(Filtro f) {
        return (root, query, cb) -> {
            List<Predicate> filtros = new ArrayList<>();
            if (f != null) {
                if (hay(f.accion())) {
                    filtros.add(cb.like(cb.lower(root.get("accion")), "%" + f.accion().trim().toLowerCase() + "%"));
                }
                if (hay(f.entidad())) {
                    filtros.add(cb.equal(root.get("entidad"), f.entidad().trim()));
                }
                if (hay(f.email())) {
                    filtros.add(cb.like(cb.lower(root.get("usuarioEmail")), "%" + f.email().trim().toLowerCase() + "%"));
                }
                if (f.resultado() != null) {
                    filtros.add(cb.equal(root.get("resultado"), f.resultado()));
                }
                if (f.desde() != null) {
                    filtros.add(cb.greaterThanOrEqualTo(root.get("creadoEn"), f.desde().atStartOfDay(zona).toInstant()));
                }
                if (f.hasta() != null) {
                    filtros.add(cb.lessThan(root.get("creadoEn"), f.hasta().plusDays(1).atStartOfDay(zona).toInstant()));
                }
            }
            return cb.and(filtros.toArray(new Predicate[0]));
        };
    }

    private static boolean hay(String texto) {
        return texto != null && !texto.isBlank();
    }

    private AuditoriaResponse aResponse(Auditoria a) {
        return new AuditoriaResponse(a.getId(),
                a.getCreadoEn() == null ? null : fechaHora.aZonaClinica(a.getCreadoEn()).toOffsetDateTime(),
                a.getUsuarioId(), a.getUsuarioEmail(), a.getAccion(), a.getEntidad(), a.getRecursoId(),
                a.getResultado(), a.getDetalle(), a.getIp());
    }
}
