# Módulos principales (Fase 4)

Todos los endpoints viven bajo `/api/v1` y exigen `Authorization: Bearer <accessToken>`.
Los permisos se validan en el backend (`@PreAuthorize` + reglas en los servicios), no solo en la interfaz.

| Módulo | Endpoint | Lectura | Escritura |
|---|---|---|---|
| Especialidades | `/especialidades` | cualquier usuario autenticado (solo ADMIN ve inactivas) | ADMINISTRADOR |
| Consultorios | `/consultorios` | ADMIN, RECEPCIONISTA, MEDICO | ADMINISTRADOR |
| Pacientes | `/pacientes` | ADMIN/RECEPCIONISTA todos; MEDICO solo pacientes con cita suya (vista reducida); PACIENTE solo el propio | ADMIN/RECEPCIONISTA; PACIENTE edita su contacto en `/pacientes/me`; activar/desactivar solo ADMIN |
| Médicos | `/medicos` | autenticado (no-personal: solo activos y sin datos de contacto) | ADMINISTRADOR |
| Horarios | `/medicos/{id}/horarios` | autenticado | ADMINISTRADOR (reemplazo completo) |
| Bloqueos | `/bloqueos` | ADMIN/RECEPCIONISTA todos; MEDICO los suyos | ADMIN crea aprobados; MEDICO solicita; ADMIN aprueba/rechaza |

## Reglas de negocio implementadas
- **Nada se borra**: especialidades, consultorios, pacientes y médicos se activan/desactivan (`PATCH …/estado`) para conservar el historial.
- **No se puede desactivar** un paciente, médico o consultorio con citas futuras vigentes (código `*_CON_CITAS`), ni un consultorio con médicos activos (`CONSULTORIO_EN_USO`).
- Desactivar un paciente o médico **desactiva también su cuenta de acceso** (y reactivar la reactiva).
- **Documento único** por (tipo, número). Formatos: DNI 8 dígitos; CE 9–12 alfanuméricos; pasaporte 6–12; otro 5–20.
- **Colegiatura única**, normalizada a mayúsculas. Un médico necesita al menos una especialidad activa.
- **No se retira una especialidad** de un médico si ya existe alguna cita con ella (clave foránea compuesta en BD + validación con mensaje claro).
- La cuenta de acceso del médico se crea **solo al registrarlo** (`cuenta`); las credenciales no se editan desde su ficha.
- **Horario semanal**: franjas por día ISO (1 = lunes … 7 = domingo) en hora local de la clínica; descanso opcional dentro de la franja; sin solapamientos; cada franja debe admitir al menos una consulta. Un reemplazo se rechaza si deja fuera citas futuras ya reservadas (`HORARIO_CONFLICTO_CITAS`).
- **Bloqueos**: apuntan a un médico **o** a un consultorio. No pueden cubrir citas vigentes (`BLOQUEO_CON_CITAS`), ni terminar en el pasado. El médico no puede solicitar `MANTENIMIENTO`.
- Fechas de entrada con desfase (`2030-02-01T08:00:00-05:00`) se guardan en UTC y se devuelven en hora de Lima.

## Paginación y filtros
`GET /pacientes?q=&activo=&pagina=0&tamano=20`, `GET /medicos?q=&especialidadId=&activo=`, `GET /bloqueos?medicoId=&consultorioId=&estado=&desde=&hasta=`.
Tamaño máximo de página: 100. Respuesta: `{contenido, pagina, tamano, totalElementos, totalPaginas}`.

## Otros endpoints útiles
- `GET /pacientes/{id}/resumen` (atendidas, canceladas, no asistió, pendientes y próxima cita) y `GET /pacientes/{id}/citas` (historial).
- `GET /medicos/{id}/resumen` y `GET /medicos/{id}/agenda?desde=2030-01-07&hasta=2030-01-13` (máx. 31 días; el médico solo ve la suya).

## Módulos posteriores
- **Citas, disponibilidad y lista de espera** (`/citas`, `/disponibilidad`, `/lista-espera`): ver `citas.md`.
- **Notificaciones** (`/notificaciones`, solo ADMINISTRADOR): ver `notificaciones.md`.
- **Reportes, panel y auditoría** (`/reportes`, `/dashboard/resumen`, `/auditoria`): ver `reportes.md`.
