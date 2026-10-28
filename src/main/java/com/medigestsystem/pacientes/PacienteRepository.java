package com.medigestsystem.pacientes;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface PacienteRepository extends JpaRepository<Paciente, Long>, JpaSpecificationExecutor<Paciente> {

    Optional<Paciente> findByTipoDocumentoAndNumeroDocumento(TipoDocumento tipoDocumento, String numeroDocumento);

    boolean existsByTipoDocumentoAndNumeroDocumento(TipoDocumento tipoDocumento, String numeroDocumento);

    Optional<Paciente> findByUsuarioId(Long usuarioId);

    Page<Paciente> findByActivo(boolean activo, Pageable pageable);

    long countByActivo(boolean activo);

    @Query("select p.id from Paciente p where p.usuario.id = :usuarioId")
    Optional<Long> buscarIdPorUsuario(@Param("usuarioId") Long usuarioId);

    /** Bloquea al paciente para que no pueda reservar dos citas superpuestas en solicitudes simultáneas. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "8000"))
    @Query("select p from Paciente p where p.id = :id")
    Optional<Paciente> obtenerConBloqueo(@Param("id") Long id);
}
