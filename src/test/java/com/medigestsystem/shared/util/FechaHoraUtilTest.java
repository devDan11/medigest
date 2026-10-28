package com.medigestsystem.shared.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;

class FechaHoraUtilTest {

    private static final Instant AHORA = Instant.parse("2026-07-01T15:00:00Z");

    private final Clock relojFijo = Clock.fixed(AHORA, ZoneOffset.UTC);
    private final FechaHoraUtil util = new FechaHoraUtil(relojFijo, ZoneId.of("America/Lima"));

    @Test
    void convierteInstanteUtcAHoraDeLima() {
        ZonedDateTime lima = util.aZonaClinica(AHORA);

        assertEquals(10, lima.getHour());
        assertEquals(ZoneOffset.ofHours(-5), lima.getOffset());
    }

    @Test
    void convierteHoraLocalDeLimaAInstanteUtc() {
        Instant utc = util.aInstante(LocalDateTime.of(2026, 7, 1, 8, 30));

        assertEquals(Instant.parse("2026-07-01T13:30:00Z"), utc);
    }

    @Test
    void usaElRelojInyectado() {
        assertEquals(AHORA, util.ahoraUtc());
        assertEquals(LocalDate.of(2026, 7, 1), util.hoyEnClinica());
    }

    @Test
    void detectaInstantesPasados() {
        assertTrue(util.esPasado(Instant.parse("2026-07-01T14:59:59Z")));
        assertFalse(util.esPasado(AHORA));
        assertFalse(util.esPasado(Instant.parse("2026-07-01T15:00:01Z")));
    }
}
