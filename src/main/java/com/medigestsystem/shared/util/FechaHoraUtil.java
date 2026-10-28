package com.medigestsystem.shared.util;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.springframework.stereotype.Component;

@Component
public class FechaHoraUtil {

    private final Clock clock;
    private final ZoneId zonaClinica;

    public FechaHoraUtil(Clock clock, ZoneId zonaClinica) {
        this.clock = clock;
        this.zonaClinica = zonaClinica;
    }

    public Instant ahoraUtc() {
        return clock.instant();
    }

    public LocalDate hoyEnClinica() {
        return LocalDate.ofInstant(clock.instant(), zonaClinica);
    }

    /** Convierte un instante almacenado en UTC a la hora local de la clínica. */
    public ZonedDateTime aZonaClinica(Instant instante) {
        return instante.atZone(zonaClinica);
    }

    /** Convierte una fecha-hora local de la clínica (entrada del usuario) a instante UTC. */
    public Instant aInstante(LocalDateTime fechaHoraLocal) {
        return fechaHoraLocal.atZone(zonaClinica).toInstant();
    }

    /** Un instante es "pasado" si es estrictamente anterior al momento actual. */
    public boolean esPasado(Instant instante) {
        return instante.isBefore(clock.instant());
    }
}
