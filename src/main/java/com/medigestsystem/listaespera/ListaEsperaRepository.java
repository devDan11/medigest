package com.medigestsystem.listaespera;

import java.time.Instant;
import java.time.LocalDate;
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
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import com.medigestsystem.medicos.Medico;

public interface ListaEsperaRepository
        extends JpaRepository<ListaEspera, Long>, JpaSpecificationExecutor<ListaEspera> {

    @EntityGraph(attributePaths = {"paciente", "medico", "especialidad", "ofertaMedico"})
    List<ListaEspera> findByEspecialidadIdAndEstadoOrderByCreadoEnAsc(Integer especialidadId, EstadoListaEspera estado);

    List<ListaEspera> findByPacienteIdAndEstado(Long pacienteId, EstadoListaEspera estado);

    @Override
    @EntityGraph(attributePaths = {"paciente", "medico", "especialidad", "ofertaMedico"})
    Page<ListaEspera> findAll(Specification<ListaEspera> spec, Pageable pageable);

    boolean existsByPacienteIdAndEspecialidadIdAndEstadoIn(Long pacienteId, Integer especialidadId,
                                                           Collection<EstadoListaEspera> estados);

    /** Solicitudes activas de la especialidad registradas antes que la indicada (para calcular la posición). */
    long countByEspecialidadIdAndEstadoAndCreadoEnBefore(Integer especialidadId, EstadoListaEspera estado, Instant antesDe);

    // --- Ofertas (retención temporal de un horario) ---

    @Query("select l from ListaEspera l where l.estado = com.medigestsystem.listaespera.EstadoListaEspera.OFERTADA "
            + "and l.ofertaMedico.id = :medicoId and l.ofertaExpiraEn > :ahora")
    List<ListaEspera> findOfertasVigentesDelMedico(@Param("medicoId") Long medicoId, @Param("ahora") Instant ahora);

    @Query("select count(l) from ListaEspera l where l.estado = com.medigestsystem.listaespera.EstadoListaEspera.OFERTADA "
            + "and l.ofertaMedico.id = :medicoId and l.ofertaExpiraEn > :ahora "
            + "and l.ofertaInicioUtc < :fin and l.ofertaFinUtc > :inicio and l.id <> :excluirId")
    long contarOfertasVigentesSolapadas(@Param("medicoId") Long medicoId, @Param("inicio") Instant inicio,
                                        @Param("fin") Instant fin, @Param("ahora") Instant ahora,
                                        @Param("excluirId") Long excluirId);

    @Query("select l.ofertaMedico.id from ListaEspera l where l.id = :id")
    Optional<Long> buscarMedicoDeOferta(@Param("id") Long id);

    @Query("select l.id from ListaEspera l where l.estado = com.medigestsystem.listaespera.EstadoListaEspera.OFERTADA "
            + "and l.ofertaExpiraEn <= :ahora")
    List<Long> idsOfertasVencidas(@Param("ahora") Instant ahora);

    @Query("select l.id from ListaEspera l where l.estado = com.medigestsystem.listaespera.EstadoListaEspera.ACTIVA "
            + "and l.fechaHasta < :hoy")
    List<Long> idsSolicitudesCaducadas(@Param("hoy") LocalDate hoy);

    /** Bloquea la solicitud mientras se acepta, rechaza, cancela o vence. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "8000"))
    @Query("select l from ListaEspera l where l.id = :id")
    Optional<ListaEspera> obtenerConBloqueo(@Param("id") Long id);

    /**
     * Reclama de forma atómica una solicitud ACTIVA para ofrecerle un horario. Devuelve 1 si la reclamó o 0
     * si otro proceso la modificó antes: así un mismo horario nunca se ofrece dos veces a la misma solicitud.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update ListaEspera l set l.estado = com.medigestsystem.listaespera.EstadoListaEspera.OFERTADA, "
            + "l.ofertaMedico = :medico, l.ofertaInicioUtc = :inicio, l.ofertaFinUtc = :fin, "
            + "l.ofertaExpiraEn = :expira, l.actualizadoEn = :ahora "
            + "where l.id = :id and l.estado = com.medigestsystem.listaespera.EstadoListaEspera.ACTIVA")
    int reclamarParaOferta(@Param("id") Long id, @Param("medico") Medico medico, @Param("inicio") Instant inicio,
                           @Param("fin") Instant fin, @Param("expira") Instant expira, @Param("ahora") Instant ahora);
}
