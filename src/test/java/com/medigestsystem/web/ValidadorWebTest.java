package com.medigestsystem.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import jakarta.validation.Validation;

import org.junit.jupiter.api.Test;

import com.medigestsystem.pacientes.TipoDocumento;
import com.medigestsystem.pacientes.dto.PacienteRequest;
import com.medigestsystem.shared.exception.ApiException;
import com.medigestsystem.web.paginas.PacienteForm;
import com.medigestsystem.web.paginas.ValidadorWeb;

class ValidadorWebTest {

    private final ValidadorWeb validador =
            new ValidadorWeb(Validation.buildDefaultValidatorFactory().getValidator());

    @Test
    void aceptaUnaSolicitudValida() {
        PacienteRequest s = new PacienteRequest("Ana", "Pérez", TipoDocumento.DNI, "12345678",
                LocalDate.of(1990, 5, 10), null, "ana@correo.test", "999 888 777", null, null, null);

        assertThat(validador.validar(s)).isSameAs(s);
    }

    @Test
    void reuneTodosLosMensajesDeValidacion() {
        PacienteRequest s = new PacienteRequest("", "", TipoDocumento.DNI, "1", LocalDate.of(2999, 1, 1), null,
                "no-es-correo", "abc", null, null, null);

        assertThatThrownBy(() -> validador.validar(s)).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getCodigo()).isEqualTo("DATOS_INVALIDOS");
            assertThat(e.getMessage()).contains("Los nombres son obligatorios", "Los apellidos son obligatorios",
                    "La fecha de nacimiento debe ser anterior a hoy", "El correo no tiene un formato válido");
        });
    }

    @Test
    void elFormularioConvierteTextosVaciosEnNulos() {
        PacienteForm form = new PacienteForm();
        form.setNombres("  Ana ");
        form.setApellidos("Pérez");
        form.setTipoDocumento(TipoDocumento.DNI);
        form.setNumeroDocumento("12345678");
        form.setFechaNacimiento(LocalDate.of(1990, 5, 10));
        form.setEmail("   ");
        form.setTelefono("");

        PacienteRequest s = form.aSolicitud();

        assertThat(s.nombres()).isEqualTo("Ana");
        assertThat(s.email()).isNull();
        assertThat(s.telefono()).isNull();
    }
}
