# Bibliotecas de terceros (autoalojadas)

Se sirven desde el propio servidor para poder aplicar una Content-Security-Policy estricta (`script-src 'self'`) y no depender de CDN externos.

| Biblioteca | Versión | Licencia | Origen |
|---|---|---|---|
| Bootstrap | 5.3.3 | MIT | npm `bootstrap` |
| Chart.js | 4.4.4 | MIT | npm `chart.js` |
| FullCalendar (bundle estándar + locale es) | 6.1.15 | MIT | npm `fullcalendar`, `@fullcalendar/core` |

Para actualizarlas: `npm install <paquete>@<versión>` en una carpeta temporal y copiar los archivos de `dist/` (sin las líneas `sourceMappingURL`).
