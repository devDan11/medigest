package com.medigestsystem.citas;

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

import com.medigestsystem.usuarios.Usuario;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "historial_citas")
public class HistorialCita {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cita_id", nullable = false)
    private Cita cita;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_evento", nullable = false, length = 20)
    private TipoEventoCita tipoEvento;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_anterior", length = 15)
    private EstadoCita estadoAnterior;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_nuevo", length = 15)
    private EstadoCita estadoNuevo;

    @Column(name = "inicio_anterior_utc", columnDefinition = "datetimeoffset(6)")
    private Instant inicioAnteriorUtc;

    @Column(name = "inicio_nuevo_utc", columnDefinition = "datetimeoffset(6)")
    private Instant inicioNuevoUtc;

    @Column(name = "motivo", length = 300)
    private String motivo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false, columnDefinition = "datetimeoffset(6)")
    private Instant creadoEn;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Cita getCita() {
        return cita;
    }

    public void setCita(Cita cita) {
        this.cita = cita;
    }

    public TipoEventoCita getTipoEvento() {
        return tipoEvento;
    }

    public void setTipoEvento(TipoEventoCita tipoEvento) {
        this.tipoEvento = tipoEvento;
    }

    public EstadoCita getEstadoAnterior() {
        return estadoAnterior;
    }

    public void setEstadoAnterior(EstadoCita estadoAnterior) {
        this.estadoAnterior = estadoAnterior;
    }

    public EstadoCita getEstadoNuevo() {
        return estadoNuevo;
    }

    public void setEstadoNuevo(EstadoCita estadoNuevo) {
        this.estadoNuevo = estadoNuevo;
    }

    public Instant getInicioAnteriorUtc() {
        return inicioAnteriorUtc;
    }

    public void setInicioAnteriorUtc(Instant inicioAnteriorUtc) {
        this.inicioAnteriorUtc = inicioAnteriorUtc;
    }

    public Instant getInicioNuevoUtc() {
        return inicioNuevoUtc;
    }

    public void setInicioNuevoUtc(Instant inicioNuevoUtc) {
        this.inicioNuevoUtc = inicioNuevoUtc;
    }

    public String getMotivo() {
        return motivo;
    }

    public void setMotivo(String motivo) {
        this.motivo = motivo;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }

    public void setCreadoEn(Instant creadoEn) {
        this.creadoEn = creadoEn;
    }
}
