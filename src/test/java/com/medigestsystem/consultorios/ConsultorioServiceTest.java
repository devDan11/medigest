package com.medigestsystem.consultorios;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.medigestsystem.auditoria.AuditoriaService;
import com.medigestsystem.citas.CitaRepository;
import com.medigestsystem.consultorios.dto.ConsultorioRequest;
import com.medigestsystem.consultorios.dto.ConsultorioResponse;
import com.medigestsystem.medicos.MedicoRepository;
import com.medigestsystem.shared.exception.ConflictoException;
import com.medigestsystem.shared.exception.ReglaNegocioException;
import com.medigestsystem.testsupport.Fixtures;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConsultorioServiceTest {

    @Mock ConsultorioRepository consultorioRepository;
    @Mock MedicoRepository medicoRepository;
    @Mock CitaRepository citaRepository;
    @Mock AuditoriaService auditoriaService;

    ConsultorioService servicio;
    Consultorio consultorio;

    @BeforeEach
    void preparar() {
        servicio = new ConsultorioService(consultorioRepository, medicoRepository, citaRepository,
                auditoriaService, Fixtures.fechaHora());
        consultorio = new Consultorio();
        consultorio.setId(3);
        consultorio.setCodigo("C-101");
        consultorio.setActivo(true);
        when(consultorioRepository.findById(3)).thenReturn(Optional.of(consultorio));
        when(consultorioRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void crearNormalizaElCodigoAMayusculas() {
        ConsultorioResponse r = servicio.crear(new ConsultorioRequest("c-202", "Piso 2"));
        assertThat(r.codigo()).isEqualTo("C-202");
    }

    @Test
    void crearRechazaCodigoDuplicado() {
        when(consultorioRepository.existsByCodigoIgnoreCase("C-101")).thenReturn(true);
        assertThatThrownBy(() -> servicio.crear(new ConsultorioRequest("c-101", "Piso 1")))
                .isInstanceOf(ConflictoException.class);
    }

    @Test
    void noSeDesactivaConMedicosActivosAsignados() {
        when(medicoRepository.existsByConsultorioIdAndActivoTrue(3)).thenReturn(true);
        assertThatThrownBy(() -> servicio.cambiarEstado(3, false)).isInstanceOf(ReglaNegocioException.class);
        assertThat(consultorio.isActivo()).isTrue();
    }

    @Test
    void noSeDesactivaConCitasFuturas() {
        when(medicoRepository.existsByConsultorioIdAndActivoTrue(3)).thenReturn(false);
        when(citaRepository.countByConsultorioIdAndInicioUtcAfterAndEstadoNotIn(anyInt(), any(), anyCollection()))
                .thenReturn(1L);
        assertThatThrownBy(() -> servicio.cambiarEstado(3, false)).isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    void sinDependenciasSeDesactiva() {
        when(medicoRepository.existsByConsultorioIdAndActivoTrue(3)).thenReturn(false);
        when(citaRepository.countByConsultorioIdAndInicioUtcAfterAndEstadoNotIn(anyInt(), any(), anyCollection()))
                .thenReturn(0L);
        assertThat(servicio.cambiarEstado(3, false).activo()).isFalse();
    }
}
