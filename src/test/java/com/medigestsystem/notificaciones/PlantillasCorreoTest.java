package com.medigestsystem.notificaciones;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.medigestsystem.citas.Cita;
import com.medigestsystem.citas.EstadoCita;
import com.medigestsystem.especialidades.Especialidad;
import com.medigestsystem.medicos.Medico;
import com.medigestsystem.pacientes.Paciente;
import com.medigestsystem.testsupport.Fixtures;

class PlantillasCorreoTest {

    final PlantillasCorreo plantillas = new PlantillasCorreo(Fixtures.LIMA, Fixtures.propiedades(Fixtures.SECRETO_PRUEBAS));

    static Cita cita() {
        Paciente p = new Paciente();
        p.setId(1L);
        p.setNombres("Ana");
        p.setApellidos("Rojas");
        p.setEmail("ana@correo.test");
        Medico m = new Medico();
        m.setId(2L);
        m.setNombres("Luis");
        m.setApellidos("Paz");
        Especialidad e = new Especialidad();
        e.setId(3);
        e.setNombre("Cardiología");
        Cita c = new Cita();
        c.setId(10L);
        c.setCodigoReserva("MG-ABC123");
        c.setPaciente(p);
        c.setMedico(m);
        c.setEspecialidad(e);
        c.setInicioUtc(Fixtures.AHORA.plus(Duration.ofDays(1)));
        c.setFinUtc(Fixtures.AHORA.plus(Duration.ofDays(1)).plus(Duration.ofMinutes(30)));
        c.setEstado(EstadoCita.CONFIRMADA);
        c.setMotivo("dolor en el pecho al caminar");
        return c;
    }

    @Test
    void elCorreoMuestraLaHoraDeLimaYNoIncluyeElMotivoClinico() {
        PlantillasCorreo.Mensaje m = plantillas.deCita(TipoNotificacion.CITA_RESERVADA, cita());

        assertThat(m.asunto()).contains("MG-ABC123");
        assertThat(m.cuerpo()).contains("Hola Ana", "Hora: 10:00", "Cardiología", "Luis Paz", "/citas/10");
        assertThat(m.cuerpo()).doesNotContain("dolor");
    }

    @Test
    void laCancelacionNoIncluyeEnlaceAlDetalle() {
        PlantillasCorreo.Mensaje m = plantillas.deCita(TipoNotificacion.CITA_CANCELADA, cita());

        assertThat(m.asunto()).startsWith("Cita cancelada");
        assertThat(m.cuerpo()).doesNotContain("/citas/");
    }

    @Test
    void elCuerpoNuncaSuperaElLimiteDeLaTabla() {
        Cita c = cita();
        c.getPaciente().setNombres("N".repeat(2000));

        assertThat(plantillas.deCita(TipoNotificacion.RECORDATORIO_CITA, c).cuerpo()).hasSizeLessThanOrEqualTo(1000);
    }

    @Test
    void rechazaTiposQueNoSonDeCita() {
        assertThatThrownBy(() -> plantillas.deCita(TipoNotificacion.REGISTRO, cita()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void laOfertaIndicaHastaCuandoPuedeAceptarse() {
        var e = new com.medigestsystem.listaespera.ListaEspera();
        e.setId(7L);
        e.setPaciente(cita().getPaciente());
        e.setEspecialidad(cita().getEspecialidad());
        Instant inicio = Fixtures.AHORA.plus(Duration.ofDays(2));
        e.setOfertaInicioUtc(inicio);
        e.setOfertaExpiraEn(Fixtures.AHORA.plus(Duration.ofMinutes(30)));

        PlantillasCorreo.Mensaje m = plantillas.deOferta(e);

        assertThat(m.cuerpo()).contains("Disponible hasta las 10:30", "Cardiología", "/lista-espera");
    }
}
