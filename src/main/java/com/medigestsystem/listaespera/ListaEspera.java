package com.medigestsystem.listaespera;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.medigestsystem.citas.Cita;
import com.medigestsystem.especialidades.Especialidad;
import com.medigestsystem.medicos.Medico;
import com.medigestsystem.pacientes.Paciente;
import com.medigestsystem.shared.persistence.EntidadAuditada;

@Entity
@Table(name = "lista_espera")
public class ListaEspera extends EntidadAuditada {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "paciente_id", nullable = false)
    private Paciente paciente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "medico_id")
    private Medico medico;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "especialidad_id", nullable = false)
    private Especialidad especialidad;

    @Column(name = "fecha_desde", nullable = false)
    private LocalDate fechaDesde;

    @Column(name = "fecha_hasta", nullable = false)
    private LocalDate fechaHasta;

    @Enumerated(EnumType.STRING)
    @Column(name = "franja", nullable = false, length = 12)
    private FranjaPreferida franja;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 12)
    private EstadoListaEspera estado;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "oferta_medico_id")
    private Medico ofertaMedico;

    @Column(name = "oferta_inicio_utc", columnDefinition = "datetimeoffset(6)")
    private Instant ofertaInicioUtc;

    @Column(name = "oferta_fin_utc", columnDefinition = "datetimeoffset(6)")
    private Instant ofertaFinUtc;

    @Column(name = "oferta_expira_en", columnDefinition = "datetimeoffset(6)")
    private Instant ofertaExpiraEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cita_id")
    private Cita cita;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Paciente getPaciente() {
        return paciente;
    }

    public void setPaciente(Paciente paciente) {
        this.paciente = paciente;
    }

    public Medico getMedico() {
        return medico;
    }

    public void setMedico(Medico medico) {
        this.medico = medico;
    }

    public Especialidad getEspecialidad() {
        return especialidad;
    }

    public void setEspecialidad(Especialidad especialidad) {
        this.especialidad = especialidad;
    }

    public LocalDate getFechaDesde() {
        return fechaDesde;
    }

    public void setFechaDesde(LocalDate fechaDesde) {
        this.fechaDesde = fechaDesde;
    }

    public LocalDate getFechaHasta() {
        return fechaHasta;
    }

    public void setFechaHasta(LocalDate fechaHasta) {
        this.fechaHasta = fechaHasta;
    }

    public FranjaPreferida getFranja() {
        return franja;
    }

    public void setFranja(FranjaPreferida franja) {
        this.franja = franja;
    }

    public EstadoListaEspera getEstado() {
        return estado;
    }

    public void setEstado(EstadoListaEspera estado) {
        this.estado = estado;
    }

    public Medico getOfertaMedico() {
        return ofertaMedico;
    }

    public void setOfertaMedico(Medico ofertaMedico) {
        this.ofertaMedico = ofertaMedico;
    }

    public Instant getOfertaInicioUtc() {
        return ofertaInicioUtc;
    }

    public void setOfertaInicioUtc(Instant ofertaInicioUtc) {
        this.ofertaInicioUtc = ofertaInicioUtc;
    }

    public Instant getOfertaFinUtc() {
        return ofertaFinUtc;
    }

    public void setOfertaFinUtc(Instant ofertaFinUtc) {
        this.ofertaFinUtc = ofertaFinUtc;
    }

    public Instant getOfertaExpiraEn() {
        return ofertaExpiraEn;
    }

    public void setOfertaExpiraEn(Instant ofertaExpiraEn) {
        this.ofertaExpiraEn = ofertaExpiraEn;
    }

    public Cita getCita() {
        return cita;
    }

    public void setCita(Cita cita) {
        this.cita = cita;
    }
}
