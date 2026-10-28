package com.medigestsystem.web.paginas;

import java.util.HashSet;
import java.util.Set;

import com.medigestsystem.medicos.dto.CuentaMedicoRequest;
import com.medigestsystem.medicos.dto.MedicoRequest;

/** Campos del formulario de médico. La cuenta de acceso solo se envía al crear y si se completó. */
public class MedicoForm {

    private String nombres;
    private String apellidos;
    private String numeroColegiatura;
    private String email;
    private String telefono;
    private Integer consultorioId;
    private Integer duracionConsultaMin;
    private Set<Integer> especialidadIds = new HashSet<>();
    private String cuentaEmail;
    private String cuentaPassword;

    public MedicoRequest aSolicitud(boolean conCuenta) {
        CuentaMedicoRequest cuenta = null;
        if (conCuenta && (PacienteForm.texto(cuentaEmail) != null || PacienteForm.texto(cuentaPassword) != null)) {
            cuenta = new CuentaMedicoRequest(PacienteForm.texto(cuentaEmail), cuentaPassword);
        }
        return new MedicoRequest(PacienteForm.texto(nombres), PacienteForm.texto(apellidos),
                PacienteForm.texto(numeroColegiatura), PacienteForm.texto(email), PacienteForm.texto(telefono),
                consultorioId, duracionConsultaMin, especialidadIds, cuenta);
    }

    public String getNombres() { return nombres; }
    public void setNombres(String nombres) { this.nombres = nombres; }
    public String getApellidos() { return apellidos; }
    public void setApellidos(String apellidos) { this.apellidos = apellidos; }
    public String getNumeroColegiatura() { return numeroColegiatura; }
    public void setNumeroColegiatura(String numeroColegiatura) { this.numeroColegiatura = numeroColegiatura; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }
    public Integer getConsultorioId() { return consultorioId; }
    public void setConsultorioId(Integer consultorioId) { this.consultorioId = consultorioId; }
    public Integer getDuracionConsultaMin() { return duracionConsultaMin; }
    public void setDuracionConsultaMin(Integer duracionConsultaMin) { this.duracionConsultaMin = duracionConsultaMin; }
    public Set<Integer> getEspecialidadIds() { return especialidadIds; }
    public void setEspecialidadIds(Set<Integer> especialidadIds) { this.especialidadIds = especialidadIds; }
    public String getCuentaEmail() { return cuentaEmail; }
    public void setCuentaEmail(String cuentaEmail) { this.cuentaEmail = cuentaEmail; }
    public String getCuentaPassword() { return cuentaPassword; }
    public void setCuentaPassword(String cuentaPassword) { this.cuentaPassword = cuentaPassword; }
}
