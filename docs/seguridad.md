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

Hasta la Fase 7 no hay servidor de correo: con el perfil `dev` el enlace se escribe en el log de la aplicación
(`[DEV] Enlace de recuperación…`). En cualquier otro perfil el token **no** se registra.

## Contraseñas

BCrypt. Política: 8–72 caracteres, al menos una letra y un número.

## Auditoría

Eventos registrados en la tabla `auditoria` (transacción propia, sobreviven a un rollback): `LOGIN_EXITO`,
`LOGIN_FALLIDO`, `CUENTA_BLOQUEADA`, `LOGIN_BLOQUEADO`, `LOGIN_RECHAZADO`, `LOGOUT`, `REGISTRO_PACIENTE`,
`RECUPERACION_SOLICITADA`, `RECUPERACION_RESTABLECIDA`, `CAMBIO_PASSWORD`, `ACCESO_DENEGADO`, `USUARIO_ACTIVADO`,
`USUARIO_DESACTIVADO`, `USUARIO_DESBLOQUEADO`, `ROLES_ACTUALIZADOS`. Nunca contienen contraseñas ni tokens.

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

## Pendiente para fases posteriores

- Cabeceras CSP estrictas y revisión de dependencias (Fase 8).
- Envío real de correos de recuperación (Fase 7).
- Pruebas de integración con SQL Server/Testcontainers de los filtros de seguridad (Fase 8).
