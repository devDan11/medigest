# MediGest — Sistema integral de gestión de citas médicas

Aplicación web y API REST para clínicas: reserva y seguimiento de citas, agenda de médicos, lista de espera con
ofertas automáticas, notificaciones por correo, reportes y auditoría. Zona horaria de la clínica **America/Lima**
(todo se almacena en UTC).

| | |
|---|---|
| Lenguaje / plataforma | Java 21, Spring Boot 4.1 (Spring Framework 7, Spring Security 7, Hibernate 7) |
| Base de datos | SQL Server 2022 (Docker), migraciones con Flyway |
| Web | Thymeleaf, Bootstrap 5, FullCalendar, Chart.js (todo autoalojado, sin CDN) |
| API | REST bajo `/api/v1`, JWT, documentación con springdoc (Swagger UI) |
| Pruebas | JUnit 5, Mockito, Testcontainers (SQL Server real) |

## Funciones

- **Citas**: reservar, confirmar, reprogramar, cancelar, registrar llegada / atención / inasistencia, con historial de cada cambio.
  Sin dobles reservas: la concurrencia se resuelve con bloqueos pesimistas, índices únicos filtrados y versionado (ver `docs/citas.md`).
- **Disponibilidad**: horarios semanales por médico, descansos, bloqueos (vacaciones, mantenimiento), duración de consulta por especialidad y consultorios.
- **Lista de espera**: al liberarse un horario se ofrece al primero de la cola; la oferta vence sola.
- **Notificaciones por correo**: reserva, confirmación, reprogramación, cancelación, recordatorio 24 h antes y oferta de espera, con cola, reintentos y sin datos clínicos (`docs/notificaciones.md`).
- **Reportes**: citas por estado, día, médico y especialidad; tasas de asistencia y cancelación; exportación CSV e impresión (`docs/reportes.md`).
- **Auditoría**: quién hizo qué y cuándo, con filtros y exportación.
- **Cuatro roles**: `ADMINISTRADOR`, `MEDICO`, `RECEPCIONISTA`, `PACIENTE`.

## Requisitos

- JDK 21 y Maven 3.9+
- Docker (SQL Server y pruebas de integración)

## Puesta en marcha

```bash
# 1. Variables de entorno (el .env NO se sube a Git)
cp .env.example .env
#    edite .env: contraseñas de la base, DEMO_PASSWORD y JWT_SECRET (openssl rand -base64 48)

# 2. SQL Server
docker compose up -d

# 3. Base y usuario de la aplicación (una sola vez; ajuste la clave de medigest_app a la de DB_PASSWORD)
docker exec -i medigest-sqlserver /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -P "<MSSQL_SA_PASSWORD>" -C \
  -v APP_PASSWORD="<DB_PASSWORD>" < scripts/create-database.sql

# 4. Aplicación con datos de demostración
SPRING_PROFILES_ACTIVE=dev,demo mvn spring-boot:run
```

Abra <http://localhost:8080> — Flyway crea el esquema y, con el perfil `demo`, se cargan datos ficticios si la base está vacía.

### Cuentas de demostración

Todas usan la contraseña que definió en `DEMO_PASSWORD`.

| Rol | Correo |
|---|---|
| Administrador | `admin@medigest.test` |
| Recepción | `recepcion1@medigest.test`, `recepcion2@medigest.test` |
| Médico | `medico1@medigest.test` … `medico8@medigest.test` |
| Paciente | `paciente1@medigest.test` … `paciente5@medigest.test` |

> El perfil `demo` se niega a ejecutarse junto a `prod`. No cargue datos demo en un entorno real.

### Perfiles

| Perfil | Uso |
|---|---|
| `dev` | Desarrollo: lee `.env`, logs detallados, Swagger público |
| `demo` | Datos ficticios (se combina con `dev`) |
| `test` | Pruebas |
| `prod` | Producción: cookie `Secure`, Swagger deshabilitado, `JWT_SECRET` obligatoria |

## Configuración

| Variable (`.env`) | Descripción |
|---|---|
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` | Conexión a SQL Server |
| `DB_ENCRYPT`, `DB_TRUST_SERVER_CERTIFICATE` | TLS de la conexión (en producción `false` solo con certificado válido) |
| `MSSQL_SA_PASSWORD` | Clave del administrador del contenedor (solo `docker compose`) |
| `JWT_SECRET` | Clave de firma de los tokens (≥ 32 caracteres) |
| `DEMO_PASSWORD` | Clave de las cuentas demo |
| `APP_URL_BASE` | URL pública, para los enlaces de los correos |
| `CORREO_HABILITADO`, `CORREO_REMITENTE`, `spring.mail.*` | Envío real de correo (por defecto desactivado) |

Políticas de citas (`medigest.citas.*`): anticipación mínima, horizonte de reserva, límite de cancelación, reprogramaciones y vigencia de ofertas.

## Pruebas

```bash
mvn clean test      # unitarias (rápidas, sin Docker)
mvn verify          # unitarias + integración con SQL Server en contenedor (necesita Docker)
```

Las pruebas de integración (`*IT`) arrancan la aplicación completa contra SQL Server real: migraciones, seguridad (cabeceras, roles),
flujo completo de una cita, reportes y **reservas simultáneas** del mismo horario (solo una gana, el resto recibe `409`).
En Macs con Apple Silicon el contenedor corre emulado y la primera ejecución tarda varios minutos.

Prueba de concurrencia contra una instancia en marcha:

```bash
ADMIN_PASSWORD='<DEMO_PASSWORD>' ./scripts/prueba-concurrencia.sh
```

## API

- Swagger UI: <http://localhost:8080/swagger-ui.html> (público solo en `dev`; en otros perfiles exige rol administrador; deshabilitado en `prod`).
- Colección Postman: `docs/postman/` (importe la colección y el entorno; escriba su `password` en el entorno).
- Errores en formato *Problem Details* con un campo `codigo` estable (`CONFLICTO_CONCURRENCIA`, `RANGO_INVALIDO`, …).

```bash
curl -s -X POST localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"admin@medigest.test","password":"<DEMO_PASSWORD>"}'
```

## Interfaz web

| Ruta | Quién |
|---|---|
| `/panel`, `/calendario`, `/citas` | todos los roles (cada uno ve lo suyo) |
| `/lista-espera` | personal y pacientes |
| `/perfil` | pacientes (sus datos de contacto) |
| `/pacientes`, `/medicos`, `/bloqueos` | personal; el médico ve sus pacientes y su agenda |
| `/reportes` | administrador y recepción |
| `/especialidades`, `/consultorios`, `/usuarios`, `/notificaciones`, `/auditoria` | administrador |

## Estructura

```
src/main/java/com/medigestsystem/
  auth, security, usuarios         autenticación, JWT, roles, bloqueo por intentos
  pacientes, medicos, especialidades, consultorios, horarios   catálogos y agenda
  disponibilidad, citas, listaespera   motor de reservas y concurrencia
  notificaciones, reportes, dashboard, auditoria   funciones avanzadas
  web                               controladores de páginas y vistas
  shared                            excepciones, DTO comunes, utilidades (fechas, CSV)
src/main/resources/db/migration     Flyway (V1 esquema, V2 datos base, V3 ajustes)
docs/                               documentación por módulo
```

## Documentación

[`docs/modelo-entidad-relacion.md`](docs/modelo-entidad-relacion.md) · [`docs/modulos.md`](docs/modulos.md) ·
[`docs/citas.md`](docs/citas.md) · [`docs/seguridad.md`](docs/seguridad.md) ·
[`docs/notificaciones.md`](docs/notificaciones.md) · [`docs/reportes.md`](docs/reportes.md)

## Seguridad en resumen

Dos cadenas de Spring Security (API stateless con JWT; web con sesión y CSRF), autorización en el backend con `@PreAuthorize`,
BCrypt, bloqueo por intentos fallidos, recuperación de contraseña con token de un solo uso (solo se guarda su hash),
auditoría de eventos, **CSP estricta** (`script-src 'self'`, sin scripts en línea ni CDN) y cabeceras de seguridad.
Detalle y revisión en [`docs/seguridad.md`](docs/seguridad.md). Nunca suba `.env` ni archivos `*.log` al repositorio.

## Limitaciones conocidas

- Las observaciones clínicas de la consulta no se almacenan (solo el motivo de la cita).
- Un token de acceso ya emitido sigue siendo válido hasta que vence (15 min) aunque se desactive la cuenta.
- La cola de notificaciones está pensada para **una sola instancia** de la aplicación.
- La CSP permite estilos en línea porque FullCalendar y Chart.js los inyectan; los scripts sí están restringidos a recursos propios.
- Un paciente que pierde una carrera de reserva ve un mensaje de conflicto genérico.
- No hay exportación `.xlsx`/PDF nativa: CSV (separador `;`, para Excel en español) e impresión desde el navegador.
