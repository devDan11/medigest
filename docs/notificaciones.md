# Notificaciones por correo

## Qué se envía

| Tipo | Cuándo | Destinatario |
|---|---|---|
| `CITA_RESERVADA` | al crearse una cita | correo del paciente |
| `CITA_CONFIRMADA` | al confirmarse | correo del paciente |
| `CITA_REPROGRAMADA` | al cambiar fecha u hora | correo del paciente |
| `CITA_CANCELADA` | al cancelarse | correo del paciente |
| `RECORDATORIO_CITA` | `medigest.correo.recordatorio-horas` (24 h) antes del inicio, solo citas PENDIENTE/CONFIRMADA | correo del paciente |
| `OFERTA_LISTA_ESPERA` | al ofrecerse un horario liberado | correo del paciente |
| Recuperación de contraseña | al solicitarla | correo de la cuenta (**no se guarda en la cola**) |

Los correos **no contienen datos clínicos**: ni motivo de consulta, ni diagnóstico, ni observaciones. Solo código de reserva,
fecha, hora (America/Lima), especialidad, profesional, consultorio y un enlace al detalle (que exige iniciar sesión).

## Cómo funciona

1. Los servicios de negocio avisan (`AvisosNegocio`) **después de confirmada** la transacción (`AFTER_COMMIT`). Un fallo al
   notificar nunca deshace ni retrasa la cita.
2. `NotificacionService` guarda la notificación como `PENDIENTE` en la tabla `notificaciones`. La columna
   `clave_idempotencia` (única) evita duplicados: p. ej. `CITA_RESERVADA:10`, `CITA_REPROGRAMADA:10:<inicio>`,
   `RECORDATORIO_CITA:10:<inicio>`.
3. `NotificacionTareas.despachar` (cada 30 s) envía las pendientes. Si falla, reintenta con espera creciente
   (`backoff-minutos × nº de intento`); al llegar a `max-intentos` queda `FALLIDA`.
4. `NotificacionTareas.generarRecordatorios` (cada 10 min) crea los recordatorios de las citas próximas.

Estados: `PENDIENTE` → `ENVIADA` | `FALLIDA` (agotó intentos) | `OMITIDA` (correo deshabilitado).
Una `FALLIDA` u `OMITIDA` puede devolverse a la cola con **Reintentar** (página `/notificaciones` o
`POST /api/v1/notificaciones/{id}/reintentar`).

## Configuración

Por defecto el correo está **deshabilitado** y las notificaciones quedan `OMITIDA` (se ven en `/notificaciones`).

En `.env` (nunca lo subas a GitHub):

```properties
CORREO_HABILITADO=true
CORREO_REMITENTE=MediGest <no-reply@tu-dominio.com>
spring.mail.host=smtp.tu-proveedor.com
spring.mail.port=587
spring.mail.username=usuario
spring.mail.password=clave-o-token-de-aplicacion
```

Con Gmail usa una *contraseña de aplicación*, no la clave de la cuenta. Con `CORREO_HABILITADO=true` y sin
`spring.mail.host` la aplicación no arranca (así se detecta el olvido).

Otros parámetros (`medigest.correo.*`): `max-intentos` (5), `backoff-minutos` (2), `tamano-lote` (20),
`recordatorio-horas` (24). Intervalos: `medigest.tareas.notificaciones-ms` (30000) y `medigest.tareas.recordatorios-ms` (600000).

## API (solo ADMINISTRADOR)

- `GET /api/v1/notificaciones?estado=&tipo=&pagina=&tamano=`
- `POST /api/v1/notificaciones/{id}/reintentar`

## Límites conocidos

- Pensado para **una sola instancia** de la aplicación. Con varias, dos despachadores podrían tomar la misma
  notificación (habría que añadir un bloqueo por fila).
- Los pacientes sin correo registrado no reciben avisos.
- Si el correo estuvo deshabilitado, las `OMITIDA` no se reenvían solas: hay que usar «Reintentar».
