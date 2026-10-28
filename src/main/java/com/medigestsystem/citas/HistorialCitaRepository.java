package com.medigestsystem.citas;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface HistorialCitaRepository extends JpaRepository<HistorialCita, Long> {

    List<HistorialCita> findByCitaIdOrderByCreadoEnAsc(Long citaId);

    long countByCitaIdAndTipoEvento(Long citaId, TipoEventoCita tipoEvento);
}
