package com.medigestsystem.shared.util;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Generación de CSV compatible con Excel: UTF-8 con BOM (para que respete las tildes), separador punto y coma (el que espera Excel en configuración regional española) y saltos CRLF.
 * Los textos que empiezan por {@code = + - @} se neutralizan con una comilla para evitar la inyección de fórmulas.
 */
public final class Csv {

    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final MediaType TIPO = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private Csv() {
    }

    public static byte[] generar(List<String> cabecera, List<List<Object>> filas) {
        StringBuilder sb = new StringBuilder();
        linea(sb, new java.util.ArrayList<Object>(cabecera));
        for (List<Object> fila : filas) {
            linea(sb, fila);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(BOM);
        out.writeBytes(sb.toString().getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    public static ResponseEntity<byte[]> respuesta(String nombreArchivo, byte[] datos) {
        return ResponseEntity.ok()
                .contentType(TIPO)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(nombreArchivo).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(datos);
    }

    static String celda(Object valor) {
        if (valor == null) {
            return "";
        }
        String texto = valor.toString();
        if (!(valor instanceof Number) && !texto.isEmpty() && "=+-@\t\r".indexOf(texto.charAt(0)) >= 0) {
            texto = "'" + texto;
        }
        if (texto.indexOf(';') >= 0 || texto.indexOf(',') >= 0 || texto.indexOf('"') >= 0 || texto.indexOf('\n') >= 0 || texto.indexOf('\r') >= 0) {
            return '"' + texto.replace("\"", "\"\"") + '"';
        }
        return texto;
    }

    private static void linea(StringBuilder sb, List<Object> valores) {
        for (int i = 0; i < valores.size(); i++) {
            if (i > 0) {
                sb.append(';');
            }
            sb.append(celda(valores.get(i)));
        }
        sb.append("\r\n");
    }
}
