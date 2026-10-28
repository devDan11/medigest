package com.medigestsystem.web;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.medigestsystem.citas.AccionCita;
import com.medigestsystem.citas.EstadoCita;
import com.medigestsystem.listaespera.EstadoListaEspera;
import com.medigestsystem.listaespera.FranjaPreferida;

/** Utilidades de presentación para las plantillas Thymeleaf ({@code ${@fmt.metodo(...)}}). */
@Component("fmt")
public class Formato {

    private static final Locale ES = Locale.forLanguageTag("es-PE");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DIA_CORTO = DateTimeFormatter.ofPattern("EEE dd/MM", ES);

    private final ZoneId zona;

    public Formato() {
        this(ZoneId.of("America/Lima"));
    }

    @Autowired
    public Formato(ZoneId zonaClinica) {
        this.zona = zonaClinica;
    }

    /** Instante almacenado en UTC, mostrado en la hora local de la clínica. */
    public String instante(Instant valor) {
        return valor == null ? "Nunca" : valor.atZone(zona).format(FECHA_HORA);
    }

    public String fechaHora(OffsetDateTime valor) {
        return valor == null ? "—" : valor.format(FECHA_HORA);
    }

    public String fecha(OffsetDateTime valor) {
        return valor == null ? "—" : valor.format(FECHA);
    }

    public String fecha(LocalDate valor) {
        return valor == null ? "—" : valor.format(FECHA);
    }

    public String hora(OffsetDateTime valor) {
        return valor == null ? "—" : valor.format(HORA);
    }

    public String diaCorto(LocalDate valor) {
        return valor.format(DIA_CORTO);
    }

    public String estado(EstadoCita estado) {
        if (estado == null) {
            return "—";
        }
        return switch (estado) {
            case PENDIENTE -> "Pendiente";
            case CONFIRMADA -> "Confirmada";
            case EN_ESPERA -> "En espera";
            case EN_ATENCION -> "En atención";
            case ATENDIDA -> "Atendida";
            case CANCELADA -> "Cancelada";
            case NO_ASISTIO -> "No asistió";
        };
    }

    /** Clase de Bootstrap para la insignia del estado. */
    public String claseEstado(EstadoCita estado) {
        if (estado == null) {
            return "text-bg-light";
        }
        return switch (estado) {
            case PENDIENTE -> "text-bg-warning";
            case CONFIRMADA -> "text-bg-primary";
            case EN_ESPERA -> "text-bg-info";
            case EN_ATENCION -> "text-bg-dark";
            case ATENDIDA -> "text-bg-success";
            case CANCELADA -> "text-bg-danger";
            case NO_ASISTIO -> "text-bg-secondary";
        };
    }

    public String estadoEspera(EstadoListaEspera estado) {
        if (estado == null) {
            return "—";
        }
        return switch (estado) {
            case ACTIVA -> "En espera";
            case OFERTADA -> "Horario ofrecido";
            case ACEPTADA -> "Aceptada";
            case VENCIDA -> "Vencida";
            case CANCELADA -> "Cancelada";
        };
    }

    public String claseEspera(EstadoListaEspera estado) {
        if (estado == null) {
            return "text-bg-light";
        }
        return switch (estado) {
            case ACTIVA -> "text-bg-info";
            case OFERTADA -> "text-bg-warning";
            case ACEPTADA -> "text-bg-success";
            case VENCIDA -> "text-bg-secondary";
            case CANCELADA -> "text-bg-danger";
        };
    }

    public String franja(FranjaPreferida franja) {
        if (franja == null) {
            return "Cualquiera";
        }
        return switch (franja) {
            case CUALQUIERA -> "Cualquier horario";
            case MANANA -> "Mañana";
            case TARDE -> "Tarde";
        };
    }

    /** Texto legible para un nombre de enumeración (p. ej. REPROGRAMADA → "Reprogramada"). */
    public String texto(Enum<?> valor) {
        if (valor == null) {
            return "—";
        }
        String minusculas = valor.name().replace('_', ' ').toLowerCase(ES);
        return Character.toUpperCase(minusculas.charAt(0)) + minusculas.substring(1);
    }

    /** ¿La lista de acciones permitidas (calculada por el backend) incluye la acción indicada? */
    public boolean puede(List<AccionCita> acciones, String accion) {
        return acciones != null && acciones.stream().anyMatch(a -> a.name().equals(accion));
    }

    /** Valor de un campo de formulario: lo reenviado tras un error si existe; si no, el valor por defecto. */
    public String valor(Map<String, String> reenviado, String campo, Object defecto) {
        if (reenviado != null && reenviado.containsKey(campo)) {
            return reenviado.get(campo);
        }
        return defecto == null ? "" : defecto.toString();
    }

    /** ¿Debe aparecer marcada la opción {@code opcion} de un campo de selección múltiple? */
    public boolean marcado(Map<String, String> reenviado, String campo, Object opcion, boolean defecto) {
        if (reenviado != null && reenviado.containsKey(campo)) {
            return Arrays.asList(reenviado.get(campo).split(",")).contains(String.valueOf(opcion));
        }
        return defecto;
    }
}
