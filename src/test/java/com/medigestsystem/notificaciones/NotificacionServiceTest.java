package com.medigestsystem.notificaciones;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;

import com.medigestsystem.auditoria.AuditoriaService;
import com.medigestsystem.citas.Cita;
import com.medigestsystem.citas.CitaRepository;
import com.medigestsystem.citas.EstadoCita;
import com.medigestsystem.listaespera.ListaEsperaRepository;
import com.medigestsystem.shared.exception.ConflictoException;
import com.medigestsystem.testsupport.Fixtures;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificacionServiceTest {

    @Mock NotificacionRepository repository;
    @Mock CitaRepository citaRepository;
    @Mock ListaEsperaRepository esperaRepository;
    @Mock ObjectProvider<CanalCorreo> canalProvider;
    @Mock CanalCorreo canal;
    @Mock AuditoriaService auditoriaService;

    NotificacionService servicio;

    @BeforeEach
    void preparar() {
        servicio = crear(new CorreoProperties(true, "MediGest <no-reply@medigest.local>", 3, 2, 20, 24));
        when(canalProvider.getIfAvailable()).thenReturn(canal);
    }

    NotificacionService crear(CorreoProperties props) {
        return new NotificacionService(repository, citaRepository, esperaRepository,
                new PlantillasCorreo(Fixtures.LIMA, Fixtures.propiedades(Fixtures.SECRETO_PRUEBAS)),
                canalProvider, props, auditoriaService, Fixtures.fechaHora());
    }

    Notificacion pendiente(int intentos) {
        Notificacion n = new Notificacion();
        n.setId(5L);
        n.setDestinatario("ana@correo.test");
        n.setAsunto("Asunto");
        n.setCuerpo("Cuerpo");
        n.setEstado(EstadoNotificacion.PENDIENTE);
        n.setIntentos(intentos);
        return n;
    }

    // ------------------------------------------------------------ creación

    @Test
    void creaUnaNotificacionPendienteSinDatosClinicos() {
        when(citaRepository.findById(10L)).thenReturn(Optional.of(PlantillasCorreoTest.cita()));

        assertThat(servicio.encolarParaCita(10L, TipoNotificacion.CITA_RESERVADA)).isTrue();

        ArgumentCaptor<Notificacion> captor = ArgumentCaptor.forClass(Notificacion.class);
        verify(repository).save(captor.capture());
        Notificacion n = captor.getValue();
        assertThat(n.getEstado()).isEqualTo(EstadoNotificacion.PENDIENTE);
        assertThat(n.getDestinatario()).isEqualTo("ana@correo.test");
        assertThat(n.getClaveIdempotencia()).isEqualTo("CITA_RESERVADA:10");
        assertThat(n.getCuerpo()).doesNotContain("dolor");
    }

    @Test
    void noDuplicaLaNotificacionSiLaClaveYaExiste() {
        when(citaRepository.findById(10L)).thenReturn(Optional.of(PlantillasCorreoTest.cita()));
        when(repository.existsByClaveIdempotencia("CITA_CONFIRMADA:10")).thenReturn(true);

        assertThat(servicio.encolarParaCita(10L, TipoNotificacion.CITA_CONFIRMADA)).isFalse();
        verify(repository, never()).save(any());
    }

    @Test
    void laReprogramacionUsaElNuevoInicioEnLaClave() {
        Cita c = PlantillasCorreoTest.cita();
        when(citaRepository.findById(10L)).thenReturn(Optional.of(c));

        servicio.encolarParaCita(10L, TipoNotificacion.CITA_REPROGRAMADA);

        ArgumentCaptor<Notificacion> captor = ArgumentCaptor.forClass(Notificacion.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getClaveIdempotencia())
                .isEqualTo("CITA_REPROGRAMADA:10:" + c.getInicioUtc().getEpochSecond());
    }

    @Test
    void sinCorreoDelPacienteNoSeNotifica() {
        Cita c = PlantillasCorreoTest.cita();
        c.getPaciente().setEmail("  ");
        when(citaRepository.findById(10L)).thenReturn(Optional.of(c));

        assertThat(servicio.encolarParaCita(10L, TipoNotificacion.CITA_RESERVADA)).isFalse();
        verify(repository, never()).save(any());
    }

    @Test
    void noSeRecuerdaUnaCitaQueYaNoEstaActiva() {
        Cita c = PlantillasCorreoTest.cita();
        c.setEstado(EstadoCita.CANCELADA);
        when(citaRepository.findById(10L)).thenReturn(Optional.of(c));

        assertThat(servicio.encolarParaCita(10L, TipoNotificacion.RECORDATORIO_CITA)).isFalse();
        verify(repository, never()).save(any());
    }

    // ------------------------------------------------------------ envío

    @Test
    void unEnvioExitosoMarcaLaNotificacionComoEnviada() {
        Notificacion n = pendiente(0);
        when(repository.findById(5L)).thenReturn(Optional.of(n));

        assertThat(servicio.procesar(5L)).isEqualTo(EstadoNotificacion.ENVIADA);

        verify(canal).enviar("ana@correo.test", "Asunto", "Cuerpo");
        assertThat(n.getIntentos()).isEqualTo(1);
        assertThat(n.getEnviadaEn()).isEqualTo(Fixtures.AHORA);
    }

    @Test
    void unFalloSeReprogramaConEsperaCreciente() {
        Notificacion n = pendiente(1);
        when(repository.findById(5L)).thenReturn(Optional.of(n));
        doThrow(new IllegalStateException("SMTP caído")).when(canal).enviar(anyString(), anyString(), anyString());

        assertThat(servicio.procesar(5L)).isEqualTo(EstadoNotificacion.PENDIENTE);

        assertThat(n.getIntentos()).isEqualTo(2);
        assertThat(n.getUltimoError()).contains("SMTP caído");
        assertThat(n.getProgramadaPara()).isEqualTo(Fixtures.AHORA.plus(Duration.ofMinutes(4)));
    }

    @Test
    void agotadosLosIntentosQuedaFallida() {
        Notificacion n = pendiente(2);
        when(repository.findById(5L)).thenReturn(Optional.of(n));
        doThrow(new IllegalStateException("SMTP caído")).when(canal).enviar(anyString(), anyString(), anyString());

        assertThat(servicio.procesar(5L)).isEqualTo(EstadoNotificacion.FALLIDA);
        assertThat(n.getIntentos()).isEqualTo(3);
    }

    @Test
    void conElCorreoDeshabilitadoQuedaOmitidaSinIntentarEnviar() {
        servicio = crear(new CorreoProperties(false, "x", 3, 2, 20, 24));
        Notificacion n = pendiente(0);
        when(repository.findById(5L)).thenReturn(Optional.of(n));

        assertThat(servicio.procesar(5L)).isEqualTo(EstadoNotificacion.OMITIDA);

        verify(canal, never()).enviar(anyString(), anyString(), anyString());
        assertThat(n.getIntentos()).isZero();
    }

    @Test
    void unaNotificacionYaProcesadaNoSeEnviaDeNuevo() {
        Notificacion n = pendiente(1);
        n.setEstado(EstadoNotificacion.ENVIADA);
        when(repository.findById(5L)).thenReturn(Optional.of(n));

        assertThat(servicio.procesar(5L)).isNull();
        verify(canal, never()).enviar(anyString(), anyString(), anyString());
    }

    // ------------------------------------------------------------ reintento manual

    @Test
    void reintentarDevuelveALaColaUnaNotificacionFallida() {
        Notificacion n = pendiente(3);
        n.setEstado(EstadoNotificacion.FALLIDA);
        n.setUltimoError("x");
        when(repository.findById(5L)).thenReturn(Optional.of(n));

        servicio.reintentar(5L);

        assertThat(n.getEstado()).isEqualTo(EstadoNotificacion.PENDIENTE);
        assertThat(n.getIntentos()).isZero();
        assertThat(n.getUltimoError()).isNull();
    }

    @Test
    void noSePuedeReintentarUnaNotificacionYaEnviada() {
        Notificacion n = pendiente(1);
        n.setEstado(EstadoNotificacion.ENVIADA);
        when(repository.findById(5L)).thenReturn(Optional.of(n));

        assertThatThrownBy(() -> servicio.reintentar(5L)).isInstanceOf(ConflictoException.class);
    }
}
