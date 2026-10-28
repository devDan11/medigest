package com.medigestsystem.notificaciones;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface NotificacionRepository extends JpaRepository<Notificacion, Long>, JpaSpecificationExecutor<Notificacion> {

    boolean existsByClaveIdempotencia(String claveIdempotencia);

    List<Notificacion> findByEstado(EstadoNotificacion estado);

    /** Ids de notificaciones listas para enviarse (sin fecha programada o ya vencida), de las más antiguas a las más nuevas. */
    @Query("select n.id from Notificacion n where n.estado = com.medigestsystem.notificaciones.EstadoNotificacion.PENDIENTE "
            + "and (n.programadaPara is null or n.programadaPara <= :ahora) order by n.id")
    List<Long> idsPendientes(@Param("ahora") Instant ahora, Pageable pageable);

    @Query("select n.estado, count(n) from Notificacion n group by n.estado")
    List<Object[]> contarPorEstado();
}
