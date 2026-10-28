package com.medigestsystem.consultorios;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface ConsultorioRepository extends JpaRepository<Consultorio, Integer> {

    Optional<Consultorio> findByCodigoIgnoreCase(String codigo);

    boolean existsByCodigoIgnoreCase(String codigo);

    List<Consultorio> findByActivoTrueOrderByCodigoAsc();

    /** Bloquea el consultorio: dos médicos que lo comparten no pueden ocuparlo a la vez. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "8000"))
    @Query("select c from Consultorio c where c.id = :id")
    Optional<Consultorio> obtenerConBloqueo(@Param("id") Integer id);
}
