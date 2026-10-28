package com.medigestsystem.citas;

import java.security.SecureRandom;

import org.springframework.stereotype.Component;

/** Genera códigos de reserva legibles y no predecibles, por ejemplo {@code MG-7K4Q9XPD}. */
@Component
public class CodigoReservaGenerator {

    private static final String ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // sin I, O, 0, 1
    private static final int LONGITUD = 8;
    private static final int INTENTOS_MAXIMOS = 10;

    private final SecureRandom azar = new SecureRandom();
    private final CitaRepository citaRepository;

    public CodigoReservaGenerator(CitaRepository citaRepository) {
        this.citaRepository = citaRepository;
    }

    public String nuevo() {
        for (int i = 0; i < INTENTOS_MAXIMOS; i++) {
            StringBuilder sb = new StringBuilder("MG-");
            for (int j = 0; j < LONGITUD; j++) {
                sb.append(ALFABETO.charAt(azar.nextInt(ALFABETO.length())));
            }
            String codigo = sb.toString();
            if (!citaRepository.existsByCodigoReserva(codigo)) {
                return codigo;
            }
        }
        throw new IllegalStateException("No se pudo generar un código de reserva único");
    }
}
