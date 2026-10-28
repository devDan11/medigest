package com.medigestsystem.web.paginas;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import com.medigestsystem.pacientes.Sexo;
import com.medigestsystem.pacientes.TipoDocumento;
import com.medigestsystem.pacientes.dto.PacienteRequest;

/** Campos del formulario de paciente. Los textos vacíos se convierten en nulos. */
public class PacienteForm {

    private String nombres;
    private String apellidos;
    private TipoDocumento tipoDocumento;
    private String numeroDocumento;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fechaNacimiento;
    private Sexo sexo;
    private String email;
    private String telefono;
    private String direccion;
    private String contactoEmergenciaNombre;
    private String contactoEmergenciaTelefono;

    public PacienteRequest aSolicitud() {
        return new PacienteRequest(texto(nombres), texto(apellidos), tipoDocumento, texto(numeroDocumento),
                fechaNacimiento, sexo, texto(email), texto(telefono), texto(direccion),
                texto(contactoEmergenciaNombre), texto(contactoEmergenciaTelefono));
    }

    static String texto(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    public String getNombres() { return nombres; }
    public void setNombres(String nombres) { this.nombres = nombres; }
    public String getApellidos() { return apellidos; }
    public void setApellidos(String apellidos) { this.apellidos = apellidos; }
    public TipoDocumento getTipoDocumento() { return tipoDocumento; }
    public void setTipoDocumento(TipoDocumento tipoDocumento) { this.tipoDocumento = tipoDocumento; }
    public String getNumeroDocumento() { return numeroDocumento; }
    public void setNumeroDocumento(String numeroDocumento) { this.numeroDocumento = numeroDocumento; }
    public LocalDate getFechaNacimiento() { return fechaNacimiento; }
    public void setFechaNacimiento(LocalDate fechaNacimiento) { this.fechaNacimiento = fechaNacimiento; }
    public Sexo getSexo() { return sexo; }
    public void setSexo(Sexo sexo) { this.sexo = sexo; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }
    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }
    public String getContactoEmergenciaNombre() { return contactoEmergenciaNombre; }
    public void setContactoEmergenciaNombre(String v) { this.contactoEmergenciaNombre = v; }
    public String getContactoEmergenciaTelefono() { return contactoEmergenciaTelefono; }
    public void setContactoEmergenciaTelefono(String v) { this.contactoEmergenciaTelefono = v; }
}
