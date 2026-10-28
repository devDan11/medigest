package com.medigestsystem.integracion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;

class CitasApiIT extends AbstractIT {

    @Test
    void flujoCompletoDeUnaCitaConHistorialAuditoriaYNotificaciones() {
        String admin = token("admin");
        Horario h = buscarHorarioLibre(admin);
        int paciente = pacientesActivos(admin).get(0);

        Resp creada = reservar(admin, paciente, h);
        assertThat(creada.status()).as(creada.body()).isEqualTo(201);
        int id = creada.json("$.id");
        assertThat(creada.<String>json("$.estado")).isEqualTo("PENDIENTE");

        Resp confirmada = patch("/api/v1/citas/" + id + "/confirmar", admin, null);
        assertThat(confirmada.status()).as(confirmada.body()).isEqualTo(200);
        assertThat(confirmada.<String>json("$.estado")).isEqualTo("CONFIRMADA");

        Resp cancelada = patch("/api/v1/citas/" + id + "/cancelar", admin, "{\"motivo\":\"prueba de integración\"}");
        assertThat(cancelada.status()).as(cancelada.body()).isEqualTo(200);
        assertThat(cancelada.<String>json("$.estado")).isEqualTo("CANCELADA");

        Resp historial = get("/api/v1/citas/" + id + "/historial", admin);
        assertThat(historial.status()).isEqualTo(200);
        assertThat(historial.<List<Object>>json("$")).hasSizeGreaterThanOrEqualTo(3);

        // Se registraron los avisos al paciente (el correo está deshabilitado: quedan PENDIENTE hasta el despacho).
        Integer avisos = jdbc.queryForObject("SELECT COUNT(*) FROM notificaciones WHERE cita_id = ?", Integer.class, id);
        assertThat(avisos).isGreaterThanOrEqualTo(2);

        Resp auditoria = get("/api/v1/auditoria?accion=CITA_&tamano=5", admin);
        assertThat(auditoria.status()).isEqualTo(200);
        assertThat(auditoria.<Integer>json("$.totalElementos")).isGreaterThanOrEqualTo(2);
    }

    @Test
    void unaCancelacionSinMotivoSeRechaza() {
        String admin = token("admin");
        Horario h = buscarHorarioLibre(admin);
        Resp creada = reservar(admin, pacientesActivos(admin).get(1), h);
        assertThat(creada.status()).isEqualTo(201);
        int id = creada.json("$.id");

        Resp r = patch("/api/v1/citas/" + id + "/cancelar", admin, "{\"motivo\":\"\"}");

        assertThat(r.status()).isEqualTo(400);
        patch("/api/v1/citas/" + id + "/cancelar", admin, "{\"motivo\":\"limpieza de la prueba\"}");
    }

    @Test
    void reservasSimultaneasDelMismoHorarioSoloDejanGanarAUna() throws Exception {
        String admin = token("admin");
        Horario h = buscarHorarioLibre(admin);
        List<Integer> pacientes = pacientesActivos(admin);
        int n = Math.min(8, pacientes.size());

        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch salida = new CountDownLatch(1);
        List<Future<Resp>> futuros = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            int paciente = pacientes.get(i);
            Callable<Resp> tarea = () -> {
                salida.await();
                return reservar(admin, paciente, h);
            };
            futuros.add(pool.submit(tarea));
        }
        salida.countDown();

        int creadas = 0;
        int conflictos = 0;
        Integer ganadora = null;
        for (Future<Resp> f : futuros) {
            Resp r = f.get();
            if (r.status() == 201) {
                creadas++;
                ganadora = r.json("$.id");
            } else if (r.status() == 409) {
                conflictos++;
            }
        }
        pool.shutdown();

        assertThat(creadas).as("citas creadas").isEqualTo(1);
        assertThat(conflictos).as("conflictos 409").isEqualTo(n - 1);
        patch("/api/v1/citas/" + ganadora + "/cancelar", admin, "{\"motivo\":\"limpieza de la prueba\"}");
    }

    @Test
    void unPacienteNoPuedeTenerDosCitasSolapadas() {
        String admin = token("admin");
        Horario h = buscarHorarioLibre(admin);
        int paciente = pacientesActivos(admin).get(2);

        Resp primera = reservar(admin, paciente, h);
        assertThat(primera.status()).isEqualTo(201);
        Resp segunda = reservar(admin, paciente, h);

        assertThat(segunda.status()).isEqualTo(409);
        patch("/api/v1/citas/" + primera.json("$.id") + "/cancelar", admin, "{\"motivo\":\"limpieza de la prueba\"}");
    }
}
