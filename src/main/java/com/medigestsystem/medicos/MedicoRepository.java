package com.medigestsystem.medicos;

import java.util.List;
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

public interface MedicoRepository extends JpaRepository<Medico, Long>, JpaSpecificationExecutor<Medico> {

    Optional<Medico> findByNumeroColegiatura(String numeroColegiatura);

    boolean existsByNumeroColegiatura(String numeroColegiatura);

    Optional<Medico> findByUsuarioId(Long usuarioId);

    Page<Medico> findByActivo(boolean activo, Pageable pageable);

    long countByActivo(boolean activo);

    List<Medico> findByActivoTrueAndEspecialidadesId(Integer especialidadId);

    boolean existsByConsultorioIdAndActivoTrue(Integer consultorioId);

    /** Solo el identificador del médico asociado a una cuenta (no carga la entidad). */
    @Query("select m.id from Medico m where m.usuario.id = :usuarioId")
    Optional<Long> buscarIdPorUsuario(@Param("usuarioId") Long usuarioId);

    /**
     * Obtiene el médico bloqueando su fila hasta el fin de la transacción. Serializa las reservas
     * del mismo médico: la segunda espera a la primera y luego ve sus citas ya confirmadas.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "8000"))
    @Query("select m from Medico m where m.id = :id")
    Optional<Medico> obtenerConBloqueo(@Param("id") Long id);
}
