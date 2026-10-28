package com.medigestsystem.horarios;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface BloqueoHorarioRepository
        extends JpaRepository<BloqueoHorario, Long>, JpaSpecificationExecutor<BloqueoHorario> {

    List<BloqueoHorario> findByMedicoId(Long medicoId);

    List<BloqueoHorario> findByConsultorioId(Integer consultorioId);

    // Bloqueos de un estado dado que se cruzan con el intervalo [inicio, fin).

    List<BloqueoHorario> findByMedicoIdAndEstadoAndInicioUtcLessThanAndFinUtcGreaterThan(
            Long medicoId, EstadoBloqueo estado, Instant fin, Instant inicio);

    List<BloqueoHorario> findByConsultorioIdAndEstadoAndInicioUtcLessThanAndFinUtcGreaterThan(
            Integer consultorioId, EstadoBloqueo estado, Instant fin, Instant inicio);

    boolean existsByMedicoIdAndEstadoAndInicioUtcLessThanAndFinUtcGreaterThan(
            Long medicoId, EstadoBloqueo estado, Instant fin, Instant inicio);

    boolean existsByConsultorioIdAndEstadoAndInicioUtcLessThanAndFinUtcGreaterThan(
            Integer consultorioId, EstadoBloqueo estado, Instant fin, Instant inicio);
}
