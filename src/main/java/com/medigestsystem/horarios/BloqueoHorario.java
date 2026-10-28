package com.medigestsystem.horarios;

import java.time.Instant;

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

import com.medigestsystem.consultorios.Consultorio;
import com.medigestsystem.medicos.Medico;
import com.medigestsystem.usuarios.Usuario;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "bloqueos_horarios")
public class BloqueoHorario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "medico_id")
    private Medico medico;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "consultorio_id")
    private Consultorio consultorio;

    @Column(name = "inicio_utc", nullable = false, columnDefinition = "datetimeoffset(6)")
    private Instant inicioUtc;

    @Column(name = "fin_utc", nullable = false, columnDefinition = "datetimeoffset(6)")
    private Instant finUtc;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 15)
    private TipoBloqueo tipo;

    @Column(name = "motivo", length = 200)
    private String motivo;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 12)
    private EstadoBloqueo estado;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creado_por")
    private Usuario creadoPor;

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false, columnDefinition = "datetimeoffset(6)")
    private Instant creadoEn;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Medico getMedico() {
        return medico;
    }

    public void setMedico(Medico medico) {
        this.medico = medico;
    }

    public Consultorio getConsultorio() {
        return consultorio;
    }

    public void setConsultorio(Consultorio consultorio) {
        this.consultorio = consultorio;
    }

    public Instant getInicioUtc() {
        return inicioUtc;
    }

    public void setInicioUtc(Instant inicioUtc) {
        this.inicioUtc = inicioUtc;
    }

    public Instant getFinUtc() {
        return finUtc;
    }

    public void setFinUtc(Instant finUtc) {
        this.finUtc = finUtc;
    }

    public TipoBloqueo getTipo() {
        return tipo;
    }

    public void setTipo(TipoBloqueo tipo) {
        this.tipo = tipo;
    }

    public String getMotivo() {
        return motivo;
    }

    public void setMotivo(String motivo) {
        this.motivo = motivo;
    }

    public EstadoBloqueo getEstado() {
        return estado;
    }

    public void setEstado(EstadoBloqueo estado) {
        this.estado = estado;
    }

    public Usuario getCreadoPor() {
        return creadoPor;
    }

    public void setCreadoPor(Usuario creadoPor) {
        this.creadoPor = creadoPor;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }

    public void setCreadoEn(Instant creadoEn) {
        this.creadoEn = creadoEn;
    }
}
