# Reportes, estadísticas, auditoría y exportaciones

Todas las fechas son **días de la clínica (America/Lima)**, ambas inclusivas; internamente los instantes se guardan en UTC.

## Estadísticas y reportes

| Endpoint | Roles | Descripción |
|---|---|---|
| `GET /api/v1/dashboard/resumen` | cualquier usuario autenticado | Indicadores del panel (globales para el personal; propios para médico y paciente) |
| `GET /api/v1/reportes/citas?desde&hasta&medicoId&especialidadId` | ADMINISTRADOR, RECEPCIONISTA | Total, citas por estado, tasas de asistencia y cancelación, citas por día y por médico |
| `GET /api/v1/reportes/especialidades?desde&hasta` | ADMINISTRADOR, RECEPCIONISTA | Citas por especialidad (total, atendidas, canceladas, no asistió) |
| `GET /api/v1/reportes/citas/exportar?vista=estado\|dia\|medico` | ADMINISTRADOR, RECEPCIONISTA | CSV de la vista elegida |
| `GET /api/v1/reportes/especialidades/exportar` | ADMINISTRADOR, RECEPCIONISTA | CSV por especialidad |

- Sin fechas se usan los **últimos 30 días**. El rango máximo es de 366 días (`RANGO_EXCEDIDO`); `desde > hasta` da `RANGO_INVALIDO`.
- **Tasa de asistencia** = atendidas / (atendidas + no asistió). **Tasa de cancelación** = canceladas / total. Son `null` si no hay base.
- «Citas por día» no cuenta las canceladas y rellena con 0 los días sin citas.

Página web: `/reportes` (gráficos con Chart.js, tablas, botón **Imprimir** con hoja de impresión limpia y descargas CSV).

## Auditoría (solo ADMINISTRADOR)

- `GET /api/v1/auditoria?accion&entidad&email&resultado&desde&hasta&pagina&tamano` (máx. 100 por página).
- `GET /api/v1/auditoria/exportar` — CSV de hasta 10 000 eventos recientes. **La propia exportación queda auditada** (`AUDITORIA_EXPORTADA`).
- Página web: `/auditoria` (Administración → Auditoría).

El registro contiene acción, entidad, recurso, resultado (`EXITO`, `FALLO`, `DENEGADO`), IP y un detalle sin datos sensibles.

## Formato de los CSV

UTF-8 con BOM (Excel respeta las tildes), separador **punto y coma** (el que Excel espera en configuración regional española), saltos CRLF. Los textos que empiezan con `=`, `+`, `-` o `@`
se prefijan con una comilla para evitar **inyección de fórmulas** al abrirlos en una hoja de cálculo.
Si tu Excel usa coma como separador, usa *Datos → Desde texto/CSV* y elige el punto y coma.

## Límites conocidos

- No hay exportación `.xlsx`/PDF nativa: CSV para hojas de cálculo e **Imprimir** (guardar como PDF desde el navegador).
- Los gráficos cargan Chart.js desde un CDN (se resolverá con la CSP en la Fase 8).
- El filtro por médico existe en la API pero no en la pantalla.
