package com.medigestsystem.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Habilita las tareas programadas (vencimiento de ofertas de la lista de espera).
 * Se pueden desactivar con {@code medigest.tareas.habilitadas=false}, por ejemplo en pruebas.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "medigest.tareas.habilitadas", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
