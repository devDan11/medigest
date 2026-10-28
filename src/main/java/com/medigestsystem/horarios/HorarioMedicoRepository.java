package com.medigestsystem.horarios;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface HorarioMedicoRepository extends JpaRepository<HorarioMedico, Long> {

    List<HorarioMedico> findByMedicoIdAndActivoTrueOrderByDiaSemanaAscHoraInicioAsc(Long medicoId);

    List<HorarioMedico> findByMedicoIdAndDiaSemanaAndActivoTrue(Long medicoId, int diaSemana);

    /** Todas las franjas del médico (activas o no), para poder reemplazar el horario completo. */
    List<HorarioMedico> findByMedicoId(Long medicoId);
}
