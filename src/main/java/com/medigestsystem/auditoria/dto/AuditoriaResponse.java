package com.medigestsystem.auditoria.dto;

import java.time.OffsetDateTime;

import com.medigestsystem.auditoria.ResultadoAuditoria;

public record AuditoriaResponse(
        Long id,
        OffsetDateTime fecha,
        Long usuarioId,
        String usuarioEmail,
        String accion,
        String entidad,
        String recursoId,
        ResultadoAuditoria resultado,
        String detalle,
        String ip
) {
}
