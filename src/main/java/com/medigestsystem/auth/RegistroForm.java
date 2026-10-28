package com.medigestsystem.auth;

import java.time.LocalDate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.format.annotation.DateTimeFormat;

import com.medigestsystem.auth.dto.RegistroPacienteRequest;
import com.medigestsystem.pacientes.Sexo;
import com.medigestsystem.pacientes.TipoDocumento;

/** Formulario web de registro (clase con getters/setters para el enlace de Thymeleaf). */
public class RegistroForm {

    @NotBlank(message = "Los nombres son obligatorios")
    @Size(max = 100, message = "Los nombres admiten hasta 100 caracteres")
    private String nombres;

    @NotBlank(message = "Los apellidos son obligatorios")
    @Size(max = 100, message = "Los apellidos admiten hasta 100 caracteres")
    private String apellidos;

    @NotNull(message = "Seleccione el tipo de documento")
    private TipoDocumento tipoDocumento = TipoDocumento.DNI;

    @NotBlank(message = "El número de documento es obligatorio")
    @Size(max = 20, message = "El número de documento admite hasta 20 caracteres")
    private String numeroDocumento;

    @NotNull(message = "La fecha de nacimiento es obligatoria")
    @Past(message = "La fecha de nacimiento debe ser anterior a hoy")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fechaNacimiento;

    private Sexo sexo;

    @NotBlank(message = "El correo es obligatorio")
    @Email(message = "El correo no tiene un formato válido")
    @Size(max = 150, message = "El correo admite hasta 150 caracteres")
    private String email;

    @Pattern(regexp = "^$|^[0-9+\\-\\s]{6,20}$", message = "El teléfono solo admite dígitos, +, - y espacios (6 a 20)")
    private String telefono;

    @NotBlank(message = "La contraseña es obligatoria")
    @Pattern(regexp = PoliticaPassword.REGEX, message = PoliticaPassword.MENSAJE)
    private String password;

    @NotBlank(message = "Confirme la contraseña")
    private String confirmacion;

    public RegistroPacienteRequest aSolicitud() {
        return new RegistroPacienteRequest(nombres, apellidos, tipoDocumento, numeroDocumento,
                fechaNacimiento, sexo, email, telefono, password);
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
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getConfirmacion() { return confirmacion; }
    public void setConfirmacion(String confirmacion) { this.confirmacion = confirmacion; }
}
