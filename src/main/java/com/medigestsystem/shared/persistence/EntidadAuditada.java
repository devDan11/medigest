package com.medigestsystem.shared.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Campos de auditoría comunes. Los instantes se guardan en UTC.
 */
@MappedSuperclass
public abstract class EntidadAuditada {

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false, columnDefinition = "datetimeoffset(6)")
    private Instant creadoEn;

    @UpdateTimestamp
    @Column(name = "actualizado_en", nullable = false, columnDefinition = "datetimeoffset(6)")
    private Instant actualizadoEn;

    public Instant getCreadoEn() {
        return creadoEn;
    }

    public Instant getActualizadoEn() {
        return actualizadoEn;
    }
}
