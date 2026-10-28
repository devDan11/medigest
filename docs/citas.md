# Módulo de citas (Fase 5)

Cubre disponibilidad, reserva, reprogramación, cancelación, ciclo de estados, control de concurrencia y lista de espera.
Todas las rutas están bajo `/api/v1` y requieren `Authorization: Bearer <accessToken>`. Los instantes se reciben y
devuelven con offset (`2030-01-09T09:00:00-05:00`); se almacenan en UTC y se muestran en `America/Lima`.

## Endpoints

| Método | Ruta | Quién | Descripción |
|---|---|---|---|
| GET | `/medicos/{id}/disponibilidad?desde&hasta` | autenticado | Horarios libres por día (máx. 31 días) |
| GET | `/disponibilidad/medicos?especialidadId&desde&hasta` | autenticado | Médicos con horarios libres y su próximo horario (máx. 14 días) |
| GET | `/citas` | según rol | Lista paginada con filtros (paciente solo ve las suyas; médico las suyas) |
| GET | `/citas/{id}` · `/citas/{id}/historial` | dueño o personal | Detalle e historial de eventos |
| POST | `/citas` | admin, recepción, paciente | Reserva (`pacienteId` solo lo indica el personal) |
| PATCH | `/citas/{id}/confirmar` | admin, recepción | PENDIENTE → CONFIRMADA |
| PATCH | `/citas/{id}/reprogramar` | admin, recepción, paciente propio | Cambia el horario conservando historial |
| PATCH | `/citas/{id}/cancelar` | admin, recepción, paciente propio | Requiere motivo |
| PATCH | `/citas/{id}/asistencia` | ver matriz | `LLEGADA`, `INICIAR`, `FINALIZAR`, `NO_ASISTIO` |
| POST/GET | `/lista-espera` | personal / paciente | Registrar y consultar solicitudes (con posición en la cola) |
| GET/DELETE | `/lista-espera/{id}` | dueño o personal | Detalle / retirar (baja lógica → `CANCELADA`) |
| POST | `/lista-espera/{id}/aceptar` · `/rechazar` | dueño o personal | Responder a un horario ofrecido |

La respuesta de una cita incluye `accionesPermitidas`, calculada para el usuario actual (lo usará el frontend).

## Reglas de negocio

- Sin fechas pasadas; el paciente reserva con ≥ 60 min de anticipación y hasta 90 días adelante.
- El inicio debe coincidir con un horario generado por la jornada del médico (respeta descanso y duración de consulta).
- Médico activo, sin bloqueos aprobados y sin otra cita en el intervalo; consultorio libre; paciente sin citas que se crucen.
- Un horario con oferta vigente de lista de espera no se puede reservar por otra persona.
- Cancelar/reprogramar como paciente: ≥ 24 h antes de la cita; máximo 3 reprogramaciones; no si ya está EN_ESPERA.
- Transiciones: PENDIENTE → CONFIRMADA | EN_ESPERA | CANCELADA | NO_ASISTIO; CONFIRMADA → EN_ESPERA | CANCELADA | NO_ASISTIO;
  EN_ESPERA → EN_ATENCION | CANCELADA | NO_ASISTIO; EN_ATENCION → ATENDIDA. ATENDIDA, CANCELADA y NO_ASISTIO son finales.
- Nada se elimina: cada evento (creación, confirmación, reprogramación, cancelación, asistencia) queda en `historial_citas` y en auditoría.

Matriz de asistencia: `LLEGADA` admin/recepción el mismo día; `INICIAR`/`FINALIZAR` admin o el médico de la cita;
`NO_ASISTIO` admin/recepción y solo después de la hora de inicio.

Códigos de error de negocio frecuentes: `FECHA_PASADA`, `ANTICIPACION_INSUFICIENTE`, `FUERA_DE_HORIZONTE`, `FUERA_DE_HORARIO`,
`MEDICO_BLOQUEADO`, `MEDICO_OCUPADO`, `CONSULTORIO_BLOQUEADO`, `CONSULTORIO_OCUPADO`, `PACIENTE_OCUPADO`,
`HORARIO_RESERVADO_TEMPORALMENTE`, `CONFLICTO_CONCURRENCIA`, `OFERTA_NO_VIGENTE`, `OFERTA_VENCIDA`.

## Concurrencia

1. **Bloqueos pesimistas en orden fijo** (cita → médico → solicitud de espera → consultorio → paciente), con timeout de 8 s,
   para evitar interbloqueos. Las comprobaciones de cruce se hacen *después* de bloquear.
2. **Red de seguridad**: índices únicos filtrados en base de datos y `@Version` en `citas`.
3. Un bloqueo no obtenido a tiempo o un choque de versión devuelve **409 `CONFLICTO_CONCURRENCIA`**.
4. Verificación práctica: `scripts/prueba-concurrencia.sh` lanza N reservas simultáneas del mismo horario y exige exactamente una `201`.

## Lista de espera

- Una solicitud vigente (ACTIVA u OFERTADA) por paciente y especialidad; médico opcional, rango de fechas y franja (mañana/tarde/cualquiera).
- Posición = lugar FIFO entre las solicitudes ACTIVAS de la especialidad.
- Cuando se cancela o reprograma una cita se publica `HorarioLiberadoEvent`; tras el commit se ofrece el horario a la primera
  solicitud compatible (médico, fechas, franja, sin cruces del paciente). La oferta dura `ofertaEsperaMin` (30 min).
- Aceptar crea la cita una sola vez (una segunda aceptación → `OFERTA_NO_VIGENTE`). Rechazar o vencer devuelve el horario a la cola
  sin volver a ofrecérselo a la misma solicitud.
- Una tarea programada (cada 60 s) vence las ofertas y las solicitudes cuyo rango ya pasó.

## Configuración (`application.yml`)

```yaml
medigest:
  citas:
    anticipacion-minima-paciente-min: 60
    horizonte-dias: 90
    limite-cancelacion-horas: 24
    max-reprogramaciones-paciente: 3
    oferta-espera-min: 30
  tareas:
    habilitadas: true
    lista-espera-ms: 60000
```

## Limitaciones conocidas

- El aviso al paciente de un horario ofrecido (correo/notificación) llega en la Fase 7; mientras tanto la oferta se ve en `GET /lista-espera`.
- Las observaciones clínicas del médico al finalizar no se guardan (no hay columna); se evaluará con el módulo de historia clínica.
- Registrar una solicitud de espera no se bloquea aunque existan horarios libres.
- La oferta reserva el horario del médico; el consultorio se vuelve a validar al aceptar.
