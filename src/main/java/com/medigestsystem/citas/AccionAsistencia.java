package com.medigestsystem.citas;

/** Acciones del control de asistencia. */
public enum AccionAsistencia {
    /** La recepcionista registra la llegada del paciente (pasa a EN_ESPERA). */
    LLEGADA,
    /** El médico inicia la atención (pasa a EN_ATENCION). */
    INICIAR,
    /** El médico finaliza la atención (pasa a ATENDIDA). */
    FINALIZAR,
    /** El paciente no se presentó (pasa a NO_ASISTIO). */
    NO_ASISTIO
}
