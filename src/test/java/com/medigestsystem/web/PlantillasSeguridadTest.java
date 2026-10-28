package com.medigestsystem.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Garantiza que las plantillas cumplen la Content-Security-Policy ({@code script-src 'self'}): ningún recurso externo,
 * ningún script en línea y ningún manejador de eventos en atributos.
 */
class PlantillasSeguridadTest {

    private static final Path RAIZ = Path.of("src", "main", "resources", "templates");
    private static final Pattern RECURSO_EXTERNO = Pattern.compile("(?i)\\b(?:src|href)\\s*=\\s*\"(?:https?:)?//");
    private static final Pattern SCRIPT_EN_LINEA = Pattern.compile("(?is)<script(?![^>]*\\b(?:th:)?src\\s*=)[^>]*>");
    private static final Pattern MANEJADOR_EVENTO = Pattern.compile("(?i)\\s(?:on[a-z]+|th:on[a-z]+)\\s*=");
    private static final Pattern JAVASCRIPT_URL = Pattern.compile("(?i)(?:href|src)\\s*=\\s*\"\\s*javascript:");

    private List<String> violaciones(Pattern patron) throws IOException {
        List<String> encontradas = new ArrayList<>();
        try (Stream<Path> archivos = Files.walk(RAIZ)) {
            for (Path p : archivos.filter(f -> f.toString().endsWith(".html")).toList()) {
                String contenido = Files.readString(p, StandardCharsets.UTF_8);
                if (patron.matcher(contenido).find()) {
                    encontradas.add(RAIZ.relativize(p).toString());
                }
            }
        }
        return encontradas;
    }

    @Test
    void hayPlantillasQueRevisar() throws IOException {
        try (Stream<Path> archivos = Files.walk(RAIZ)) {
            assertThat(archivos.filter(f -> f.toString().endsWith(".html")).count()).isGreaterThan(10);
        }
    }

    @Test
    void ningunaPlantillaCargaRecursosDeOtrosDominios() throws IOException {
        assertThat(violaciones(RECURSO_EXTERNO)).isEmpty();
    }

    @Test
    void ningunaPlantillaTieneScriptsEnLinea() throws IOException {
        assertThat(violaciones(SCRIPT_EN_LINEA)).isEmpty();
    }

    @Test
    void ningunaPlantillaUsaManejadoresDeEventosEnAtributos() throws IOException {
        assertThat(violaciones(MANEJADOR_EVENTO)).isEmpty();
        assertThat(violaciones(JAVASCRIPT_URL)).isEmpty();
    }
}
