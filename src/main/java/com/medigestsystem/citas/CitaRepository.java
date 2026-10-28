package com.medigestsystem.citas;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface CitaRepository extends JpaRepository<Cita, Long>, JpaSpecificationExecutor<Cita> {

    Optional<Cita> findByCodigoReserva(String codigoReserva);

    boolean existsByCodigoReserva(String codigoReserva);

    @EntityGraph(attributePaths = {"paciente", "medico", "especialidad", "consultorio"})
    Page<Cita> findByPacienteId(Long pacienteId, Pageable pageable);

    @EntityGraph(attributePaths = {"paciente", "medico", "especialidad", "consultorio"})
    Page<Cita> findByMedicoId(Long medicoId, Pageable pageable);

    /** Búsqueda filtrada con las relaciones necesarias ya cargadas (evita consultas N+1). */
    @Override
    @EntityGraph(attributePaths = {"paciente", "medico", "especialidad", "consultorio"})
    Page<Cita> findAll(Specification<Cita> spec, Pageable pageable);

    List<Cita> findByPacienteIdOrderByInicioUtcDesc(Long pacienteId);

    long countByEstado(EstadoCita estado);

    long countByPacienteIdAndEstado(Long pacienteId, EstadoCita estado);

    long countByMedicoIdAndEstado(Long medicoId, EstadoCita estado);

    // --- Relaciones de historial (no se pierden al desactivar registros) ---

    boolean existsByMedicoIdAndPacienteId(Long medicoId, Long pacienteId);

    boolean existsByMedicoIdAndEspecialidadId(Long medicoId, Integer especialidadId);

    long countByPacienteIdAndInicioUtcAfterAndEstadoNotIn(Long pacienteId, Instant desde,
                                                          Collection<EstadoCita> excluidos);

    long countByMedicoIdAndInicioUtcAfterAndEstadoNotIn(Long medicoId, Instant desde,
                                                        Collection<EstadoCita> excluidos);

    long countByConsultorioIdAndInicioUtcAfterAndEstadoNotIn(Integer consultorioId, Instant desde,
                                                             Collection<EstadoCita> excluidos);

    List<Cita> findByMedicoIdAndInicioUtcAfterAndEstadoNotIn(Long medicoId, Instant desde,
                                                             Collection<EstadoCita> excluidos);

    @EntityGraph(attributePaths = {"paciente", "medico", "especialidad", "consultorio"})
    Optional<Cita> findFirstByPacienteIdAndInicioUtcAfterAndEstadoInOrderByInicioUtcAsc(
            Long pacienteId, Instant desde, Collection<EstadoCita> estados);

    @EntityGraph(attributePaths = {"paciente", "medico", "especialidad", "consultorio"})
    List<Cita> findByMedicoIdAndInicioUtcGreaterThanEqualAndInicioUtcLessThanOrderByInicioUtcAsc(
            Long medicoId, Instant desde, Instant hasta);

    // --- Citas que ocupan un rango (para calcular disponibilidad) ---

    List<Cita> findByMedicoIdAndInicioUtcLessThanAndFinUtcGreaterThanAndEstadoNotIn(
            Long medicoId, Instant fin, Instant inicio, Collection<EstadoCita> excluidos);

    List<Cita> findByConsultorioIdAndInicioUtcLessThanAndFinUtcGreaterThanAndEstadoNotIn(
            Integer consultorioId, Instant fin, Instant inicio, Collection<EstadoCita> excluidos);

    // --- Solapamiento con un intervalo [inicio, fin) ---

    @Query("select count(c) from Cita c where c.medico.id = :medicoId and c.estado not in :excluidos "
            + "and c.inicioUtc < :fin and c.finUtc > :inicio")
    long contarSolapadasMedico(@Param("medicoId") Long medicoId, @Param("inicio") Instant inicio,
                               @Param("fin") Instant fin, @Param("excluidos") Collection<EstadoCita> excluidos);

    @Query("select count(c) from Cita c where c.consultorio.id = :consultorioId and c.estado not in :excluidos "
            + "and c.inicioUtc < :fin and c.finUtc > :inicio")
    long contarSolapadasConsultorio(@Param("consultorioId") Integer consultorioId, @Param("inicio") Instant inicio,
                                    @Param("fin") Instant fin, @Param("excluidos") Collection<EstadoCita> excluidos);

    /** Variante que ignora una cita (la que se está reprogramando). {@code excluirId} = -1 si no hay ninguna. */
    @Query("select count(c) from Cita c where c.medico.id = :medicoId and c.estado not in :excluidos "
            + "and c.inicioUtc < :fin and c.finUtc > :inicio and c.id <> :excluirId")
    long contarSolapadasMedicoSin(@Param("medicoId") Long medicoId, @Param("inicio") Instant inicio,
                                  @Param("fin") Instant fin, @Param("excluidos") Collection<EstadoCita> excluidos,
                                  @Param("excluirId") Long excluirId);

    @Query("select count(c) from Cita c where c.consultorio.id = :consultorioId and c.estado not in :excluidos "
            + "and c.inicioUtc < :fin and c.finUtc > :inicio and c.id <> :excluirId")
    long contarSolapadasConsultorioSin(@Param("consultorioId") Integer consultorioId,
                                       @Param("inicio") Instant inicio, @Param("fin") Instant fin,
                                       @Param("excluidos") Collection<EstadoCita> excluidos,
                                       @Param("excluirId") Long excluirId);

    @Query("select count(c) from Cita c where c.paciente.id = :pacienteId and c.estado not in :excluidos "
            + "and c.inicioUtc < :fin and c.finUtc > :inicio and c.id <> :excluirId")
    long contarSolapadasPacienteSin(@Param("pacienteId") Long pacienteId, @Param("inicio") Instant inicio,
                                    @Param("fin") Instant fin, @Param("excluidos") Collection<EstadoCita> excluidos,
                                    @Param("excluirId") Long excluirId);

    /** Bloquea la cita: cancelar, confirmar o reprogramar la misma cita a la vez no puede mezclarse. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "8000"))
    @Query("select c from Cita c where c.id = :id")
    Optional<Cita> obtenerConBloqueo(@Param("id") Long id);

    /** Conteo de citas por estado en un rango (-1 = sin filtrar por médico / paciente). */
    @Query("select c.estado, count(c) from Cita c where c.inicioUtc >= :desde and c.inicioUtc < :hasta "
            + "and (:medicoId = -1 or c.medico.id = :medicoId) and (:pacienteId = -1 or c.paciente.id = :pacienteId) "
            + "group by c.estado")
    List<Object[]> contarPorEstado(@Param("desde") Instant desde, @Param("hasta") Instant hasta,
                                   @Param("medicoId") Long medicoId, @Param("pacienteId") Long pacienteId);

    /** Instantes de inicio de las citas no canceladas de un rango (para agrupar por día local). */
    @Query("select c.inicioUtc from Cita c where c.inicioUtc >= :desde and c.inicioUtc < :hasta "
            + "and c.estado <> com.medigestsystem.citas.EstadoCita.CANCELADA "
            + "and (:medicoId = -1 or c.medico.id = :medicoId) and (:pacienteId = -1 or c.paciente.id = :pacienteId)")
    List<Instant> iniciosEntre(@Param("desde") Instant desde, @Param("hasta") Instant hasta,
                               @Param("medicoId") Long medicoId, @Param("pacienteId") Long pacienteId);

    /** Ids de citas activas cuyo inicio cae en (desde, hasta]: candidatas a recordatorio. */
    @Query("select c.id from Cita c where c.inicioUtc > :desde and c.inicioUtc <= :hasta and c.estado in :estados order by c.inicioUtc")
    List<Long> idsParaRecordatorio(@Param("desde") Instant desde, @Param("hasta") Instant hasta,
                                   @Param("estados") Collection<EstadoCita> estados);

    // ------------------------------------------------------------------ reportes (-1 = sin filtro)

    @Query("select c.estado, count(c) from Cita c where c.inicioUtc >= :desde and c.inicioUtc < :hasta "
            + "and (:medicoId = -1 or c.medico.id = :medicoId) and (:especialidadId = -1 or c.especialidad.id = :especialidadId) "
            + "group by c.estado")
    List<Object[]> contarEstadosReporte(@Param("desde") Instant desde, @Param("hasta") Instant hasta,
                                        @Param("medicoId") Long medicoId, @Param("especialidadId") Integer especialidadId);

    @Query("select c.inicioUtc, c.estado from Cita c where c.inicioUtc >= :desde and c.inicioUtc < :hasta "
            + "and (:medicoId = -1 or c.medico.id = :medicoId) and (:especialidadId = -1 or c.especialidad.id = :especialidadId)")
    List<Object[]> diasReporte(@Param("desde") Instant desde, @Param("hasta") Instant hasta,
                               @Param("medicoId") Long medicoId, @Param("especialidadId") Integer especialidadId);

    @Query("select c.medico.id, c.medico.nombres, c.medico.apellidos, c.estado, count(c) from Cita c "
            + "where c.inicioUtc >= :desde and c.inicioUtc < :hasta "
            + "and (:medicoId = -1 or c.medico.id = :medicoId) and (:especialidadId = -1 or c.especialidad.id = :especialidadId) "
            + "group by c.medico.id, c.medico.nombres, c.medico.apellidos, c.estado")
    List<Object[]> medicosReporte(@Param("desde") Instant desde, @Param("hasta") Instant hasta,
                                  @Param("medicoId") Long medicoId, @Param("especialidadId") Integer especialidadId);

    @Query("select c.especialidad.id, c.especialidad.nombre, c.estado, count(c) from Cita c "
            + "where c.inicioUtc >= :desde and c.inicioUtc < :hasta "
            + "group by c.especialidad.id, c.especialidad.nombre, c.estado")
    List<Object[]> especialidadesReporte(@Param("desde") Instant desde, @Param("hasta") Instant hasta);
}
