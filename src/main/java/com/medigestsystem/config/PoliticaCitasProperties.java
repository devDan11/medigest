package com.medigestsystem.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Políticas de gestión de citas. Todas son configurables con el prefijo {@code medigest.citas}.
 *
 * @param anticipacionMinimaPacienteMin minutos mínimos entre "ahora" y el inicio de una cita reservada por un paciente
 * @param horizonteDias                 días máximos hacia el futuro en que se puede reservar
 * @param limiteCancelacionHoras        horas de anticipación que exige la política para que un paciente cancele o reprograme
 * @param maxReprogramacionesPaciente   reprogramaciones que un paciente puede hacer sobre una misma cita
 * @param ofertaEsperaMin               minutos que se retiene un horario ofrecido desde la lista de espera
 */
@ConfigurationProperties(prefix = "medigest.citas")
public record PoliticaCitasProperties(
        @DefaultValue("60") int anticipacionMinimaPacienteMin,
        @DefaultValue("90") int horizonteDias,
        @DefaultValue("24") int limiteCancelacionHoras,
        @DefaultValue("3") int maxReprogramacionesPaciente,
        @DefaultValue("30") int ofertaEsperaMin
) {
}
