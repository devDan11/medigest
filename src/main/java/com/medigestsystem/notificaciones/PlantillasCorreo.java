package com.medigestsystem.notificaciones;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.medigestsystem.citas.Cita;
import com.medigestsystem.config.MediGestProperties;
import com.medigestsystem.consultorios.Consultorio;
import com.medigestsystem.listaespera.ListaEspera;

/**
 * Textos de los correos. Nunca incluyen datos clínicos (motivo de consulta, diagnóstico, observaciones):
 * solo lo necesario para ubicar la cita.
 */
@Component
public class PlantillasCorreo {

    public record Mensaje(String asunto, String cuerpo) {
    }

    private static final Locale ES = Locale.forLanguageTag("es-PE");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy", ES);
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm", ES);
    private static final int MAX_CUERPO = 1000;

    private final ZoneId zona;
    private final String urlBase;

    public PlantillasCorreo(ZoneId zonaClinica, MediGestProperties propiedades) {
        this.zona = zonaClinica;
        String base = propiedades.aplicacion().urlBase();
        this.urlBase = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    public Mensaje deCita(TipoNotificacion tipo, Cita c) {
        String intro;
        String asunto;
        switch (tipo) {
            case CITA_RESERVADA -> {
                asunto = "Cita reservada";
                intro = "Tu cita fue registrada.";
            }
            case CITA_CONFIRMADA -> {
                asunto = "Cita confirmada";
                intro = "Tu cita fue confirmada.";
            }
            case CITA_REPROGRAMADA -> {
                asunto = "Cita reprogramada";
                intro = "Tu cita cambió de fecha u hora. Revisa el nuevo horario.";
            }
            case CITA_CANCELADA -> {
                asunto = "Cita cancelada";
                intro = "Tu cita fue cancelada.";
            }
            case RECORDATORIO_CITA -> {
                asunto = "Recordatorio de tu cita";
                intro = "Te recordamos que tienes una cita próxima.";
            }
            default -> throw new IllegalArgumentException("Tipo no válido para una cita: " + tipo);
        }
        ZonedDateTime inicio = c.getInicioUtc().atZone(zona);
        StringBuilder sb = new StringBuilder();
        sb.append("Hola ").append(c.getPaciente().getNombres()).append(",\n\n").append(intro).append("\n\n");
        sb.append("Código de reserva: ").append(c.getCodigoReserva()).append('\n');
        sb.append("Fecha: ").append(FECHA.format(inicio)).append('\n');
        sb.append("Hora: ").append(HORA.format(inicio)).append(" (hora de Lima)\n");
        sb.append("Especialidad: ").append(c.getEspecialidad().getNombre()).append('\n');
        sb.append("Profesional: ").append(c.getMedico().getNombres()).append(' ').append(c.getMedico().getApellidos()).append('\n');
        Consultorio k = c.getConsultorio();
        if (k != null) {
            sb.append("Consultorio: ").append(k.getCodigo()).append('\n');
        }
        if (tipo != TipoNotificacion.CITA_CANCELADA) {
            sb.append("\nDetalle de la cita: ").append(urlBase).append("/citas/").append(c.getId()).append('\n');
        }
        sb.append(PIE);
        return new Mensaje(asunto + " · " + c.getCodigoReserva(), limitar(sb.toString()));
    }

    public Mensaje deOferta(ListaEspera e) {
        ZonedDateTime inicio = e.getOfertaInicioUtc().atZone(zona);
        ZonedDateTime vence = e.getOfertaExpiraEn().atZone(zona);
        StringBuilder sb = new StringBuilder();
        sb.append("Hola ").append(e.getPaciente().getNombres()).append(",\n\n");
        sb.append("Se liberó un horario para tu solicitud de espera.\n\n");
        sb.append("Especialidad: ").append(e.getEspecialidad().getNombre()).append('\n');
        sb.append("Fecha: ").append(FECHA.format(inicio)).append('\n');
        sb.append("Hora: ").append(HORA.format(inicio)).append(" (hora de Lima)\n");
        sb.append("Disponible hasta las ").append(HORA.format(vence)).append(" para aceptarlo.\n");
        sb.append("\nAcéptalo aquí: ").append(urlBase).append("/lista-espera\n");
        sb.append(PIE);
        return new Mensaje("Se liberó un horario para ti", limitar(sb.toString()));
    }

    private static final String PIE = "\nMediGest · Este mensaje es automático, no respondas a este correo.\n";

    private static String limitar(String texto) {
        return texto.length() <= MAX_CUERPO ? texto : texto.substring(0, MAX_CUERPO - 1) + "…";
    }
}
