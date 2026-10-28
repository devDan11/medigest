package com.medigestsystem.notificaciones;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {

    boolean existsByClaveIdempotencia(String claveIdempotencia);

    List<Notificacion> findByEstado(EstadoNotificacion estado);
}
