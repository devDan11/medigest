# Seguridad de MediGest (Fase 3)

## Arquitectura

Dos cadenas de Spring Security independientes (`SecurityConfig`):

| Cadena | Alcance | Autenticación | CSRF | Sesión |
|---|---|---|---|---|
| API (`@Order(1)`) | `/api/**` | JWT Bearer (HS256) | desactivado (no hay cookies) | stateless |
| Web (`@Order(2)`) | resto | formulario de login | activado | HTTP, `HttpOnly`, `SameSite=Lax` (`Secure` en prod) |

La autorización se aplica en el backend con `@PreAuthorize` (método) y reglas por URL; ocultar botones en
la interfaz nunca es la única barrera.

## Roles

`ADMINISTRADOR`, `MEDICO`, `RECEPCIONISTA`, `PACIENTE`. Se expresan como autoridades `ROLE_<NOMBRE>`.

## Tokens JWT

- **Acceso** (15 min): claims `sub` (id de usuario), `email`, `roles`, `tipo=acceso`. Es el único aceptado en `/api/**`.
- **Refresco** (7 días): `tipo=refresco` + huella de la contraseña vigente. Solo sirve en `/api/v1/auth/refresh`.
  Cambiar o restablecer la contraseña invalida todos los refrescos anteriores; desactivar o bloquear la cuenta también.
- Un token de refresco no sirve como acceso y viceversa. Firma alterada, vencido, otro emisor o texto cualquiera → `401`.
- Limitación conocida: un token de **acceso** ya emitido sigue siendo válido hasta que vence (máx. 15 min) aunque
  la cuenta se desactive. Se acepta por ser stateless; reducir `medigest.seguridad.jwt.acceso-minutos` si se requiere más rigor.

Clave de firma: variable de entorno `JWT_SECRET` (mínimo 32 caracteres). Genere una con:

```bash
openssl rand -base64 48
```

Sin `JWT_SECRET`, fuera del perfil `prod` se genera una clave efímera (los tokens mueren al reiniciar);
con perfil `prod` la aplicación **no arranca**.

## Bloqueo por intentos fallidos

5 fallos consecutivos → cuenta bloqueada 15 minutos (`medigest.seguridad.bloqueo.*`). Un login correcto reinicia el
conteo. Durante el bloqueo, ni la clave correcta entra. El administrador puede levantarlo con
`POST /api/v1/usuarios/{id}/desbloquear`. Las respuestas no distinguen entre clave incorrecta, cuenta bloqueada o
desactivada (mismo mensaje y código 401), para no revelar el estado de una cuenta.

## Recuperación de contraseña

1. `POST /api/v1/auth/forgot-password` (o `/recuperar` en la web). Respuesta idéntica exista o no el correo.
2. Se genera un token aleatorio de 256 bits; en base de datos solo se guarda su **hash SHA-256**. Vigencia 30 min, un
   solo uso, un solo enlace vigente por usuario, máximo una solicitud por minuto.
3. `POST /api/v1/auth/reset-password` (o `/restablecer?token=…`). Al terminar se desbloquea la cuenta.

Con `CORREO_HABILITADO=true` el enlace se envía por correo (`CorreoRecuperacionNotificador`) directamente por el canal:
**nunca se guarda en la tabla de notificaciones** ni se escribe en el log, y un fallo de envío no se revela al solicitante.
Con el correo deshabilitado, solo el perfil `dev` escribe el enlace en el log (`[DEV] Enlace de recuperación…`); en otro
perfil el token **no** se registra.

## Contraseñas

BCrypt. Política: 8–72 caracteres, al menos una letra y un número.

## Auditoría

Eventos registrados en la tabla `auditoria` (transacción propia, sobreviven a un rollback): `LOGIN_EXITO`,
`LOGIN_FALLIDO`, `CUENTA_BLOQUEADA`, `LOGIN_BLOQUEADO`, `LOGIN_RECHAZADO`, `LOGOUT`, `REGISTRO_PACIENTE`,
`RECUPERACION_SOLICITADA`, `RECUPERACION_RESTABLECIDA`, `CAMBIO_PASSWORD`, `ACCESO_DENEGADO`, `USUARIO_ACTIVADO`,
`USUARIO_DESACTIVADO`, `USUARIO_DESBLOQUEADO`, `ROLES_ACTUALIZADOS`, los eventos de citas y lista de espera (`CITA_*`, `ESPERA_*`),
`NOTIFICACION_REINTENTADA` y `AUDITORIA_EXPORTADA`. Nunca contienen contraseñas ni tokens. Consulta: `/auditoria` y `GET /api/v1/auditoria` (solo administrador).

## Endpoints de la Fase 3

| Método | Ruta | Acceso |
|---|---|---|
| POST | `/api/v1/auth/login` | público |
| POST | `/api/v1/auth/register` | público (crea paciente) |
| POST | `/api/v1/auth/refresh` | público (requiere token de refresco) |
| POST | `/api/v1/auth/forgot-password` | público |
| POST | `/api/v1/auth/reset-password` | público (requiere token) |
| GET | `/api/v1/auth/me` | autenticado |
| POST | `/api/v1/auth/cambiar-password` | autenticado |
| GET | `/api/v1/usuarios?q=&pagina=&tamano=` | ADMINISTRADOR |
| GET | `/api/v1/usuarios/{id}` | ADMINISTRADOR |
| PATCH | `/api/v1/usuarios/{id}/estado` | ADMINISTRADOR |
| POST | `/api/v1/usuarios/{id}/desbloquear` | ADMINISTRADOR |
| PUT | `/api/v1/usuarios/{id}/roles` | ADMINISTRADOR |

Reglas: un administrador no puede desactivarse ni quitarse el rol a sí mismo, y nunca se puede dejar el sistema sin
administrador activo.

Páginas web: `/login`, `/registro`, `/recuperar`, `/restablecer`, `/inicio`, `POST /logout`.
Swagger UI: público solo con `medigest.seguridad.swagger-publico=true` (perfil `dev`); en otro caso exige ADMINISTRADOR
(y en `prod` está deshabilitado).

## Cómo probar con curl

```bash
# Login (cuentas demo; la clave es el valor de DEMO_PASSWORD)
curl -s -X POST localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"admin@medigest.test","password":"<DEMO_PASSWORD>"}'

TOKEN=<accessToken>
curl -s localhost:8080/api/v1/auth/me -H "Authorization: Bearer $TOKEN"
curl -s localhost:8080/api/v1/usuarios -H "Authorization: Bearer $TOKEN"   # solo ADMINISTRADOR
curl -si localhost:8080/api/v1/usuarios                                    # 401 sin token
```

## Cabeceras de seguridad y CSP (Fase 8)

Todas las respuestas llevan `Content-Security-Policy`, `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`,
`Referrer-Policy: strict-origin-when-cross-origin` y `Permissions-Policy` (sin geolocalización, cámara, micrófono ni pagos).

| Cadena | CSP |
|---|---|
| Web | `default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'; connect-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'` |
| API | `default-src 'none'; frame-ancestors 'none'` |

- Bootstrap, Chart.js y FullCalendar se **autoalojan** en `/vendor` (licencias en `static/vendor/LICENCIAS.md`): sin CDN.
- No hay `<script>` en línea ni manejadores `onclick=`: la interfaz usa atributos `data-*` y archivos de `static/js`.
  `PlantillasSeguridadTest` falla si alguna plantilla incumple esto.
- `'unsafe-inline'` solo se permite en **estilos** (FullCalendar y Chart.js los inyectan); los scripts están restringidos a recursos propios.
- Tras activar HTTPS en producción, añada HSTS en el proxy o en Spring Security y use `server.servlet.session.cookie.secure=true` (ya activo en `prod`).

## Revisión de seguridad (Fase 8)

| Control | Estado |
|---|---|
| Autorización en el backend (`@PreAuthorize` + reglas por URL), no solo en la interfaz | ✔ probado en `SeguridadIT` |
| CSRF en la web; API stateless sin cookies | ✔ |
| Contraseñas con BCrypt; tokens de recuperación solo como hash SHA-256 | ✔ |
| Mismo error para usuario inexistente y clave incorrecta | ✔ probado en `SeguridadIT` |
| Bloqueo por intentos fallidos | ✔ |
| Consultas con parámetros enlazados (JPA/JPQL), sin SQL concatenado | ✔ |
| Escape de salida en plantillas (Thymeleaf `th:text`) | ✔ |
| CSV sin inyección de fórmulas (`=`, `+`, `-`, `@`) | ✔ probado en `CsvTest` |
| Correos sin datos clínicos; enlace de recuperación fuera de la cola y del log | ✔ |
| Secretos fuera del repositorio (`.env`, logs, `_to_delete/` en `.gitignore`) | ✔ |
| Errores sin trazas ni mensajes internos hacia el cliente | ✔ (`GlobalExceptionHandler`, `server.error.*`) |
| Dependencias: revisar periódicamente (`mvn versions:display-dependency-updates`, Dependabot) | recomendado |

Límites conocidos: un token de acceso emitido sigue valiendo hasta vencer (15 min); no hay limitación de tasa por IP en
`/api/v1/auth/login` (sí bloqueo por cuenta); para producción coloque la aplicación tras un proxy con HTTPS y limitación de tasa.
