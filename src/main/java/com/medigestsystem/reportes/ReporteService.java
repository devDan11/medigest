package com.medigestsystem.reportes;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medigestsystem.citas.CitaRepository;
import com.medigestsystem.citas.EstadoCita;
import com.medigestsystem.dashboard.DashboardResponse.DiaConteo;
import com.medigestsystem.reportes.dto.FilaResumen;
import com.medigestsystem.reportes.dto.ReporteCitasResponse;
import com.medigestsystem.reportes.dto.ReporteEspecialidadesResponse;
import com.medigestsystem.shared.exception.SolicitudInvalidaException;
import com.medigestsystem.shared.util.Csv;
import com.medigestsystem.shared.util.FechaHoraUtil;

/** Estadísticas y reportes de citas. Solo lectura; el control de acceso está en los controladores. */
@Service
@Transactional(readOnly = true)
public class ReporteService {

    static final int DIAS_POR_DEFECTO = 30;
    static final int DIAS_MAXIMOS = 366;

    private final CitaRepository citaRepository;
    private final FechaHoraUtil fechaHora;
    private final ZoneId zona;

    public ReporteService(CitaRepository citaRepository, FechaHoraUtil fechaHora, ZoneId zonaClinica) {
        this.citaRepository = citaRepository;
        this.fechaHora = fechaHora;
        this.zona = zonaClinica;
    }

    public ReporteCitasResponse citas(LocalDate desde, LocalDate hasta, Long medicoId, Integer especialidadId) {
        LocalDate[] rango = normalizar(desde, hasta);
        Instant ini = rango[0].atStartOfDay(zona).toInstant();
        Instant fin = rango[1].plusDays(1).atStartOfDay(zona).toInstant();
        long medico = medicoId == null ? -1L : medicoId;
        int especialidad = especialidadId == null ? -1 : especialidadId;

        Map<EstadoCita, Long> porEstado = new EnumMap<>(EstadoCita.class);
        for (EstadoCita e : EstadoCita.values()) {
            porEstado.put(e, 0L);
        }
        for (Object[] f : citaRepository.contarEstadosReporte(ini, fin, medico, especialidad)) {
            porEstado.put((EstadoCita) f[0], ((Number) f[1]).longValue());
        }
        long total = porEstado.values().stream().mapToLong(Long::longValue).sum();
        long atendidas = porEstado.get(EstadoCita.ATENDIDA);
        long noAsistio = porEstado.get(EstadoCita.NO_ASISTIO);
        long canceladas = porEstado.get(EstadoCita.CANCELADA);

        Map<LocalDate, Long> dias = new LinkedHashMap<>();
        for (LocalDate d = rango[0]; !d.isAfter(rango[1]); d = d.plusDays(1)) {
            dias.put(d, 0L);
        }
        for (Object[] f : citaRepository.diasReporte(ini, fin, medico, especialidad)) {
            if (f[1] != EstadoCita.CANCELADA) {
                dias.merge(((Instant) f[0]).atZone(zona).toLocalDate(), 1L, Long::sum);
            }
        }
        List<DiaConteo> porDia = new ArrayList<>();
        dias.forEach((d, n) -> porDia.add(new DiaConteo(d, n)));

        List<FilaResumen> porMedico = agrupar(citaRepository.medicosReporte(ini, fin, medico, especialidad), true);
        return new ReporteCitasResponse(rango[0], rango[1], total, porEstado,
                porcentaje(atendidas, atendidas + noAsistio), porcentaje(canceladas, total), porDia, porMedico);
    }

    public ReporteEspecialidadesResponse especialidades(LocalDate desde, LocalDate hasta) {
        LocalDate[] rango = normalizar(desde, hasta);
        Instant ini = rango[0].atStartOfDay(zona).toInstant();
        Instant fin = rango[1].plusDays(1).atStartOfDay(zona).toInstant();
        return new ReporteEspecialidadesResponse(rango[0], rango[1],
                agrupar(citaRepository.especialidadesReporte(ini, fin), false));
    }

    /** CSV de una vista del reporte de citas: {@code estado}, {@code dia} o {@code medico}. */
    public byte[] csvCitas(String vista, LocalDate desde, LocalDate hasta, Long medicoId, Integer especialidadId) {
        ReporteCitasResponse r = citas(desde, hasta, medicoId, especialidadId);
        List<List<Object>> filas = new ArrayList<>();
        switch (vista == null ? "dia" : vista) {
            case "estado" -> {
                r.porEstado().forEach((e, n) -> filas.add(List.of(e.name(), n)));
                return Csv.generar(List.of("Estado", "Citas"), filas);
            }
            case "medico" -> {
                r.porMedico().forEach(f -> filas.add(filaCsv(f)));
                return Csv.generar(cabeceraFilas("Médico"), filas);
            }
            case "dia" -> {
                r.porDia().forEach(d -> filas.add(List.of(d.fecha().toString(), d.total())));
                return Csv.generar(List.of("Fecha", "Citas (sin canceladas)"), filas);
            }
            default -> throw new SolicitudInvalidaException("VISTA_INVALIDA", "La vista debe ser estado, dia o medico.");
        }
    }

    public byte[] csvEspecialidades(LocalDate desde, LocalDate hasta) {
        List<List<Object>> filas = new ArrayList<>();
        especialidades(desde, hasta).especialidades().forEach(f -> filas.add(filaCsv(f)));
        return Csv.generar(cabeceraFilas("Especialidad"), filas);
    }

    // ------------------------------------------------------------------ apoyo

    private static List<String> cabeceraFilas(String primera) {
        return List.of(primera, "Total", "Atendidas", "Canceladas", "No asistió");
    }

    private static List<Object> filaCsv(FilaResumen f) {
        return List.<Object>of(f.nombre(), f.total(), f.atendidas(), f.canceladas(), f.noAsistio());
    }

    /** Fecha desde/hasta por defecto (últimos 30 días) y validación del rango. */
    LocalDate[] normalizar(LocalDate desde, LocalDate hasta) {
        LocalDate h = hasta == null ? fechaHora.hoyEnClinica() : hasta;
        LocalDate d = desde == null ? h.minusDays(DIAS_POR_DEFECTO - 1L) : desde;
        if (d.isAfter(h)) {
            throw new SolicitudInvalidaException("RANGO_INVALIDO", "La fecha inicial no puede ser posterior a la final.");
        }
        if (ChronoUnit.DAYS.between(d, h) + 1 > DIAS_MAXIMOS) {
            throw new SolicitudInvalidaException("RANGO_EXCEDIDO", "El período no puede superar " + DIAS_MAXIMOS + " días.");
        }
        return new LocalDate[]{d, h};
    }

    private static Double porcentaje(long parte, long base) {
        return base == 0 ? null : Math.round(parte * 1000.0 / base) / 10.0;
    }

    /**
     * Acumula filas {@code [id, (nombres, apellidos | nombre), estado, cantidad]} en una fila por entidad.
     */
    private static List<FilaResumen> agrupar(List<Object[]> filas, boolean conApellidos) {
        record Acum(String nombre, long[] n) {
        }
        Map<Long, Acum> mapa = new LinkedHashMap<>();
        for (Object[] f : filas) {
            long id = ((Number) f[0]).longValue();
            String nombre = conApellidos ? f[1] + " " + f[2] : (String) f[1];
            EstadoCita estado = (EstadoCita) f[conApellidos ? 3 : 2];
            long cantidad = ((Number) f[conApellidos ? 4 : 3]).longValue();
            Acum a = mapa.computeIfAbsent(id, k -> new Acum(nombre, new long[4]));
            a.n()[0] += cantidad;
            if (estado == EstadoCita.ATENDIDA) {
                a.n()[1] += cantidad;
            } else if (estado == EstadoCita.CANCELADA) {
                a.n()[2] += cantidad;
            } else if (estado == EstadoCita.NO_ASISTIO) {
                a.n()[3] += cantidad;
            }
        }
        List<FilaResumen> resultado = new ArrayList<>();
        mapa.forEach((id, a) -> resultado.add(new FilaResumen(id, a.nombre(), a.n()[0], a.n()[1], a.n()[2], a.n()[3])));
        resultado.sort(Comparator.comparingLong(FilaResumen::total).reversed().thenComparing(FilaResumen::nombre));
        return resultado;
    }
}
